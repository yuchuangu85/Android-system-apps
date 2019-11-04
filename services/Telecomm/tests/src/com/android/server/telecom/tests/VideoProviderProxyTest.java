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

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.IBinder;
import android.telecom.VideoProfile;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.internal.telecom.IVideoProvider;
import com.android.server.telecom.Analytics;
import com.android.server.telecom.Call;
import com.android.server.telecom.CurrentUserProxy;
import com.android.server.telecom.TelecomSystem;
import com.android.server.telecom.VideoProviderProxy;

import org.junit.Before;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

public class VideoProviderProxyTest extends TelecomTestCase {

    private TelecomSystem.SyncRoot mLock;
    private VideoProviderProxy mVideoProviderProxy;
    @Mock private IVideoProvider mVideoProvider;
    @Mock private IBinder mIBinder;
    @Mock private Call mCall;
    @Mock private Analytics.CallInfo mCallInfo;
    @Mock private CurrentUserProxy mCurrentUserProxy;
    @Mock private VideoProviderProxy.Listener mListener;

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        MockitoAnnotations.initMocks(this);
        mLock = new TelecomSystem.SyncRoot() { };

        when(mVideoProvider.asBinder()).thenReturn(mIBinder);
        doNothing().when(mIBinder).linkToDeath(any(), anyInt());
        when(mCall.getAnalytics()).thenReturn(mCallInfo);
        doNothing().when(mCallInfo).addVideoEvent(anyInt(), anyInt());
        doNothing().when(mCall).maybeEnableSpeakerForVideoUpgrade(anyInt());
        mVideoProviderProxy = new VideoProviderProxy(mLock, mVideoProvider, mCall,
                mCurrentUserProxy);
        mVideoProviderProxy.addListener(mListener);
    }

    /**
     * Tests the case where we receive a request to upgrade to video, except:
     * 1. Phone account says we support video.
     * 2. Call says we don't support video.
     *
     * Ensures that we send back a response immediately to indicate the call should remain as
     * audio-only.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testReceiveUpgradeRequestWhenLocalDoesntSupportVideo() throws Exception {
        // Given a call which supports video at the phone account level, but is not currently
        // marked as supporting video locally.
        when(mCall.isLocallyVideoCapable()).thenReturn(false);
        when(mCall.isVideoCallingSupportedByPhoneAccount()).thenReturn(true);

        // Simulate receiving a request to upgrade to video.
        mVideoProviderProxy.getVideoCallListenerBinder().receiveSessionModifyRequest(
                new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));

        // Make sure that we send back a response rejecting the request.
        ArgumentCaptor<VideoProfile> capturedProfile = ArgumentCaptor.forClass(VideoProfile.class);
        verify(mVideoProvider).sendSessionModifyResponse(capturedProfile.capture());
        assertEquals(VideoProfile.STATE_AUDIO_ONLY, capturedProfile.getValue().getVideoState());
    }

    /**
     * Tests the case where we receive a request to upgrade to video and video is supported.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testReceiveUpgradeRequestWhenVideoIsSupported() throws Exception {
        // Given a call which supports video at the phone account level, and is currently marked as
        // supporting video locally.
        when(mCall.isLocallyVideoCapable()).thenReturn(true);
        when(mCall.isVideoCallingSupportedByPhoneAccount()).thenReturn(true);

        // Simulate receiving a request to upgrade to video.
        mVideoProviderProxy.getVideoCallListenerBinder().receiveSessionModifyRequest(
                new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));

        // Ensure it gets proxied back to the caller.

        ArgumentCaptor<VideoProfile> capturedProfile = ArgumentCaptor.forClass(VideoProfile.class);
        verify(mListener).onSessionModifyRequestReceived(any(), capturedProfile.capture());
        assertEquals(VideoProfile.STATE_BIDIRECTIONAL, capturedProfile.getValue().getVideoState());
    }

    /**
     * Tests the case where dialer requests an upgrade to video; we should try to change to speaker.
     * @throws Exception
     */
    @SmallTest
    @Test
    public void testTryToEnableSpeakerOnVideoUpgrade() throws Exception {
        mVideoProviderProxy.onSendSessionModifyRequest(
                new VideoProfile(VideoProfile.STATE_AUDIO_ONLY),
                new VideoProfile(VideoProfile.STATE_BIDIRECTIONAL));
        verify(mCall).maybeEnableSpeakerForVideoUpgrade(eq(VideoProfile.STATE_BIDIRECTIONAL));
    }
}
