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

package com.android.car.audio;

import static org.junit.Assert.assertEquals;

import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class AudioFocusTest {

    private static final String TAG = "AudioFocusTest";

    private static final int TEST_TIMING_TOLERANCE_MS = 100;

    // ContextNumber.INVALID
    private static final AudioAttributes ATTR_VIRTUAL_SOURCE = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VIRTUAL_SOURCE)
            .setContentType(AudioAttributes.USAGE_VIRTUAL_SOURCE)
            .build();
    // ContextNumber.MUSIC
    private static final AudioAttributes ATTR_MEDIA = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build();
    // ContextNumber.NAVIGATION
    private static final AudioAttributes ATTR_DRIVE_DIR = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // ContextNumber.VOICE_COMMAND
    private static final AudioAttributes ATTR_A11Y = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // ContextNumber.CALL_RING
    private static final AudioAttributes ATTR_RINGTONE = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // ContextNumber.CALL
    private static final AudioAttributes ATTR_VOICE_COM = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();
    // ContextNumber.ALARM
    private static final AudioAttributes ATTR_ALARM = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // ContextNumber.NOTIFICATION
    private static final AudioAttributes ATTR_NOTIFICATION = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_NOTIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build();
    // ContextNumber.SYSTEM_SOUND
    private static final AudioAttributes ATTR_A11Y_NOTIFICATION = new AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build();

    private AudioManager mAudioManager;

    @Before
    public void setUp() {
        mAudioManager = new AudioManager(ApplicationProvider.getApplicationContext());
    }

    @Test
    public void individualAttributeFocusRequest_focusRequestGranted() throws Exception {
        // Make sure each usage is able to request and release audio focus individually
        requestAndLoseFocusForAttribute(ATTR_VIRTUAL_SOURCE);
        requestAndLoseFocusForAttribute(ATTR_MEDIA);
        requestAndLoseFocusForAttribute(ATTR_DRIVE_DIR);
        requestAndLoseFocusForAttribute(ATTR_A11Y);
        requestAndLoseFocusForAttribute(ATTR_RINGTONE);
        requestAndLoseFocusForAttribute(ATTR_VOICE_COM);
        requestAndLoseFocusForAttribute(ATTR_ALARM);
        requestAndLoseFocusForAttribute(ATTR_NOTIFICATION);
        requestAndLoseFocusForAttribute(ATTR_A11Y_NOTIFICATION);
    }

    @Test
    public void exclusiveInteractionsForFocusGain_requestGrantedAndFocusLossSent()
            throws Exception {
        // For each interaction the focus request is granted and on the second request
        // focus lost is dispatched to the first focus listener

        // Test Exclusive interactions with audio focus gain request without pause
        // instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN, false);
        // Test Exclusive interactions with audio focus gain request with pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN, true);
    }

    @Test
    public void exclusiveInteractionsTransient_requestGrantedAndFocusLossSent()
            throws Exception {
        // For each interaction the focus request is granted and on the second request
        // focus lost transient is dispatched to the first focus listener

        // Test Exclusive interactions with audio focus gain transient request
        // without pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, false);
        // Test Exclusive interactions with audio focus gain transient request
        // with pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, true);
    }

    @Test
    public void exclusiveInteractionsTransientMayDuck_requestGrantedAndFocusLossSent()
            throws Exception {
        // For each interaction the focus request is granted and on the second request
        // focus lost transient is dispatched to the first focus listener

        // Test exclusive interactions with audio focus transient may duck focus request
        // without pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                false);
        // Test exclusive interactions with audio focus transient may duck focus request
        // with pause instead of ducking
        testExclusiveInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK,
                true);
    }

    @Test
    public void rejectedInteractions_focusRequestRejected() throws Exception {
        // Test different paired interaction between different usages
        // for each interaction pair the first focus request will be granted but the second
        // will be rejected
        int interaction = CarAudioFocus.INTERACTION_REJECT;
        int gain = AudioManager.AUDIOFOCUS_GAIN;
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_VIRTUAL_SOURCE, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_MEDIA, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_DRIVE_DIR, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_A11Y, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_RINGTONE, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_VOICE_COM, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_ALARM, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_NOTIFICATION, interaction, gain, false);
        testInteraction(ATTR_VIRTUAL_SOURCE, ATTR_A11Y_NOTIFICATION, interaction, gain, false);

        testInteraction(ATTR_MEDIA, ATTR_VIRTUAL_SOURCE, interaction, gain, false);

        testInteraction(ATTR_DRIVE_DIR, ATTR_VIRTUAL_SOURCE, interaction, gain, false);

        testInteraction(ATTR_A11Y, ATTR_VIRTUAL_SOURCE, interaction, gain, false);
        testInteraction(ATTR_A11Y, ATTR_DRIVE_DIR, interaction, gain, false);
        testInteraction(ATTR_A11Y, ATTR_NOTIFICATION, interaction, gain, false);
        testInteraction(ATTR_A11Y, ATTR_A11Y_NOTIFICATION, interaction, gain, false);

        testInteraction(ATTR_RINGTONE, ATTR_VIRTUAL_SOURCE, interaction, gain, false);
        testInteraction(ATTR_RINGTONE, ATTR_MEDIA, interaction, gain, false);
        testInteraction(ATTR_RINGTONE, ATTR_ALARM, interaction, gain, false);
        testInteraction(ATTR_RINGTONE, ATTR_NOTIFICATION, interaction, gain, false);

        testInteraction(ATTR_VOICE_COM, ATTR_VIRTUAL_SOURCE, interaction, gain, false);
        testInteraction(ATTR_VOICE_COM, ATTR_MEDIA, interaction, gain, false);
        testInteraction(ATTR_VOICE_COM, ATTR_A11Y, interaction, gain, false);

        testInteraction(ATTR_ALARM, ATTR_VIRTUAL_SOURCE, interaction, gain, false);

        testInteraction(ATTR_NOTIFICATION, ATTR_VIRTUAL_SOURCE, interaction, gain, false);

        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_VIRTUAL_SOURCE, interaction, gain, false);
    }

    @Test
    public void concurrentInteractionsFocusGain_requestGrantedAndFocusLossSent() throws Exception {
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by two usages.
        // The focus request will be granted for both and on the second focus request focus
        // lost will dispatched to the first focus listener listener.
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN, false);
    }

    @Test
    public void concurrentInteractionsTransientGain_requestGrantedAndFocusLossTransientSent()
            throws Exception {
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by first usage and focus gain transient
        // is requested by second usage.
        // The focus request will be granted for both and on the second focus request focus
        // lost transient will dispatched to the first focus listener listener.
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, false);
        // Repeat the test this time with pause for ducking on first listener
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT, true);
    }

    @Test
    public void concurrentInteractionsTransientGainMayDuck_requestGrantedAndNoFocusLossSent()
            throws Exception {
        // Test concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        // For this test permanent focus gain is requested by first usage and focus gain transient
        // may duck is requested by second usage.
        // The focus request will be granted for both but no focus lost is sent to the first focus
        // listener, as each usage actually has shared focus and  should play at the same time.
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, false);
        // Test the same behaviour but this time with pause for ducking on the first focus listener
        testConcurrentInteractions(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK, true);
    }

    private void testConcurrentInteractions(int gain, boolean pauseForDucking)
            throws Exception {
        // Test paired concurrent interactions i.e. interactions that can
        // potentially gain focus at the same time.
        int interaction = CarAudioFocus.INTERACTION_CONCURRENT;
        testInteraction(ATTR_MEDIA, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_A11Y_NOTIFICATION, interaction, gain, pauseForDucking);

        testInteraction(ATTR_DRIVE_DIR, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_A11Y_NOTIFICATION, interaction, gain, pauseForDucking);

        testInteraction(ATTR_A11Y, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y, ATTR_A11Y, interaction, gain, pauseForDucking);

        testInteraction(ATTR_RINGTONE, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_RINGTONE, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_RINGTONE, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_RINGTONE, ATTR_VOICE_COM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_VOICE_COM, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COM, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COM, ATTR_VOICE_COM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COM, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_VOICE_COM, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);

        testInteraction(ATTR_ALARM, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_A11Y_NOTIFICATION, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NOTIFICATION, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_NOTIFICATION, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_A11Y_NOTIFICATION, interaction, gain,
                pauseForDucking);


        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_DRIVE_DIR, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_ALARM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_NOTIFICATION, interaction, gain,
                pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_A11Y_NOTIFICATION, interaction, gain,
                pauseForDucking);
    }

    private void testExclusiveInteractions(int gain, boolean pauseForDucking)
            throws Exception {

        // Test exclusive interaction, interaction where each usage will not share focus with other
        // another usage. As a result once focus is gained any current focus listener
        // in this interaction will lose focus.
        int interaction = CarAudioFocus.INTERACTION_EXCLUSIVE;
        testInteraction(ATTR_MEDIA, ATTR_MEDIA, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_VOICE_COM, interaction, gain, pauseForDucking);
        testInteraction(ATTR_MEDIA, ATTR_ALARM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_DRIVE_DIR, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_DRIVE_DIR, ATTR_VOICE_COM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_A11Y, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y, ATTR_VOICE_COM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_ALARM, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_ALARM, ATTR_VOICE_COM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_NOTIFICATION, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_NOTIFICATION, ATTR_VOICE_COM, interaction, gain, pauseForDucking);

        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_A11Y, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_RINGTONE, interaction, gain, pauseForDucking);
        testInteraction(ATTR_A11Y_NOTIFICATION, ATTR_VOICE_COM, interaction, gain, pauseForDucking);
    }


    /**
     * Test paired usage interactions with gainType and pause instead ducking
     * @param attributes1 Attributes of the first usage (first focus requester) in the interaction
     * @param attributes2 Attributes of the second usage (second focus requester) in the interaction
     * @param interaction type of interaction {@link CarAudioFocus.INTERACTION_REJECT},
     * {@link CarAudioFocus.INTERACTION_EXCLUSIVE}, {@link CarAudioFocus.INTERACTION_CONCURRENT}
     * @param gainType Type of gain {@link AudioManager.AUDIOFOCUS_GAIN} ,
     * {@link CarAudioFocus.AUDIOFOCUS_GAIN_TRANSIENT},
     * {@link CarAudioFocus.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK}
     * @param pauseForDucking flag to indicate if the first focus listener should pause
     *                        instead of ducking
     * @throws Exception
     */
    private void testInteraction(AudioAttributes attributes1,
            AudioAttributes attributes2,
            int interaction,
            int gainType,
            boolean pauseForDucking) throws Exception {

        final FocusChangeListener focusChangeListener1 = new FocusChangeListener();
        final AudioFocusRequest audioFocusRequest1 = new AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attributes1)
                .setOnAudioFocusChangeListener(focusChangeListener1)
                .setForceDucking(false)
                .setWillPauseWhenDucked(pauseForDucking)
                .build();

        final FocusChangeListener focusChangeListener2 = new FocusChangeListener();
        final AudioFocusRequest audioFocusRequest2 = new AudioFocusRequest
                .Builder(gainType)
                .setAudioAttributes(attributes2)
                .setOnAudioFocusChangeListener(focusChangeListener2)
                .setForceDucking(false)
                .build();

        int expectedLoss = 0;

        // Each focus gain type will return a different focus lost type
        switch (gainType) {
            case AudioManager.AUDIOFOCUS_GAIN:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                break;
            case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                expectedLoss = AudioManager.AUDIOFOCUS_LOSS_TRANSIENT;
                // Note loss or gain will not be sent as both can live concurrently
                if (interaction == CarAudioFocus.INTERACTION_CONCURRENT && !pauseForDucking) {
                    expectedLoss = AudioManager.AUDIOFOCUS_NONE;
                }
                break;
        }

        int secondRequestResultsExpected = AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

        if (interaction == CarAudioFocus.INTERACTION_REJECT) {
            secondRequestResultsExpected = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }

        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest1);
        String message = "Focus gain request failed  for 1st "
                + AudioAttributes.usageToString(attributes1.getUsage());
        assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED, requestResult);


        requestResult = mAudioManager.requestAudioFocus(audioFocusRequest2);
        message = "Focus gain request failed for 2nd "
                + AudioAttributes.usageToString(attributes2.getUsage());
        assertEquals(message, secondRequestResultsExpected, requestResult);

        // If the results is rejected for second one we only have to clean up first
        // as the second focus request is rejected
        if (interaction == CarAudioFocus.INTERACTION_REJECT) {
            requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest1);
            message = "Focus loss request failed for 1st "
                    + AudioAttributes.usageToString(attributes1.getUsage());
            assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED, requestResult);
        }

        // If exclusive we expect to lose focus on 1st one
        // unless we have a concurrent interaction
        if (interaction == CarAudioFocus.INTERACTION_EXCLUSIVE
                || interaction == CarAudioFocus.INTERACTION_CONCURRENT) {
            Thread.sleep(TEST_TIMING_TOLERANCE_MS);
            message = "Focus change was not dispatched for 1st "
                    + AudioAttributes.usageToString(ATTR_MEDIA.getUsage());
            assertEquals(message, expectedLoss,
                    focusChangeListener1.getFocusChangeAndReset());

            requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest2);
            message = "Focus loss request failed  for 2nd "
                    + AudioAttributes.usageToString(ATTR_MEDIA.getUsage());
            assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED, requestResult);

            // If the loss was transient then we should have received back on 1st
            if ((gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
                    || gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)) {

                // Since ducking and concurrent can exist together
                // this needs to be skipped as the focus lost is not sent
                if (!(interaction == CarAudioFocus.INTERACTION_CONCURRENT
                        && gainType == AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)) {
                    Thread.sleep(TEST_TIMING_TOLERANCE_MS);
                    message = "Focus change was not dispatched for 1st "
                            + AudioAttributes.usageToString(ATTR_MEDIA.getUsage());
                    assertEquals(message, AudioManager.AUDIOFOCUS_GAIN,
                            focusChangeListener1.getFocusChangeAndReset());
                }
                // For concurrent focus interactions still needs to be released
                message = "Focus loss request failed  for 1st  "
                        + AudioAttributes.usageToString(attributes1.getUsage());
                requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest1);
                assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED,
                        requestResult);
            }
        }
    }

    /**
     * Verifies usage can request audio focus and release it
     * @param attribute usage attribute to request focus
     * @throws Exception
     */
    private void requestAndLoseFocusForAttribute(AudioAttributes attribute)  throws Exception {
        final FocusChangeListener focusChangeListener = new FocusChangeListener();
        final AudioFocusRequest audioFocusRequest = new AudioFocusRequest
                .Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(attribute)
                .setOnAudioFocusChangeListener(focusChangeListener)
                .setForceDucking(false)
                .build();


        int requestResult = mAudioManager.requestAudioFocus(audioFocusRequest);
        String message = "Focus gain request failed  for "
                + AudioAttributes.usageToString(attribute.getUsage());
        assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED, requestResult);

        Thread.sleep(TEST_TIMING_TOLERANCE_MS);
        // Verify no focus changed dispatched
        message = "Focus change was dispatched for "
                + AudioAttributes.usageToString(attribute.getUsage());
        assertEquals(message, AudioManager.AUDIOFOCUS_NONE,
                focusChangeListener.getFocusChangeAndReset());

        requestResult = mAudioManager.abandonAudioFocusRequest(audioFocusRequest);
        message = "Focus loss request failed  for "
                + AudioAttributes.usageToString(attribute.getUsage());
        assertEquals(message, AudioManager.AUDIOFOCUS_REQUEST_GRANTED, requestResult);
    }

    private static class FocusChangeListener implements AudioManager.OnAudioFocusChangeListener {
        private final Object mLock = new Object();
        private int mFocusChange = AudioManager.AUDIOFOCUS_NONE;

        int getFocusChangeAndReset() {
            final int change;
            synchronized (mLock) {
                change = mFocusChange;
                mFocusChange = AudioManager.AUDIOFOCUS_NONE;
            }
            return change;
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            synchronized (mLock) {
                mFocusChange = focusChange;
            }
        }
    }
}
