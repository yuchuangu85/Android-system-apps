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


import android.annotation.Nullable;
import android.app.ActivityManager;
import android.app.Notification;
import android.app.Notification.MessagingStyle.Message;
import android.app.PendingIntent;
import android.app.Person;
import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;

import com.android.car.assist.client.tts.TextToSpeechHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Handles Assistant request fallbacks in the case that Assistant cannot fulfill the request for
 * any given reason.
 * <p/>
 * Simply reads out the notification messages for read requests, and speaks out
 * an error message for other requests.
 */
public class FallbackAssistant {

    private static final String TAG = FallbackAssistant.class.getSimpleName();

    private final Context mContext;
    private final TextToSpeechHelper mTextToSpeechHelper;
    private final RequestIdGenerator mRequestIdGenerator;
    private Map<Long, ActionRequestInfo> mRequestIdToActionRequestInfo = new HashMap<>();
    // String that means "says", to be used when reading out a message (i.e. <Sender> says
    // <Message).
    private final String mVerbForSays;

    private final TextToSpeechHelper.Listener mListener = new TextToSpeechHelper.Listener() {
        @Override
        public void onTextToSpeechStarted(long requestId) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTextToSpeechStarted");
            }
        }

        @Override
        public void onTextToSpeechStopped(long requestId, boolean error) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onTextToSpeechStopped");
            }

            if (error) {
                Toast.makeText(mContext, mContext.getString(R.string.assist_action_failed_toast),
                        Toast.LENGTH_LONG).show();
            }
            finishAction(requestId, error);
        }
    };

    /** Listener to allow clients to be alerted when their requested message has been read. **/
    public interface Listener {
        /**
         * Called after the TTS engine has finished reading aloud the message.
         */
        void onMessageRead(boolean hasError);
    }

    public FallbackAssistant(Context context) {
        mContext = context;
        mTextToSpeechHelper = new TextToSpeechHelper(context, mListener);
        mRequestIdGenerator = new RequestIdGenerator();
        mVerbForSays = mContext.getString(R.string.says);
    }

    /**
     * Handles a fallback read action by reading all messages in the notification.
     *
     * @param sbn the payload notification from which to extract messages from
     */
    public void handleReadAction(StatusBarNotification sbn, Listener listener) {
        if (mTextToSpeechHelper.isSpeaking()) {
            mTextToSpeechHelper.requestStop();
        }

        Parcelable[] messagesBundle = sbn.getNotification().extras
                .getParcelableArray(Notification.EXTRA_MESSAGES);

        if (messagesBundle == null || messagesBundle.length == 0) {
            listener.onMessageRead(/* hasError= */ true);
            return;
        }

        List<CharSequence> messages = new ArrayList<>();

        List<Message> messageList = Message.getMessagesFromBundleArray(messagesBundle);
        if (messageList == null || messageList.isEmpty()) {
            Log.w(TAG, "No messages could be extracted from the bundle");
            listener.onMessageRead(/* hasError= */ true);
            return;
        }
        // The sender should be the same for all the messages.
        Person sender = messageList.get(0).getSenderPerson();
        if (sender != null) {
            messages.add(sender.getName());
            messages.add(mVerbForSays);
        }
        for (Message message : messageList) {
            messages.add(message.getText());
        }

        long requestId = mRequestIdGenerator.generateRequestId();

        if (mTextToSpeechHelper.requestPlay(messages, requestId)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Requesting TTS to read message with requestId: " + requestId);
            }
            mRequestIdToActionRequestInfo.put(requestId, new ActionRequestInfo(sbn, listener));
        } else {
            listener.onMessageRead(/* hasError= */ true);
        }
    }

    /**
     * Handles generic (non-read) actions by reading out an error message.
     *
     * @param errorMessage the error message to read out
     */
    public void handleErrorMessage(CharSequence errorMessage, Listener listener) {
        if (mTextToSpeechHelper.isSpeaking()) {
            mTextToSpeechHelper.requestStop();
        }

        long requestId = mRequestIdGenerator.generateRequestId();
        if (mTextToSpeechHelper.requestPlay(Collections.singletonList(errorMessage),
                requestId)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Requesting TTS to read error with requestId: " + requestId);
            }
            mRequestIdToActionRequestInfo.put(requestId, new ActionRequestInfo(
                    /* statusBarNotification= */ null,
                    listener));
        } else {
            listener.onMessageRead(/* hasError= */ true);
        }
    }

    private void finishAction(long requestId, boolean hasError) {
        if (!mRequestIdToActionRequestInfo.containsKey(requestId)) {
            Log.w(TAG, "No actionRequestInfo found for requestId: " + requestId);
            return;
        }

        ActionRequestInfo info = mRequestIdToActionRequestInfo.remove(requestId);

        if (info.getStatusBarNotification() != null && !hasError) {
            sendMarkAsReadIntent(info.getStatusBarNotification());
        }

        info.getListener().onMessageRead(hasError);
    }

    private void sendMarkAsReadIntent(StatusBarNotification sbn) {
        NotificationCompat.Action markAsReadAction = CarAssistUtils.getMarkAsReadAction(
                sbn.getNotification());
        boolean isDebugLoggable = Log.isLoggable(TAG, Log.DEBUG);

        if (markAsReadAction != null) {
            if (sendPendingIntent(markAsReadAction.getActionIntent(),
                    null /* resultIntent */) != ActivityManager.START_SUCCESS
                    && isDebugLoggable) {
                Log.d(TAG, "Could not relay mark as read event to the messaging app.");
            }
        } else if (isDebugLoggable) {
            Log.d(TAG, "Car compat message notification has no mark as read action: "
                    + sbn.getKey());
        }
    }

    private int sendPendingIntent(PendingIntent pendingIntent, Intent resultIntent) {
        try {
            return pendingIntent.sendAndReturnResult(/* context= */ mContext, /* code= */ 0,
                    /* intent= */ resultIntent, /* onFinished= */null,
                    /* handler= */ null, /* requiredPermissions= */ null,
                    /* options= */ null);
        } catch (PendingIntent.CanceledException e) {
            // Do not take down the app over this
            Log.w(TAG, "Sending contentIntent failed: " + e);
            return ActivityManager.START_ABORTED;
        }
    }

    /** Helper class that generates unique IDs per TTS request. **/
    private class RequestIdGenerator {
        private long mCounter;

        RequestIdGenerator() {
            mCounter = 0;
        }

        public long generateRequestId() {
            return ++mCounter;
        }
    }

    /**
     * Contains all of the information needed to start and finish actions supported by the
     * FallbackAssistant.
     **/
    private class ActionRequestInfo {
        private final StatusBarNotification mStatusBarNotification;
        private final Listener mListener;

        ActionRequestInfo(@Nullable StatusBarNotification statusBarNotification,
                Listener listener) {
            mStatusBarNotification = statusBarNotification;
            mListener = listener;
        }

        @Nullable
        StatusBarNotification getStatusBarNotification() {
            return mStatusBarNotification;
        }

        Listener getListener() {
            return mListener;
        }
    }
}
