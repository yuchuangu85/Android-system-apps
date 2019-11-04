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
package com.android.car;

import static android.car.CarProjectionManager.ProjectionAccessPointCallback.ERROR_GENERIC;
import static android.car.projection.ProjectionStatus.PROJECTION_STATE_INACTIVE;
import static android.car.projection.ProjectionStatus.PROJECTION_STATE_READY_TO_PROJECT;
import static android.net.wifi.WifiManager.EXTRA_PREVIOUS_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_FAILURE_REASON;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_INTERFACE_NAME;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_MODE;
import static android.net.wifi.WifiManager.EXTRA_WIFI_AP_STATE;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_DISABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLED;
import static android.net.wifi.WifiManager.WIFI_AP_STATE_ENABLING;
import static android.net.wifi.WifiManager.WIFI_FREQUENCY_BAND_5GHZ;

import android.annotation.Nullable;
import android.app.ActivityOptions;
import android.bluetooth.BluetoothDevice;
import android.car.CarProjectionManager;
import android.car.CarProjectionManager.ProjectionAccessPointCallback;
import android.car.CarProjectionManager.ProjectionKeyEventHandler;
import android.car.ICarProjection;
import android.car.ICarProjectionKeyEventHandler;
import android.car.ICarProjectionStatusListener;
import android.car.projection.ProjectionOptions;
import android.car.projection.ProjectionStatus;
import android.car.projection.ProjectionStatus.ProjectionState;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.net.wifi.WifiConfiguration;
import android.net.wifi.WifiManager;
import android.net.wifi.WifiManager.LocalOnlyHotspotCallback;
import android.net.wifi.WifiManager.LocalOnlyHotspotReservation;
import android.net.wifi.WifiManager.SoftApCallback;
import android.net.wifi.WifiScanner;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import com.android.car.BinderInterfaceContainer.BinderInterface;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.io.PrintWriter;
import java.lang.ref.WeakReference;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.List;

/**
 * Car projection service allows to bound to projected app to boost it priority.
 * It also enables projected applications to handle voice action requests.
 */
class CarProjectionService extends ICarProjection.Stub implements CarServiceBase,
        BinderInterfaceContainer.BinderEventHandler<ICarProjectionKeyEventHandler>,
        CarProjectionManager.ProjectionKeyEventHandler {
    private static final String TAG = CarLog.TAG_PROJECTION;
    private static final boolean DBG = true;

    private final CarInputService mCarInputService;
    private final CarBluetoothService mCarBluetoothService;
    private final Context mContext;
    private final WifiManager mWifiManager;
    private final Handler mHandler;
    private final Object mLock = new Object();

    @GuardedBy("mLock")
    private final HashMap<IBinder, WirelessClient> mWirelessClients = new HashMap<>();

    @GuardedBy("mLock")
    private @Nullable LocalOnlyHotspotReservation mLocalOnlyHotspotReservation;


    @GuardedBy("mLock")
    private @Nullable ProjectionSoftApCallback mSoftApCallback;

    @GuardedBy("mLock")
    private final HashMap<IBinder, ProjectionReceiverClient> mProjectionReceiverClients =
            new HashMap<>();

    @Nullable
    private String mApBssid;

    @GuardedBy("mLock")
    private @Nullable WifiScanner mWifiScanner;

    @GuardedBy("mLock")
    private @ProjectionState int mCurrentProjectionState = PROJECTION_STATE_INACTIVE;

    @GuardedBy("mLock")
    private ProjectionOptions mProjectionOptions;

    @GuardedBy("mLock")
    private @Nullable String mCurrentProjectionPackage;

    private final BinderInterfaceContainer<ICarProjectionStatusListener>
            mProjectionStatusListeners = new BinderInterfaceContainer<>();

    @GuardedBy("mLock")
    private final ProjectionKeyEventHandlerContainer mKeyEventHandlers;

    private static final int WIFI_MODE_TETHERED = 1;
    private static final int WIFI_MODE_LOCALONLY = 2;

    // Could be one of the WIFI_MODE_* constants.
    // TODO: read this from user settings, support runtime switch
    private int mWifiMode;

    private final ServiceConnection mConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName className, IBinder service) {
                synchronized (mLock) {
                    mBound = true;
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName className) {
                // Service has crashed.
                Log.w(CarLog.TAG_PROJECTION, "Service disconnected: " + className);
                synchronized (mLock) {
                    mRegisteredService = null;
                }
                unbindServiceIfBound();
            }
        };

    private boolean mBound;
    private Intent mRegisteredService;

    CarProjectionService(Context context, @Nullable Handler handler,
            CarInputService carInputService, CarBluetoothService carBluetoothService) {
        mContext = context;
        mHandler = handler == null ? new Handler() : handler;
        mCarInputService = carInputService;
        mCarBluetoothService = carBluetoothService;
        mKeyEventHandlers = new ProjectionKeyEventHandlerContainer(this);
        mWifiManager = context.getSystemService(WifiManager.class);

        final Resources res = mContext.getResources();
        setAccessPointTethering(res.getBoolean(R.bool.config_projectionAccessPointTethering));
    }

    @Override
    public void registerProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(mContext);
        // We assume one active projection app running in the system at one time.
        synchronized (mLock) {
            if (serviceIntent.filterEquals(mRegisteredService) && mBound) {
                return;
            }
            if (mRegisteredService != null) {
                Log.w(CarLog.TAG_PROJECTION, "Registering new service[" + serviceIntent
                        + "] while old service[" + mRegisteredService + "] is still running");
            }
            unbindServiceIfBound();
        }
        bindToService(serviceIntent);
    }

    @Override
    public void unregisterProjectionRunner(Intent serviceIntent) {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            if (!serviceIntent.filterEquals(mRegisteredService)) {
                Log.w(CarLog.TAG_PROJECTION, "Request to unbind unregistered service["
                        + serviceIntent + "]. Registered service[" + mRegisteredService + "]");
                return;
            }
            mRegisteredService = null;
        }
        unbindServiceIfBound();
    }

    private void bindToService(Intent serviceIntent) {
        synchronized (mLock) {
            mRegisteredService = serviceIntent;
        }
        UserHandle userHandle = UserHandle.getUserHandleForUid(Binder.getCallingUid());
        mContext.bindServiceAsUser(serviceIntent, mConnection, Context.BIND_AUTO_CREATE,
                userHandle);
    }

    private void unbindServiceIfBound() {
        synchronized (mLock) {
            if (!mBound) {
                return;
            }
            mBound = false;
            mRegisteredService = null;
        }
        mContext.unbindService(mConnection);
    }

    @Override
    public void registerKeyEventHandler(
            ICarProjectionKeyEventHandler eventHandler, byte[] eventMask) {
        ICarImpl.assertProjectionPermission(mContext);
        BitSet events = BitSet.valueOf(eventMask);
        Preconditions.checkArgument(
                events.length() <= CarProjectionManager.NUM_KEY_EVENTS,
                "Unknown handled event");
        synchronized (mLock) {
            ProjectionKeyEventHandler info = mKeyEventHandlers.get(eventHandler);
            if (info == null) {
                info = new ProjectionKeyEventHandler(mKeyEventHandlers, eventHandler, events);
                mKeyEventHandlers.addBinderInterface(info);
            } else {
                info.setHandledEvents(events);
            }

            updateInputServiceHandlerLocked();
        }
    }

    @Override
    public void unregisterKeyEventHandler(ICarProjectionKeyEventHandler eventHandler) {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            mKeyEventHandlers.removeBinder(eventHandler);
            updateInputServiceHandlerLocked();
        }
    }

    @Override
    public void startProjectionAccessPoint(final Messenger messenger, IBinder binder)
            throws RemoteException {
        ICarImpl.assertProjectionPermission(mContext);
        //TODO: check if access point already started with the desired configuration.
        registerWirelessClient(WirelessClient.of(messenger, binder));
        startAccessPoint();
    }

    @Override
    public void stopProjectionAccessPoint(IBinder token) {
        ICarImpl.assertProjectionPermission(mContext);
        Log.i(TAG, "Received stop access point request from " + token);

        boolean shouldReleaseAp;
        synchronized (mLock) {
            if (!unregisterWirelessClientLocked(token)) {
                Log.w(TAG, "Client " + token + " was not registered");
                return;
            }
            shouldReleaseAp = mWirelessClients.isEmpty();
        }

        if (shouldReleaseAp) {
            stopAccessPoint();
        }
    }

    @Override
    public int[] getAvailableWifiChannels(int band) {
        ICarImpl.assertProjectionPermission(mContext);
        WifiScanner scanner;
        synchronized (mLock) {
            // Lazy initialization
            if (mWifiScanner == null) {
                mWifiScanner = mContext.getSystemService(WifiScanner.class);
            }
            scanner = mWifiScanner;
        }
        if (scanner == null) {
            Log.w(TAG, "Unable to get WifiScanner");
            return new int[0];
        }

        List<Integer> channels = scanner.getAvailableChannels(band);
        if (channels == null || channels.isEmpty()) {
            Log.w(TAG, "WifiScanner reported no available channels");
            return new int[0];
        }

        int[] array = new int[channels.size()];
        for (int i = 0; i < channels.size(); i++) {
            array[i] = channels.get(i);
        }
        return array;
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @param token   A {@link IBinder} to be used as an identity for the request. If the process
     *                owning the token dies, the request will automatically be released.
     * @return True if the profile was successfully inhibited, false if an error occurred.
     */
    @Override
    public boolean requestBluetoothProfileInhibit(
            BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Log.d(TAG, "requestBluetoothProfileInhibit device=" + device + " profile=" + profile
                    + " from uid " + Binder.getCallingUid());
        }
        ICarImpl.assertProjectionPermission(mContext);
        try {
            if (device == null) {
                // Will be caught by AIDL and thrown to caller.
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return mCarBluetoothService.requestProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error in requestBluetoothProfileInhibit", e);
            throw e;
        }
    }

    /**
     * Release an inhibit request made by {@link #requestBluetoothProfileInhibit}, and reconnect the
     * profile if no other inhibit requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @param token   The token provided in the original call to
     *                {@link #requestBluetoothProfileInhibit}.
     * @return True if the request was released, false if an error occurred.
     */
    @Override
    public boolean releaseBluetoothProfileInhibit(
            BluetoothDevice device, int profile, IBinder token) {
        if (DBG) {
            Log.d(TAG, "releaseBluetoothProfileInhibit device=" + device + " profile=" + profile
                    + " from uid " + Binder.getCallingUid());
        }
        ICarImpl.assertProjectionPermission(mContext);
        try {
            if (device == null) {
                // Will be caught by AIDL and thrown to caller.
                throw new NullPointerException("Device must not be null");
            }
            if (token == null) {
                throw new NullPointerException("Token must not be null");
            }
            return mCarBluetoothService.releaseProfileInhibit(device, profile, token);
        } catch (RuntimeException e) {
            Log.e(TAG, "Error in releaseBluetoothProfileInhibit", e);
            throw e;
        }
    }

    @Override
    public void updateProjectionStatus(ProjectionStatus status, IBinder token)
            throws RemoteException {
        if (DBG) {
            Log.d(TAG, "updateProjectionStatus, status: " + status + ", token: " + token);
        }
        ICarImpl.assertProjectionPermission(mContext);
        final String packageName = status.getPackageName();
        final int callingUid = Binder.getCallingUid();
        final int userHandleId = Binder.getCallingUserHandle().getIdentifier();
        final int packageUid;

        try {
            packageUid =
                    mContext.getPackageManager().getPackageUidAsUser(packageName, userHandleId);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException("Package " + packageName + " does not exist", e);
        }

        if (callingUid != packageUid) {
            throw new SecurityException(
                    "UID " + callingUid + " cannot update status for package " + packageName);
        }

        synchronized (mLock) {
            ProjectionReceiverClient client = getOrCreateProjectionReceiverClientLocked(token);
            client.mProjectionStatus = status;

            // If the projection package that's reporting its projection state is the currently
            // active projection package, update the state. If it is a different package, update the
            // current projection state if the new package is reporting that it is projecting or if
            // it is reporting that it's ready to project, and the current package has an inactive
            // projection state.
            if (status.isActive()
                    || (status.getState() == PROJECTION_STATE_READY_TO_PROJECT
                            && mCurrentProjectionState == PROJECTION_STATE_INACTIVE)
                    || TextUtils.equals(packageName, mCurrentProjectionPackage)) {
                mCurrentProjectionState = status.getState();
                mCurrentProjectionPackage = packageName;
            }
        }
        notifyProjectionStatusChanged(null /* notify all listeners */);
    }

    @Override
    public void registerProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(mContext);
        mProjectionStatusListeners.addBinder(listener);

        // Immediately notify listener with the current status.
        notifyProjectionStatusChanged(listener);
    }

    @Override
    public void unregisterProjectionStatusListener(ICarProjectionStatusListener listener)
            throws RemoteException {
        ICarImpl.assertProjectionStatusPermission(mContext);
        mProjectionStatusListeners.removeBinder(listener);
    }

    private ProjectionReceiverClient getOrCreateProjectionReceiverClientLocked(
            IBinder token) throws RemoteException {
        ProjectionReceiverClient client;
        client = mProjectionReceiverClients.get(token);
        if (client == null) {
            client = new ProjectionReceiverClient(() -> unregisterProjectionReceiverClient(token));
            token.linkToDeath(client.mDeathRecipient, 0 /* flags */);
            mProjectionReceiverClients.put(token, client);
        }
        return client;
    }

    private void unregisterProjectionReceiverClient(IBinder token) {
        synchronized (mLock) {
            ProjectionReceiverClient client = mProjectionReceiverClients.remove(token);
            if (client == null) {
                Log.w(TAG, "Projection receiver client for token " + token + " doesn't exist");
                return;
            }
            token.unlinkToDeath(client.mDeathRecipient, 0);
            if (TextUtils.equals(
                    client.mProjectionStatus.getPackageName(), mCurrentProjectionPackage)) {
                mCurrentProjectionPackage = null;
                mCurrentProjectionState = PROJECTION_STATE_INACTIVE;
            }
        }
    }

    private void notifyProjectionStatusChanged(
            @Nullable ICarProjectionStatusListener singleListenerToNotify)
            throws RemoteException {
        int currentState;
        String currentPackage;
        List<ProjectionStatus> statuses = new ArrayList<>();
        synchronized (mLock) {
            for (ProjectionReceiverClient client : mProjectionReceiverClients.values()) {
                statuses.add(client.mProjectionStatus);
            }
            currentState = mCurrentProjectionState;
            currentPackage = mCurrentProjectionPackage;
        }

        if (DBG) {
            Log.d(TAG, "Notify projection status change, state: " + currentState + ", pkg: "
                    + currentPackage + ", listeners: " + mProjectionStatusListeners.size()
                    + ", listenerToNotify: " + singleListenerToNotify);
        }

        if (singleListenerToNotify == null) {
            for (BinderInterface<ICarProjectionStatusListener> listener :
                    mProjectionStatusListeners.getInterfaces()) {
                try {
                    listener.binderInterface.onProjectionStatusChanged(
                            currentState, currentPackage, statuses);
                } catch (RemoteException ex) {
                    Log.e(TAG, "Error calling to projection status listener", ex);
                }
            }
        } else {
            singleListenerToNotify.onProjectionStatusChanged(
                    currentState, currentPackage, statuses);
        }
    }

    @Override
    public Bundle getProjectionOptions() {
        ICarImpl.assertProjectionPermission(mContext);
        synchronized (mLock) {
            if (mProjectionOptions == null) {
                mProjectionOptions = createProjectionOptionsBuilder()
                        .build();
            }
        }
        return mProjectionOptions.toBundle();
    }

    private ProjectionOptions.Builder createProjectionOptionsBuilder() {
        Resources res = mContext.getResources();

        ProjectionOptions.Builder builder = ProjectionOptions.builder();

        ActivityOptions activityOptions = createActivityOptions(res);
        if (activityOptions != null) {
            builder.setProjectionActivityOptions(activityOptions);
        }

        String consentActivity = res.getString(R.string.config_projectionConsentActivity);
        if (!TextUtils.isEmpty(consentActivity)) {
            builder.setConsentActivity(ComponentName.unflattenFromString(consentActivity));
        }

        builder.setUiMode(res.getInteger(R.integer.config_projectionUiMode));
        return builder;
    }

    @Nullable
    private static ActivityOptions createActivityOptions(Resources res) {
        ActivityOptions activityOptions = ActivityOptions.makeBasic();
        boolean changed = false;
        int displayId = res.getInteger(R.integer.config_projectionActivityDisplayId);
        if (displayId != -1) {
            activityOptions.setLaunchDisplayId(displayId);
            changed = true;
        }
        int[] rawBounds = res.getIntArray(R.array.config_projectionActivityLaunchBounds);
        if (rawBounds != null && rawBounds.length == 4) {
            Rect bounds = new Rect(rawBounds[0], rawBounds[1], rawBounds[2], rawBounds[3]);
            activityOptions.setLaunchBounds(bounds);
            changed = true;
        }
        return changed ? activityOptions : null;
    }

    private void startAccessPoint() {
        synchronized (mLock) {
            switch (mWifiMode) {
                case WIFI_MODE_LOCALONLY: {
                    startLocalOnlyApLocked();
                    break;
                }
                case WIFI_MODE_TETHERED: {
                    startTetheredApLocked();
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected Access Point mode during starting: " + mWifiMode);
                    break;
                }
            }
        }
    }

    private void stopAccessPoint() {
        sendApStopped();

        synchronized (mLock) {
            switch (mWifiMode) {
                case WIFI_MODE_LOCALONLY: {
                    stopLocalOnlyApLocked();
                    break;
                }
                case WIFI_MODE_TETHERED: {
                    stopTetheredApLocked();
                    break;
                }
                default: {
                    Log.wtf(TAG, "Unexpected Access Point mode during stopping : " + mWifiMode);
                }
            }
        }
    }

    private void startTetheredApLocked() {
        Log.d(TAG, "startTetheredApLocked");

        if (mSoftApCallback == null) {
            mSoftApCallback = new ProjectionSoftApCallback();
            mWifiManager.registerSoftApCallback(mSoftApCallback, mHandler);
            ensureApConfiguration();
        }

        if (!mWifiManager.startSoftAp(null /* use existing config*/)) {
            // The indicates that AP might be already started.
            if (mWifiManager.getWifiApState() == WIFI_AP_STATE_ENABLED) {
                sendApStarted(mWifiManager.getWifiApConfiguration());
            } else {
                Log.e(TAG, "Failed to start soft AP");
                sendApFailed(ERROR_GENERIC);
            }
        }
    }

    private void stopTetheredApLocked() {
        Log.d(TAG, "stopTetheredAp");

        if (mSoftApCallback != null) {
            mWifiManager.unregisterSoftApCallback(mSoftApCallback);
            mSoftApCallback = null;
            if (!mWifiManager.stopSoftAp()) {
                Log.w(TAG, "Failed to request soft AP to stop.");
            }
        }
    }

    private void startLocalOnlyApLocked() {
        if (mLocalOnlyHotspotReservation != null) {
            Log.i(TAG, "Local-only hotspot is already registered.");
            sendApStarted(mLocalOnlyHotspotReservation.getWifiConfiguration());
            return;
        }

        Log.i(TAG, "Requesting to start local-only hotspot.");
        mWifiManager.startLocalOnlyHotspot(new LocalOnlyHotspotCallback() {
            @Override
            public void onStarted(LocalOnlyHotspotReservation reservation) {
                Log.d(TAG, "Local-only hotspot started");
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = reservation;
                }
                sendApStarted(reservation.getWifiConfiguration());
            }

            @Override
            public void onStopped() {
                Log.i(TAG, "Local-only hotspot stopped.");
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = null;
                }
                sendApStopped();
            }

            @Override
            public void onFailed(int localonlyHostspotFailureReason) {
                Log.w(TAG, "Local-only hotspot failed, reason: "
                        + localonlyHostspotFailureReason);
                synchronized (mLock) {
                    mLocalOnlyHotspotReservation = null;
                }
                int reason;
                switch (localonlyHostspotFailureReason) {
                    case LocalOnlyHotspotCallback.ERROR_NO_CHANNEL:
                        reason = ProjectionAccessPointCallback.ERROR_NO_CHANNEL;
                        break;
                    case LocalOnlyHotspotCallback.ERROR_TETHERING_DISALLOWED:
                        reason = ProjectionAccessPointCallback.ERROR_TETHERING_DISALLOWED;
                        break;
                    case LocalOnlyHotspotCallback.ERROR_INCOMPATIBLE_MODE:
                        reason = ProjectionAccessPointCallback.ERROR_INCOMPATIBLE_MODE;
                        break;
                    default:
                        reason = ERROR_GENERIC;

                }
                sendApFailed(reason);
            }
        }, mHandler);
    }

    private void stopLocalOnlyApLocked() {
        Log.i(TAG, "stopLocalOnlyApLocked");

        if (mLocalOnlyHotspotReservation == null) {
            Log.w(TAG, "Requested to stop local-only hotspot which was already stopped.");
            return;
        }

        mLocalOnlyHotspotReservation.close();
        mLocalOnlyHotspotReservation = null;
    }

    private void sendApStarted(WifiConfiguration wifiConfiguration) {
        WifiConfiguration localWifiConfig = new WifiConfiguration(wifiConfiguration);
        localWifiConfig.BSSID = mApBssid;

        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_STARTED;
        message.obj = localWifiConfig;
        Log.i(TAG, "Sending PROJECTION_AP_STARTED, ssid: "
                + localWifiConfig.getPrintableSsid()
                + ", apBand: " + localWifiConfig.apBand
                + ", apChannel: " + localWifiConfig.apChannel
                + ", bssid: " + localWifiConfig.BSSID);
        sendApStatusMessage(message);
    }

    private void sendApStopped() {
        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_STOPPED;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    private void sendApFailed(int reason) {
        Message message = Message.obtain();
        message.what = CarProjectionManager.PROJECTION_AP_FAILED;
        message.arg1 = reason;
        sendApStatusMessage(message);
        unregisterWirelessClients();
    }

    private void sendApStatusMessage(Message message) {
        List<WirelessClient> clients;
        synchronized (mLock) {
            clients = new ArrayList<>(mWirelessClients.values());
        }
        for (WirelessClient client : clients) {
            client.send(message);
        }
    }

    @Override
    public void init() {
        mContext.registerReceiver(
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final int currState = intent.getIntExtra(EXTRA_WIFI_AP_STATE,
                                WIFI_AP_STATE_DISABLED);
                        final int prevState = intent.getIntExtra(EXTRA_PREVIOUS_WIFI_AP_STATE,
                                WIFI_AP_STATE_DISABLED);
                        final int errorCode = intent.getIntExtra(EXTRA_WIFI_AP_FAILURE_REASON, 0);
                        final String ifaceName =
                                intent.getStringExtra(EXTRA_WIFI_AP_INTERFACE_NAME);
                        final int mode = intent.getIntExtra(EXTRA_WIFI_AP_MODE,
                                WifiManager.IFACE_IP_MODE_UNSPECIFIED);
                        handleWifiApStateChange(currState, prevState, errorCode, ifaceName, mode);
                    }
                },
                new IntentFilter(WifiManager.WIFI_AP_STATE_CHANGED_ACTION));
    }

    private void handleWifiApStateChange(int currState, int prevState, int errorCode,
            String ifaceName, int mode) {
        if (currState == WIFI_AP_STATE_ENABLING || currState == WIFI_AP_STATE_ENABLED) {
            Log.d(TAG,
                    "handleWifiApStateChange, curState: " + currState + ", prevState: " + prevState
                            + ", errorCode: " + errorCode + ", ifaceName: " + ifaceName + ", mode: "
                            + mode);

            try {
                NetworkInterface iface = NetworkInterface.getByName(ifaceName);
                byte[] bssid = iface.getHardwareAddress();
                mApBssid = String.format("%02x:%02x:%02x:%02x:%02x:%02x",
                        bssid[0], bssid[1], bssid[2], bssid[3], bssid[4], bssid[5]);
            } catch (SocketException e) {
                Log.e(TAG, e.toString(), e);
            }
        }
    }

    @Override
    public void release() {
        synchronized (mLock) {
            mKeyEventHandlers.clear();
        }
    }

    @Override
    public void onBinderDeath(
            BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> iface) {
        unregisterKeyEventHandler(iface.binderInterface);
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("**CarProjectionService**");
        synchronized (mLock) {
            writer.println("Registered key event handlers:");
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler>
                    handler : mKeyEventHandlers.getInterfaces()) {
                ProjectionKeyEventHandler
                        projectionKeyEventHandler = (ProjectionKeyEventHandler) handler;
                writer.print("  ");
                writer.println(projectionKeyEventHandler.toString());
            }

            writer.println("Local-only hotspot reservation: " + mLocalOnlyHotspotReservation);
            writer.println("Wireless clients: " +  mWirelessClients.size());
            writer.println("Current wifi mode: " + mWifiMode);
            writer.println("SoftApCallback: " + mSoftApCallback);
            writer.println("Bound to projection app: " + mBound);
            writer.println("Registered Service: " + mRegisteredService);
            writer.println("Current projection state: " + mCurrentProjectionState);
            writer.println("Current projection package: " + mCurrentProjectionPackage);
            writer.println("Projection status: " + mProjectionReceiverClients);
            writer.println("Projection status listeners: "
                    + mProjectionStatusListeners.getInterfaces());
            writer.println("WifiScanner: " + mWifiScanner);
        }
    }

    @Override
    public void onKeyEvent(@CarProjectionManager.KeyEventNum int keyEvent) {
        Log.d(TAG, "Dispatching key event: " + keyEvent);
        synchronized (mLock) {
            for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler>
                    eventHandlerInterface : mKeyEventHandlers.getInterfaces()) {
                ProjectionKeyEventHandler eventHandler =
                        (ProjectionKeyEventHandler) eventHandlerInterface;

                if (eventHandler.canHandleEvent(keyEvent)) {
                    try {
                        // oneway
                        eventHandler.binderInterface.onKeyEvent(keyEvent);
                    } catch (RemoteException e) {
                        Log.e(TAG, "Cannot dispatch event to client", e);
                    }
                }
            }
        }
    }

    @GuardedBy("mLock")
    private void updateInputServiceHandlerLocked() {
        BitSet newEvents = computeHandledEventsLocked();

        if (!newEvents.isEmpty()) {
            mCarInputService.setProjectionKeyEventHandler(this, newEvents);
        } else {
            mCarInputService.setProjectionKeyEventHandler(null, null);
        }
    }

    @GuardedBy("mLock")
    private BitSet computeHandledEventsLocked() {
        BitSet rv = new BitSet();
        for (BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler>
                handlerInterface : mKeyEventHandlers.getInterfaces()) {
            rv.or(((ProjectionKeyEventHandler) handlerInterface).mHandledEvents);
        }
        return rv;
    }

    void setUiMode(Integer uiMode) {
        synchronized (mLock) {
            mProjectionOptions = createProjectionOptionsBuilder()
                    .setUiMode(uiMode)
                    .build();
        }
    }

    void setAccessPointTethering(boolean tetherEnabled) {
        synchronized (mLock) {
            mWifiMode = tetherEnabled ? WIFI_MODE_TETHERED : WIFI_MODE_LOCALONLY;
        }
    }

    private static class ProjectionKeyEventHandlerContainer
            extends BinderInterfaceContainer<ICarProjectionKeyEventHandler> {
        ProjectionKeyEventHandlerContainer(CarProjectionService service) {
            super(service);
        }

        ProjectionKeyEventHandler get(ICarProjectionKeyEventHandler projectionCallback) {
            return (ProjectionKeyEventHandler) getBinderInterface(projectionCallback);
        }
    }

    private static class ProjectionKeyEventHandler extends
            BinderInterfaceContainer.BinderInterface<ICarProjectionKeyEventHandler> {
        private BitSet mHandledEvents;

        private ProjectionKeyEventHandler(
                ProjectionKeyEventHandlerContainer holder,
                ICarProjectionKeyEventHandler binder,
                BitSet handledEvents) {
            super(holder, binder);
            mHandledEvents = handledEvents;
        }

        private boolean canHandleEvent(int event) {
            return mHandledEvents.get(event);
        }

        private void setHandledEvents(BitSet handledEvents) {
            mHandledEvents = handledEvents;
        }

        @Override
        public String toString() {
            return "ProjectionKeyEventHandler{events=" + mHandledEvents + "}";
        }
    }

    private void registerWirelessClient(WirelessClient client) throws RemoteException {
        synchronized (mLock) {
            if (unregisterWirelessClientLocked(client.token)) {
                Log.i(TAG, "Client was already registered, override it.");
            }
            mWirelessClients.put(client.token, client);
        }
        client.token.linkToDeath(new WirelessClientDeathRecipient(this, client), 0);
    }

    private void unregisterWirelessClients() {
        synchronized (mLock) {
            for (WirelessClient client: mWirelessClients.values()) {
                client.token.unlinkToDeath(client.deathRecipient, 0);
            }
            mWirelessClients.clear();
        }
    }

    private boolean unregisterWirelessClientLocked(IBinder token) {
        WirelessClient client = mWirelessClients.remove(token);
        if (client != null) {
            token.unlinkToDeath(client.deathRecipient, 0);
        }

        return client != null;
    }

    private void ensureApConfiguration() {
        // Always prefer 5GHz configuration whenever it is available.
        WifiConfiguration apConfig = mWifiManager.getWifiApConfiguration();
        if (apConfig != null && apConfig.apBand != WIFI_FREQUENCY_BAND_5GHZ
                && mWifiManager.is5GHzBandSupported()) {
            apConfig.apBand = WIFI_FREQUENCY_BAND_5GHZ;
            mWifiManager.setWifiApConfiguration(apConfig);
        }
    }

    private class ProjectionSoftApCallback implements SoftApCallback {
        private boolean mCurrentStateCall = true;

        @Override
        public void onStateChanged(int state, int softApFailureReason) {
            Log.i(TAG, "ProjectionSoftApCallback, onStateChanged, state: " + state
                    + ", failed reason: " + softApFailureReason
                    + ", currentStateCall: " + mCurrentStateCall);
            if (mCurrentStateCall) {
                // When callback gets registered framework always sends the current state as the
                // first call. We should ignore current state call to be in par with
                // local-only behavior.
                mCurrentStateCall = false;
                return;
            }

            switch (state) {
                case WifiManager.WIFI_AP_STATE_ENABLED: {
                    sendApStarted(mWifiManager.getWifiApConfiguration());
                    break;
                }
                case WifiManager.WIFI_AP_STATE_DISABLED: {
                    sendApStopped();
                    break;
                }
                case WifiManager.WIFI_AP_STATE_FAILED: {
                    Log.w(TAG, "WIFI_AP_STATE_FAILED, reason: " + softApFailureReason);
                    int reason;
                    switch (softApFailureReason) {
                        case WifiManager.SAP_START_FAILURE_NO_CHANNEL:
                            reason = ProjectionAccessPointCallback.ERROR_NO_CHANNEL;
                            break;
                        default:
                            reason = ProjectionAccessPointCallback.ERROR_GENERIC;
                    }
                    sendApFailed(reason);
                    break;
                }
            }
        }

        @Override
        public void onNumClientsChanged(int numClients) {
            Log.i(TAG, "ProjectionSoftApCallback, onNumClientsChanged: " + numClients);
        }
    }

    private static class WirelessClient {
        public final Messenger messenger;
        public final IBinder token;
        public @Nullable DeathRecipient deathRecipient;

        private WirelessClient(Messenger messenger, IBinder token) {
            this.messenger = messenger;
            this.token = token;
        }

        private static WirelessClient of(Messenger messenger, IBinder token) {
            return new WirelessClient(messenger, token);
        }

        void send(Message message) {
            try {
                Log.d(TAG, "Sending message " + message.what + " to " + this);
                messenger.send(message);
            } catch (RemoteException e) {
                Log.e(TAG, "Failed to send message", e);
            }
        }

        @Override
        public String toString() {
            return getClass().getSimpleName()
                    + "{token= " + token
                    + ", deathRecipient=" + deathRecipient + "}";
        }
    }

    private static class WirelessClientDeathRecipient implements DeathRecipient {
        final WeakReference<CarProjectionService> mServiceRef;
        final WirelessClient mClient;

        WirelessClientDeathRecipient(CarProjectionService service, WirelessClient client) {
            mServiceRef = new WeakReference<>(service);
            mClient = client;
            mClient.deathRecipient = this;
        }

        @Override
        public void binderDied() {
            Log.w(TAG, "Wireless client " + mClient + " died.");
            CarProjectionService service = mServiceRef.get();
            if (service == null) return;

            synchronized (service.mLock) {
                service.unregisterWirelessClientLocked(mClient.token);
            }
        }
    }

    private static class ProjectionReceiverClient {
        private final DeathRecipient mDeathRecipient;
        private ProjectionStatus mProjectionStatus;

        ProjectionReceiverClient(DeathRecipient deathRecipient) {
            mDeathRecipient = deathRecipient;
        }

        @Override
        public String toString() {
            return "ProjectionReceiverClient{"
                    + "mDeathRecipient=" + mDeathRecipient
                    + ", mProjectionStatus=" + mProjectionStatus
                    + '}';
        }
    }
}
