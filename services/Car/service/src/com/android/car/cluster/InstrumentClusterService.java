/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.car.cluster;

import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.car.CarAppFocusManager;
import android.car.cluster.IInstrumentClusterManagerCallback;
import android.car.cluster.IInstrumentClusterManagerService;
import android.car.cluster.renderer.IInstrumentCluster;
import android.car.cluster.renderer.IInstrumentClusterNavigation;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;

import com.android.car.AppFocusService;
import com.android.car.AppFocusService.FocusOwnershipCallback;
import com.android.car.CarInputService;
import com.android.car.CarInputService.KeyEventListener;
import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;

import java.io.PrintWriter;
import java.util.Objects;

/**
 * Service responsible for interaction with car's instrument cluster.
 *
 * @hide
 */
@SystemApi
public class InstrumentClusterService implements CarServiceBase, FocusOwnershipCallback,
        KeyEventListener {
    private static final String TAG = CarLog.TAG_CLUSTER;
    private static final ContextOwner NO_OWNER = new ContextOwner(0, 0);

    private final Context mContext;
    private final AppFocusService mAppFocusService;
    private final CarInputService mCarInputService;
    /**
     * TODO: (b/121277787) Remove this on master.
     * @deprecated CarInstrumentClusterManager is being deprecated.
     */
    @Deprecated
    private final ClusterManagerService mClusterManagerService = new ClusterManagerService();
    private final Object mSync = new Object();
    @GuardedBy("mSync")
    private ContextOwner mNavContextOwner = NO_OWNER;
    @GuardedBy("mSync")
    private IInstrumentCluster mRendererService;
    // If renderer service crashed / stopped and this class fails to rebind with it immediately,
    // we should wait some time before next attempt. This may happen during APK update for example.
    private DeferredRebinder mDeferredRebinder;
    // Whether {@link android.car.cluster.renderer.InstrumentClusterRendererService} is bound
    // (although not necessarily connected)
    private boolean mRendererBound = false;

    /**
     * Connection to {@link android.car.cluster.renderer.InstrumentClusterRendererService}
     */
    private final ServiceConnection mRendererServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder binder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceConnected, name: " + name + ", binder: " + binder);
            }
            IInstrumentCluster service = IInstrumentCluster.Stub.asInterface(binder);
            ContextOwner navContextOwner;
            synchronized (mSync) {
                mRendererService = service;
                navContextOwner = mNavContextOwner;
            }
            if (navContextOwner != null && service != null) {
                notifyNavContextOwnerChanged(service, navContextOwner);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onServiceDisconnected, name: " + name);
            }
            mContext.unbindService(this);
            mRendererBound = false;

            synchronized (mSync) {
                mRendererService = null;
            }

            if (mDeferredRebinder == null) {
                mDeferredRebinder = new DeferredRebinder();
            }
            mDeferredRebinder.rebind();
        }
    };

    public InstrumentClusterService(Context context, AppFocusService appFocusService,
            CarInputService carInputService) {
        mContext = context;
        mAppFocusService = appFocusService;
        mCarInputService = carInputService;
    }

    @Override
    public void init() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "init");
        }

        mAppFocusService.registerContextOwnerChangedCallback(this /* FocusOwnershipCallback */);
        mCarInputService.setInstrumentClusterKeyListener(this /* KeyEventListener */);
        // TODO(b/124246323) Start earlier once data storage for cluster is clarified
        //  for early boot.
        CarLocalServices.getService(CarUserService.class).runOnUser0Unlock(() -> {
            mRendererBound = bindInstrumentClusterRendererService();
        });
    }

    @Override
    public void release() {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "release");
        }

        mAppFocusService.unregisterContextOwnerChangedCallback(this);
        if (mRendererBound) {
            mContext.unbindService(mRendererServiceConnection);
            mRendererBound = false;
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**" + getClass().getSimpleName() + "**");
        writer.println("bound with renderer: " + mRendererBound);
        writer.println("renderer service: " + mRendererService);
        writer.println("context owner: " + mNavContextOwner);
    }

    @Override
    public void onFocusAcquired(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, true);
    }

    @Override
    public void onFocusAbandoned(int appType, int uid, int pid) {
        changeNavContextOwner(appType, uid, pid, false);
    }

    private void changeNavContextOwner(int appType, int uid, int pid, boolean acquire) {
        if (appType != CarAppFocusManager.APP_FOCUS_TYPE_NAVIGATION) {
            return;
        }

        IInstrumentCluster service;
        ContextOwner requester = new ContextOwner(uid, pid);
        ContextOwner newOwner = acquire ? requester : NO_OWNER;
        synchronized (mSync) {
            if ((acquire && Objects.equals(mNavContextOwner, requester))
                    || (!acquire && !Objects.equals(mNavContextOwner, requester))) {
                // Nothing to do here. Either the same owner is acquiring twice, or someone is
                // abandoning a focus they didn't have.
                Log.w(TAG, "Invalid nav context owner change (acquiring: " + acquire
                        + "), current owner: [" + mNavContextOwner
                        + "], requester: [" + requester + "]");
                return;
            }

            mNavContextOwner = newOwner;
            service = mRendererService;
        }

        if (service != null) {
            notifyNavContextOwnerChanged(service, newOwner);
        }
    }

    private static void notifyNavContextOwnerChanged(IInstrumentCluster service,
            ContextOwner owner) {
        try {
            service.setNavigationContextOwner(owner.uid, owner.pid);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to call setNavigationContextOwner", e);
        }
    }

    private boolean bindInstrumentClusterRendererService() {
        String rendererService = mContext.getString(R.string.instrumentClusterRendererService);
        if (TextUtils.isEmpty(rendererService)) {
            Log.i(TAG, "Instrument cluster renderer was not configured");
            return false;
        }

        Log.d(TAG, "bindInstrumentClusterRendererService, component: " + rendererService);

        Intent intent = new Intent();
        intent.setComponent(ComponentName.unflattenFromString(rendererService));
        return mContext.bindServiceAsUser(intent, mRendererServiceConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.SYSTEM);
    }

    @Nullable
    public IInstrumentClusterNavigation getNavigationService() {
        try {
            IInstrumentCluster service = getInstrumentClusterRendererService();
            return service == null ? null : service.getNavigationService();
        } catch (RemoteException e) {
            Log.e(TAG, "getNavigationServiceBinder" , e);
            return null;
        }
    }

    /**
     * @deprecated {@link android.car.cluster.CarInstrumentClusterManager} is now deprecated.
     */
    @Deprecated
    public IInstrumentClusterManagerService.Stub getManagerService() {
        return mClusterManagerService;
    }

    @Override
    public void onKeyEvent(KeyEvent event) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "InstrumentClusterService#onKeyEvent: " + event);
        }

        IInstrumentCluster service = getInstrumentClusterRendererService();
        if (service != null) {
            try {
                service.onKeyEvent(event);
            } catch (RemoteException e) {
                Log.e(TAG, "onKeyEvent", e);
            }
        }
    }

    private IInstrumentCluster getInstrumentClusterRendererService() {
        IInstrumentCluster service;
        synchronized (mSync) {
            service = mRendererService;
        }
        return service;
    }

    private static class ContextOwner {
        final int uid;
        final int pid;

        ContextOwner(int uid, int pid) {
            this.uid = uid;
            this.pid = pid;
        }

        @Override
        public String toString() {
            return "uid: " + uid + ", pid: " + pid;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ContextOwner that = (ContextOwner) o;
            return uid == that.uid && pid == that.pid;
        }

        @Override
        public int hashCode() {
            return Objects.hash(uid, pid);
        }
    }

    /**
     * TODO: (b/121277787) Remove on master
     * @deprecated CarClusterManager is being deprecated.
     */
    @Deprecated
    private class ClusterManagerService extends IInstrumentClusterManagerService.Stub {
        @Override
        public void startClusterActivity(Intent intent) throws RemoteException {
            // No op.
        }

        @Override
        public void registerCallback(IInstrumentClusterManagerCallback callback)
                throws RemoteException {
            // No op.
        }

        @Override
        public void unregisterCallback(IInstrumentClusterManagerCallback callback)
                throws RemoteException {
            // No op.
        }
    }

    private class DeferredRebinder extends Handler {
        private static final long NEXT_REBIND_ATTEMPT_DELAY_MS = 1000L;
        private static final int NUMBER_OF_ATTEMPTS = 10;

        public void rebind() {
            mRendererBound = bindInstrumentClusterRendererService();

            if (!mRendererBound) {
                removeMessages(0);
                sendMessageDelayed(obtainMessage(0, NUMBER_OF_ATTEMPTS, 0),
                        NEXT_REBIND_ATTEMPT_DELAY_MS);
            }
        }

        @Override
        public void handleMessage(Message msg) {
            mRendererBound = bindInstrumentClusterRendererService();

            if (mRendererBound) {
                Log.w(TAG, "Failed to bound to render service, next attempt in "
                        + NEXT_REBIND_ATTEMPT_DELAY_MS + "ms.");

                int attempts = msg.arg1;
                if (--attempts >= 0) {
                    sendMessageDelayed(obtainMessage(0, attempts, 0), NEXT_REBIND_ATTEMPT_DELAY_MS);
                } else {
                    Log.wtf(TAG, "Failed to rebind with cluster rendering service");
                }
            }
        }
    }
}
