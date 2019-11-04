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
package com.android.car;

import android.app.ActivityManager;
import android.car.Car;
import android.car.media.CarMediaManager;
import android.car.media.CarMediaManager.MediaSourceChangedListener;
import android.car.media.ICarMedia;
import android.car.media.ICarMediaSourceListener;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.media.session.MediaController;
import android.media.session.MediaController.TransportControls;
import android.media.session.MediaSession;
import android.media.session.MediaSession.Token;
import android.media.session.MediaSessionManager;
import android.media.session.MediaSessionManager.OnActiveSessionsChangedListener;
import android.media.session.PlaybackState;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.service.media.MediaBrowserService;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.PrintWriter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * CarMediaService manages the currently active media source for car apps. This is different from
 * the MediaSessionManager's active sessions, as there can only be one active source in the car,
 * through both browse and playback.
 *
 * In the car, the active media source does not necessarily have an active MediaSession, e.g. if
 * it were being browsed only. However, that source is still considered the active source, and
 * should be the source displayed in any Media related UIs (Media Center, home screen, etc).
 */
public class CarMediaService extends ICarMedia.Stub implements CarServiceBase {

    private static final String SOURCE_KEY = "media_source_component";
    private static final String PLAYBACK_STATE_KEY = "playback_state";
    private static final String SHARED_PREF = "com.android.car.media.car_media_service";
    private static final String COMPONENT_NAME_SEPARATOR = ",";
    private static final String MEDIA_CONNECTION_ACTION = "com.android.car.media.MEDIA_CONNECTION";

    private final Context mContext;
    private final UserManager mUserManager;
    private final MediaSessionManager mMediaSessionManager;
    private final MediaSessionUpdater mMediaSessionUpdater = new MediaSessionUpdater();
    private ComponentName mPrimaryMediaComponent;
    private ComponentName mPreviousMediaComponent;
    private SharedPreferences mSharedPrefs;
    // MediaController for the current active user's active media session. This controller can be
    // null if playback has not been started yet.
    private MediaController mActiveUserMediaController;
    private SessionChangedListener mSessionsListener;
    private boolean mStartPlayback;
    private boolean mPlayOnMediaSourceChanged;

    private boolean mPendingInit;
    private int mCurrentUser;

    private final RemoteCallbackList<ICarMediaSourceListener> mMediaSourceListeners =
            new RemoteCallbackList();

    private final Handler mMainHandler = new Handler(Looper.getMainLooper());

    // Handler to receive PlaybackState callbacks from the active media controller.
    private final Handler mHandler;
    private final HandlerThread mHandlerThread;

    /** The package name of the last media source that was removed while being primary. */
    private String mRemovedMediaSourcePackage;

    private final IntentFilter mPackageUpdateFilter;
    private boolean mIsPackageUpdateReceiverRegistered;

    /**
     * Listens to {@link Intent#ACTION_PACKAGE_REMOVED}, {@link Intent#ACTION_PACKAGE_REPLACED} and
     * {@link Intent#ACTION_PACKAGE_ADDED} so we can reset the media source to null when its
     * application is uninstalled, and restore it when the application is reinstalled.
     */
    private final BroadcastReceiver mPackageUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent.getData() == null) {
                return;
            }
            String intentPackage = intent.getData().getSchemeSpecificPart();
            if (Intent.ACTION_PACKAGE_REMOVED.equals(intent.getAction())) {
                if (mPrimaryMediaComponent != null
                        && mPrimaryMediaComponent.getPackageName().equals(intentPackage)) {
                    mRemovedMediaSourcePackage = intentPackage;
                    setPrimaryMediaSource(null);
                }
            } else if (Intent.ACTION_PACKAGE_REPLACED.equals(intent.getAction())
                    || Intent.ACTION_PACKAGE_ADDED.equals(intent.getAction())) {
                if (mRemovedMediaSourcePackage != null
                        && mRemovedMediaSourcePackage.equals(intentPackage)) {
                    ComponentName mediaSource = getMediaSource(intentPackage, "");
                    if (mediaSource != null) {
                        setPrimaryMediaSource(mediaSource);
                    }
                }
            }
        }
    };

    private final BroadcastReceiver mUserSwitchReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            mCurrentUser = ActivityManager.getCurrentUser();
            if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                Log.d(CarLog.TAG_MEDIA, "Switched to user " + mCurrentUser);
            }
            if (mUserManager.isUserUnlocked(mCurrentUser)) {
                initUser();
            } else {
                mPendingInit = true;
            }
        }
    };

    public CarMediaService(Context context) {
        mContext = context;
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mMediaSessionManager = mContext.getSystemService(MediaSessionManager.class);

        mHandlerThread = new HandlerThread(CarLog.TAG_MEDIA);
        mHandlerThread.start();
        mHandler = new Handler(mHandlerThread.getLooper());

        mPackageUpdateFilter = new IntentFilter();
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REMOVED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        mPackageUpdateFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        mPackageUpdateFilter.addDataScheme("package");

        IntentFilter userSwitchFilter = new IntentFilter();
        userSwitchFilter.addAction(Intent.ACTION_USER_SWITCHED);
        mContext.registerReceiver(mUserSwitchReceiver, userSwitchFilter);

        mPlayOnMediaSourceChanged =
                mContext.getResources().getBoolean(R.bool.autoPlayOnMediaSourceChanged);
        mCurrentUser = ActivityManager.getCurrentUser();
        updateMediaSessionCallbackForCurrentUser();
    }

    @Override
    public void init() {
        // Nothing to do. Reason: this method is only called once after rebooting, but we need to
        // init user state each time a new user is unlocked, so this method is not the right
        // place to call initUser().
    }

    private void initUser() {
        if (mSharedPrefs == null) {
            mSharedPrefs = mContext.getSharedPreferences(SHARED_PREF, Context.MODE_PRIVATE);
        }
        if (mIsPackageUpdateReceiverRegistered) {
            mContext.unregisterReceiver(mPackageUpdateReceiver);
        }
        UserHandle currentUser = new UserHandle(mCurrentUser);
        mContext.registerReceiverAsUser(mPackageUpdateReceiver, currentUser,
                mPackageUpdateFilter, null, null);
        mIsPackageUpdateReceiverRegistered = true;

        mPrimaryMediaComponent = getLastMediaSource();
        mActiveUserMediaController = null;
        String key = PLAYBACK_STATE_KEY + mCurrentUser;
        mStartPlayback =
                mSharedPrefs.getInt(key, PlaybackState.STATE_NONE) == PlaybackState.STATE_PLAYING;
        updateMediaSessionCallbackForCurrentUser();
        notifyListeners();

        // Start a service on the current user that binds to the media browser of the current media
        // source. We start a new service because this one runs on user 0, and MediaBrowser doesn't
        // provide an API to connect on a specific user.
        Intent serviceStart = new Intent(MEDIA_CONNECTION_ACTION);
        serviceStart.setPackage(mContext.getResources().getString(R.string.serviceMediaConnection));
        mContext.startForegroundServiceAsUser(serviceStart, currentUser);
    }

    @Override
    public void release() {
        mMediaSessionUpdater.unregisterCallbacks();
    }

    @Override
    public void dump(PrintWriter writer) {
        writer.println("*CarMediaService*");
        writer.println("\tCurrent media component: " + (mPrimaryMediaComponent == null ? "-"
                : mPrimaryMediaComponent.flattenToString()));
        writer.println("\tPrevious media component: " + (mPreviousMediaComponent == null ? "-"
                : mPreviousMediaComponent.flattenToString()));
        if (mActiveUserMediaController != null) {
            writer.println(
                    "\tCurrent media controller: " + mActiveUserMediaController.getPackageName());
            writer.println(
                    "\tCurrent browse service extra: " + getClassName(mActiveUserMediaController));
        }
        writer.println("\tNumber of active media sessions: "
                + mMediaSessionManager.getActiveSessionsForUser(null,
                ActivityManager.getCurrentUser()).size());
    }

    /**
     * @see {@link CarMediaManager#setMediaSource(ComponentName)}
     */
    @Override
    public synchronized void setMediaSource(@NonNull ComponentName componentName) {
        ICarImpl.assertPermission(mContext, android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Log.d(CarLog.TAG_MEDIA, "Changing media source to: " + componentName.getPackageName());
        }
        setPrimaryMediaSource(componentName);
    }

    /**
     * @see {@link CarMediaManager#getMediaSource()}
     */
    @Override
    public synchronized ComponentName getMediaSource() {
        ICarImpl.assertPermission(mContext, android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        return mPrimaryMediaComponent;
    }

    /**
     * @see {@link CarMediaManager#registerMediaSourceListener(MediaSourceChangedListener)}
     */
    @Override
    public synchronized void registerMediaSourceListener(ICarMediaSourceListener callback) {
        ICarImpl.assertPermission(mContext, android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        mMediaSourceListeners.register(callback);
    }

    /**
     * @see {@link CarMediaManager#unregisterMediaSourceListener(ICarMediaSourceListener)}
     */
    @Override
    public synchronized void unregisterMediaSourceListener(ICarMediaSourceListener callback) {
        ICarImpl.assertPermission(mContext, android.Manifest.permission.MEDIA_CONTENT_CONTROL);
        mMediaSourceListeners.unregister(callback);
    }

    /**
     * Sets user lock / unlocking status on main thread. This is coming from system server through
     * ICar binder call.
     *
     * @param userHandle Handle of user
     * @param unlocked   unlocked (=true) or locked (=false)
     */
    public void setUserLockStatus(int userHandle, boolean unlocked) {
        mMainHandler.post(new Runnable() {
            @Override
            public void run() {
                if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                    Log.d(CarLog.TAG_MEDIA,
                            "User " + userHandle + " is " + (unlocked ? "unlocked" : "locked"));
                }
                // Nothing else to do when it is locked back.
                if (!unlocked) {
                    return;
                }
                // No need to handle user0, non current foreground user, or ephemeral user.
                if (userHandle == UserHandle.USER_SYSTEM
                        || userHandle != ActivityManager.getCurrentUser()
                        || mUserManager.getUserInfo(userHandle).isEphemeral()) {
                    return;
                }
                if (mPendingInit) {
                    initUser();
                    mPendingInit = false;
                }
            }
        });
    }

    private void updateMediaSessionCallbackForCurrentUser() {
        if (mSessionsListener != null) {
            mMediaSessionManager.removeOnActiveSessionsChangedListener(mSessionsListener);
        }
        mSessionsListener = new SessionChangedListener(ActivityManager.getCurrentUser());
        mMediaSessionManager.addOnActiveSessionsChangedListener(mSessionsListener, null,
                ActivityManager.getCurrentUser(), null);
        mMediaSessionUpdater.registerCallbacks(mMediaSessionManager.getActiveSessionsForUser(
                null, ActivityManager.getCurrentUser()));
    }

    /**
     * Attempts to play the current source using MediaController.TransportControls.play()
     */
    private void play() {
        if (mActiveUserMediaController != null) {
            if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                Log.d(CarLog.TAG_MEDIA, "playing " + mActiveUserMediaController.getPackageName());
            }
            TransportControls controls = mActiveUserMediaController.getTransportControls();
            if (controls != null) {
                controls.play();
            } else {
                Log.e(CarLog.TAG_MEDIA, "Can't start playback, transport controls unavailable "
                        + mActiveUserMediaController.getPackageName());
            }
        }
    }

    /**
     * Attempts to stop the current source using MediaController.TransportControls.stop()
     */
    private void stop() {
        if (mActiveUserMediaController != null) {
            if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
                Log.d(CarLog.TAG_MEDIA, "stopping " + mActiveUserMediaController.getPackageName());
            }
            TransportControls controls = mActiveUserMediaController.getTransportControls();
            if (controls != null) {
                controls.stop();
            } else {
                Log.e(CarLog.TAG_MEDIA, "Can't stop playback, transport controls unavailable "
                        + mActiveUserMediaController.getPackageName());
            }
        }
    }

    private class SessionChangedListener implements OnActiveSessionsChangedListener {
        private final int mCurrentUser;

        SessionChangedListener(int currentUser) {
            mCurrentUser = currentUser;
        }

        @Override
        public void onActiveSessionsChanged(List<MediaController> controllers) {
            if (ActivityManager.getCurrentUser() != mCurrentUser) {
                Log.e(CarLog.TAG_MEDIA, "Active session callback for old user: " + mCurrentUser);
                return;
            }
            mMediaSessionUpdater.registerCallbacks(controllers);
        }
    }

    private class MediaControllerCallback extends MediaController.Callback {

        private final MediaController mMediaController;
        private int mPreviousPlaybackState;

        private MediaControllerCallback(MediaController mediaController) {
            mMediaController = mediaController;
            PlaybackState state = mediaController.getPlaybackState();
            mPreviousPlaybackState = (state == null) ? PlaybackState.STATE_NONE : state.getState();
        }

        private void register() {
            mMediaController.registerCallback(this);
        }

        private void unregister() {
            mMediaController.unregisterCallback(this);
        }

        @Override
        public void onPlaybackStateChanged(@Nullable PlaybackState state) {
            if (state.getState() == PlaybackState.STATE_PLAYING
                    && state.getState() != mPreviousPlaybackState) {
                ComponentName mediaSource = getMediaSource(mMediaController.getPackageName(),
                        getClassName(mMediaController));
                if (mediaSource != null && !mediaSource.equals(mPrimaryMediaComponent)
                        && Log.isLoggable(CarLog.TAG_MEDIA, Log.INFO)) {
                    Log.i(CarLog.TAG_MEDIA, "Changing media source due to playback state change: "
                            + mediaSource.flattenToString());
                }
                setPrimaryMediaSource(mediaSource);
            }
            mPreviousPlaybackState = state.getState();
        }
    }

    private class MediaSessionUpdater {
        private Map<Token, MediaControllerCallback> mCallbacks = new HashMap<>();

        /**
         * Register a {@link MediaControllerCallback} for each given controller. Note that if a
         * controller was already watched, we don't register a callback again. This prevents an
         * undesired revert of the primary media source. Callbacks for previously watched
         * controllers that are not present in the given list are unregistered.
         */
        private void registerCallbacks(List<MediaController> newControllers) {

            List<MediaController> additions = new ArrayList<>(newControllers.size());
            Map<MediaSession.Token, MediaControllerCallback> updatedCallbacks =
                    new HashMap<>(newControllers.size());

            for (MediaController controller : newControllers) {
                MediaSession.Token token = controller.getSessionToken();
                MediaControllerCallback callback = mCallbacks.get(token);
                if (callback == null) {
                    callback = new MediaControllerCallback(controller);
                    callback.register();
                    additions.add(controller);
                }
                updatedCallbacks.put(token, callback);
            }

            for (MediaSession.Token token : mCallbacks.keySet()) {
                if (!updatedCallbacks.containsKey(token)) {
                    mCallbacks.get(token).unregister();
                }
            }

            mCallbacks = updatedCallbacks;
            updatePrimaryMediaSourceWithCurrentlyPlaying(additions);
            // If there are no playing media sources, and we don't currently have the controller
            // for the active source, check the active sessions for a matching controller. If this
            // is called after a user switch, its possible for a matching controller to already be
            // active before the user is unlocked, so we check all of the current controllers
            if (mActiveUserMediaController == null) {
                updateActiveMediaController(newControllers);
            }
        }

        /**
         * Unregister all MediaController callbacks
         */
        private void unregisterCallbacks() {
            for (Map.Entry<Token, MediaControllerCallback> entry : mCallbacks.entrySet()) {
                entry.getValue().unregister();
            }
        }
    }

    /**
     * Updates the primary media source, then notifies content observers of the change
     */
    private synchronized void setPrimaryMediaSource(@Nullable ComponentName componentName) {
        if (mPrimaryMediaComponent != null && mPrimaryMediaComponent.equals((componentName))) {
            return;
        }

        stop();

        mStartPlayback = mPlayOnMediaSourceChanged;
        mPreviousMediaComponent = mPrimaryMediaComponent;
        mPrimaryMediaComponent = componentName;
        updateActiveMediaController(mMediaSessionManager
                .getActiveSessionsForUser(null, ActivityManager.getCurrentUser()));

        if (mSharedPrefs != null) {
            if (mPrimaryMediaComponent != null && !TextUtils.isEmpty(
                    mPrimaryMediaComponent.flattenToString())) {
                saveLastMediaSource(mPrimaryMediaComponent);
                mRemovedMediaSourcePackage = null;
            }
        } else {
            // Shouldn't reach this unless there is some other error in CarService
            Log.e(CarLog.TAG_MEDIA, "Error trying to save last media source, prefs uninitialized");
        }
        notifyListeners();
    }

    private void notifyListeners() {
        int i = mMediaSourceListeners.beginBroadcast();
        while (i-- > 0) {
            try {
                ICarMediaSourceListener callback = mMediaSourceListeners.getBroadcastItem(i);
                callback.onMediaSourceChanged(mPrimaryMediaComponent);
            } catch (RemoteException e) {
                Log.e(CarLog.TAG_MEDIA, "calling onMediaSourceChanged failed " + e);
            }
        }
        mMediaSourceListeners.finishBroadcast();
    }

    private MediaController.Callback mMediaControllerCallback = new MediaController.Callback() {
        @Override
        public void onPlaybackStateChanged(PlaybackState state) {
            savePlaybackState(state);
            // Try to start playback if the new state allows the play action
            maybeRestartPlayback(state);
        }
    };

    /**
     * Finds the currently playing media source, then updates the active source if the component
     * name is different.
     */
    private synchronized void updatePrimaryMediaSourceWithCurrentlyPlaying(
            List<MediaController> controllers) {
        for (MediaController controller : controllers) {
            if (controller.getPlaybackState() != null
                    && controller.getPlaybackState().getState() == PlaybackState.STATE_PLAYING) {
                String newPackageName = controller.getPackageName();
                String newClassName = getClassName(controller);
                if (!matchPrimaryMediaSource(newPackageName, newClassName)) {
                    ComponentName mediaSource = getMediaSource(newPackageName, newClassName);
                    if (Log.isLoggable(CarLog.TAG_MEDIA, Log.INFO)) {
                        Log.i(CarLog.TAG_MEDIA,
                                "Changing media source due to playback state change: "
                                + mediaSource.flattenToString());
                    }
                    setPrimaryMediaSource(mediaSource);
                }
                return;
            }
        }
    }

    private boolean matchPrimaryMediaSource(@NonNull String newPackageName,
            @NonNull String newClassName) {
        if (mPrimaryMediaComponent != null && mPrimaryMediaComponent.getPackageName().equals(
                newPackageName)) {
            // If the class name of currently active source is not specified, only checks package
            // name; otherwise checks both package name and class name.
            if (TextUtils.isEmpty(newClassName)) {
                return true;
            } else {
                return newClassName.equals(mPrimaryMediaComponent.getClassName());
            }
        }
        return false;
    }


    private boolean isMediaService(@NonNull ComponentName componentName) {
        return getMediaService(componentName) != null;
    }

    /*
     * Gets the media service that matches the componentName for the current foreground user.
     */
    private ComponentName getMediaService(@NonNull ComponentName componentName) {
        String packageName = componentName.getPackageName();
        String className = componentName.getClassName();

        PackageManager packageManager = mContext.getPackageManager();
        Intent mediaIntent = new Intent();
        mediaIntent.setPackage(packageName);
        mediaIntent.setAction(MediaBrowserService.SERVICE_INTERFACE);
        List<ResolveInfo> mediaServices = packageManager.queryIntentServicesAsUser(mediaIntent,
                PackageManager.GET_RESOLVED_FILTER, ActivityManager.getCurrentUser());

        for (ResolveInfo service : mediaServices) {
            String serviceName = service.serviceInfo.name;
            if (!TextUtils.isEmpty(serviceName)
                    // If className is not specified, returns the first service in the package;
                    // otherwise returns the matched service.
                    // TODO(b/136274456): find a proper way to handle the case where there are
                    //  multiple services and the className is not specified.

                    && (TextUtils.isEmpty(className) || serviceName.equals(className))) {
                return new ComponentName(packageName, serviceName);
            }
        }

        if (Log.isLoggable(CarLog.TAG_MEDIA, Log.DEBUG)) {
            Log.d(CarLog.TAG_MEDIA, "No MediaBrowseService with ComponentName: "
                    + componentName.flattenToString());
        }
        return null;
    }

    /*
     * Gets the component name of the media service.
     */
    @Nullable
    private ComponentName getMediaSource(@NonNull String packageName, @NonNull String className) {
        return getMediaService(new ComponentName(packageName, className));
    }

    private void saveLastMediaSource(@NonNull ComponentName component) {
        String componentName = component.flattenToString();
        String key = SOURCE_KEY + mCurrentUser;
        String serialized = mSharedPrefs.getString(key, null);
        if (serialized == null) {
            mSharedPrefs.edit().putString(key, componentName).apply();
        } else {
            Deque<String> componentNames = getComponentNameList(serialized);
            componentNames.remove(componentName);
            componentNames.addFirst(componentName);
            mSharedPrefs.edit().putString(key, serializeComponentNameList(componentNames))
                    .apply();
        }
    }

    private ComponentName getLastMediaSource() {
        String key = SOURCE_KEY + mCurrentUser;
        String serialized = mSharedPrefs.getString(key, null);
        if (!TextUtils.isEmpty(serialized)) {
            for (String name : getComponentNameList(serialized)) {
                ComponentName componentName = ComponentName.unflattenFromString(name);
                if (isMediaService(componentName)) {
                    return componentName;
                }
            }
        }

        String defaultMediaSource = mContext.getString(R.string.default_media_source);
        ComponentName defaultComponent = ComponentName.unflattenFromString(defaultMediaSource);
        if (isMediaService(defaultComponent)) {
            return defaultComponent;
        }
        return null;
    }

    private String serializeComponentNameList(Deque<String> componentNames) {
        return componentNames.stream().collect(Collectors.joining(COMPONENT_NAME_SEPARATOR));
    }

    private Deque<String> getComponentNameList(String serialized) {
        String[] componentNames = serialized.split(COMPONENT_NAME_SEPARATOR);
        return new ArrayDeque(Arrays.asList(componentNames));
    }

    private void savePlaybackState(PlaybackState playbackState) {
        int state = playbackState != null ? playbackState.getState() : PlaybackState.STATE_NONE;
        if (state == PlaybackState.STATE_PLAYING) {
            // No longer need to request play if audio was resumed already via some other means,
            // e.g. Assistant starts playback, user uses hardware button, etc.
            mStartPlayback = false;
        }
        if (mSharedPrefs != null) {
            String key = PLAYBACK_STATE_KEY + mCurrentUser;
            mSharedPrefs.edit().putInt(key, state).apply();
        }
    }

    private void maybeRestartPlayback(PlaybackState state) {
        if (mStartPlayback && state != null
                && (state.getActions() & PlaybackState.ACTION_PLAY) != 0) {
            play();
            mStartPlayback = false;
        }
    }

    /**
     * Updates active media controller from the list that has the same component name as the primary
     * media component. Clears callback and resets media controller to null if not found.
     */
    private void updateActiveMediaController(List<MediaController> mediaControllers) {
        if (mPrimaryMediaComponent == null) {
            return;
        }
        if (mActiveUserMediaController != null) {
            mActiveUserMediaController.unregisterCallback(mMediaControllerCallback);
            mActiveUserMediaController = null;
        }
        for (MediaController controller : mediaControllers) {
            if (matchPrimaryMediaSource(controller.getPackageName(), getClassName(controller))) {
                mActiveUserMediaController = controller;
                // Specify Handler to receive callbacks on, to avoid defaulting to the calling
                // thread; this method can be called from the MediaSessionManager callback.
                // Using the version of this method without passing a handler causes a
                // RuntimeException for failing to create a Handler.
                PlaybackState state = mActiveUserMediaController.getPlaybackState();
                savePlaybackState(state);
                mActiveUserMediaController.registerCallback(mMediaControllerCallback, mHandler);
                maybeRestartPlayback(state);
                return;
            }
        }
    }

    @NonNull
    private static String getClassName(@NonNull MediaController controller) {
        Bundle sessionExtras = controller.getExtras();
        String value =
                sessionExtras == null ? "" : sessionExtras.getString(
                        Car.CAR_EXTRA_BROWSE_SERVICE_FOR_SESSION);
        return value != null ? value : "";
    }
}
