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
package com.android.tv.common.compat.internal;

import android.support.annotation.Nullable;
import android.util.Log;
import com.android.tv.common.compat.api.PrivateCommandSender;
import com.android.tv.common.compat.api.RecordingClientCallbackCompatEvents;
import com.android.tv.common.compat.api.TvViewCompatCommands;
import com.android.tv.common.compat.internal.Commands.OnDevMessage;
import com.android.tv.common.compat.internal.Commands.PrivateCommand;
import com.android.tv.common.compat.internal.RecordingEvents.NotifyDevToast;
import com.android.tv.common.compat.internal.RecordingEvents.RecordingSessionEvent;

/**
 * Sends {@link RecordingCommands} to the {@link android.media.tv.TvInputService.RecordingSession}
 * via {@link PrivateCommandSender} and receives notification events from the session forwarding
 * them to {@link RecordingClientCallbackCompatEvents}
 */
public final class RecordingClientCompatProcessor
        extends ViewCompatProcessor<PrivateCommand, RecordingSessionEvent>
        implements TvViewCompatCommands {
    private static final String TAG = "RecordingClientCompatProcessor";

    @Nullable private final RecordingClientCallbackCompatEvents mCallback;

    public RecordingClientCompatProcessor(
            PrivateCommandSender commandSender,
            @Nullable RecordingClientCallbackCompatEvents callback) {
        super(commandSender, RecordingSessionEvent.parser());
        mCallback = callback;
    }

    @Override
    public void devMessage(String message) {
        OnDevMessage devMessage = OnDevMessage.newBuilder().setMessage(message).build();
        PrivateCommand privateCommand =
                createPrivateCommandCommand().setOnDevMessage(devMessage).build();
        sendCompatCommand(privateCommand);
    }

    private PrivateCommand.Builder createPrivateCommandCommand() {
        return PrivateCommand.newBuilder().setCompatVersion(Constants.TIF_COMPAT_VERSION);
    }

    @Override
    protected final void handleSessionEvent(String inputId, RecordingSessionEvent sessionEvent) {
        switch (sessionEvent.getEventCase()) {
            case NOTIFY_DEV_MESSAGE:
                handle(inputId, sessionEvent.getNotifyDevMessage());
                break;
            case RECORDING_STARTED:
                handle(inputId, sessionEvent.getRecordingStarted());
                break;

            case EVENT_NOT_SET:
                Log.w(TAG, "Error event not set compat notify  ");
        }
    }

    private void handle(String inputId, NotifyDevToast devToast) {
        if (devToast != null && mCallback != null) {
            mCallback.onDevToast(inputId, devToast.getMessage());
        }
    }

    private void handle(String inputId, RecordingEvents.RecordingStarted recStart) {
        if (recStart != null && mCallback != null) {
            mCallback.onRecordingStarted(inputId, recStart.getUri());
        }
    }
}
