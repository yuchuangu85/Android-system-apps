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
package com.android.tv.common.support.tis;

import android.annotation.TargetApi;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputManager;
import android.media.tv.TvInputService.Session;
import android.media.tv.TvTrackInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.support.annotation.FloatRange;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.View;
import java.util.List;

/**
 * Custom {@link android.media.tv.TvInputService.Session} class that uses delegation and a callback
 * to separate it from the TvInputService for easier testing.
 */
public abstract class TifSession {

    private final TifSessionCallbacks callback;

    /**
     * Creates TV Input Framework Session with the given callback.
     *
     * <p>The callback is used to pass notification to the actual {@link
     * android.media.tv.TvInputService.Session}.
     *
     * <p>Pass a mock callback for tests.
     */
    protected TifSession(TifSessionCallbacks callback) {
        this.callback = callback;
    }

    /**
     * Called after this session had been created and the callback is attached.
     *
     * <p>Do not call notify methods in the constructor, instead call them here if needed at
     * creation time. eg @{@link Session#notifyTimeShiftStatusChanged(int)}.
     */
    public void onSessionCreated() {}

    /** @see Session#onRelease() */
    public void onRelease() {}

    /** @see Session#onSetSurface(Surface) */
    public abstract boolean onSetSurface(@Nullable Surface surface);

    /** @see Session#onSurfaceChanged(int, int, int) */
    public abstract void onSurfaceChanged(int format, int width, int height);

    /** @see Session#onSetStreamVolume(float) */
    public abstract void onSetStreamVolume(@FloatRange(from = 0.0, to = 1.0) float volume);

    /** @see Session#onTune(Uri) */
    public abstract boolean onTune(Uri channelUri);

    /** @see Session#onSetCaptionEnabled(boolean) */
    public abstract void onSetCaptionEnabled(boolean enabled);

    /** @see Session#onUnblockContent(TvContentRating) */
    public abstract void onUnblockContent(TvContentRating unblockedRating);

    /** @see Session#onTimeShiftGetCurrentPosition() */
    @TargetApi(Build.VERSION_CODES.M)
    public long onTimeShiftGetCurrentPosition() {
        return TvInputManager.TIME_SHIFT_INVALID_TIME;
    }

    /** @see Session#onTimeShiftGetStartPosition() */
    @TargetApi(Build.VERSION_CODES.M)
    public long onTimeShiftGetStartPosition() {
        return TvInputManager.TIME_SHIFT_INVALID_TIME;
    }

    /** @see Session#onTimeShiftPause() */
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftPause() {}

    /** @see Session#onTimeShiftResume() */
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftResume() {}

    /** @see Session#onTimeShiftSeekTo(long) */
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftSeekTo(long timeMs) {}

    /** @see Session#onTimeShiftSetPlaybackParams(PlaybackParams) */
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftSetPlaybackParams(PlaybackParams params) {}

    public void onParentalControlsChanged() {}

    /** @see Session#notifyChannelRetuned(Uri) */
    public final void notifyChannelRetuned(final Uri channelUri) {
        callback.notifyChannelRetuned(channelUri);
    }

    /** @see Session#notifyTracksChanged(List) */
    public final void notifyTracksChanged(final List<TvTrackInfo> tracks) {
        callback.notifyTracksChanged(tracks);
    }

    /** @see Session#notifyTrackSelected(int, String) */
    public final void notifyTrackSelected(final int type, final String trackId) {
        callback.notifyTrackSelected(type, trackId);
    }

    /** @see Session#notifyVideoAvailable() */
    public final void notifyVideoAvailable() {
        callback.notifyVideoAvailable();
    }

    /** @see Session#notifyVideoUnavailable(int) */
    public final void notifyVideoUnavailable(final int reason) {
        callback.notifyVideoUnavailable(reason);
    }

    /** @see Session#notifyContentAllowed() */
    public final void notifyContentAllowed() {
        callback.notifyContentAllowed();
    }

    /** @see Session#notifyContentBlocked(TvContentRating) */
    public final void notifyContentBlocked(@NonNull final TvContentRating rating) {
        callback.notifyContentBlocked(rating);
    }

    /** @see Session#notifyTimeShiftStatusChanged(int) */
    @TargetApi(VERSION_CODES.M)
    public final void notifyTimeShiftStatusChanged(final int status) {
        callback.notifyTimeShiftStatusChanged(status);
    }

    /** @see Session#setOverlayViewEnabled(boolean) */
    public void setOverlayViewEnabled(boolean enabled) {
        callback.setOverlayViewEnabled(enabled);
    }

    /** @see Session#onCreateOverlayView() */
    public View onCreateOverlayView() {
        return null;
    }

    /** @see Session#onOverlayViewSizeChanged(int, int) */
    public void onOverlayViewSizeChanged(int width, int height) {}

    /**
     * Callbacks used to notify the {@link android.media.tv.TvInputService.Session}.
     *
     * <p>This is implemented internally by {@link WrappedSession}, and can be mocked for tests.
     */
    public interface TifSessionCallbacks {
        /** @see Session#notifyChannelRetuned(Uri) */
        void notifyChannelRetuned(final Uri channelUri);
        /** @see Session#notifyTracksChanged(List) */
        void notifyTracksChanged(final List<TvTrackInfo> tracks);
        /** @see Session#notifyTrackSelected(int, String) */
        void notifyTrackSelected(final int type, final String trackId);
        /** @see Session#notifyVideoAvailable() */
        void notifyVideoAvailable();
        /** @see Session#notifyVideoUnavailable(int) */
        void notifyVideoUnavailable(final int reason);
        /** @see Session#notifyContentAllowed() */
        void notifyContentAllowed();
        /** @see Session#notifyContentBlocked(TvContentRating) */
        void notifyContentBlocked(@NonNull final TvContentRating rating);
        /** @see Session#notifyTimeShiftStatusChanged(int) */
        @TargetApi(VERSION_CODES.M)
        void notifyTimeShiftStatusChanged(final int status);
        /** @see Session#setOverlayViewEnabled(boolean) */
        void setOverlayViewEnabled(boolean enabled);
    }

    /**
     * Creates a {@link TifSession}.
     *
     * <p>This is used by {@link WrappedSession} to create the desired {@code TifSession}. Should be
     * used with <a href="http://go/autofactory">go/autofactory</a>.
     */
    public interface TifSessionFactory {
        TifSession create(TifSessionCallbacks callbacks, String inputId);
    }
}
