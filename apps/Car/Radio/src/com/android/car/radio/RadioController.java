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

package com.android.car.radio;

import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.hardware.radio.RadioMetadata;
import android.media.session.PlaybackState;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.platform.ProgramInfoExt;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.bands.RegionConfig;
import com.android.car.radio.service.RadioAppService;
import com.android.car.radio.service.RadioAppServiceWrapper;
import com.android.car.radio.service.RadioAppServiceWrapper.ConnectionState;
import com.android.car.radio.storage.RadioStorage;
import com.android.car.radio.util.Log;

import java.util.List;
import java.util.Objects;

/**
 * The main controller of the radio app.
 */
public class RadioController {
    private static final String TAG = "BcRadioApp.controller";

    private final Object mLock = new Object();
    private final RadioActivity mActivity;

    private final RadioAppServiceWrapper mAppService = new RadioAppServiceWrapper();
    private final DisplayController mDisplayController;
    private final RadioStorage mRadioStorage;

    @Nullable private ProgramInfo mCurrentProgram;

    public RadioController(@NonNull RadioActivity activity) {
        mActivity = Objects.requireNonNull(activity);

        mDisplayController = new DisplayController(activity, this);

        mRadioStorage = RadioStorage.getInstance(activity);
        mRadioStorage.getFavorites().observe(activity, this::onFavoritesChanged);

        mAppService.getCurrentProgram().observe(activity, this::onCurrentProgramChanged);
        mAppService.getConnectionState().observe(activity, this::onConnectionStateChanged);

        mDisplayController.setBackwardSeekButtonListener(this::onBackwardSeekClick);
        mDisplayController.setForwardSeekButtonListener(this::onForwardSeekClick);
        mDisplayController.setPlayButtonCallback(this::onSwitchToPlayState);
        mDisplayController.setFavoriteToggleListener(this::onFavoriteToggled);
    }

    private void onConnectionStateChanged(@ConnectionState int state) {
        mDisplayController.setState(state);
        if (state == RadioAppServiceWrapper.STATE_CONNECTED) {
            mActivity.setProgramListSupported(mAppService.isProgramListSupported());
            mActivity.setSupportedProgramTypes(getRegionConfig().getSupportedProgramTypes());
        }
    }

    /**
     * Starts the controller and establishes connection with {@link RadioAppService}.
     */
    public void start() {
        mAppService.bind(mActivity);
    }

    /**
     * Closes {@link RadioAppService} connection and cleans up the resources.
     */
    public void shutdown() {
        mAppService.unbind();
    }

    /**
     * See {@link RadioAppServiceWrapper#getPlaybackState}.
     */
    @NonNull
    public LiveData<Integer> getPlaybackState() {
        return mAppService.getPlaybackState();
    }

    /**
     * See {@link RadioAppServiceWrapper#getCurrentProgram}.
     */
    @NonNull
    public LiveData<ProgramInfo> getCurrentProgram() {
        return mAppService.getCurrentProgram();
    }

    /**
     * See {@link RadioAppServiceWrapper#getProgramList}.
     */
    @NonNull
    public LiveData<List<ProgramInfo>> getProgramList() {
        return mAppService.getProgramList();
    }

    /**
     * Tunes the radio to the given channel.
     */
    public void tune(ProgramSelector sel) {
        mAppService.tune(sel);
    }

    /**
     * Steps the radio tuner in the given direction, see {@link RadioAppServiceWrapper#step}.
     */
    public void step(boolean forward) {
        mAppService.step(forward);
    }

    /**
     * Switch radio band. Currently, this only supports FM and AM bands.
     *
     * @param pt {@link ProgramType} to switch to.
     */
    public void switchBand(@NonNull ProgramType pt) {
        mAppService.switchBand(pt);
    }

    @NonNull
    public RegionConfig getRegionConfig() {
        return mAppService.getRegionConfig();
    }

    private void onFavoritesChanged(List<Program> favorites) {
        synchronized (mLock) {
            if (mCurrentProgram == null) return;
            boolean isFav = RadioStorage.isFavorite(favorites, mCurrentProgram.getSelector());
            mDisplayController.setCurrentIsFavorite(isFav);
        }
    }

    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        synchronized (mLock) {
            mCurrentProgram = Objects.requireNonNull(info);
            ProgramSelector sel = info.getSelector();
            RadioMetadata meta = ProgramInfoExt.getMetadata(info);

            mDisplayController.setChannel(sel);

            mDisplayController.setStationName(
                    ProgramInfoExt.getProgramName(info, ProgramInfoExt.NAME_NO_CHANNEL_FALLBACK));

            if (meta.containsKey(RadioMetadata.METADATA_KEY_TITLE)
                    || meta.containsKey(RadioMetadata.METADATA_KEY_ARTIST)) {
                mDisplayController.setDetails(
                        meta.getString(RadioMetadata.METADATA_KEY_TITLE),
                        meta.getString(RadioMetadata.METADATA_KEY_ARTIST));
            } else {
                mDisplayController.setDetails(meta.getString(RadioMetadata.METADATA_KEY_RDS_RT));
            }

            mDisplayController.setCurrentIsFavorite(mRadioStorage.isFavorite(sel));
        }
    }

    private void onBackwardSeekClick(View v) {
        mDisplayController.startSeekAnimation(false);
        mAppService.seek(false);
    }

    private void onForwardSeekClick(View v) {
        mDisplayController.startSeekAnimation(true);
        mAppService.seek(true);
    }

    private void onSwitchToPlayState(@PlaybackState.State int newPlayState) {
        switch (newPlayState) {
            case PlaybackState.STATE_PLAYING:
                mAppService.setMuted(false);
                break;
            case PlaybackState.STATE_PAUSED:
            case PlaybackState.STATE_STOPPED:
                mAppService.setMuted(true);
                break;
            default:
                Log.e(TAG, "Invalid request to switch to play state " + newPlayState);
        }
    }

    private void onFavoriteToggled(boolean addFavorite) {
        synchronized (mLock) {
            if (mCurrentProgram == null) return;

            if (addFavorite) {
                mRadioStorage.addFavorite(Program.fromProgramInfo(mCurrentProgram));
            } else {
                mRadioStorage.removeFavorite(mCurrentProgram.getSelector());
            }
        }
    }
}
