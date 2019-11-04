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

package com.android.server.telecom.tests;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.media.MediaPlayer;
import android.media.ToneGenerator;
import android.test.suitebuilder.annotation.SmallTest;

import androidx.test.filters.FlakyTest;

import com.android.server.telecom.CallAudioManager;
import com.android.server.telecom.CallAudioRoutePeripheralAdapter;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.TelecomSystem;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;

@RunWith(JUnit4.class)
public class InCallTonePlayerTest extends TelecomTestCase {

    private InCallTonePlayer.Factory mFactory;

    @Mock
    private CallAudioRoutePeripheralAdapter mCallAudioRoutePeripheralAdapter;

    @Mock
    private TelecomSystem.SyncRoot mLock;

    @Mock
    private ToneGenerator mToneGenerator;

    @Mock
    private InCallTonePlayer.ToneGeneratorFactory mToneGeneratorFactory;

    private InCallTonePlayer.MediaPlayerAdapter mMediaPlayerAdapter =
            new InCallTonePlayer.MediaPlayerAdapter() {
        private MediaPlayer.OnCompletionListener mListener;

        @Override
        public void setLooping(boolean isLooping) {
            // Do nothing.
        }

        @Override
        public void setOnCompletionListener(MediaPlayer.OnCompletionListener listener) {
            mListener = listener;
        }

        @Override
        public void start() {
            mListener.onCompletion(null);
        }

        @Override
        public void release() {
            // Do nothing.
        }

        @Override
        public int getDuration() {
            return 0;
        }
    };

    @Mock
    private InCallTonePlayer.MediaPlayerFactory mMediaPlayerFactory;

    @Mock
    private InCallTonePlayer.AudioManagerAdapter mAudioManagerAdapter;

    @Mock
    private CallAudioManager mCallAudioManager;

    private InCallTonePlayer mInCallTonePlayer;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();

        when(mToneGeneratorFactory.get(anyInt(), anyInt())).thenReturn(mToneGenerator);
        when(mMediaPlayerFactory.get(anyInt(), any())).thenReturn(mMediaPlayerAdapter);

        mFactory = new InCallTonePlayer.Factory(mCallAudioRoutePeripheralAdapter, mLock,
                mToneGeneratorFactory, mMediaPlayerFactory, mAudioManagerAdapter);
        mFactory.setCallAudioManager(mCallAudioManager);
        mInCallTonePlayer = mFactory.createPlayer(InCallTonePlayer.TONE_CALL_ENDED);
    }

    @Override
    @After
    public void tearDown() throws Exception {
        super.tearDown();
        mInCallTonePlayer.cleanup();
    }

    @SmallTest
    @Test
    public void testNoEndCallToneInSilence() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(false);
        assertFalse(mInCallTonePlayer.startTone());

        // Verify we didn't play a tone.
        verify(mCallAudioManager, never()).setIsTonePlaying(eq(true));
        verify(mMediaPlayerFactory, never()).get(anyInt(), any());
    }

    @FlakyTest
    @SmallTest
    @Test
    public void testEndCallToneWhenNotSilenced() {
        when(mAudioManagerAdapter.isVolumeOverZero()).thenReturn(true);
        assertTrue(mInCallTonePlayer.startTone());

        // Verify we did play a tone.
        verify(mMediaPlayerFactory, timeout(5000)).get(anyInt(), any());
        verify(mCallAudioManager).setIsTonePlaying(eq(true));
    }
}
