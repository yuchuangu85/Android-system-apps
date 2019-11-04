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

import android.support.annotation.NonNull;
import android.util.Log;
import com.android.tv.common.compat.api.PrivateCommandSender;
import com.android.tv.common.compat.api.TvInputCallbackCompatEvents;
import com.android.tv.common.compat.api.TvViewCompatCommands;
import com.android.tv.common.compat.internal.Commands.OnDevMessage;
import com.android.tv.common.compat.internal.Commands.PrivateCommand;
import com.android.tv.common.compat.internal.Events.NotifyDevToast;
import com.android.tv.common.compat.internal.Events.NotifySignalStrength;
import com.android.tv.common.compat.internal.Events.SessionEvent;

/**
 * Sends {@link TvViewCompatCommands} to the {@link android.media.tv.TvInputService.Session} via
 * {@link PrivateCommandSender} and receives notification events from the session forwarding them to
 * {@link TvInputCallbackCompatEvents}
 */
public final class TvViewCompatProcessor extends ViewCompatProcessor<PrivateCommand, SessionEvent>
        implements TvViewCompatCommands {
    private static final String TAG = "TvViewCompatProcessor";

    private TvInputCallbackCompatEvents mCallback;

    public TvViewCompatProcessor(PrivateCommandSender commandSender) {
        super(commandSender, SessionEvent.parser());
    }

    @Override
    public void devMessage(String message) {
        OnDevMessage devMessage = Commands.OnDevMessage.newBuilder().setMessage(message).build();
        Commands.PrivateCommand privateCommand =
                createPrivateCommandCommand().setOnDevMessage(devMessage).build();
        sendCompatCommand(privateCommand);
    }

    @NonNull
    private PrivateCommand.Builder createPrivateCommandCommand() {
        return PrivateCommand.newBuilder().setCompatVersion(Constants.TIF_COMPAT_VERSION);
    }

    public void onDevToast(String inputId, String message) {}

    public void onSignalStrength(String inputId, int value) {}

    @Override
    protected final void handleSessionEvent(String inputId, Events.SessionEvent sessionEvent) {
        switch (sessionEvent.getEventCase()) {
            case NOTIFY_DEV_MESSAGE:
                handle(inputId, sessionEvent.getNotifyDevMessage());
                break;
            case NOTIFY_SIGNAL_STRENGTH:
                handle(inputId, sessionEvent.getNotifySignalStrength());
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

    private void handle(String inputId, NotifySignalStrength signalStrength) {
        if (signalStrength != null && mCallback != null) {
            mCallback.onSignalStrength(inputId, signalStrength.getSignalStrength());
        }
    }

    public void setCallback(TvInputCallbackCompatEvents callback) {
        this.mCallback = callback;
    }
}
