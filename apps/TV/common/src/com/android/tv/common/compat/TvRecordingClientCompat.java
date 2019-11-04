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

package com.android.tv.common.compat;

import android.content.Context;
import android.media.tv.TvRecordingClient;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.RequiresApi;
import android.util.ArrayMap;
import com.android.tv.common.compat.api.PrivateCommandSender;
import com.android.tv.common.compat.api.RecordingClientCallbackCompatEvents;
import com.android.tv.common.compat.api.TvRecordingClientCompatCommands;
import com.android.tv.common.compat.internal.RecordingClientCompatProcessor;

/**
 * TIF Compatibility for {@link TvRecordingClient}.
 *
 * <p>Extends {@code TvRecordingClient} in a backwards compatible way.
 */
@RequiresApi(api = VERSION_CODES.N)
public class TvRecordingClientCompat extends TvRecordingClient
        implements TvRecordingClientCompatCommands, PrivateCommandSender {

    private final RecordingClientCompatProcessor mProcessor;

    /**
     * Creates a new TvRecordingClient object.
     *
     * @param context The application context to create a TvRecordingClient with.
     * @param tag A short name for debugging purposes.
     * @param callback The callback to receive recording status changes.
     * @param handler The handler to invoke the callback on.
     */
    public TvRecordingClientCompat(
            Context context, String tag, RecordingCallback callback, Handler handler) {
        super(context, tag, callback, handler);
        RecordingCallbackCompat compatEvents =
                callback instanceof RecordingCallbackCompat
                        ? (RecordingCallbackCompat) callback
                        : null;
        mProcessor = new RecordingClientCompatProcessor(this, compatEvents);
        if (compatEvents != null) {
            compatEvents.mClientCompatProcessor = mProcessor;
        }
    }

    /** Tell the session to Display a debug message dev builds only. */
    @Override
    public void devMessage(String message) {
        mProcessor.devMessage(message);
    }

    /**
     * TIF Compatibility for {@link RecordingCallback}.
     *
     * <p>Extends {@code RecordingCallback} in a backwards compatible way.
     */
    public static class RecordingCallbackCompat extends RecordingCallback
            implements RecordingClientCallbackCompatEvents {
        private final ArrayMap<String, Integer> inputCompatVersionMap = new ArrayMap<>();
        private RecordingClientCompatProcessor mClientCompatProcessor;

        @Override
        public void onEvent(String inputId, String eventType, Bundle eventArgs) {
            if (mClientCompatProcessor != null
                    && !mClientCompatProcessor.handleEvent(inputId, eventType, eventArgs)) {
                super.onEvent(inputId, eventType, eventArgs);
            }
        }

        public int getTifCompatVersionForInput(String inputId) {
            return inputCompatVersionMap.containsKey(inputId)
                    ? inputCompatVersionMap.get(inputId)
                    : 0;
        }

        /** Display a message as a toast on dev builds only. */
        @Override
        public void onDevToast(String inputId, String message) {}

        /** Recording started. */
        @Override
        public void onRecordingStarted(String inputId, String recUri) {}
    }
}
