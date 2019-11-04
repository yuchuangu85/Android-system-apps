/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */
package com.android.tv.dvr;

import android.content.Context;
import android.media.PlaybackParams;
import android.media.session.PlaybackState;
import android.media.tv.TvTrackInfo;
import android.media.tv.TvView;
import android.net.Uri;
import android.support.annotation.Nullable;
import com.android.tv.InputSessionManager;
import com.android.tv.InputSessionManager.TvViewSession;
import com.android.tv.TvSingletons;
import com.android.tv.common.compat.TvViewCompat.TvInputCallbackCompat;
import com.android.tv.dvr.ui.playback.DvrPlayer;
import com.android.tv.ui.AppLayerTvView;
import com.android.tv.ui.api.TunableTvViewPlayingApi;
import java.util.List;

/**
 * A {@link TvView} wrapper to handle events and TvView session.
 */
public class DvrTvView implements TunableTvViewPlayingApi {

    private final AppLayerTvView mTvView;
    private DvrPlayer mDvrPlayer;
    private String mInputId;
    private Uri mRecordedProgramUri;
    private TvInputCallbackCompat mTvInputCallbackCompat;
    private InputSessionManager mInputSessionManager;
    private TvViewSession mSession;

    public DvrTvView(Context context, AppLayerTvView tvView, DvrPlayer player) {
        mTvView = tvView;
        mDvrPlayer = player;
        mInputSessionManager = TvSingletons.getSingletons(context).getInputSessionManager();
    }

    @Override
    public boolean isPlaying() {
        return mDvrPlayer.getPlaybackState() == PlaybackState.STATE_PLAYING;
    }

    @Override
    public void setStreamVolume(float volume) {
        mTvView.setStreamVolume(volume);
    }

    @Override
    public void setTimeShiftListener(TimeShiftListener listener) {
        // TimeShiftListener is never called from DvrTvView because TimeShift is always available
        // and onRecordStartTimeChanged is not called during playback.
    }

    @Override
    public boolean isTimeShiftAvailable() {
        return true;
    }

    @Override
    public void timeShiftPlay() {
        if (mInputId != null && mRecordedProgramUri != null) {
            mTvView.timeShiftPlay(mInputId, mRecordedProgramUri);
        }
    }

    public void timeShiftPlay(String inputId, Uri recordedProgramUri) {
        mInputId = inputId;
        mRecordedProgramUri = recordedProgramUri;
        mSession.timeShiftPlay(inputId, recordedProgramUri);
    }

    @Override
    public void timeShiftPause() {
        mTvView.timeShiftPause();
    }

    @Override
    public void timeShiftRewind(int speed) {
        PlaybackParams params = new PlaybackParams();
        params.setSpeed(speed * -1);
        mTvView.timeShiftSetPlaybackParams(params);
    }

    @Override
    public void timeShiftFastForward(int speed) {
        PlaybackParams params = new PlaybackParams();
        params.setSpeed(speed);
        mTvView.timeShiftSetPlaybackParams(params);
    }

    @Override
    public void timeShiftSeekTo(long timeMs) {
        mTvView.timeShiftSeekTo(timeMs);
    }

    @Override
    public long timeShiftGetCurrentPositionMs() {
        return mDvrPlayer.getPlaybackPosition();
    }

    public void setCaptionEnabled(boolean enabled) {
        mTvView.setCaptionEnabled(enabled);
    }

    public void timeShiftResume() {
        mTvView.timeShiftResume();
    }

    public void reset() {
        mSession.reset();
    }

    public List<TvTrackInfo> getTracks(int type) {
        return mTvView.getTracks(type);
    }

    public void selectTrack(int type, String trackId) {
        mTvView.selectTrack(type, trackId);
    }

    public void timeShiftSetPlaybackParams(PlaybackParams params) {
        mTvView.timeShiftSetPlaybackParams(params);
    }

    public void setTimeShiftPositionCallback(@Nullable TvView.TimeShiftPositionCallback callback) {
        mTvView.setTimeShiftPositionCallback(callback);
    }

    public void setCallback(@Nullable TvInputCallbackCompat callback) {
        mTvInputCallbackCompat = callback;
        mTvView.setCallback(callback);
    }

    public void init() {
        mSession = mInputSessionManager.createTvViewSession(mTvView, this, mTvInputCallbackCompat);
    }

    public void release() {
        mInputSessionManager.releaseTvViewSession(mSession);
        mInputSessionManager = null;
        mDvrPlayer = null;
    }
}
