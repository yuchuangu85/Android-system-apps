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
package com.android.car.assist;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.service.notification.StatusBarNotification;
import android.service.voice.VoiceInteractionService;
import android.service.voice.VoiceInteractionSession;

import androidx.annotation.StringDef;

import com.android.car.assist.payloadhandlers.NotificationPayloadHandler;

/**
 * An active voice interaction session on the car, providing additional actions which assistant
 * should act on. Override the {@link #onShow(String, Bundle, int)} to received the action specified
 * by the voice session initiator.
 */
public abstract class CarVoiceInteractionSession extends VoiceInteractionSession {
    /** The key used for the action {@link String} in the payload {@link Bundle}. */
    public static final String KEY_ACTION = "KEY_ACTION";

    /**
     * The key used for the {@link CarVoiceInteractionSession#VOICE_ACTION_HANDLE_EXCEPTION} payload
     * {@link Bundle}. Must map to a {@link ExceptionValue}.
     */
    public static final String KEY_EXCEPTION = "KEY_EXCEPTION";

    /**
     * The key used for the payload {@link Bundle}, if a {@link StatusBarNotification} is used as
     * the payload.
     */
    public static final String KEY_NOTIFICATION = "KEY_NOTIFICATION";

    /** Indicates to assistant that no action was specified. */
    public static final String VOICE_ACTION_NO_ACTION = "VOICE_ACTION_NO_ACTION";

    /** Indicates to assistant that a read action is being requested for a given payload. */
    public static final String VOICE_ACTION_READ_NOTIFICATION = "VOICE_ACTION_READ_NOTIFICATION";

    /** Indicates to assistant that a reply action is being requested for a given payload. */
    public static final String VOICE_ACTION_REPLY_NOTIFICATION = "VOICE_ACTION_REPLY_NOTIFICATION";

    /**
     * Indicates to assistant that it should resolve the exception in the given payload (found in
     * {@link CarVoiceInteractionSession#KEY_EXCEPTION}'s value).
     */
    public static final String VOICE_ACTION_HANDLE_EXCEPTION = "VOICE_ACTION_HANDLE_EXCEPTION";

    /**
     * The list of exceptions the active voice service must handle.
     */
    @StringDef({EXCEPTION_NOTIFICATION_LISTENER_PERMISSIONS_MISSING})
    public @interface ExceptionValue {}

    /**
     * Indicates to assistant that it is missing the Notification Listener permission, and should
     * request this permission from the user.
     **/
    public static final String EXCEPTION_NOTIFICATION_LISTENER_PERMISSIONS_MISSING =
            "EXCEPTION_NOTIFICATION_LISTENER_PERMISSIONS_MISSING";


    private final NotificationPayloadHandler mNotificationPayloadHandler;

    public CarVoiceInteractionSession(Context context) {
        super(context);
        mNotificationPayloadHandler = new NotificationPayloadHandler(getContext());
    }

    public CarVoiceInteractionSession(Context context, Handler handler) {
        super(context, handler);
        mNotificationPayloadHandler = new NotificationPayloadHandler(getContext());
    }

    /**
     * Returns the notification payload handler, which can be used to handle actions related to
     * notification payloads.
     */
    public NotificationPayloadHandler getNotificationPayloadHandler() {
        return mNotificationPayloadHandler;
    }

    @Override
    public final void onShow(Bundle args, int showFlags) {
        super.onShow(args, showFlags);
        if (args != null && isCarNotificationSource(showFlags)) {
            String action = getRequestedVoiceAction(args);
            if (!VOICE_ACTION_NO_ACTION.equals(action)) {
                onShow(action, args, showFlags);
                return;
            }
        }
        onShow(VOICE_ACTION_NO_ACTION, args, showFlags);
    }

    /**
     * Called when the session UI is going to be shown.  This is called after
     * {@link #onCreateContentView} (if the session's content UI needed to be created) and
     * immediately prior to the window being shown.  This may be called while the window
     * is already shown, if a show request has come in while it is shown, to allow you to
     * update the UI to match the new show arguments.
     *
     * @param action The action that is being requested for this session
     *               (e.g. {@link CarVoiceInteractionSession#VOICE_ACTION_READ_NOTIFICATION},
     *               {@link CarVoiceInteractionSession#VOICE_ACTION_REPLY_NOTIFICATION}).
     * @param args The arguments that were supplied to
     * {@link VoiceInteractionService#showSession VoiceInteractionService.showSession}.
     * @param flags The show flags originally provided to
     * {@link VoiceInteractionService#showSession VoiceInteractionService.showSession}.
     */
    protected abstract void onShow(String action, Bundle args, int flags);

    /**
     * Returns true if the request was initiated for a car notification.
     */
    private static boolean isCarNotificationSource(int flags) {
        return (flags & SHOW_SOURCE_NOTIFICATION) != 0;
    }

    /**
     * Returns the action {@link String} provided in the args {@Bundle},
     * or {@link CarVoiceInteractionSession#VOICE_ACTION_NO_ACTION} if no such string was provided.
     */
    protected static String getRequestedVoiceAction(Bundle args) {
        return args.getString(KEY_ACTION, VOICE_ACTION_NO_ACTION);
    }
}
