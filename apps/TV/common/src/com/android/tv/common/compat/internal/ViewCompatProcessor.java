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
import android.util.ArrayMap;
import android.util.Log;
import com.android.tv.common.compat.api.PrivateCommandSender;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Parser;

/**
 * Sends {@code commands} to the {@code session} via {@link PrivateCommandSender} and receives
 * notification events from the session forwarding them to {@link
 * com.android.tv.common.compat.api.TvInputCallbackCompatEvents}
 */
abstract class ViewCompatProcessor<
        C extends GeneratedMessageLite<C, ?>, E extends GeneratedMessageLite<E, ?>> {
    private static final String TAG = "ViewCompatProcessor";
    private final ArrayMap<String, Integer> inputCompatVersionMap = new ArrayMap<>();

    private final Parser<E> mEventParser;
    private final PrivateCommandSender mCommandSender;

    ViewCompatProcessor(PrivateCommandSender commandSender, Parser<E> eventParser) {
        mCommandSender = commandSender;
        mEventParser = eventParser;
    }

    private final E sessionEventFromBundle(Bundle eventArgs) throws InvalidProtocolBufferException {

        byte[] protoBytes = eventArgs.getByteArray(Constants.EVENT_COMPAT_NOTIFY);
        return protoBytes == null || protoBytes.length == 0
                ? null
                : mEventParser.parseFrom(protoBytes);
    }

    final void sendCompatCommand(C privateCommand) {
        try {
            Bundle data = new Bundle();
            data.putByteArray(Constants.ACTION_COMPAT_ON, privateCommand.toByteArray());
            mCommandSender.sendAppPrivateCommand(Constants.ACTION_COMPAT_ON, data);
        } catch (Exception e) {
            Log.w(TAG, "Error sending compat action " + privateCommand, e);
        }
    }

    public boolean handleEvent(String inputId, String eventType, Bundle eventArgs) {
        switch (eventType) {
            case Constants.EVENT_GET_VERSION:
                int version = eventArgs.getInt(Constants.EVENT_GET_VERSION, 0);
                inputCompatVersionMap.put(inputId, version);
                return true;
            case Constants.EVENT_COMPAT_NOTIFY:
                try {
                    E sessionEvent = sessionEventFromBundle(eventArgs);
                    if (sessionEvent != null) {
                        handleSessionEvent(inputId, sessionEvent);
                    } else {
                        String errorMessage =
                                eventArgs.getString(Constants.EVENT_COMPAT_NOTIFY_ERROR);
                        Log.w(TAG, "Error sent in compat notify  " + errorMessage);
                    }

                } catch (InvalidProtocolBufferException e) {
                    Log.w(TAG, "Error parsing in compat notify for  " + inputId);
                }

                return true;
            default:
                return false;
        }
    }

    protected abstract void handleSessionEvent(String inputId, E sessionEvent);
}
