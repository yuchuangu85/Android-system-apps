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
 * limitations under the License.
 */
package com.android.car.audio;

import android.hardware.automotive.audiocontrol.V1_0.ContextNumber;
import android.media.AudioAttributes;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.audiopolicy.AudioMix;
import android.media.audiopolicy.AudioMixingRule;
import android.media.audiopolicy.AudioPolicy;
import android.util.Log;
import android.util.SparseIntArray;

import com.android.car.CarLog;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Builds dynamic audio routing in a car from audio zone configuration.
 */
/* package */ class CarAudioDynamicRouting {

    static final int[] CONTEXT_NUMBERS = new int[] {
            ContextNumber.MUSIC,
            ContextNumber.NAVIGATION,
            ContextNumber.VOICE_COMMAND,
            ContextNumber.CALL_RING,
            ContextNumber.CALL,
            ContextNumber.ALARM,
            ContextNumber.NOTIFICATION,
            ContextNumber.SYSTEM_SOUND
    };

    static final SparseIntArray USAGE_TO_CONTEXT = new SparseIntArray();

    static final int DEFAULT_AUDIO_USAGE = AudioAttributes.USAGE_MEDIA;

    // For legacy stream type based volume control.
    // Values in STREAM_TYPES and STREAM_TYPE_USAGES should be aligned.
    static final int[] STREAM_TYPES = new int[] {
            AudioManager.STREAM_MUSIC,
            AudioManager.STREAM_ALARM,
            AudioManager.STREAM_RING
    };
    static final int[] STREAM_TYPE_USAGES = new int[] {
            AudioAttributes.USAGE_MEDIA,
            AudioAttributes.USAGE_ALARM,
            AudioAttributes.USAGE_NOTIFICATION_RINGTONE
    };

    static {
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_UNKNOWN, ContextNumber.MUSIC);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_MEDIA, ContextNumber.MUSIC);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_VOICE_COMMUNICATION, ContextNumber.CALL);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING,
                ContextNumber.CALL);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_ALARM, ContextNumber.ALARM);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION, ContextNumber.NOTIFICATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION_RINGTONE, ContextNumber.CALL_RING);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST,
                ContextNumber.NOTIFICATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT,
                ContextNumber.NOTIFICATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED,
                ContextNumber.NOTIFICATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_NOTIFICATION_EVENT, ContextNumber.NOTIFICATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY,
                ContextNumber.VOICE_COMMAND);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
                ContextNumber.NAVIGATION);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
                ContextNumber.SYSTEM_SOUND);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_GAME, ContextNumber.MUSIC);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_VIRTUAL_SOURCE, ContextNumber.INVALID);
        USAGE_TO_CONTEXT.put(AudioAttributes.USAGE_ASSISTANT, ContextNumber.VOICE_COMMAND);
    }

    private final CarAudioZone[] mCarAudioZones;

    CarAudioDynamicRouting(CarAudioZone[] carAudioZones) {
        mCarAudioZones = carAudioZones;
    }

    void setupAudioDynamicRouting(AudioPolicy.Builder builder) {
        for (CarAudioZone zone : mCarAudioZones) {
            for (CarVolumeGroup group : zone.getVolumeGroups()) {
                setupAudioDynamicRoutingForGroup(group, builder);
            }
        }
    }

    /**
     * Enumerates all physical buses in a given volume group and attach the mixing rules.
     * @param group {@link CarVolumeGroup} instance to enumerate the buses with
     * @param builder {@link AudioPolicy.Builder} to attach the mixing rules
     */
    private void setupAudioDynamicRoutingForGroup(CarVolumeGroup group,
            AudioPolicy.Builder builder) {
        // Note that one can not register audio mix for same bus more than once.
        for (int busNumber : group.getBusNumbers()) {
            boolean hasContext = false;
            CarAudioDeviceInfo info = group.getCarAudioDeviceInfoForBus(busNumber);
            AudioFormat mixFormat = new AudioFormat.Builder()
                    .setSampleRate(info.getSampleRate())
                    .setEncoding(info.getEncodingFormat())
                    .setChannelMask(info.getChannelCount())
                    .build();
            AudioMixingRule.Builder mixingRuleBuilder = new AudioMixingRule.Builder();
            for (int contextNumber : group.getContextsForBus(busNumber)) {
                hasContext = true;
                int[] usages = getUsagesForContext(contextNumber);
                for (int usage : usages) {
                    mixingRuleBuilder.addRule(
                            new AudioAttributes.Builder().setUsage(usage).build(),
                            AudioMixingRule.RULE_MATCH_ATTRIBUTE_USAGE);
                }
                Log.d(CarLog.TAG_AUDIO, "Bus number: " + busNumber
                        + " contextNumber: " + contextNumber
                        + " sampleRate: " + info.getSampleRate()
                        + " channels: " + info.getChannelCount()
                        + " usages: " + Arrays.toString(usages));
            }
            if (hasContext) {
                // It's a valid case that an audio output bus is defined in
                // audio_policy_configuration and no context is assigned to it.
                // In such case, do not build a policy mix with zero rules.
                AudioMix audioMix = new AudioMix.Builder(mixingRuleBuilder.build())
                        .setFormat(mixFormat)
                        .setDevice(info.getAudioDeviceInfo())
                        .setRouteFlags(AudioMix.ROUTE_FLAG_RENDER)
                        .build();
                builder.addMix(audioMix);
            }
        }
    }

    private int[] getUsagesForContext(int contextNumber) {
        final List<Integer> usages = new ArrayList<>();
        for (int i = 0; i < CarAudioDynamicRouting.USAGE_TO_CONTEXT.size(); i++) {
            if (CarAudioDynamicRouting.USAGE_TO_CONTEXT.valueAt(i) == contextNumber) {
                usages.add(CarAudioDynamicRouting.USAGE_TO_CONTEXT.keyAt(i));
            }
        }
        return usages.stream().mapToInt(i -> i).toArray();
    }
}
