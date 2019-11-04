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
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.lifecycle.Observer;

import com.android.car.dialer.Constants;
import com.android.car.dialer.R;
import com.android.car.dialer.livedata.UnreadMissedCallLiveData;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.TelecomActivity;
import com.android.car.dialer.ui.TelecomPageTab;
import com.android.car.telephony.common.PhoneCallLog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Controller that manages the missed call notifications. */
public final class MissedCallNotificationController {
    private static final String TAG = "CD.MissedCallNotification";
    private static final String CHANNEL_ID = "com.android.car.dialer.missedcall";
    // A random number that is used for notification id.
    private static final int NOTIFICATION_ID = 20190520;

    private static MissedCallNotificationController sMissedCallNotificationController;

    /**
     * Initialized a globally accessible {@link MissedCallNotificationController} which can be
     * retrieved by {@link #get}. If this function is called a second time before calling {@link
     * #tearDown()}, an {@link IllegalStateException} will be thrown.
     *
     * @param applicationContext Application context.
     */
    public static void init(Context applicationContext) {
        if (sMissedCallNotificationController == null) {
            sMissedCallNotificationController = new MissedCallNotificationController(
                    applicationContext);
        } else {
            throw new IllegalStateException(
                    "MissedCallNotificationController has been initialized.");
        }
    }

    /**
     * Gets the global {@link MissedCallNotificationController} instance. Make sure {@link
     * #init(Context)} is called before calling this method.
     */
    public static MissedCallNotificationController get() {
        if (sMissedCallNotificationController == null) {
            throw new IllegalStateException(
                    "Call MissedCallNotificationController.init(Context) before calling this "
                            + "function");
        }
        return sMissedCallNotificationController;
    }

    /** Tear down the global missed call notification controller. */
    public void tearDown() {
        mUnreadMissedCallLiveData.removeObserver(mUnreadMissedCallObserver);
        sMissedCallNotificationController = null;
    }

    private final Context mContext;
    private final NotificationManager mNotificationManager;
    private final UnreadMissedCallLiveData mUnreadMissedCallLiveData;
    private final Observer<List<PhoneCallLog>> mUnreadMissedCallObserver;
    private final List<PhoneCallLog> mCurrentPhoneCallLogList;
    private CompletableFuture<Void> mUpdateNotificationFuture;

    @TargetApi(26)
    private MissedCallNotificationController(Context context) {
        mContext = context;
        mNotificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        CharSequence name = mContext.getString(R.string.missed_call_notification_channel_name);
        NotificationChannel notificationChannel = new NotificationChannel(CHANNEL_ID, name,
                NotificationManager.IMPORTANCE_DEFAULT);
        mNotificationManager.createNotificationChannel(notificationChannel);

        mCurrentPhoneCallLogList = new ArrayList<>();
        mUnreadMissedCallLiveData = UnreadMissedCallLiveData.newInstance(context);
        mUnreadMissedCallObserver = this::updateNotifications;
        mUnreadMissedCallLiveData.observeForever(mUnreadMissedCallObserver);
    }

    /**
     * The phone call log list might be null when switching users if permission gets denied and
     * throws exception.
     */
    private void updateNotifications(@Nullable List<PhoneCallLog> phoneCallLogs) {
        List<PhoneCallLog> updatedPhoneCallLogs =
                phoneCallLogs == null ? Collections.emptyList() : phoneCallLogs;
        for (PhoneCallLog phoneCallLog : updatedPhoneCallLogs) {
            showMissedCallNotification(phoneCallLog);
            if (mCurrentPhoneCallLogList.contains(phoneCallLog)) {
                mCurrentPhoneCallLogList.remove(phoneCallLog);
            }
        }

        for (PhoneCallLog phoneCallLog : mCurrentPhoneCallLogList) {
            cancelMissedCallNotification(phoneCallLog);
        }
        mCurrentPhoneCallLogList.clear();
        mCurrentPhoneCallLogList.addAll(updatedPhoneCallLogs);
    }

    private void showMissedCallNotification(PhoneCallLog callLog) {
        L.d(TAG, "show missed call notification %s", callLog);
        if (mUpdateNotificationFuture != null) {
            mUpdateNotificationFuture.cancel(true);
        }
        String phoneNumber = callLog.getPhoneNumberString();
        mUpdateNotificationFuture = NotificationUtils.getDisplayNameAndRoundedAvatar(
                mContext, phoneNumber)
                .thenAcceptAsync((pair) -> {
                    Notification.Builder builder = new Notification.Builder(mContext, CHANNEL_ID)
                            .setSmallIcon(R.drawable.ic_phone)
                            .setLargeIcon(pair.second)
                            .setContentTitle(mContext.getString(R.string.notification_missed_call)
                                    + String.format(" (%d)", callLog.getAllCallRecords().size()))
                            .setContentText(pair.first)
                            .setContentIntent(getContentPendingIntent())
                            .setDeleteIntent(getDeleteIntent())
                            .setOnlyAlertOnce(true)
                            .setShowWhen(true)
                            .setWhen(callLog.getLastCallEndTimestamp())
                            .setAutoCancel(false);

                    if (!TextUtils.isEmpty(phoneNumber)) {
                        builder.addAction(getAction(phoneNumber, R.string.call_back,
                                NotificationService.ACTION_CALL_BACK_MISSED));
                        // TODO: add action button to send message
                    }

                    mNotificationManager.notify(
                            getTag(callLog),
                            NOTIFICATION_ID,
                            builder.build());
                }, mContext.getMainExecutor());
    }

    private void cancelMissedCallNotification(PhoneCallLog phoneCallLog) {
        L.d(TAG, "cancel missed call notification %s", phoneCallLog);
        mNotificationManager.cancel(getTag(phoneCallLog), NOTIFICATION_ID);
    }

    private PendingIntent getContentPendingIntent() {
        Intent intent = new Intent(mContext, TelecomActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        intent.setAction(Constants.Intents.ACTION_SHOW_PAGE);
        intent.putExtra(Constants.Intents.EXTRA_SHOW_PAGE, TelecomPageTab.Page.CALL_HISTORY);
        intent.putExtra(Constants.Intents.EXTRA_ACTION_READ_MISSED, true);
        PendingIntent pendingIntent = PendingIntent.getActivity(mContext, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    private PendingIntent getDeleteIntent() {
        Intent intent = new Intent(NotificationService.ACTION_READ_ALL_MISSED, null, mContext,
                NotificationReceiver.class);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(
                mContext,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    private Notification.Action getAction(String phoneNumberString, @StringRes int actionText,
            String intentAction) {
        CharSequence text = mContext.getString(actionText);
        PendingIntent intent = PendingIntent.getBroadcast(
                mContext,
                0,
                getIntent(intentAction, phoneNumberString),
                PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Action.Builder(null, text, intent).build();
    }

    private Intent getIntent(String action, String phoneNumberString) {
        Intent intent = new Intent(action, null, mContext, NotificationReceiver.class);
        intent.putExtra(NotificationService.EXTRA_CALL_ID, phoneNumberString);
        return intent;
    }

    private String getTag(@NonNull PhoneCallLog phoneCallLog) {
        return String.valueOf(phoneCallLog.hashCode());
    }
}
