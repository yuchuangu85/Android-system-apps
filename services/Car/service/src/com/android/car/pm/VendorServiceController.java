/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.pm;

import static android.content.Context.BIND_AUTO_CREATE;

import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.res.Resources;
import android.os.Debug;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.R;
import com.android.car.user.CarUserService;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

/**
 * Class that responsible for controlling vendor services that was opted in to be bound/started
 * by the Car Service.
 *
 * <p>Thread-safety note: all code runs in the {@code Handler} provided in the constructor, whenever
 * possible pass {@link #mHandler} when subscribe for callbacks otherwise redirect code to the
 * handler.
 */
class VendorServiceController implements CarUserService.UserCallback {
    private static final boolean DBG = true;

    private static final int MSG_SWITCH_USER = 1;
    private static final int MSG_USER_LOCK_CHANGED = 2;

    private final List<VendorServiceInfo> mVendorServiceInfos = new ArrayList<>();
    private final HashMap<ConnectionKey, VendorServiceConnection> mConnections =
            new HashMap<>();
    private final Context mContext;
    private final UserManager mUserManager;
    private final Handler mHandler;
    private final CarUserManagerHelper mUserManagerHelper;
    private CarUserService mCarUserService;


    VendorServiceController(Context context, Looper looper,
            CarUserManagerHelper userManagerHelper) {
        mContext = context;
        mUserManager = context.getSystemService(UserManager.class);
        mUserManagerHelper = userManagerHelper;
        mHandler = new Handler(looper) {
            @Override
            public void handleMessage(Message msg) {
                VendorServiceController.this.handleMessage(msg);
            }
        };
    }

    private void handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_SWITCH_USER: {
                int userId = msg.arg1;
                doSwitchUser(userId);
                break;
            }
            case MSG_USER_LOCK_CHANGED: {
                int userId = msg.arg1;
                boolean locked = msg.arg2 == 1;
                doUserLockChanged(userId, locked);
                break;
            }
            default:
                Log.e(CarLog.TAG_PACKAGE, "Unexpected message " + msg);
        }
    }

    void init() {
        if (!loadXmlConfiguration()) {
            return;  // Nothing to do
        }

        mCarUserService = CarLocalServices.getService(CarUserService.class);
        mCarUserService.addUserCallback(this);

        startOrBindServicesIfNeeded();
    }

    void release() {
        if (mCarUserService != null) {
            mCarUserService.removeUserCallback(this);
        }

        for (ConnectionKey key : mConnections.keySet()) {
            stopOrUnbindService(key.mVendorServiceInfo, key.mUserHandle);
        }
        mVendorServiceInfos.clear();
        mConnections.clear();
    }

    private void doSwitchUser(int userId) {
        // Stop all services which which do not run under foreground or system user.
        final int fgUser = mUserManagerHelper.getCurrentForegroundUserId();
        if (fgUser != userId) {
            Log.w(CarLog.TAG_PACKAGE, "Received userSwitch event for user " + userId
                    + " while current foreground user is " + fgUser + "."
                    + " Ignore the switch user event.");
            return;
        }

        for (VendorServiceConnection connection : mConnections.values()) {
            final int connectedUserId = connection.mUser.getIdentifier();
            if (connectedUserId != UserHandle.USER_SYSTEM && connectedUserId != userId) {
                connection.stopOrUnbindService();
            }
        }

        if (userId != UserHandle.USER_SYSTEM) {
            startOrBindServicesForUser(UserHandle.of(userId));
        } else {
            Log.e(CarLog.TAG_PACKAGE, "Unexpected to receive switch user event for system user");
        }
    }

    private void doUserLockChanged(int userId, boolean unlocked) {
        final int currentUserId = mUserManagerHelper.getCurrentForegroundUserId();

        if (DBG) {
            Log.i(CarLog.TAG_PACKAGE, "onUserLockedChanged, user: " + userId
                    + ", unlocked: " + unlocked + ", currentUser: " + currentUserId);
        }
        if (unlocked && (userId == currentUserId || userId == UserHandle.USER_SYSTEM)) {
            startOrBindServicesForUser(UserHandle.of(userId));
        } else if (!unlocked && userId != UserHandle.USER_SYSTEM) {
            for (ConnectionKey key : mConnections.keySet()) {
                if (key.mUserHandle.getIdentifier() == userId) {
                    stopOrUnbindService(key.mVendorServiceInfo, key.mUserHandle);
                }
            }
        }
    }

    private void startOrBindServicesForUser(UserHandle user) {
        boolean unlocked = mUserManager.isUserUnlockingOrUnlocked(user);
        boolean systemUser = UserHandle.SYSTEM.equals(user);
        for (VendorServiceInfo service: mVendorServiceInfos) {
            boolean userScopeChecked = (!systemUser && service.isForegroundUserService())
                    || (systemUser && service.isSystemUserService());
            boolean triggerChecked = service.shouldStartAsap()
                    || (unlocked && service.shouldStartOnUnlock());

            if (userScopeChecked && triggerChecked) {
                startOrBindService(service, user);
            }
        }
    }

    private void startOrBindServicesIfNeeded() {
        int userId = mUserManagerHelper.getCurrentForegroundUserId();
        startOrBindServicesForUser(UserHandle.SYSTEM);
        if (userId > 0) {
            startOrBindServicesForUser(UserHandle.of(userId));
        }
    }

    @Override
    public void onUserLockChanged(int userId, boolean unlocked) {
        Message msg = mHandler.obtainMessage(MSG_USER_LOCK_CHANGED, userId, unlocked ? 1 : 0);
        mHandler.executeOrSendMessage(msg);
    }

    @Override
    public void onSwitchUser(int userId) {
        mHandler.removeMessages(MSG_SWITCH_USER);
        Message msg = mHandler.obtainMessage(MSG_SWITCH_USER, userId, 0);
        mHandler.executeOrSendMessage(msg);
    }

    private void startOrBindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = getOrCreateConnection(key);
        if (!connection.startOrBindService()) {
            Log.e(CarLog.TAG_PACKAGE, "Failed to start or bind service " + service);
            mConnections.remove(key);
        }
    }

    private void stopOrUnbindService(VendorServiceInfo service, UserHandle user) {
        ConnectionKey key = ConnectionKey.of(service, user);
        VendorServiceConnection connection = mConnections.get(key);
        if (connection != null) {
            connection.stopOrUnbindService();
        }
    }

    private VendorServiceConnection getOrCreateConnection(ConnectionKey key) {
        VendorServiceConnection connection = mConnections.get(key);
        if (connection == null) {
            connection = new VendorServiceConnection(mContext, mHandler, mUserManagerHelper,
                    key.mVendorServiceInfo, key.mUserHandle);
            mConnections.put(key, connection);
        }

        return connection;
    }

    /** Loads data from XML resources and returns true if any services needs to be started/bound. */
    private boolean loadXmlConfiguration() {
        final Resources res = mContext.getResources();
        for (String rawServiceInfo: res.getStringArray(R.array.config_earlyStartupServices)) {
            if (TextUtils.isEmpty(rawServiceInfo)) {
                continue;
            }
            VendorServiceInfo service = VendorServiceInfo.parse(rawServiceInfo);
            mVendorServiceInfos.add(service);
            if (DBG) {
                Log.i(CarLog.TAG_PACKAGE, "Registered vendor service: " + service);
            }
        }
        Log.i(CarLog.TAG_PACKAGE, "Found " + mVendorServiceInfos.size()
                + " services to be started/bound");

        return !mVendorServiceInfos.isEmpty();
    }

    /**
     * Represents connection to the vendor service.
     */
    private static class VendorServiceConnection implements ServiceConnection {
        private static final int REBIND_DELAY_MS = 1000;
        private static final int MAX_RECENT_FAILURES = 5;
        private static final int FAILURE_COUNTER_RESET_TIMEOUT = 5 * 60 * 1000; // 5 min.
        private static final int MSG_REBIND = 0;
        private static final int MSG_FAILURE_COUNTER_RESET = 1;

        private int mRecentFailures = 0;
        private boolean mBound = false;
        private boolean mStarted = false;
        private boolean mStopRequested = false;
        private final VendorServiceInfo mVendorServiceInfo;
        private final Context mContext;
        private final UserHandle mUser;
        private final Handler mHandler;
        private final Handler mFailureHandler;
        private final CarUserManagerHelper mUserManagerHelper;

        VendorServiceConnection(Context context, Handler handler,
                CarUserManagerHelper userManagerHelper, VendorServiceInfo vendorServiceInfo,
                UserHandle user) {
            mContext = context;
            mHandler = handler;
            mUserManagerHelper = userManagerHelper;
            mVendorServiceInfo = vendorServiceInfo;
            mUser = user;

            mFailureHandler = new Handler(handler.getLooper()) {
                @Override
                public void handleMessage(Message msg) {
                    handleFailureMessage(msg);
                }
            };
        }

        boolean startOrBindService() {
            if (mStarted || mBound) {
                return true;  // Already started or bound
            }

            if (DBG) {
                Log.d(CarLog.TAG_PACKAGE, "startOrBindService " + mVendorServiceInfo.toShortString()
                        + ", as user: " + mUser + ", bind: " + mVendorServiceInfo.shouldBeBound()
                        + ", stack:  " + Debug.getCallers(5));
            }
            mStopRequested = false;

            Intent intent = mVendorServiceInfo.getIntent();
            if (mVendorServiceInfo.shouldBeBound()) {
                return mContext.bindServiceAsUser(intent, this, BIND_AUTO_CREATE, mHandler, mUser);
            } else if (mVendorServiceInfo.shouldBeStartedInForeground()) {
                mStarted = mContext.startForegroundServiceAsUser(intent, mUser) != null;
                return mStarted;
            } else {
                mStarted = mContext.startServiceAsUser(intent, mUser) != null;
                return mStarted;
            }
        }

        void stopOrUnbindService() {
            mStopRequested = true;
            if (mStarted) {
                mContext.stopServiceAsUser(mVendorServiceInfo.getIntent(), mUser);
                mStarted = false;
            } else if (mBound) {
                mContext.unbindService(this);
                mBound = false;
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            mBound = true;
            if (DBG) {
                Log.d(CarLog.TAG_PACKAGE, "onServiceConnected, name: " + name);
            }
            if (mStopRequested) {
                stopOrUnbindService();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            mBound = false;
            if (DBG) {
                Log.d(CarLog.TAG_PACKAGE, "onServiceDisconnected, name: " + name);
            }
            tryToRebind();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            mBound = false;
            tryToRebind();
        }

        private void tryToRebind() {
            if (mStopRequested) {
                return;
            }

            if (UserHandle.of(mUserManagerHelper.getCurrentForegroundUserId()).equals(mUser)
                    || UserHandle.SYSTEM.equals(mUser)) {
                mFailureHandler.sendMessageDelayed(
                        mFailureHandler.obtainMessage(MSG_REBIND), REBIND_DELAY_MS);
                scheduleResetFailureCounter();
            } else {
                Log.w(CarLog.TAG_PACKAGE, "No need to rebind anymore as the user " + mUser
                        + " is no longer in foreground.");
            }
        }

        private void scheduleResetFailureCounter() {
            mFailureHandler.removeMessages(MSG_FAILURE_COUNTER_RESET);
            mFailureHandler.sendMessageDelayed(
                    mFailureHandler.obtainMessage(MSG_FAILURE_COUNTER_RESET),
                    FAILURE_COUNTER_RESET_TIMEOUT);
        }

        private void handleFailureMessage(Message msg) {
            switch (msg.what) {
                case MSG_REBIND: {
                    if (mRecentFailures < MAX_RECENT_FAILURES && !mBound) {
                        Log.i(CarLog.TAG_PACKAGE, "Attempting to rebind to the service "
                                + mVendorServiceInfo.toShortString());
                        ++mRecentFailures;
                        startOrBindService();
                    } else {
                        Log.w(CarLog.TAG_PACKAGE, "Exceeded maximum number of attempts to rebind"
                                + "to the service " + mVendorServiceInfo.toShortString());
                    }
                    break;
                }
                case MSG_FAILURE_COUNTER_RESET:
                    mRecentFailures = 0;
                    break;
                default:
                    Log.e(CarLog.TAG_PACKAGE,
                            "Unexpected message received in failure handler: " + msg.what);
            }
        }
    }

    /** Defines a key in the HashMap to store connection on per user and vendor service basis */
    private static class ConnectionKey {
        private final UserHandle mUserHandle;
        private final VendorServiceInfo mVendorServiceInfo;

        private ConnectionKey(VendorServiceInfo service, UserHandle user) {
            mVendorServiceInfo = service;
            mUserHandle = user;
        }

        static ConnectionKey of(VendorServiceInfo service, UserHandle user) {
            return new ConnectionKey(service, user);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (!(o instanceof ConnectionKey)) {
                return false;
            }
            ConnectionKey that = (ConnectionKey) o;
            return Objects.equals(mUserHandle, that.mUserHandle)
                    && Objects.equals(mVendorServiceInfo, that.mVendorServiceInfo);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUserHandle, mVendorServiceInfo);
        }
    }
}
