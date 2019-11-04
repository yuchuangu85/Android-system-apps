/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car;

import android.annotation.CallbackExecutor;
import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.bluetooth.BluetoothDevice;
import android.car.projection.ProjectionOptions;
import android.car.projection.ProjectionStatus;
import android.car.projection.ProjectionStatus.ProjectionState;
import android.content.Intent;
import android.net.wifi.WifiConfiguration;
import android.os.Binder;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.os.Messenger;
import android.os.RemoteException;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.util.Preconditions;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

/**
 * CarProjectionManager allows applications implementing projection to register/unregister itself
 * with projection manager, listen for voice notification.
 *
 * A client must have {@link Car#PERMISSION_CAR_PROJECTION} permission in order to access this
 * manager.
 *
 * @hide
 */
@SystemApi
public final class CarProjectionManager implements CarManagerBase {
    private static final String TAG = CarProjectionManager.class.getSimpleName();

    private final Binder mToken = new Binder();
    private final Object mLock = new Object();

    /**
     * Listener to get projected notifications.
     *
     * Currently only voice search request is supported.
     */
    public interface CarProjectionListener {
        /**
         * Voice search was requested by the user.
         */
        void onVoiceAssistantRequest(boolean fromLongPress);
    }

    /**
     * Interface for projection apps to receive and handle key events from the system.
     */
    public interface ProjectionKeyEventHandler {
        /**
         * Called when a projection key event occurs.
         *
         * @param event The projection key event that occurred.
         */
        void onKeyEvent(@KeyEventNum int event);
    }
    /**
     * Flag for {@link #registerProjectionListener(CarProjectionListener, int)}: subscribe to
     * voice-search short-press requests.
     *
     * @deprecated Use {@link #addKeyEventHandler(Set, ProjectionKeyEventHandler)} with the
     * {@link #KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP} event instead.
     */
    @Deprecated
    public static final int PROJECTION_VOICE_SEARCH = 0x1;
    /**
     * Flag for {@link #registerProjectionListener(CarProjectionListener, int)}: subscribe to
     * voice-search long-press requests.
     *
     * @deprecated Use {@link #addKeyEventHandler(Set, ProjectionKeyEventHandler)} with the
     * {@link #KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN} event instead.
     */
    @Deprecated
    public static final int PROJECTION_LONG_PRESS_VOICE_SEARCH = 0x2;

    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_VOICE_ASSIST}
     * key is pressed down.
     *
     * If the key is released before the long-press timeout,
     * {@link #KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP} will be fired. If the key is held past the
     * long-press timeout, {@link #KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN} will be fired,
     * followed by {@link #KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP}.
     */
    public static final int KEY_EVENT_VOICE_SEARCH_KEY_DOWN = 0;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_VOICE_ASSIST}
     * key is released after a short-press.
     */
    public static final int KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP = 1;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_VOICE_ASSIST}
     * key is held down past the long-press timeout.
     */
    public static final int KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN = 2;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_VOICE_ASSIST}
     * key is released after a long-press.
     */
    public static final int KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP = 3;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_CALL} key is
     * pressed down.
     *
     * If the key is released before the long-press timeout,
     * {@link #KEY_EVENT_CALL_SHORT_PRESS_KEY_UP} will be fired. If the key is held past the
     * long-press timeout, {@link #KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN} will be fired, followed by
     * {@link #KEY_EVENT_CALL_LONG_PRESS_KEY_UP}.
     */
    public static final int KEY_EVENT_CALL_KEY_DOWN = 4;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_CALL} key is
     * released after a short-press.
     */
    public static final int KEY_EVENT_CALL_SHORT_PRESS_KEY_UP = 5;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_CALL} key is
     * held down past the long-press timeout.
     */
    public static final int KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN = 6;
    /**
     * Event for {@link #addKeyEventHandler}: fired when the {@link KeyEvent#KEYCODE_CALL} key is
     * released after a long-press.
     */
    public static final int KEY_EVENT_CALL_LONG_PRESS_KEY_UP = 7;

    /** @hide */
    public static final int NUM_KEY_EVENTS = 8;

    /** @hide */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef(prefix = "KEY_EVENT_", value = {
            KEY_EVENT_VOICE_SEARCH_KEY_DOWN,
            KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP,
            KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN,
            KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_UP,
            KEY_EVENT_CALL_KEY_DOWN,
            KEY_EVENT_CALL_SHORT_PRESS_KEY_UP,
            KEY_EVENT_CALL_LONG_PRESS_KEY_DOWN,
            KEY_EVENT_CALL_LONG_PRESS_KEY_UP,
    })
    @Target({ElementType.TYPE_USE})
    public @interface KeyEventNum {}

    /** @hide */
    public static final int PROJECTION_AP_STARTED = 0;
    /** @hide */
    public static final int PROJECTION_AP_STOPPED = 1;
    /** @hide */
    public static final int PROJECTION_AP_FAILED = 2;

    private final ICarProjection mService;
    private final Handler mHandler;
    private final Executor mHandlerExecutor;

    @GuardedBy("mLock")
    private CarProjectionListener mListener;
    @GuardedBy("mLock")
    private int mVoiceSearchFilter;
    private final ProjectionKeyEventHandler mLegacyListenerTranslator =
            this::translateKeyEventToLegacyListener;

    private final ICarProjectionKeyEventHandlerImpl mBinderHandler =
            new ICarProjectionKeyEventHandlerImpl(this);

    @GuardedBy("mLock")
    private final Map<ProjectionKeyEventHandler, KeyEventHandlerRecord> mKeyEventHandlers =
            new HashMap<>();
    @GuardedBy("mLock")
    private BitSet mHandledEvents = new BitSet();

    private ProjectionAccessPointCallbackProxy mProjectionAccessPointCallbackProxy;

    private final Set<ProjectionStatusListener> mProjectionStatusListeners = new LinkedHashSet<>();
    private CarProjectionStatusListenerImpl mCarProjectionStatusListener;

    // Only one access point proxy object per process.
    private static final IBinder mAccessPointProxyToken = new Binder();

    /**
     * Interface to receive for projection status updates.
     */
    public interface ProjectionStatusListener {
        /**
         * This method gets invoked if projection status has been changed.
         *
         * @param state - current projection state
         * @param packageName - if projection is currently running either in the foreground or
         *                      in the background this argument will contain its package name
         * @param details - contains detailed information about all currently registered projection
         *                  receivers.
         */
        void onProjectionStatusChanged(@ProjectionState int state, @Nullable String packageName,
                @NonNull List<ProjectionStatus> details);
    }

    /**
     * @hide
     */
    public CarProjectionManager(IBinder service, Handler handler) {
        mService = ICarProjection.Stub.asInterface(service);
        mHandler = handler;
        mHandlerExecutor = handler::post;
    }

    /**
     * Compatibility with previous APIs due to typo
     * @hide
     */
    public void regsiterProjectionListener(CarProjectionListener listener, int voiceSearchFilter) {
        registerProjectionListener(listener, voiceSearchFilter);
    }

    /**
     * Register listener to monitor projection. Only one listener can be registered and
     * registering multiple times will lead into only the last listener to be active.
     * @param listener
     * @param voiceSearchFilter Flags of voice search requests to get notification.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void registerProjectionListener(@NonNull CarProjectionListener listener,
            int voiceSearchFilter) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        synchronized (mLock) {
            if (mListener == null || mVoiceSearchFilter != voiceSearchFilter) {
                addKeyEventHandler(
                        translateVoiceSearchFilter(voiceSearchFilter),
                        mLegacyListenerTranslator);
            }
            mListener = listener;
            mVoiceSearchFilter = voiceSearchFilter;
        }
    }

    /**
     * Compatibility with previous APIs due to typo
     * @hide
     */
    public void unregsiterProjectionListener() {
       unregisterProjectionListener();
    }

    /**
     * Unregister listener and stop listening projection events.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void unregisterProjectionListener() {
        synchronized (mLock) {
            removeKeyEventHandler(mLegacyListenerTranslator);
            mListener = null;
            mVoiceSearchFilter = 0;
        }
    }

    @SuppressWarnings("deprecation")
    private static Set<Integer> translateVoiceSearchFilter(int voiceSearchFilter) {
        Set<Integer> rv = new ArraySet<>(Integer.bitCount(voiceSearchFilter));
        int i = 0;
        if ((voiceSearchFilter & PROJECTION_VOICE_SEARCH) != 0) {
            rv.add(KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP);
        }
        if ((voiceSearchFilter & PROJECTION_LONG_PRESS_VOICE_SEARCH) != 0) {
            rv.add(KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN);
        }
        return rv;
    }

    private void translateKeyEventToLegacyListener(@KeyEventNum int keyEvent) {
        CarProjectionListener legacyListener;
        boolean fromLongPress;

        synchronized (mLock) {
            if (mListener == null) {
                return;
            }
            legacyListener = mListener;

            if (keyEvent == KEY_EVENT_VOICE_SEARCH_SHORT_PRESS_KEY_UP) {
                fromLongPress = false;
            } else if (keyEvent == KEY_EVENT_VOICE_SEARCH_LONG_PRESS_KEY_DOWN) {
                fromLongPress = true;
            } else {
                Log.e(TAG, "Unexpected key event " + keyEvent);
                return;
            }
        }

        Log.d(TAG, "Voice assistant request, long-press = " + fromLongPress);

        legacyListener.onVoiceAssistantRequest(fromLongPress);
    }

    /**
     * Adds a {@link ProjectionKeyEventHandler} to be called for the given set of key events.
     *
     * If the given event handler is already registered, the event set and {@link Executor} for that
     * event handler will be replaced with those provided.
     *
     * For any event with a defined event handler, the system will suppress its default behavior for
     * that event, and call the event handler instead. (For instance, if an event handler is defined
     * for {@link #KEY_EVENT_CALL_SHORT_PRESS_KEY_UP}, the system will not open the dialer when the
     * {@link KeyEvent#KEYCODE_CALL CALL} key is short-pressed.)
     *
     * Callbacks on the event handler will be run on the {@link Handler} designated to run callbacks
     * from {@link Car}.
     *
     * @param events        The set of key events to which to subscribe.
     * @param eventHandler  The {@link ProjectionKeyEventHandler} to call when those events occur.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void addKeyEventHandler(
            @NonNull Set<@KeyEventNum Integer> events,
            @NonNull ProjectionKeyEventHandler eventHandler) {
        addKeyEventHandler(events, null, eventHandler);
    }

    /**
     * Adds a {@link ProjectionKeyEventHandler} to be called for the given set of key events.
     *
     * If the given event handler is already registered, the event set and {@link Executor} for that
     * event handler will be replaced with those provided.
     *
     * For any event with a defined event handler, the system will suppress its default behavior for
     * that event, and call the event handler instead. (For instance, if an event handler is defined
     * for {@link #KEY_EVENT_CALL_SHORT_PRESS_KEY_UP}, the system will not open the dialer when the
     * {@link KeyEvent#KEYCODE_CALL CALL} key is short-pressed.)
     *
     * Callbacks on the event handler will be run on the given {@link Executor}, or, if it is null,
     * the {@link Handler} designated to run callbacks for {@link Car}.
     *
     * @param events        The set of key events to which to subscribe.
     * @param executor      An {@link Executor} on which to run callbacks.
     * @param eventHandler  The {@link ProjectionKeyEventHandler} to call when those events occur.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void addKeyEventHandler(
            @NonNull Set<@KeyEventNum Integer> events,
            @CallbackExecutor @Nullable Executor executor,
            @NonNull ProjectionKeyEventHandler eventHandler) {
        BitSet eventMask = new BitSet();
        for (int event : events) {
            Preconditions.checkArgument(event >= 0 && event < NUM_KEY_EVENTS, "Invalid key event");
            eventMask.set(event);
        }

        if (eventMask.isEmpty()) {
            removeKeyEventHandler(eventHandler);
            return;
        }

        if (executor == null) {
            executor = mHandlerExecutor;
        }

        synchronized (mLock) {
            KeyEventHandlerRecord record = mKeyEventHandlers.get(eventHandler);
            if (record == null) {
                record = new KeyEventHandlerRecord(executor, eventMask);
                mKeyEventHandlers.put(eventHandler, record);
            } else {
                record.mExecutor = executor;
                record.mSubscribedEvents = eventMask;
            }

            updateHandledEventsLocked();
        }
    }

    /**
     * Removes a previously registered {@link ProjectionKeyEventHandler}.
     *
     * @param eventHandler The listener to remove.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void removeKeyEventHandler(@NonNull ProjectionKeyEventHandler eventHandler) {
        synchronized (mLock) {
            KeyEventHandlerRecord record = mKeyEventHandlers.remove(eventHandler);
            if (record != null) {
                updateHandledEventsLocked();
            }
        }
    }

    @GuardedBy("mLock")
    private void updateHandledEventsLocked() {
        BitSet events = new BitSet();

        for (KeyEventHandlerRecord record : mKeyEventHandlers.values()) {
            events.or(record.mSubscribedEvents);
        }

        if (events.equals(mHandledEvents)) {
            // No changes.
            return;
        }

        try {
            if (!events.isEmpty()) {
                Log.d(TAG, "Registering handler with system for " + events);
                byte[] eventMask = events.toByteArray();
                mService.registerKeyEventHandler(mBinderHandler, eventMask);
            } else {
                Log.d(TAG, "Unregistering handler with system");
                mService.unregisterKeyEventHandler(mBinderHandler);
            }
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }

        mHandledEvents = events;
    }

    /**
     * Registers projection runner on projection start with projection service
     * to create reverse binding.
     * @param serviceIntent
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void registerProjectionRunner(@NonNull Intent serviceIntent) {
        Preconditions.checkNotNull("serviceIntent cannot be null");
        synchronized (mLock) {
            try {
                mService.registerProjectionRunner(serviceIntent);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Unregisters projection runner on projection stop with projection service to create
     * reverse binding.
     * @param serviceIntent
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void unregisterProjectionRunner(@NonNull Intent serviceIntent) {
        Preconditions.checkNotNull("serviceIntent cannot be null");
        synchronized (mLock) {
            try {
                mService.unregisterProjectionRunner(serviceIntent);
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    /**
     * Request to start Wi-Fi access point if it hasn't been started yet for wireless projection
     * receiver app.
     *
     * <p>A process can have only one request to start an access point, subsequent call of this
     * method will invalidate previous calls.
     *
     * @param callback to receive notifications when access point status changed for the request
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void startProjectionAccessPoint(@NonNull ProjectionAccessPointCallback callback) {
        Preconditions.checkNotNull(callback, "callback cannot be null");
        synchronized (mLock) {
            Looper looper = mHandler.getLooper();
            ProjectionAccessPointCallbackProxy proxy =
                    new ProjectionAccessPointCallbackProxy(this, looper, callback);
            try {
                mService.startProjectionAccessPoint(proxy.getMessenger(), mAccessPointProxyToken);
                mProjectionAccessPointCallbackProxy = proxy;
            } catch (RemoteException e) {
                throw e.rethrowFromSystemServer();
            }
        }
    }

    /**
     * Returns a list of available Wi-Fi channels. A channel is specified as frequency in MHz,
     * e.g. channel 1 will be represented as 2412 in the list.
     *
     * @param band one of the values from {@code android.net.wifi.WifiScanner#WIFI_BAND_*}
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public @NonNull List<Integer> getAvailableWifiChannels(int band) {
        try {
            int[] channels = mService.getAvailableWifiChannels(band);
            List<Integer> channelList = new ArrayList<>(channels.length);
            for (int v : channels) {
                channelList.add(v);
            }
            return channelList;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Stop Wi-Fi Access Point for wireless projection receiver app.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void stopProjectionAccessPoint() {
        ProjectionAccessPointCallbackProxy proxy;
        synchronized (mLock) {
            proxy = mProjectionAccessPointCallbackProxy;
            mProjectionAccessPointCallbackProxy = null;
        }
        if (proxy == null) {
            return;
        }

        try {
            mService.stopProjectionAccessPoint(mAccessPointProxyToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Request to disconnect the given profile on the given device, and prevent it from reconnecting
     * until either the request is released, or the process owning the given token dies.
     *
     * @param device  The device on which to inhibit a profile.
     * @param profile The {@link android.bluetooth.BluetoothProfile} to inhibit.
     * @return True if the profile was successfully inhibited, false if an error occurred.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public boolean requestBluetoothProfileInhibit(
            @NonNull BluetoothDevice device, int profile) {
        Preconditions.checkNotNull(device, "device cannot be null");
        try {
            return mService.requestBluetoothProfileInhibit(device, profile, mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Release an inhibit request made by {@link #requestBluetoothProfileInhibit}, and reconnect the
     * profile if no other inhibit requests are active.
     *
     * @param device  The device on which to release the inhibit request.
     * @param profile The profile on which to release the inhibit request.
     * @return True if the request was released, false if an error occurred.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public boolean releaseBluetoothProfileInhibit(@NonNull BluetoothDevice device, int profile) {
        Preconditions.checkNotNull(device, "device cannot be null");
        try {
            return mService.releaseBluetoothProfileInhibit(device, profile, mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Call this method to report projection status of your app. The aggregated status (from other
     * projection apps if available) will be broadcasted to interested parties.
     *
     * @param status the reported status that will be distributed to the interested listeners
     *
     * @see #registerProjectionStatusListener(ProjectionStatusListener)
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public void updateProjectionStatus(@NonNull ProjectionStatus status) {
        Preconditions.checkNotNull(status, "status cannot be null");
        try {
            mService.updateProjectionStatus(status, mToken);
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Register projection status listener. See {@link ProjectionStatusListener} for details. It is
     * allowed to register multiple listeners.
     *
     * <p>Note: provided listener will be called immediately with the most recent status.
     *
     * @param listener the listener to receive notification for any projection status changes
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION_STATUS)
    public void registerProjectionStatusListener(@NonNull ProjectionStatusListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        synchronized (mLock) {
            mProjectionStatusListeners.add(listener);

            if (mCarProjectionStatusListener == null) {
                mCarProjectionStatusListener = new CarProjectionStatusListenerImpl(this);
                try {
                    mService.registerProjectionStatusListener(mCarProjectionStatusListener);
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            } else {
                // Already subscribed to Car Service, immediately notify listener with the current
                // projection status in the event handler thread.
                mHandler.post(() ->
                        listener.onProjectionStatusChanged(
                                mCarProjectionStatusListener.mCurrentState,
                                mCarProjectionStatusListener.mCurrentPackageName,
                                mCarProjectionStatusListener.mDetails));
            }
        }
    }

    /**
     * Unregister provided listener from projection status notifications
     *
     * @param listener the listener for projection status notifications that was previously
     * registered with {@link #unregisterProjectionStatusListener(ProjectionStatusListener)}
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION_STATUS)
    public void unregisterProjectionStatusListener(@NonNull ProjectionStatusListener listener) {
        Preconditions.checkNotNull(listener, "listener cannot be null");
        synchronized (mLock) {
            if (!mProjectionStatusListeners.remove(listener)
                    || !mProjectionStatusListeners.isEmpty()) {
                return;
            }
            unregisterProjectionStatusListenerFromCarServiceLocked();
        }
    }

    private void unregisterProjectionStatusListenerFromCarServiceLocked() {
        try {
            mService.unregisterProjectionStatusListener(mCarProjectionStatusListener);
            mCarProjectionStatusListener = null;
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    private void handleProjectionStatusChanged(@ProjectionState int state,
            String packageName, List<ProjectionStatus> details) {
        List<ProjectionStatusListener> listeners;
        synchronized (mLock) {
            listeners = new ArrayList<>(mProjectionStatusListeners);
        }
        for (ProjectionStatusListener listener : listeners) {
            listener.onProjectionStatusChanged(state, packageName, details);
        }
    }

    /**
     * Returns {@link Bundle} object that contains customization for projection app. This bundle
     * can be parsed using {@link ProjectionOptions}.
     */
    @RequiresPermission(Car.PERMISSION_CAR_PROJECTION)
    public @NonNull Bundle getProjectionOptions() {
        try {
            return mService.getProjectionOptions();
        } catch (RemoteException e) {
            throw e.rethrowFromSystemServer();
        }
    }

    /**
     * Callback class for applications to receive updates about the LocalOnlyHotspot status.
     */
    public abstract static class ProjectionAccessPointCallback {
        public static final int ERROR_NO_CHANNEL = 1;
        public static final int ERROR_GENERIC = 2;
        public static final int ERROR_INCOMPATIBLE_MODE = 3;
        public static final int ERROR_TETHERING_DISALLOWED = 4;

        /** Called when access point started successfully. */
        public void onStarted(WifiConfiguration wifiConfiguration) {}
        /** Called when access point is stopped. No events will be sent after that. */
        public void onStopped() {}
        /** Called when access point failed to start. No events will be sent after that. */
        public void onFailed(int reason) {}
    }

    /**
     * Callback proxy for LocalOnlyHotspotCallback objects.
     */
    private static class ProjectionAccessPointCallbackProxy {
        private static final String LOG_PREFIX =
                ProjectionAccessPointCallbackProxy.class.getSimpleName() + ": ";

        private final Handler mHandler;
        private final WeakReference<CarProjectionManager> mCarProjectionManagerRef;
        private final Messenger mMessenger;

        ProjectionAccessPointCallbackProxy(CarProjectionManager manager, Looper looper,
                final ProjectionAccessPointCallback callback) {
            mCarProjectionManagerRef = new WeakReference<>(manager);

            mHandler = new Handler(looper) {
                @Override
                public void handleMessage(Message msg) {
                    Log.d(TAG, LOG_PREFIX + "handle message what: " + msg.what + " msg: " + msg);

                    CarProjectionManager manager = mCarProjectionManagerRef.get();
                    if (manager == null) {
                        Log.w(TAG, LOG_PREFIX + "handle message post GC");
                        return;
                    }

                    switch (msg.what) {
                        case PROJECTION_AP_STARTED:
                            WifiConfiguration config = (WifiConfiguration) msg.obj;
                            if (config == null) {
                                Log.e(TAG, LOG_PREFIX + "config cannot be null.");
                                callback.onFailed(ProjectionAccessPointCallback.ERROR_GENERIC);
                                return;
                            }
                            callback.onStarted(config);
                            break;
                        case PROJECTION_AP_STOPPED:
                            Log.i(TAG, LOG_PREFIX + "hotspot stopped");
                            callback.onStopped();
                            break;
                        case PROJECTION_AP_FAILED:
                            int reasonCode = msg.arg1;
                            Log.w(TAG, LOG_PREFIX + "failed to start.  reason: "
                                    + reasonCode);
                            callback.onFailed(reasonCode);
                            break;
                        default:
                            Log.e(TAG, LOG_PREFIX + "unhandled message.  type: " + msg.what);
                    }
                }
            };
            mMessenger = new Messenger(mHandler);
        }

        Messenger getMessenger() {
            return mMessenger;
        }
    }

    private static class ICarProjectionKeyEventHandlerImpl
            extends ICarProjectionKeyEventHandler.Stub {

        private final WeakReference<CarProjectionManager> mManager;

        private ICarProjectionKeyEventHandlerImpl(CarProjectionManager manager) {
            mManager = new WeakReference<>(manager);
        }

        @Override
        public void onKeyEvent(@KeyEventNum int event) {
            Log.d(TAG, "Received projection key event " + event);
            final CarProjectionManager manager = mManager.get();
            if (manager == null) {
                return;
            }

            List<Pair<ProjectionKeyEventHandler, Executor>> toDispatch = new ArrayList<>();
            synchronized (manager.mLock) {
                for (Map.Entry<ProjectionKeyEventHandler, KeyEventHandlerRecord> entry :
                        manager.mKeyEventHandlers.entrySet()) {
                    if (entry.getValue().mSubscribedEvents.get(event)) {
                        toDispatch.add(Pair.create(entry.getKey(), entry.getValue().mExecutor));
                    }
                }
            }

            for (Pair<ProjectionKeyEventHandler, Executor> entry : toDispatch) {
                ProjectionKeyEventHandler listener = entry.first;
                entry.second.execute(() -> listener.onKeyEvent(event));
            }
        }
    }

    private static class KeyEventHandlerRecord {
        @NonNull Executor mExecutor;
        @NonNull BitSet mSubscribedEvents;

        KeyEventHandlerRecord(@NonNull Executor executor, @NonNull BitSet subscribedEvents) {
            mExecutor = executor;
            mSubscribedEvents = subscribedEvents;
        }
    }

    private static class CarProjectionStatusListenerImpl
            extends ICarProjectionStatusListener.Stub {

        private @ProjectionState int mCurrentState;
        private @Nullable String mCurrentPackageName;
        private List<ProjectionStatus> mDetails = new ArrayList<>(0);

        private final WeakReference<CarProjectionManager> mManagerRef;

        private CarProjectionStatusListenerImpl(CarProjectionManager mgr) {
            mManagerRef = new WeakReference<>(mgr);
        }

        @Override
        public void onProjectionStatusChanged(int projectionState,
                String packageName,
                List<ProjectionStatus> details) {
            CarProjectionManager mgr = mManagerRef.get();
            if (mgr != null) {
                mgr.mHandler.post(() -> {
                    mCurrentState = projectionState;
                    mCurrentPackageName = packageName;
                    mDetails = Collections.unmodifiableList(details);

                    mgr.handleProjectionStatusChanged(projectionState, packageName, mDetails);
                });
            }
        }
    }
}
