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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.support.v4.media.session.PlaybackStateCompat;

import androidx.lifecycle.Lifecycle;

import com.android.car.arch.common.testing.CaptureObserver;
import com.android.car.arch.common.testing.InstantTaskExecutorRule;
import com.android.car.arch.common.testing.TestLifecycleOwner;
import com.android.car.media.common.TestConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowLooper;

import java.util.concurrent.TimeUnit;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class ProgressLiveDataTest {
    private static final long START_TIME = 500L;
    private static final long START_PROGRESS = 1000L;
    private static final long MAX_PROGRESS = 100000; // 100 seconds

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();

    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    @Mock
    private PlaybackStateCompat mPlaybackState;
    private long mLastPositionUpdateTime;

    private long mCurrentElapsedTime;
    private ProgressLiveData mProgressLiveData;

    @Before
    public void setUp() {
        ShadowLooper.pauseMainLooper();
        mCurrentElapsedTime = START_TIME;
        mLastPositionUpdateTime = START_TIME;
        when(mPlaybackState.getLastPositionUpdateTime()).thenAnswer(
                invocation -> mLastPositionUpdateTime);
        when(mPlaybackState.getPosition()).thenReturn(START_PROGRESS);
        when(mPlaybackState.getPlaybackSpeed()).thenReturn(1F);
        when(mPlaybackState.getState()).thenReturn(PlaybackStateCompat.STATE_PLAYING);
        mProgressLiveData = new ProgressLiveData(mPlaybackState, MAX_PROGRESS,
                this::getCurrentElapsedTime);
    }

    private long getCurrentElapsedTime() {
        return mCurrentElapsedTime;
    }

    private void advanceElapsedTime(long time) {
        mCurrentElapsedTime += time;
        ShadowLooper.idleMainLooper(time, TimeUnit.MILLISECONDS);
    }

    @Test
    public void testSetsValueOnActive() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(START_PROGRESS);
    }

    @Test
    public void testUnknownProgress() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        when(mPlaybackState.getPosition())
                .thenReturn(PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(
                PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN);
    }

    @Test
    public void testMovesForwardAtNormalSpeed() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);
        progressObserver.reset();

        advanceElapsedTime(ProgressLiveData.UPDATE_INTERVAL_MS);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(
                START_PROGRESS + ProgressLiveData.UPDATE_INTERVAL_MS);
    }

    @Test
    public void testMovesForwardAtCustomSpeed() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);
        float speed = 2F;
        when(mPlaybackState.getPlaybackSpeed()).thenReturn(speed);
        progressObserver.reset();

        advanceElapsedTime(ProgressLiveData.UPDATE_INTERVAL_MS);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(
                (long) (START_PROGRESS + ProgressLiveData.UPDATE_INTERVAL_MS * speed));
    }

    @Test
    public void testDoesntMoveForwardWhenPaused() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);
        when(mPlaybackState.getState()).thenReturn(PlaybackStateCompat.STATE_PAUSED);
        progressObserver.reset();

        advanceElapsedTime(ProgressLiveData.UPDATE_INTERVAL_MS);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(
                START_PROGRESS);
    }

    @Test
    public void testDoesntMoveForwardWhenStopped() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);
        when(mPlaybackState.getState()).thenReturn(PlaybackStateCompat.STATE_STOPPED);
        progressObserver.reset();

        advanceElapsedTime(ProgressLiveData.UPDATE_INTERVAL_MS);

        assertThat(progressObserver.hasBeenNotified()).isTrue();
        assertThat(progressObserver.getObservedValue().getProgress()).isEqualTo(
                START_PROGRESS);
    }

    @Test
    public void testDoesntUpdateWhenInactive() {
        CaptureObserver<PlaybackProgress> progressObserver = new CaptureObserver<>();
        mProgressLiveData.observe(mLifecycleOwner, progressObserver);
        mLifecycleOwner.markState(Lifecycle.State.DESTROYED);
        progressObserver.reset();

        advanceElapsedTime(ProgressLiveData.UPDATE_INTERVAL_MS);

        assertThat(progressObserver.hasBeenNotified()).isFalse();
    }
}
