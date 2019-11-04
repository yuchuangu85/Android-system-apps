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

package com.android.car.vms;

import android.car.Car;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArrayMap;
import android.util.Log;

import com.android.car.CarServiceBase;
import com.android.car.R;
import com.android.car.hal.VmsHalService;
import com.android.car.user.CarUserService;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Manages service connections lifecycle for VMS publisher clients.
 *
 * Binds to system-level clients at boot and creates/destroys bindings for userspace clients
 * according to the Android user lifecycle.
 */
public class VmsClientManager implements CarServiceBase {
    private static final boolean DBG = true;
    private static final String TAG = "VmsClientManager";
    private static final String HAL_CLIENT_NAME = "VmsHalClient";

    /**
     * Interface for receiving updates about client connections.
     */
    public interface ConnectionListener {
        /**
         * Called when a client connection is established or re-established.
         *
         * @param clientName    String that uniquely identifies the service and user.
         * @param clientService The IBinder of the client's communication channel.
         */
        void onClientConnected(String clientName, IBinder clientService);

        /**
         * Called when a client connection is terminated.
         *
         * @param clientName String that uniquely identifies the service and user.
         */
        void onClientDisconnected(String clientName);
    }

    private final Context mContext;
    private final Handler mHandler;
    private final UserManager mUserManager;
    private final CarUserService mUserService;
    private final CarUserManagerHelper mUserManagerHelper;
    private final int mMillisBeforeRebind;

    @GuardedBy("mListeners")
    private final ArrayList<ConnectionListener> mListeners = new ArrayList<>();
    @GuardedBy("mSystemClients")
    private final Map<String, ClientConnection> mSystemClients = new ArrayMap<>();
    @GuardedBy("mSystemClients")
    private IBinder mHalClient;
    @GuardedBy("mSystemClients")
    private boolean mSystemUserUnlocked;

    @GuardedBy("mCurrentUserClients")
    private final Map<String, ClientConnection> mCurrentUserClients = new ArrayMap<>();
    @GuardedBy("mCurrentUserClients")
    private int mCurrentUser;

    @GuardedBy("mRebindCounts")
    private final Map<String, AtomicLong> mRebindCounts = new ArrayMap<>();

    @VisibleForTesting
    final Runnable mSystemUserUnlockedListener = () -> {
        synchronized (mSystemClients) {
            mSystemUserUnlocked = true;
        }
        bindToSystemClients();
    };

    @VisibleForTesting
    final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (DBG) Log.d(TAG, "Received " + intent);
            synchronized (mCurrentUserClients) {
                int currentUserId = mUserManagerHelper.getCurrentForegroundUserId();
                if (mCurrentUser != currentUserId) {
                    terminate(mCurrentUserClients);
                }
                mCurrentUser = currentUserId;

                if (Intent.ACTION_USER_UNLOCKED.equals(intent.getAction())
                        || mUserManager.isUserUnlocked(mCurrentUser)) {
                    bindToSystemClients();
                    bindToUserClients();
                }
            }
        }
    };

    /**
     * Constructor for client managers.
     *
     * @param context           Context to use for registering receivers and binding services.
     * @param userService       User service for registering system unlock listener.
     * @param userManagerHelper User manager for querying current user state.
     * @param halService        Service providing the HAL client interface
     */
    public VmsClientManager(Context context, CarUserService userService,
            CarUserManagerHelper userManagerHelper, VmsHalService halService) {
        mContext = context;
        mHandler = new Handler(Looper.getMainLooper());
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mUserService = userService;
        mUserManagerHelper = userManagerHelper;
        mMillisBeforeRebind = mContext.getResources().getInteger(
                com.android.car.R.integer.millisecondsBeforeRebindToVmsPublisher);
        halService.setPublisherConnectionCallbacks(this::onHalConnected, this::onHalDisconnected);
    }

    @Override
    public void init() {
        mUserService.runOnUser0Unlock(mSystemUserUnlockedListener);

        IntentFilter userSwitchFilter = new IntentFilter();
        userSwitchFilter.addAction(Intent.ACTION_USER_SWITCHED);
        userSwitchFilter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverAsUser(mUserSwitchReceiver, UserHandle.ALL, userSwitchFilter, null,
                null);
    }

    @Override
    public void release() {
        mContext.unregisterReceiver(mUserSwitchReceiver);
        notifyListenersOnClientDisconnected(HAL_CLIENT_NAME);
        synchronized (mSystemClients) {
            terminate(mSystemClients);
        }
        synchronized (mCurrentUserClients) {
            terminate(mCurrentUserClients);
        }
    }

    @Override
    public void dump(PrintWriter writer) {
        dumpMetrics(writer);
    }

    @Override
    public void dumpMetrics(PrintWriter writer) {
        writer.println("*" + getClass().getSimpleName() + "*");
        synchronized (mSystemClients) {
            writer.println("mHalClient: " + (mHalClient != null ? "connected" : "disconnected"));
            writer.println("mSystemClients:");
            dumpConnections(writer, mSystemClients);
        }
        synchronized (mCurrentUserClients) {
            writer.println("mCurrentUserClients:");
            dumpConnections(writer, mCurrentUserClients);
            writer.println("mCurrentUser:" + mCurrentUser);
        }
        synchronized (mRebindCounts) {
            writer.println("mRebindCounts:");
            for (Map.Entry<String, AtomicLong> entry : mRebindCounts.entrySet()) {
                writer.printf("\t%s: %s\n", entry.getKey(), entry.getValue());
            }
        }
    }

    private void dumpConnections(PrintWriter writer, Map<String, ClientConnection> connectionMap) {
        for (ClientConnection connection : connectionMap.values()) {
            writer.printf("\t%s: %s\n",
                    connection.mName.getPackageName(),
                    connection.mIsBound ? "connected" : "disconnected");
        }
    }

    /**
     * Registers a new client connection state listener.
     *
     * @param listener Listener to register.
     */
    public void registerConnectionListener(ConnectionListener listener) {
        synchronized (mListeners) {
            if (!mListeners.contains(listener)) {
                mListeners.add(listener);
            }
        }
        notifyListenerOfConnectedClients(listener);
    }

    /**
     * Unregisters a client connection state listener.
     *
     * @param listener Listener to remove.
     */
    public void unregisterConnectionListener(ConnectionListener listener) {
        synchronized (mListeners) {
            mListeners.remove(listener);
        }
    }

    private void bindToSystemClients() {
        String[] clientNames = mContext.getResources().getStringArray(
                R.array.vmsPublisherSystemClients);
        Log.i(TAG, "Attempting to bind " + clientNames.length + " system client(s)");
        synchronized (mSystemClients) {
            if (!mSystemUserUnlocked) {
                return;
            }
            for (String clientName : clientNames) {
                bind(mSystemClients, clientName, UserHandle.SYSTEM);
            }
        }
    }

    private void bindToUserClients() {
        synchronized (mCurrentUserClients) {
            // To avoid the risk of double-binding, clients running as the system user must only
            // ever be bound in bindToSystemClients().
            // In a headless multi-user system, the system user will never be in the foreground.
            if (mCurrentUser == UserHandle.USER_SYSTEM) {
                Log.e(TAG, "System user in foreground. Userspace clients will not be bound.");
                return;
            }

            String[] clientNames = mContext.getResources().getStringArray(
                    R.array.vmsPublisherUserClients);
            Log.i(TAG, "Attempting to bind " + clientNames.length + " user client(s)");
            UserHandle currentUserHandle = UserHandle.of(mCurrentUser);
            for (String clientName : clientNames) {
                bind(mCurrentUserClients, clientName, currentUserHandle);
            }
        }
    }

    private void bind(Map<String, ClientConnection> connectionMap, String clientName,
            UserHandle userHandle) {
        if (connectionMap.containsKey(clientName)) {
            Log.i(TAG, "Already bound: " + clientName);
            return;
        }

        ComponentName name = ComponentName.unflattenFromString(clientName);
        if (name == null) {
            Log.e(TAG, "Invalid client name: " + clientName);
            return;
        }

        ServiceInfo serviceInfo;
        try {
            serviceInfo = mContext.getPackageManager().getServiceInfo(name,
                    PackageManager.MATCH_DIRECT_BOOT_AUTO);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, "Client not installed: " + clientName);
            return;
        }

        if (!Car.PERMISSION_BIND_VMS_CLIENT.equals(serviceInfo.permission)) {
            Log.w(TAG, "Client service: " + clientName
                    + " does not require " + Car.PERMISSION_BIND_VMS_CLIENT + " permission");
            return;
        }

        ClientConnection connection = new ClientConnection(name, userHandle);
        if (connection.bind()) {
            Log.i(TAG, "Client bound: " + connection);
            connectionMap.put(clientName, connection);
        } else {
            Log.w(TAG, "Binding failed: " + connection);
        }
    }

    private void terminate(Map<String, ClientConnection> connectionMap) {
        connectionMap.values().forEach(ClientConnection::terminate);
        connectionMap.clear();
    }

    private void notifyListenerOfConnectedClients(ConnectionListener listener) {
        synchronized (mSystemClients) {
            if (mHalClient != null) {
                listener.onClientConnected(HAL_CLIENT_NAME, mHalClient);
            }
            mSystemClients.values().forEach(conn -> conn.notifyIfConnected(listener));
        }
        synchronized (mCurrentUserClients) {
            mCurrentUserClients.values().forEach(conn -> conn.notifyIfConnected(listener));
        }
    }

    private void notifyListenersOnClientConnected(String clientName, IBinder clientService) {
        synchronized (mListeners) {
            for (ConnectionListener listener : mListeners) {
                listener.onClientConnected(clientName, clientService);
            }
        }
    }

    private void notifyListenersOnClientDisconnected(String clientName) {
        synchronized (mListeners) {
            for (ConnectionListener listener : mListeners) {
                listener.onClientDisconnected(clientName);
            }
        }
    }

    private void onHalConnected(IBinder halClient) {
        synchronized (mSystemClients) {
            mHalClient = halClient;
            notifyListenersOnClientConnected(HAL_CLIENT_NAME, mHalClient);
        }
    }

    private void onHalDisconnected() {
        synchronized (mSystemClients) {
            mHalClient = null;
            notifyListenersOnClientDisconnected(HAL_CLIENT_NAME);
        }
        synchronized (mRebindCounts) {
            mRebindCounts.computeIfAbsent(HAL_CLIENT_NAME, k -> new AtomicLong()).incrementAndGet();
        }
    }

    class ClientConnection implements ServiceConnection {
        private final ComponentName mName;
        private final UserHandle mUser;
        private final String mFullName;
        private boolean mIsBound = false;
        private boolean mIsTerminated = false;
        private IBinder mClientService;

        ClientConnection(ComponentName name, UserHandle user) {
            mName = name;
            mUser = user;
            mFullName = mName.flattenToString() + " U=" + mUser.getIdentifier();
        }

        synchronized boolean bind() {
            if (mIsBound) {
                return true;
            }
            if (mIsTerminated) {
                return false;
            }

            if (DBG) Log.d(TAG, "binding: " + mFullName);
            Intent intent = new Intent();
            intent.setComponent(mName);
            try {
                mIsBound = mContext.bindServiceAsUser(intent, this, Context.BIND_AUTO_CREATE,
                        mHandler, mUser);
            } catch (SecurityException e) {
                Log.e(TAG, "While binding " + mFullName, e);
            }

            return mIsBound;
        }

        synchronized void unbind() {
            if (!mIsBound) {
                return;
            }

            if (DBG) Log.d(TAG, "unbinding: " + mFullName);
            try {
                mContext.unbindService(this);
            } catch (Throwable t) {
                Log.e(TAG, "While unbinding " + mFullName, t);
            }
            mIsBound = false;
            if (mClientService != null) {
                notifyListenersOnClientDisconnected(mFullName);
            }
            mClientService = null;
        }

        synchronized void rebind() {
            unbind();
            if (DBG) {
                Log.d(TAG,
                        String.format("rebinding %s after %dms", mFullName, mMillisBeforeRebind));
            }
            if (!mIsTerminated) {
                mHandler.postDelayed(this::bind, mMillisBeforeRebind);
                synchronized (mRebindCounts) {
                    mRebindCounts.computeIfAbsent(mName.getPackageName(), k -> new AtomicLong())
                            .incrementAndGet();
                }
            }
        }

        synchronized void terminate() {
            if (DBG) Log.d(TAG, "terminating: " + mFullName);
            mIsTerminated = true;
            unbind();
        }

        synchronized void notifyIfConnected(ConnectionListener listener) {
            if (mClientService != null) {
                listener.onClientConnected(mFullName, mClientService);
            }
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            if (DBG) Log.d(TAG, "onServiceConnected: " + mFullName);
            mClientService = service;
            notifyListenersOnClientConnected(mFullName, mClientService);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            if (DBG) Log.d(TAG, "onServiceDisconnected: " + mFullName);
            rebind();
        }

        @Override
        public String toString() {
            return mFullName;
        }
    }
}
