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
import android.content.Context;
import android.media.PlaybackParams;
import android.media.tv.TvContentRating;
import android.media.tv.TvInputService.Session;
import android.net.Uri;
import android.os.Build;
import android.support.annotation.FloatRange;
import android.support.annotation.Nullable;
import android.view.Surface;
import android.view.View;
import com.android.tv.common.support.tis.TifSession.TifSessionCallbacks;
import com.android.tv.common.support.tis.TifSession.TifSessionFactory;

/**
 * Delegates all call to a {@link TifSession} and removes the session from the {@link
 * SessionManager} when {@link Session#onRelease()} is called.
 */
final class WrappedSession extends Session implements TifSessionCallbacks {

    private final SessionManager listener;
    private final TifSession delegate;

    WrappedSession(
            Context context,
            SessionManager sessionManager,
            TifSessionFactory sessionFactory,
            String inputId) {
        super(context);
        this.listener = sessionManager;
        this.delegate = sessionFactory.create(this, inputId);
    }

    @Override
    public void onRelease() {
        delegate.onRelease();
        listener.removeSession(this);
    }

    @Override
    public boolean onSetSurface(@Nullable Surface surface) {
        return delegate.onSetSurface(surface);
    }

    @Override
    public void onSurfaceChanged(int format, int width, int height) {
        delegate.onSurfaceChanged(format, width, height);
    }

    @Override
    public void onSetStreamVolume(@FloatRange(from = 0.0, to = 1.0) float volume) {
        delegate.onSetStreamVolume(volume);
    }

    @Override
    public boolean onTune(Uri channelUri) {
        return delegate.onTune(channelUri);
    }

    @Override
    public void onSetCaptionEnabled(boolean enabled) {
        delegate.onSetCaptionEnabled(enabled);
    }

    @Override
    public void onUnblockContent(TvContentRating unblockedRating) {
        delegate.onUnblockContent(unblockedRating);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public long onTimeShiftGetCurrentPosition() {
        return delegate.onTimeShiftGetCurrentPosition();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public long onTimeShiftGetStartPosition() {
        return delegate.onTimeShiftGetStartPosition();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftPause() {
        delegate.onTimeShiftPause();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftResume() {
        delegate.onTimeShiftResume();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftSeekTo(long timeMs) {
        delegate.onTimeShiftSeekTo(timeMs);
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void onTimeShiftSetPlaybackParams(PlaybackParams params) {
        delegate.onTimeShiftSetPlaybackParams(params);
    }

    public void onParentalControlsChanged() {
        delegate.onParentalControlsChanged();
    }

    @Override
    @TargetApi(Build.VERSION_CODES.M)
    public void notifyTimeShiftStatusChanged(int status) {
        // TODO(nchalko): why is the required for call from TisSession.onSessionCreated to work
        super.notifyTimeShiftStatusChanged(status);
    }

    @Override
    public void setOverlayViewEnabled(boolean enabled) {
        super.setOverlayViewEnabled(enabled);
    }

    @Override
    public View onCreateOverlayView() {
        return delegate.onCreateOverlayView();
    }

    @Override
    public void onOverlayViewSizeChanged(int width, int height) {
        delegate.onOverlayViewSizeChanged(width, height);
    }
}
