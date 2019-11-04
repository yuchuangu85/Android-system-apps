/*
 * Copyright (C) 2017 The Android Open Source Project
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


import static com.android.managedprovisioning.common.NotificationHelper.CHANNEL_ID;
import static com.android.managedprovisioning.common.NotificationHelper.ENCRYPTION_NOTIFICATION_ID;
import static com.android.managedprovisioning.common.NotificationHelper.PRIVACY_REMINDER_NOTIFICATION_ID;
import static com.google.common.truth.Truth.assertThat;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.SystemClock;
import android.service.notification.StatusBarNotification;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@SmallTest
public class NotificationHelperTest {

    private static final int NOTIFICATION_TIMEOUT_MS = 5000;

    private NotificationHelper mNotificationHelper;
    private NotificationManager mNotificationManager;
    private Context mContext;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getTargetContext();
        mNotificationHelper = new NotificationHelper(mContext);
        mNotificationManager = mContext.getSystemService(NotificationManager.class);
        removeAllNotifications();
    }

    @After
    public void tearDown() {
        removeAllNotifications();
    }

    @Test
    public void testShowResumeNotification() throws Exception {
        assertThat(mNotificationManager.getActiveNotifications().length).isEqualTo(0);

        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING);
        mNotificationHelper.showResumeNotification(intent);

        waitForNotification();
        StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
        assertThat(notifications.length).isEqualTo(1);
        StatusBarNotification notification = notifications[0];
        assertThat(notification.getId()).isEqualTo(ENCRYPTION_NOTIFICATION_ID);
        assertThat(notification.getNotification().getChannel()).isEqualTo(CHANNEL_ID);
        assertThat(notification.getNotification().extras.getString(Notification.EXTRA_TITLE))
                .isEqualTo(mContext.getString(R.string.continue_provisioning_notify_title));
    }

    @Test
    public void testShowPrivacyReminderNotification() throws Exception {
        assertThat(mNotificationManager.getActiveNotifications().length).isEqualTo(0);

        mNotificationHelper.showPrivacyReminderNotification(
                mContext, NotificationManager.IMPORTANCE_DEFAULT);

        waitForNotification();
        StatusBarNotification[] notifications = mNotificationManager.getActiveNotifications();
        assertThat(notifications.length).isEqualTo(1);
        StatusBarNotification notification = notifications[0];
        assertThat(notification.getId()).isEqualTo(PRIVACY_REMINDER_NOTIFICATION_ID);
        assertThat(notification.getNotification().getChannel()).isEqualTo(CHANNEL_ID);
        assertThat(notification.getNotification().extras.getString(Notification.EXTRA_TITLE))
                .isEqualTo(mContext.getString(
                        R.string.fully_managed_device_provisioning_privacy_title));
    }

    private void waitForNotification() throws InterruptedException {
        long elapsed = SystemClock.elapsedRealtime();
        while(SystemClock.elapsedRealtime() - elapsed < NOTIFICATION_TIMEOUT_MS
                && mNotificationManager.getActiveNotifications().length == 0) {
            Thread.sleep(10);
        }
    }

    private void removeAllNotifications() {
        mNotificationManager.cancelAll();
    }
}
