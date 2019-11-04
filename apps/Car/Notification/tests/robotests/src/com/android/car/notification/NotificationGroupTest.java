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

package com.android.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.app.Notification;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
public class NotificationGroupTest {

    private Context mContext;
    @Rule
    public MockitoRule mMockitoRule = MockitoJUnit.rule();

    private static final String PKG_1 = "package_1";
    private static final String PKG_2 = "package_2";
    private static final String OP_PKG = "OpPackage";
    private static final int ID = 1;
    private static final String TAG = "Tag";
    private static final int UID = 2;
    private static final int INITIAL_PID = 3;
    private static final String CHANNEL_ID = "CHANNEL_ID";
    private static final String CONTENT_TITLE = "CONTENT_TITLE";
    private static final String OVERRIDE_GROUP_KEY = "OVERRIDE_GROUP_KEY";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);

    private Notification.Builder mNotificationBuilder;
    private NotificationGroup mNotificationGroup;
    private StatusBarNotification mNOTIFICATION1;
    private StatusBarNotification mNotification2;

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mNotificationGroup = new NotificationGroup();
        mNotificationBuilder = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        mNOTIFICATION1 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification2 = new StatusBarNotification(PKG_2, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
    }

    /**
     * Test that the NotificationGroup addNotification should add sbn.
     */
    @Test
    public void addNotification_shouldAdd() {
        mNotificationGroup.addNotification(mNOTIFICATION1);
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.getChildCount()).isEqualTo(2);
    }

    /**
     * Test that the NotificationGroup addNotification should throw error when notifications added
     * with different group key.
     */
    @Test
    public void addNotification_shouldThrowError() {
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThrows(IllegalStateException.class,
                () -> mNotificationGroup.addNotification(mNotification2));
    }

    /**
     * Test that the NotificationGroup setGroupSummaryNotification should return false.
     */
    @Test
    public void setGroupSummaryNotification_shouldReturnFalse() {
        mNotificationGroup.setGroupSummaryNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.isGroup()).isFalse();
    }

    /**
     * Test that the NotificationGroup setGroupSummaryNotification should return true.
     */
    @Test
    public void setGroupSummaryNotification_shouldReturnTrue() {
        mNotificationGroup.setGroupSummaryNotification(mNOTIFICATION1);
        mNotificationGroup.addNotification(mNOTIFICATION1);
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.getChildCount()).isEqualTo(2);
        assertThat(mNotificationGroup.getGroupSummaryNotification()).isEqualTo(mNOTIFICATION1);
        assertThat(mNotificationGroup.isGroup()).isTrue();
    }

    @Test
    public void setGroupKey_shouldSetGroupKey() {
        mNotificationGroup.setGroupKey(mNOTIFICATION1.getGroupKey());
        assertThat(mNotificationGroup.getGroupKey()).isEqualTo(mNOTIFICATION1.getGroupKey());
    }

    @Test
    public void getChildNotifications_shouldReturnListOfAddedNotifications() {
        mNotificationGroup.addNotification(mNOTIFICATION1);
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.getChildCount()).isEqualTo(2);
        assertThat(mNotificationGroup.getChildNotifications().get(0)).isEqualTo(mNOTIFICATION1);
        assertThat(mNotificationGroup.getChildNotifications().get(1)).isEqualTo(mNOTIFICATION1);
    }

    @Test
    public void setChildTitles_shouldReturnListOfStringWithChildTitles() {
        List<String> childTitles = new ArrayList<>();
        childTitles.add("childTitles_1");
        childTitles.add("childTitles_2");
        mNotificationGroup.setChildTitles(childTitles);
        assertThat(mNotificationGroup.getChildTitles()).isEqualTo(childTitles);
    }

    @Test
    public void generateChildTitles_shouldReturnListOfStringWithChildTiles() {
        mNotificationGroup.addNotification(mNOTIFICATION1);
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.generateChildTitles().get(0)).isEqualTo(CONTENT_TITLE);
        assertThat(mNotificationGroup.generateChildTitles().get(1)).isEqualTo(CONTENT_TITLE);
    }

    @Test
    public void getSingleNotification_returnTheOnlyNotificationInNotificationList() {
        mNotificationGroup.addNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.getSingleNotification()).isEqualTo(mNOTIFICATION1);
    }

    @Test
    public void getSingleNotification_shouldReturnNull() {
        assertThat(mNotificationGroup.getSingleNotification()).isNull();
    }

    @Test
    public void getNotificationForSorting_shouldReturnGroupSummaryNotification() {
        mNotificationGroup.setGroupSummaryNotification(mNOTIFICATION1);
        assertThat(mNotificationGroup.getNotificationForSorting()).isEqualTo(mNOTIFICATION1);
    }

    @Test
    public void getNotificationForSorting_shouldReturnNull() {
        assertThat(mNotificationGroup.getSingleNotification()).isNull();
    }

}
