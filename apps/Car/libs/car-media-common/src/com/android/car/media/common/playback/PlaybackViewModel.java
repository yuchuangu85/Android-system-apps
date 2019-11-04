/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.common.playback;

import static androidx.lifecycle.Transformations.switchMap;

import static com.android.car.arch.common.LiveDataFunctions.dataOf;
import static com.android.car.media.common.playback.PlaybackStateAnnotations.Actions;

import android.annotation.IntDef;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.media.MediaMetadata;
import android.os.Bundle;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.RatingCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;

import com.android.car.media.common.CustomPlaybackAction;
import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;
import com.android.car.media.common.R;
import com.android.car.media.common.source.MediaSourceColors;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.internal.annotations.VisibleForTesting;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * ViewModel for media playback.
 * <p>
 * Observes changes to the provided MediaController to expose playback state and metadata
 * observables.
 * <p>
 * PlaybackViewModel is a singleton tied to the application to provide a single source of truth.
 */
public class PlaybackViewModel extends AndroidViewModel {
    private static final String TAG = "PlaybackViewModel";

    private static final String ACTION_SET_RATING =
            "com.android.car.media.common.ACTION_SET_RATING";
    private static final String EXTRA_SET_HEART = "com.android.car.media.common.EXTRA_SET_HEART";

    private static PlaybackViewModel sInstance;

    /** Returns the PlaybackViewModel singleton tied to the application. */
    public static PlaybackViewModel get(@NonNull Application application) {
        if (sInstance == null) {
            sInstance = new PlaybackViewModel(application);
        }
        return sInstance;
    }

    /**
     * Possible main actions.
     */
    @IntDef({ACTION_PLAY, ACTION_STOP, ACTION_PAUSE, ACTION_DISABLED})
    @Retention(RetentionPolicy.SOURCE)
    public @interface Action {
    }

    /**
     * Main action is disabled. The source can't play media at this time
     */
    public static final int ACTION_DISABLED = 0;
    /**
     * Start playing
     */
    public static final int ACTION_PLAY = 1;
    /**
     * Stop playing
     */
    public static final int ACTION_STOP = 2;
    /**
     * Pause playing
     */
    public static final int ACTION_PAUSE = 3;

    /** Needs to be a MediaMetadata because the compat class doesn't implement equals... */
    private static final MediaMetadata EMPTY_MEDIA_METADATA = new MediaMetadata.Builder().build();

    private final MediaControllerCallback mMediaControllerCallback = new MediaControllerCallback();
    private final Observer<MediaControllerCompat> mMediaControllerObserver =
            mMediaControllerCallback::onMediaControllerChanged;

    private final MediaSourceColors.Factory mColorsFactory;
    private final MutableLiveData<MediaSourceColors> mColors = dataOf(null);

    private final MutableLiveData<MediaItemMetadata> mMetadata = dataOf(null);

    // Filters out queue items with no description or title and converts them to MediaItemMetadata
    private final MutableLiveData<List<MediaItemMetadata>> mSanitizedQueue = dataOf(null);

    private final MutableLiveData<Boolean> mHasQueue = dataOf(null);

    private final MutableLiveData<CharSequence> mQueueTitle = dataOf(null);

    private final MutableLiveData<PlaybackController> mPlaybackControls = dataOf(null);

    private final MutableLiveData<PlaybackStateWrapper> mPlaybackStateWrapper = dataOf(null);

    private final LiveData<PlaybackProgress> mProgress =
            switchMap(mPlaybackStateWrapper,
                    state -> state == null ? dataOf(new PlaybackProgress(0L, 0L))
                            : new ProgressLiveData(state.mState, state.getMaxProgress()));

    private PlaybackViewModel(Application application) {
        this(application, MediaSourceViewModel.get(application).getMediaController());
    }

    @VisibleForTesting
    public PlaybackViewModel(Application application, LiveData<MediaControllerCompat> controller) {
        super(application);
        mColorsFactory = new MediaSourceColors.Factory(application);
        controller.observeForever(mMediaControllerObserver);
    }

    /**
     * Returns a LiveData that emits the colors for the currently set media source.
     */
    public LiveData<MediaSourceColors> getMediaSourceColors() {
        return mColors;
    }

    /**
     * Returns a LiveData that emits a MediaItemMetadata of the current media item in the session
     * managed by the provided {@link MediaControllerCompat}.
     */
    public LiveData<MediaItemMetadata> getMetadata() {
        return mMetadata;
    }

    /**
     * Returns a LiveData that emits the current queue as MediaItemMetadatas where items without a
     * title have been filtered out.
     */
    public LiveData<List<MediaItemMetadata>> getQueue() {
        return mSanitizedQueue;
    }

    /**
     * Returns a LiveData that emits whether the MediaController has a non-empty queue
     */
    public LiveData<Boolean> hasQueue() {
        return mHasQueue;
    }

    /**
     * Returns a LiveData that emits the current queue title.
     */
    public LiveData<CharSequence> getQueueTitle() {
        return mQueueTitle;
    }

    /**
     * Returns a LiveData that emits an object for controlling the currently selected
     * MediaController.
     */
    public LiveData<PlaybackController> getPlaybackController() {
        return mPlaybackControls;
    }

    /** Returns a {@PlaybackStateWrapper} live data. */
    public LiveData<PlaybackStateWrapper> getPlaybackStateWrapper() {
        return mPlaybackStateWrapper;
    }

    /**
     * Returns a LiveData that emits the current playback progress, in milliseconds. This is a
     * value between 0 and {@link #getPlaybackStateWrapper#getMaxProgress()} or
     * {@link PlaybackStateCompat#PLAYBACK_POSITION_UNKNOWN} if the current position is unknown.
     * This value will update on its own periodically (less than a second) while active.
     */
    public LiveData<PlaybackProgress> getProgress() {
        return mProgress;
    }

    @VisibleForTesting
    MediaControllerCompat getMediaController() {
        return mMediaControllerCallback.mMediaController;
    }

    @VisibleForTesting
    MediaMetadataCompat getMediaMetadata() {
        return mMediaControllerCallback.mMediaMetadata;
    }


    private class MediaControllerCallback extends MediaControllerCompat.Callback {

        private MediaControllerCompat mMediaController;
        private MediaMetadataCompat mMediaMetadata;
        private PlaybackStateCompat mPlaybackState;

        void onMediaControllerChanged(MediaControllerCompat controller) {
            if (mMediaController == controller) {
                Log.w(TAG, "onMediaControllerChanged noop");
                return;
            }

            if (mMediaController != null) {
                mMediaController.unregisterCallback(this);
            }

            mMediaMetadata = null;
            mPlaybackState = null;
            mMediaController = controller;
            mPlaybackControls.setValue(new PlaybackController(controller));

            if (mMediaController != null) {
                mMediaController.registerCallback(this);

                mColors.setValue(mColorsFactory.extractColors(controller.getPackageName()));

                // The apps don't always send updates so make sure we fetch the most recent values.
                onMetadataChanged(mMediaController.getMetadata());
                onPlaybackStateChanged(mMediaController.getPlaybackState());
                onQueueChanged(mMediaController.getQueue());
                onQueueTitleChanged(mMediaController.getQueueTitle());
            } else {
                mColors.setValue(null);
                onMetadataChanged(null);
                onPlaybackStateChanged(null);
                onQueueChanged(null);
                onQueueTitleChanged(null);
            }

            updatePlaybackStatus();
        }

        @Override
        public void onSessionDestroyed() {
            Log.w(TAG, "onSessionDestroyed");
            onMediaControllerChanged(null);
        }

        @Override
        public void onMetadataChanged(@Nullable MediaMetadataCompat mmdCompat) {
            // MediaSession#setMetadata builds an empty MediaMetadata when its argument is null,
            // yet MediaMetadataCompat doesn't implement equals... so if the given mmdCompat's
            // MediaMetadata equals EMPTY_MEDIA_METADATA, set mMediaMetadata to null to keep
            // the code simpler everywhere else.
            if ((mmdCompat != null) && EMPTY_MEDIA_METADATA.equals(mmdCompat.getMediaMetadata())) {
                mMediaMetadata = null;
            } else {
                mMediaMetadata = mmdCompat;
            }
            MediaItemMetadata item =
                    (mMediaMetadata != null) ? new MediaItemMetadata(mMediaMetadata) : null;
            mMetadata.setValue(item);
            updatePlaybackStatus();
        }

        @Override
        public void onQueueTitleChanged(CharSequence title) {
            mQueueTitle.setValue(title);
        }

        @Override
        public void onQueueChanged(@Nullable List<MediaSessionCompat.QueueItem> queue) {
            List<MediaItemMetadata> filtered = queue == null ? Collections.emptyList()
                    : queue.stream()
                            .filter(item -> item.getDescription() != null
                                    && item.getDescription().getTitle() != null)
                            .map(MediaItemMetadata::new)
                            .collect(Collectors.toList());

            mSanitizedQueue.setValue(filtered);
            mHasQueue.setValue(!filtered.isEmpty());
        }

        @Override
        public void onPlaybackStateChanged(PlaybackStateCompat playbackState) {
            mPlaybackState = playbackState;
            updatePlaybackStatus();
        }

        private void updatePlaybackStatus() {
            if (mMediaController != null && mPlaybackState != null) {
                mPlaybackStateWrapper.setValue(
                        new PlaybackStateWrapper(mMediaController, mMediaMetadata, mPlaybackState));
            } else {
                mPlaybackStateWrapper.setValue(null);
            }
        }
    }

    /** Convenient extension of {@link PlaybackStateCompat}. */
    public static final class PlaybackStateWrapper {

        private final MediaControllerCompat mMediaController;
        @Nullable
        private final MediaMetadataCompat mMetadata;
        private final PlaybackStateCompat mState;

        PlaybackStateWrapper(@NonNull MediaControllerCompat mediaController,
                @Nullable MediaMetadataCompat metadata, @NonNull PlaybackStateCompat state) {
            mMediaController = mediaController;
            mMetadata = metadata;
            mState = state;
        }

        /** Returns true if there's enough information in the state to show a UI for it. */
        public boolean shouldDisplay() {
            return (mMetadata != null) || (getMainAction() != ACTION_DISABLED);
        }

        /** Returns the main action. */
        @Action
        public int getMainAction() {
            @Actions long actions = mState.getActions();
            @Action int stopAction = ACTION_DISABLED;
            if ((actions & (PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE)) != 0) {
                stopAction = ACTION_PAUSE;
            } else if ((actions & PlaybackStateCompat.ACTION_STOP) != 0) {
                stopAction = ACTION_STOP;
            }

            switch (mState.getState()) {
                case PlaybackStateCompat.STATE_PLAYING:
                case PlaybackStateCompat.STATE_BUFFERING:
                case PlaybackStateCompat.STATE_CONNECTING:
                case PlaybackStateCompat.STATE_FAST_FORWARDING:
                case PlaybackStateCompat.STATE_REWINDING:
                case PlaybackStateCompat.STATE_SKIPPING_TO_NEXT:
                case PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS:
                case PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM:
                    return stopAction;
                case PlaybackStateCompat.STATE_STOPPED:
                case PlaybackStateCompat.STATE_PAUSED:
                case PlaybackStateCompat.STATE_NONE:
                case PlaybackStateCompat.STATE_ERROR:
                    return (actions & PlaybackStateCompat.ACTION_PLAY) != 0 ? ACTION_PLAY
                            : ACTION_DISABLED;
                default:
                    Log.w(TAG, String.format("Unknown PlaybackState: %d", mState.getState()));
                    return ACTION_DISABLED;
            }
        }

        /**
         * Returns the duration of the media item in milliseconds. The current position in this
         * duration can be obtained by calling {@link #getProgress()}.
         */
        public long getMaxProgress() {
            return mMetadata == null ? 0 :
                    mMetadata.getLong(MediaMetadataCompat.METADATA_KEY_DURATION);
        }

        /** Returns whether the current media source is playing a media item. */
        public boolean isPlaying() {
            return mState.getState() == PlaybackStateCompat.STATE_PLAYING;
        }

        /** Returns whether the media source supports skipping to the next item. */
        public boolean isSkipNextEnabled() {
            return (mState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_NEXT) != 0;
        }

        /** Returns whether the media source supports skipping to the previous item. */
        public boolean isSkipPreviousEnabled() {
            return (mState.getActions() & PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS) != 0;
        }

        /**
         * Returns whether the media source supports seeking to a new location in the media stream.
         */
        public boolean isSeekToEnabled() {
            return (mState.getActions() & PlaybackStateCompat.ACTION_SEEK_TO) != 0;
        }

        /** Returns whether the media source requires reserved space for the skip to next action. */
        public boolean isSkipNextReserved() {
            return mMediaController.getExtras() != null
                    && mMediaController.getExtras().getBoolean(
                    MediaConstants.SLOT_RESERVATION_SKIP_TO_NEXT);
        }

        /**
         * Returns whether the media source requires reserved space for the skip to previous action.
         */
        public boolean iSkipPreviousReserved() {
            return mMediaController.getExtras() != null
                    && mMediaController.getExtras().getBoolean(
                    MediaConstants.SLOT_RESERVATION_SKIP_TO_PREV);
        }

        /** Returns whether the media source is loading (e.g.: buffering, connecting, etc.). */
        public boolean isLoading() {
            int state = mState.getState();
            return state == PlaybackStateCompat.STATE_BUFFERING
                    || state == PlaybackStateCompat.STATE_CONNECTING
                    || state == PlaybackStateCompat.STATE_FAST_FORWARDING
                    || state == PlaybackStateCompat.STATE_REWINDING
                    || state == PlaybackStateCompat.STATE_SKIPPING_TO_NEXT
                    || state == PlaybackStateCompat.STATE_SKIPPING_TO_PREVIOUS
                    || state == PlaybackStateCompat.STATE_SKIPPING_TO_QUEUE_ITEM;
        }

        /** See {@link PlaybackStateCompat#getErrorMessage}. */
        public CharSequence getErrorMessage() {
            return mState.getErrorMessage();
        }

        /** See {@link PlaybackStateCompat#getErrorCode()}. */
        public int getErrorCode() {
            return mState.getErrorCode();
        }

        /** See {@link PlaybackStateCompat#getActiveQueueItemId}. */
        public long getActiveQueueItemId() {
            return mState.getActiveQueueItemId();
        }

        /** See {@link PlaybackStateCompat#getState}. */
        @PlaybackStateCompat.State
        public int getState() {
            return mState.getState();
        }

        /** See {@link PlaybackStateCompat#getExtras}. */
        public Bundle getExtras() {
            return mState.getExtras();
        }

        @VisibleForTesting
        PlaybackStateCompat getStateCompat() {
            return mState;
        }

        /**
         * Returns a sorted list of custom actions available. Call {@link
         * RawCustomPlaybackAction#fetchDrawable(Context)} to get the appropriate icon Drawable.
         */
        public List<RawCustomPlaybackAction> getCustomActions() {
            List<RawCustomPlaybackAction> actions = new ArrayList<>();
            RawCustomPlaybackAction ratingAction = getRatingAction();
            if (ratingAction != null) actions.add(ratingAction);

            for (PlaybackStateCompat.CustomAction action : mState.getCustomActions()) {
                String packageName = mMediaController.getPackageName();
                actions.add(
                        new RawCustomPlaybackAction(action.getIcon(), packageName,
                                action.getAction(),
                                action.getExtras()));
            }
            return actions;
        }

        @Nullable
        private RawCustomPlaybackAction getRatingAction() {
            long stdActions = mState.getActions();
            if ((stdActions & PlaybackStateCompat.ACTION_SET_RATING) == 0) return null;

            int ratingType = mMediaController.getRatingType();
            if (ratingType != RatingCompat.RATING_HEART) return null;

            boolean hasHeart = false;
            if (mMetadata != null) {
                RatingCompat rating = mMetadata.getRating(
                        MediaMetadataCompat.METADATA_KEY_USER_RATING);
                hasHeart = rating != null && rating.hasHeart();
            }

            int iconResource = hasHeart ? R.drawable.ic_star_filled : R.drawable.ic_star_empty;
            Bundle extras = new Bundle();
            extras.putBoolean(EXTRA_SET_HEART, !hasHeart);
            return new RawCustomPlaybackAction(iconResource, null, ACTION_SET_RATING, extras);
        }
    }


    /**
     * Wraps the {@link android.media.session.MediaController.TransportControls TransportControls}
     * for a {@link MediaControllerCompat} to send commands.
     */
    // TODO(arnaudberry) does this wrapping make sense since we're still null checking the wrapper?
    // Should we call action methods on the model class instead ?
    public class PlaybackController {
        private final MediaControllerCompat mMediaController;

        private PlaybackController(@Nullable MediaControllerCompat mediaController) {
            mMediaController = mediaController;
        }

        /**
         * Sends a 'play' command to the media source
         */
        public void play() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().play();
            }
        }

        /**
         * Sends a 'skip previews' command to the media source
         */
        public void skipToPrevious() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().skipToPrevious();
            }
        }

        /**
         * Sends a 'skip next' command to the media source
         */
        public void skipToNext() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().skipToNext();
            }
        }

        /**
         * Sends a 'pause' command to the media source
         */
        public void pause() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().pause();
            }
        }

        /**
         * Sends a 'stop' command to the media source
         */
        public void stop() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().stop();
            }
        }

        /**
         * Moves to a new location in the media stream
         *
         * @param pos Position to move to, in milliseconds.
         */
        public void seekTo(long pos) {
            if (mMediaController != null) {
                PlaybackStateCompat oldState = mMediaController.getPlaybackState();
                PlaybackStateCompat newState = new PlaybackStateCompat.Builder(oldState)
                        .setState(oldState.getState(), pos, oldState.getPlaybackSpeed())
                        .build();
                mMediaControllerCallback.onPlaybackStateChanged(newState);

                mMediaController.getTransportControls().seekTo(pos);
            }
        }

        /**
         * Sends a custom action to the media source
         *
         * @param action identifier of the custom action
         * @param extras additional data to send to the media source.
         */
        public void doCustomAction(String action, Bundle extras) {
            if (mMediaController == null) return;
            MediaControllerCompat.TransportControls cntrl = mMediaController.getTransportControls();

            if (ACTION_SET_RATING.equals(action)) {
                boolean setHeart = extras != null && extras.getBoolean(EXTRA_SET_HEART, false);
                cntrl.setRating(RatingCompat.newHeartRating(setHeart));
            } else {
                cntrl.sendCustomAction(action, extras);
            }
        }

        /**
         * Starts playing a given media item. This id corresponds to {@link
         * MediaItemMetadata#getId()}.
         */
        public void playItem(String mediaItemId) {
            if (mMediaController != null) {
                mMediaController.getTransportControls().playFromMediaId(mediaItemId, null);
            }
        }

        /**
         * Skips to a particular item in the media queue. This id is {@link
         * MediaItemMetadata#mQueueId} of the items obtained through {@link
         * PlaybackViewModel#getQueue()}.
         */
        public void skipToQueueItem(long queueId) {
            if (mMediaController != null) {
                mMediaController.getTransportControls().skipToQueueItem(queueId);
            }
        }

        /**
         * Prepares the current media source for playback.
         */
        public void prepare() {
            if (mMediaController != null) {
                mMediaController.getTransportControls().prepare();
            }
        }
    }

    /**
     * Abstract representation of a custom playback action. A custom playback action represents a
     * visual element that can be used to trigger playback actions not included in the standard
     * {@link PlaybackController} class. Custom actions for the current media source are exposed
     * through {@link PlaybackStateWrapper#getCustomActions}
     * <p>
     * Does not contain a {@link Drawable} representation of the icon. Instances of this object
     * should be converted to a {@link CustomPlaybackAction} via {@link
     * RawCustomPlaybackAction#fetchDrawable(Context)} for display.
     */
    public static class RawCustomPlaybackAction {
        // TODO (keyboardr): This class (and associtated translation code) will be merged with
        // CustomPlaybackAction in a future CL.
        /**
         * Icon to display for this custom action
         */
        public final int mIcon;
        /**
         * If true, use the resources from the this package to resolve the icon. If null use our own
         * resources.
         */
        @Nullable
        public final String mPackageName;
        /**
         * Action identifier used to request this action to the media service
         */
        @NonNull
        public final String mAction;
        /**
         * Any additional information to send along with the action identifier
         */
        @Nullable
        public final Bundle mExtras;

        /**
         * Creates a custom action
         */
        public RawCustomPlaybackAction(int icon, String packageName,
                @NonNull String action,
                @Nullable Bundle extras) {
            mIcon = icon;
            mPackageName = packageName;
            mAction = action;
            mExtras = extras;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            RawCustomPlaybackAction that = (RawCustomPlaybackAction) o;

            return mIcon == that.mIcon
                    && Objects.equals(mPackageName, that.mPackageName)
                    && Objects.equals(mAction, that.mAction)
                    && Objects.equals(mExtras, that.mExtras);
        }

        @Override
        public int hashCode() {
            return Objects.hash(mIcon, mPackageName, mAction, mExtras);
        }

        /**
         * Converts this {@link RawCustomPlaybackAction} into a {@link CustomPlaybackAction} by
         * fetching the appropriate drawable for the icon.
         *
         * @param context Context into which the icon will be drawn
         * @return the converted CustomPlaybackAction or null if appropriate {@link Resources}
         * cannot be obtained
         */
        @Nullable
        public CustomPlaybackAction fetchDrawable(@NonNull Context context) {
            Drawable icon;
            if (mPackageName == null) {
                icon = context.getDrawable(mIcon);
            } else {
                Resources resources = getResourcesForPackage(context, mPackageName);
                if (resources == null) {
                    return null;
                } else {
                    // the resources may be from another package. we need to update the
                    // configuration
                    // using the context from the activity so we get the drawable from the
                    // correct DPI
                    // bucket.
                    resources.updateConfiguration(context.getResources().getConfiguration(),
                            context.getResources().getDisplayMetrics());
                    icon = resources.getDrawable(mIcon, null);
                }
            }
            return new CustomPlaybackAction(icon, mAction, mExtras);
        }

        private Resources getResourcesForPackage(Context context, String packageName) {
            try {
                return context.getPackageManager().getResourcesForApplication(packageName);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Unable to get resources for " + packageName);
                return null;
            }
        }
    }

}
