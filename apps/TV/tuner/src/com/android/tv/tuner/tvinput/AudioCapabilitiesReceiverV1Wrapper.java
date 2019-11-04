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

package com.android.tv.tuner.tvinput;

import android.content.Context;
import android.os.Handler;
import android.util.Log;
import com.google.android.exoplayer.audio.AudioCapabilities;
import com.google.android.exoplayer.audio.AudioCapabilitiesReceiver;

/**
 * Wraps {@link AudioCapabilitiesReceiver} to support listening for audio capabilities changes on
 * custom threads.
 */
public final class AudioCapabilitiesReceiverV1Wrapper {

    private static final String TAG = "AudioCapabilitiesReceiverV1Wrapper";

    private final AudioCapabilitiesReceiver mAudioCapabilitiesReceiver;
    private final Handler mHandler;
    private final AudioCapabilitiesReceiver.Listener mListener;
    private boolean mRegistered;

    /**
     * Creates an instance.
     *
     * @param context A context for registering the receiver.
     * @param handler A handler on the which mListener events will be posted.
     * @param listener The listener to notify when audio capabilities change.
     */
    public AudioCapabilitiesReceiverV1Wrapper(
            Context context, Handler handler, AudioCapabilitiesReceiver.Listener listener) {
        mAudioCapabilitiesReceiver =
                new AudioCapabilitiesReceiver(context, this::onAudioCapabilitiesChanged);
        mHandler = handler;
        mListener = listener;
    }

    /** @see AudioCapabilitiesReceiver#register() */
    public AudioCapabilities register() {
        mRegistered = true;
        return mAudioCapabilitiesReceiver.register();
    }

    /** @see AudioCapabilitiesReceiver#unregister() */
    public void unregister() {
        if (mRegistered) {
            try {
                mAudioCapabilitiesReceiver.unregister();
            } catch (IllegalArgumentException e) {
                // Workaround for b/115739362.
                Log.e(
                        TAG,
                        "Ignoring exception when unregistering audio capabilities receiver: ",
                        e);
            }
            mRegistered = false;
        } else {
            Log.e(TAG, "Attempt to unregister a non-registered audio capabilities receiver.");
        }
    }

    private void onAudioCapabilitiesChanged(AudioCapabilities audioCapabilities) {
        mHandler.post(() -> mListener.onAudioCapabilitiesChanged(audioCapabilities));
    }
}
