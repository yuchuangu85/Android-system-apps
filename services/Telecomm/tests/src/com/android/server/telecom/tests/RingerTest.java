/*
 * Copyright (C) 2017 The Android Open Source Project
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

import android.app.NotificationManager;
import android.content.Context;
import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.VolumeShaper;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.os.Parcel;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.telecom.TelecomManager;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.server.telecom.AsyncRingtonePlayer;
import com.android.server.telecom.Call;
import com.android.server.telecom.CallState;
import com.android.server.telecom.InCallController;
import com.android.server.telecom.InCallTonePlayer;
import com.android.server.telecom.Ringer;
import com.android.server.telecom.RingtoneFactory;
import com.android.server.telecom.SystemSettingsUtil;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.stubbing.Answer;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;

@RunWith(JUnit4.class)
public class RingerTest extends TelecomTestCase {
    private static final Uri FAKE_RINGTONE_URI = Uri.parse("content://media/fake/audio/1729");
    private static class UriVibrationEffect extends VibrationEffect {
        final Uri mUri;

        private UriVibrationEffect(Uri uri) {
            mUri = uri;
        }

        @Override
        public void validate() {
            // not needed
        }

        @Override
        public long getDuration() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            // not needed
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            UriVibrationEffect that = (UriVibrationEffect) o;
            return Objects.equals(mUri, that.mUri);
        }
    }

    @Mock InCallTonePlayer.Factory mockPlayerFactory;
    @Mock SystemSettingsUtil mockSystemSettingsUtil;
    @Mock AsyncRingtonePlayer mockRingtonePlayer;
    @Mock RingtoneFactory mockRingtoneFactory;
    @Mock Vibrator mockVibrator;
    @Mock InCallController mockInCallController;
    @Spy Ringer.VibrationEffectProxy spyVibrationEffectProxy;

    @Mock InCallTonePlayer mockTonePlayer;
    @Mock Call mockCall1;
    @Mock Call mockCall2;

    Ringer mRingerUnderTest;
    AudioManager mockAudioManager;
    CompletableFuture<Boolean> mFuture = new CompletableFuture<>();
    CompletableFuture<Void> mRingCompletionFuture = new CompletableFuture<>();

    @Override
    @Before
    public void setUp() throws Exception {
        super.setUp();
        mContext = mComponentContextFixture.getTestDouble().getApplicationContext();
        doAnswer(invocation -> {
            Uri ringtoneUriForEffect = invocation.getArgument(0);
            return new UriVibrationEffect(ringtoneUriForEffect);
        }).when(spyVibrationEffectProxy).get(any(), any());
        when(mockPlayerFactory.createPlayer(anyInt())).thenReturn(mockTonePlayer);
        mockAudioManager =
                (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockSystemSettingsUtil.isHapticPlaybackSupported(any(Context.class))).thenReturn(true);
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        when(mockTonePlayer.startTone()).thenReturn(true);
        when(notificationManager.matchesCallFilter(any(Bundle.class))).thenReturn(true);
        when(mockRingtoneFactory.hasHapticChannels(any(Ringtone.class))).thenReturn(false);
        when(mockRingtonePlayer.play(any(RingtoneFactory.class), any(Call.class),
                nullable(VolumeShaper.Configuration.class), anyBoolean())).thenReturn(mFuture);
        mRingerUnderTest = new Ringer(mockPlayerFactory, mContext, mockSystemSettingsUtil,
                mockRingtonePlayer, mockRingtoneFactory, mockVibrator, spyVibrationEffectProxy,
                mockInCallController);
        when(mockCall1.getState()).thenReturn(CallState.RINGING);
        when(mockCall2.getState()).thenReturn(CallState.RINGING);
        mRingerUnderTest.setBlockOnRingingFuture(mRingCompletionFuture);
    }

    @SmallTest
    @Test
    public void testNoActionInTheaterMode() {
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mFuture.complete(false); // not using audio coupled haptics
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockSystemSettingsUtil.isTheaterModeOn(any(Context.class))).thenReturn(true);
        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        verify(mockTonePlayer, never()).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                eq(null), eq(false));
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWithExternalRinger() {
        mFuture.complete(false); // not using audio coupled haptics
        Bundle externalRingerExtra = new Bundle();
        externalRingerExtra.putBoolean(TelecomManager.EXTRA_CALL_EXTERNAL_RINGER, true);
        when(mockCall1.getIntentExtras()).thenReturn(externalRingerExtra);
        when(mockCall2.getIntentExtras()).thenReturn(externalRingerExtra);
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        verify(mockTonePlayer, never()).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWhenDialerRings() throws Exception {
        mFuture.complete(false); // not using audio coupled haptics
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockInCallController.doesConnectedDialerSupportRinging()).thenReturn(true);
        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer, never()).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testAudioFocusStillAcquiredWhenDialerRings() throws Exception {
        mFuture.complete(false); // not using audio coupled haptics
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockInCallController.doesConnectedDialerSupportRinging()).thenReturn(true);
        ensureRingerIsAudible();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer, never()).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoActionWhenCallIsSelfManaged() {
        mFuture.complete(false); // not using audio coupled haptics
        // Start call waiting to make sure that it doesn't stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockCall2.isSelfManaged()).thenReturn(true);
        // We do want to acquire audio focus when self-managed
        assertTrue(mRingerUnderTest.startRinging(mockCall2, true));
        verify(mockTonePlayer, never()).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testCallWaitingButNoRingForSpecificContacts() {
        mFuture.complete(false); // not using audio coupled haptics
        NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        when(notificationManager.matchesCallFilter(any(Bundle.class))).thenReturn(false);
        // Start call waiting to make sure that it does stop when we start ringing
        mRingerUnderTest.startCallWaiting(mockCall1);
        verify(mockTonePlayer).startTone();

        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testNoVibrateDueToAudioCoupledHaptics() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        enableVibrationWhenRinging();
        // Pretend we're using audio coupled haptics.
        mFuture.complete(true);
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer).play(
                any(RingtoneFactory.class), any(Call.class), isNull(), eq(true));
        verify(mockVibrator, never()).vibrate(any(VibrationEffect.class),
                any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testVibrateButNoRingForNullRingtone() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(null);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationWhenRinging();
        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator).vibrate(eq(mRingerUnderTest.mDefaultVibrationEffect),
                any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testVibrateButNoRingForSilentRingtone() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(mockRingtone);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationWhenRinging();
        assertFalse(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator).vibrate(eq(mRingerUnderTest.mDefaultVibrationEffect),
                any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testStopRingingBeforeHapticsLookupComplete() throws Exception {
        enableVibrationWhenRinging();
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockRingtoneFactory.getRingtone(nullable(Call.class))).thenReturn(mockRingtone);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);

        mRingerUnderTest.startRinging(mockCall1, false);
        // Make sure we haven't started the vibrator yet, but have started ringing.
        verify(mockRingtonePlayer).play(nullable(RingtoneFactory.class), nullable(Call.class),
                nullable(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never()).vibrate(nullable(VibrationEffect.class),
                nullable(AudioAttributes.class));
        // Simulate something stopping the ringer
        mRingerUnderTest.stopRinging();
        verify(mockRingtonePlayer).stop();
        verify(mockVibrator, never()).cancel();
        // Simulate the haptics computation finishing
        mFuture.complete(false);
        // Then make sure that we don't actually start vibrating.
        verify(mockVibrator, never()).vibrate(nullable(VibrationEffect.class),
                nullable(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testCustomVibrationForRingtone() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(mockRingtone);
        when(mockRingtone.getUri()).thenReturn(FAKE_RINGTONE_URI);
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationWhenRinging();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer).play(any(RingtoneFactory.class), any(Call.class), eq(null),
                eq(true));
        verify(mockVibrator).vibrate(eq(spyVibrationEffectProxy.get(FAKE_RINGTONE_URI, mContext)),
                any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testRingAndNoVibrate() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationOnlyWhenNotRinging();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer).play(any(RingtoneFactory.class), any(Call.class), eq(null),
                eq(false));
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testRingWithRampingRinger() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        ensureRingerIsAudible();
        enableRampingRinger();
        enableRampingRingerFromDeviceConfig();
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationWhenRinging();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, false));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer).play(
            any(RingtoneFactory.class), any(Call.class), any(VolumeShaper.Configuration.class),
                eq(true));
    }

    @SmallTest
    @Test
    public void testSilentRingWithHfpStillAcquiresFocus1() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(mockRingtone);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationOnlyWhenNotRinging();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, true));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    @SmallTest
    @Test
    public void testSilentRingWithHfpStillAcquiresFocus2() throws Exception {
        mRingerUnderTest.startCallWaiting(mockCall1);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(null);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(0);
        mFuture.complete(false); // not using audio coupled haptics
        enableVibrationOnlyWhenNotRinging();
        assertTrue(mRingerUnderTest.startRinging(mockCall2, true));
        mRingCompletionFuture.get();
        verify(mockTonePlayer).stopTone();
        verify(mockRingtonePlayer, never()).play(any(RingtoneFactory.class), any(Call.class),
                any(VolumeShaper.Configuration.class), anyBoolean());
        verify(mockVibrator, never())
                .vibrate(any(VibrationEffect.class), any(AudioAttributes.class));
    }

    private void ensureRingerIsAudible() {
        Ringtone mockRingtone = mock(Ringtone.class);
        when(mockRingtoneFactory.getRingtone(any(Call.class))).thenReturn(mockRingtone);
        when(mockAudioManager.getRingerModeInternal()).thenReturn(AudioManager.RINGER_MODE_NORMAL);
        when(mockAudioManager.getStreamVolume(AudioManager.STREAM_RING)).thenReturn(100);
    }

    private void enableVibrationWhenRinging() {
        when(mockVibrator.hasVibrator()).thenReturn(true);
        when(mockSystemSettingsUtil.canVibrateWhenRinging(any(Context.class))).thenReturn(true);
    }

    private void enableVibrationOnlyWhenNotRinging() {
        when(mockVibrator.hasVibrator()).thenReturn(true);
        when(mockSystemSettingsUtil.canVibrateWhenRinging(any(Context.class))).thenReturn(false);
    }

    private void enableRampingRinger() {
        when(mockSystemSettingsUtil.applyRampingRinger(any(Context.class))).thenReturn(true);
    }

    private void enableRampingRingerFromDeviceConfig() {
        when(mockSystemSettingsUtil.enableRampingRingerFromDeviceConfig()).thenReturn(true);
    }
}
