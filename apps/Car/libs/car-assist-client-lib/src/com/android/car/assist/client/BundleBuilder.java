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
package com.android.car.assist.client;

import static com.android.car.assist.CarVoiceInteractionSession.KEY_ACTION;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_EXCEPTION;
import static com.android.car.assist.CarVoiceInteractionSession.KEY_NOTIFICATION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_HANDLE_EXCEPTION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_READ_NOTIFICATION;
import static com.android.car.assist.CarVoiceInteractionSession.VOICE_ACTION_REPLY_NOTIFICATION;

import android.os.Bundle;
import android.service.notification.StatusBarNotification;

import com.android.car.assist.CarVoiceInteractionSession.ExceptionValue;

/**
 * Helper class for building Bundle arguments. Used by {@link CarAssistUtils}.
 */
class BundleBuilder {
    /**
     * Returns a {@link Bundle} to be delivered to Assistant to indicate that the notification
     * should be read out.
     *
     * @param notification The notification that will be added to the bundle.
     * @return The bundle that can be sent to Assistant.
     */
    static Bundle buildAssistantReadBundle(StatusBarNotification notification) {
        Bundle args = new Bundle();
        args.putString(KEY_ACTION, VOICE_ACTION_READ_NOTIFICATION);
        args.putParcelable(KEY_NOTIFICATION, notification);
        return args;
    }

    /**
     * Returns a {@link Bundle} to be delivered to Assistant to indicate that the notification
     * should be replied to.
     *
     * @param notification The notification that will be added to the bundle.
     * @return The bundle that can be sent to Assistant.
     */
    static Bundle buildAssistantReplyBundle(StatusBarNotification notification) {
        Bundle args = new Bundle();
        args.putString(KEY_ACTION, VOICE_ACTION_REPLY_NOTIFICATION);
        args.putParcelable(KEY_NOTIFICATION, notification);
        return args;
    }

    /**
     * Returns a {@link Bundle} to be delivered to Assistant to indicate that it should handle
     * the specified {@input exception}.
     *
     * @return The bundle that can be sent to Assistant.
     */
    static Bundle buildAssistantHandleExceptionBundle(
            @ExceptionValue String exception) {
        Bundle args = new Bundle();
        args.putString(KEY_ACTION, VOICE_ACTION_HANDLE_EXCEPTION);
        args.putString(KEY_EXCEPTION, exception);
        return args;
    }
}
