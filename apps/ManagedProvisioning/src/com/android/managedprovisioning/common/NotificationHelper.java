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
package com.android.managedprovisioning.common;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;

import static com.android.internal.util.Preconditions.checkNotNull;

/**
 * Helper methods for showing notifications, such as the provisioning reminder and
 * privacy reminder notifications.
 */
public class NotificationHelper {
    @VisibleForTesting
    static final String CHANNEL_ID = "ManagedProvisioning";

    @VisibleForTesting
    static final int ENCRYPTION_NOTIFICATION_ID = 1;

    @VisibleForTesting
    static final int PRIVACY_REMINDER_NOTIFICATION_ID = 2;

    private final Context mContext;

    public NotificationHelper(Context context) {
        mContext = checkNotNull(context);
    }

    /**
     * Notification asking the user to resume provisioning after encryption has happened.
     */
    public void showResumeNotification(Intent intent) {
        final NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                mContext.getString(R.string.encrypt), NotificationManager.IMPORTANCE_HIGH);
        notificationManager.createNotificationChannel(channel);

        final PendingIntent resumePendingIntent = PendingIntent.getActivity(
                mContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
        final Notification.Builder notify = new Notification.Builder(mContext)
                .setChannelId(CHANNEL_ID)
                .setContentIntent(resumePendingIntent)
                .setContentTitle(mContext
                        .getString(R.string.continue_provisioning_notify_title))
                .setContentText(mContext.getString(R.string.continue_provisioning_notify_text))
                .setSmallIcon(com.android.internal.R.drawable.ic_corp_statusbar_icon)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setColor(mContext.getResources().getColor(
                        com.android.internal.R.color.system_notification_accent_color))
                .setAutoCancel(true);
        notificationManager.notify(ENCRYPTION_NOTIFICATION_ID, notify.build());
    }

    public void showPrivacyReminderNotification(Context context,
            @NotificationManager.Importance int importance) {
        final NotificationManager notificationManager =
                (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        final NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, mContext.getString(R.string.app_label), importance);
        notificationManager.createNotificationChannel(channel);

        final Notification.Builder notify = new Notification.Builder(mContext, CHANNEL_ID)
                .setColor(context.getColor(R.color.notification_background))
                .setColorized(true)
                .setContentTitle(mContext.getString(
                        R.string.fully_managed_device_provisioning_privacy_title))
                .setContentText(
                        mContext.getString(R.string.fully_managed_device_provisioning_privacy_body))
                .setStyle(new Notification.BigTextStyle().bigText(mContext.getString(
                        R.string.fully_managed_device_provisioning_privacy_body)))
                .setSmallIcon(com.android.internal.R.drawable.ic_corp_statusbar_icon)
                .setVisibility(Notification.VISIBILITY_PUBLIC)
                .setAutoCancel(true);
        notificationManager.notify(PRIVACY_REMINDER_NOTIFICATION_ID, notify.build());
    }
}
