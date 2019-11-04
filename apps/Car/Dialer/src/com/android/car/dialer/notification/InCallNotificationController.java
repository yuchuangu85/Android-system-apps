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

package com.android.car.dialer.notification;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.graphics.drawable.Icon;
import android.telecom.Call;
import android.text.TextUtils;

import androidx.annotation.StringRes;

import com.android.car.dialer.Constants;
import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.activecall.InCallActivity;
import com.android.car.telephony.common.CallDetail;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/** Controller that manages the heads up notification for incoming calls. */
public final class InCallNotificationController {
    private static final String TAG = "CD.InCallNotificationController";
    private static final String CHANNEL_ID = "com.android.car.dialer.incoming";
    // A random number that is used for notification id.
    private static final int NOTIFICATION_ID = 20181105;

    private static InCallNotificationController sInCallNotificationController;

    /**
     * Initialized a globally accessible {@link InCallNotificationController} which can be retrieved
     * by {@link #get}. If this function is called a second time before calling {@link #tearDown()},
     * an {@link IllegalStateException} will be thrown.
     *
     * @param applicationContext Application context.
     */
    public static void init(Context applicationContext) {
        if (sInCallNotificationController == null) {
            sInCallNotificationController = new InCallNotificationController(applicationContext);
        } else {
            throw new IllegalStateException("InCallNotificationController has been initialized.");
        }
    }

    /**
     * Gets the global {@link InCallNotificationController} instance. Make sure
     * {@link #init(Context)} is called before calling this method.
     */
    public static InCallNotificationController get() {
        if (sInCallNotificationController == null) {
            throw new IllegalStateException(
                    "Call InCallNotificationController.init(Context) before calling this function");
        }
        return sInCallNotificationController;
    }

    public static void tearDown() {
        sInCallNotificationController = null;
    }

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final Notification.Builder mNotificationBuilder;
    private CompletableFuture<Void> mNotificationFuture;

    @TargetApi(26)
    private InCallNotificationController(Context context) {
        mContext = context;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);

        CharSequence name = mContext.getString(R.string.in_call_notification_channel_name);
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_HIGH);
        mNotificationManager.createNotificationChannel(notificationChannel);

        Intent intent = new Intent(mContext, InCallActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(Constants.Intents.EXTRA_SHOW_INCOMING_CALL, true);
        PendingIntent fullscreenIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);

        mNotificationBuilder = new Notification.Builder(mContext, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_phone)
                .setContentText(mContext.getString(R.string.notification_incoming_call))
                .setFullScreenIntent(fullscreenIntent, /* highPriority= */true)
                .setCategory(Notification.CATEGORY_CALL)
                .setOngoing(true)
                .setAutoCancel(false);
    }


    /** Show a new incoming call notification or update the existing incoming call notification. */
    @TargetApi(26)
    public void showInCallNotification(Call call) {
        L.d(TAG, "showInCallNotification");

        if (mNotificationFuture != null) {
            mNotificationFuture.cancel(true);
        }

        CallDetail callDetail = CallDetail.fromTelecomCallDetail(call.getDetails());
        String number = callDetail.getNumber();
        String tag = call.getDetails().getTelecomCallId();
        mNotificationBuilder
                .setLargeIcon((Icon) null)
                .setContentTitle(number)
                .setActions(
                        getAction(call, R.string.answer_call,
                                NotificationService.ACTION_ANSWER_CALL),
                        getAction(call, R.string.decline_call,
                                NotificationService.ACTION_DECLINE_CALL));
        mNotificationManager.notify(
                tag,
                NOTIFICATION_ID,
                mNotificationBuilder.build());

        mNotificationFuture = NotificationUtils.getDisplayNameAndRoundedAvatar(mContext, number)
                .thenAcceptAsync((pair) -> {
                    // Check that the notification hasn't already been dismissed
                    if (Arrays.stream(mNotificationManager.getActiveNotifications()).anyMatch((n) ->
                            n.getId() == NOTIFICATION_ID && TextUtils.equals(n.getTag(), tag))) {
                        mNotificationBuilder
                                .setLargeIcon(pair.second)
                                .setContentTitle(pair.first);

                        mNotificationManager.notify(
                                tag,
                                NOTIFICATION_ID,
                                mNotificationBuilder.build());
                    }
                }, mContext.getMainExecutor());
    }

    /** Cancel the incoming call notification for the given call. */
    public void cancelInCallNotification(Call call) {
        L.d(TAG, "cancelInCallNotification");
        if (call.getDetails() != null) {
            mNotificationManager.cancel(call.getDetails().getTelecomCallId(), NOTIFICATION_ID);
        }
    }

    private Notification.Action getAction(Call call, @StringRes int actionText,
            String intentAction) {
        CharSequence text = mContext.getString(actionText);
        PendingIntent intent = PendingIntent.getBroadcast(
                mContext,
                0,
                getIntent(intentAction, call),
                PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, text, intent).build();
    }

    private Intent getIntent(String action, Call call) {
        Intent intent = new Intent(action, null, mContext, NotificationReceiver.class);
        intent.putExtra(NotificationService.EXTRA_CALL_ID, call.getDetails().getTelecomCallId());
        return intent;
    }
}
