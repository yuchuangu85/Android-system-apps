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

import android.os.Bundle;
import android.util.Log;
import com.android.tv.common.compat.api.SessionEventNotifier;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;

/**
 * Sends {@code events} to the TV App via {@link SessionEventNotifier} and receives {@code commands}
 * from TV App.
 */
abstract class SessionCompatProcessor<
        C extends GeneratedMessageLite<C, ?>, E extends GeneratedMessageLite<E, ?>> {
    private static final String TAG = "SessionCompatProcessor";
    private final SessionEventNotifier mSessionEventNotifier;
    private final Parser<C> mCommandParser;

    SessionCompatProcessor(SessionEventNotifier sessionEventNotifier, Parser<C> commandParser) {
        mSessionEventNotifier = sessionEventNotifier;
        mCommandParser = commandParser;
    }

    public final boolean handleAppPrivateCommand(String action, Bundle data) {
        switch (action) {
            case Constants.ACTION_GET_VERSION:
                Bundle response = new Bundle();
                response.putInt(Constants.EVENT_GET_VERSION, Constants.TIF_COMPAT_VERSION);
                mSessionEventNotifier.notifySessionEvent(Constants.EVENT_GET_VERSION, response);
                return true;
            case Constants.ACTION_COMPAT_ON:
                byte[] bytes = data.getByteArray(Constants.ACTION_COMPAT_ON);
                try {
                    C privateCommand = mCommandParser.parseFrom(bytes);
                    onCompat(privateCommand);
                } catch (InvalidProtocolBufferException e) {
                    Log.w(TAG, "Error parsing compat data", e);
                }

                return true;
            default:
                return false;
        }
    }

    abstract void onCompat(C privateCommand);

    final void notifyCompat(E event) {
        Bundle response = new Bundle();
        try {
            byte[] bytes = event.toByteArray();
            response.putByteArray(Constants.EVENT_COMPAT_NOTIFY, bytes);
        } catch (Exception e) {
            Log.w(TAG, "Failed to send " + event, e);
            response.putString(Constants.EVENT_COMPAT_NOTIFY_ERROR, e.getMessage());
        }
        mSessionEventNotifier.notifySessionEvent(Constants.EVENT_COMPAT_NOTIFY, response);
    }
}
