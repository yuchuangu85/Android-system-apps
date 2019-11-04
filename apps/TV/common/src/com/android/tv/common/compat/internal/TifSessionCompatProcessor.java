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

import android.util.Log;
import com.android.tv.common.compat.api.SessionCompatCommands;
import com.android.tv.common.compat.api.SessionCompatEvents;
import com.android.tv.common.compat.api.SessionEventNotifier;
import com.android.tv.common.compat.internal.Commands.PrivateCommand;
import com.android.tv.common.compat.internal.Events.NotifyDevToast;
import com.android.tv.common.compat.internal.Events.NotifySignalStrength;
import com.android.tv.common.compat.internal.Events.SessionEvent;

/**
 * Sends {@link SessionCompatEvents} to the TV App via {@link SessionEventNotifier} and receives
 * Commands from TV App forwarding them to {@link SessionCompatCommands}
 */
public final class TifSessionCompatProcessor
        extends SessionCompatProcessor<PrivateCommand, SessionEvent>
        implements SessionCompatEvents {

    private static final String TAG = "TifSessionCompatProcessor";

    private final SessionCompatCommands mSessionOnCompat;

    public TifSessionCompatProcessor(
            SessionEventNotifier sessionEventNotifier, SessionCompatCommands sessionOnCompat) {
        super(sessionEventNotifier, PrivateCommand.parser());
        mSessionOnCompat = sessionOnCompat;
    }

    @Override
    protected void onCompat(Commands.PrivateCommand privateCommand) {
        switch (privateCommand.getCommandCase()) {
            case ON_DEV_MESSAGE:
                mSessionOnCompat.onDevMessage(privateCommand.getOnDevMessage().getMessage());
                break;
            case COMMAND_NOT_SET:
                Log.w(TAG, "Command not set ");
        }
    }

    @Override
    public void notifyDevToast(String message) {
        NotifyDevToast devMessage = NotifyDevToast.newBuilder().setMessage(message).build();
        SessionEvent sessionEvent = createSessionEvent().setNotifyDevMessage(devMessage).build();
        notifyCompat(sessionEvent);
    }

    @Override
    public void notifySignalStrength(int value) {
        NotifySignalStrength signalStrength =
                NotifySignalStrength.newBuilder().setSignalStrength(value).build();
        SessionEvent sessionEvent =
                createSessionEvent().setNotifySignalStrength(signalStrength).build();
        notifyCompat(sessionEvent);
    }

    private SessionEvent.Builder createSessionEvent() {
        return SessionEvent.newBuilder().setCompatVersion(Constants.TIF_COMPAT_VERSION);
    }
}
