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

import android.app.Notification;
import android.content.Context;
import android.os.Bundle;
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
public class CarNotificationDiffTest {

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
    private static final String OVERRIDE_GROUP_KEY_123 = "OVERRIDE_GROUP_KEY_123";
    private static final long POST_TIME = 12345l;
    private static final UserHandle USER_HANDLE = new UserHandle(12);

    private Notification.Builder mNotificationBuilder1;
    private Notification.Builder mNotificationBuilder2;

    private StatusBarNotification mNotification1;
    private StatusBarNotification mNotification2;
    private StatusBarNotification mNotification3;
    private StatusBarNotification mNotification4;
    private NotificationGroup mNotificationGroup1;
    private NotificationGroup mNotificationGroup2;
    private NotificationGroup mNotificationGroup3;
    private NotificationGroup mNotificationGroup4;
    private List<NotificationGroup> mNotificationGroupList1;
    private List<NotificationGroup> mNotificationGroupList2;
    private List<NotificationGroup> mNotificationGroupList3;
    private List<NotificationGroup> mNotificationGroupList4;

    @Before
    public void setupBaseActivityAndLayout() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mNotificationBuilder1 = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        mNotificationBuilder2 = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setColor(123)
                .setFlag(0, false)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        mNotificationGroup1 = new NotificationGroup();
        mNotificationGroup2 = new NotificationGroup();
        mNotificationGroup3 = new NotificationGroup();
        mNotificationGroupList1 = new ArrayList<>();
        mNotificationGroupList2 = new ArrayList<>();
        mNotificationGroupList3 = new ArrayList<>();
        mNotification1 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification2 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification3 = new StatusBarNotification(PKG_2, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder2.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY_123, POST_TIME);
        mNotificationGroup1.addNotification(mNotification1);
        mNotificationGroup2.addNotification(mNotification2);
        mNotificationGroup2.addNotification(mNotification2);
        mNotificationGroup3.addNotification(mNotification3);
        mNotificationGroupList1.add(mNotificationGroup1);
        mNotificationGroupList2.add(mNotificationGroup2);
        mNotificationGroupList3.add(mNotificationGroup3);
    }

    /**
     * Test that the CarNotificationDiff's sameNotificationKey should return true.
     */
    @Test
    public void sameNotification_shouldReturnTrue() {
        assertThat(
                CarNotificationDiff.sameNotificationKey(mNotification1, mNotification1)).isTrue();
    }

    /**
     * Test that the CarNotificationDiff's sameNotificationKey should return false.
     */
    @Test
    public void differentNotificationKey_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKey(mNotification1, mNotification3)).isFalse();
    }

    @Test
    public void differentNotification_firstIsNull_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKey(null, mNotification2)).isFalse();
    }

    @Test
    public void differentNotification_secondIsNull_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKey(mNotification1, null)).isFalse();
    }

    @Test
    public void differentNotificationKeyAndFlag_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKeyAndFlags(mNotification1,
                        mNotification3)).isFalse();
    }

    @Test
    public void differentNotificationKeyAndFlag_firstIsNull_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKeyAndFlags(null, mNotification3)).isFalse();
    }

    @Test
    public void differentNotificationKeyAndFlag_secondIsNull_returnsFalse() {
        assertThat(
                CarNotificationDiff.sameNotificationKeyAndFlags(mNotification1, null)).isFalse();
    }

    @Test
    public void sameNotificationKeyAndFlag_shouldReturnTrue() {
        assertThat(
                CarNotificationDiff.sameNotificationKeyAndFlags(mNotification1,
                        mNotification1)).isTrue();
    }

    /**
     * Test that the CarNotificationDiff's sameGroupUniqueIdentifiers should return true.
     */
    @Test
    public void sameGroupUniqueIdentifiers_shouldReturnTrue() {
        assertThat(CarNotificationDiff.sameGroupUniqueIdentifiers(mNotificationGroup1,
                mNotificationGroup1)).isTrue();
    }

    @Test
    public void sameGroupUniqueIdentifiers_differenKeys_shouldReturnFalse() {
        assertThat(CarNotificationDiff.sameGroupUniqueIdentifiers(mNotificationGroup1,
                mNotificationGroup3)).isFalse();
    }

    @Test
    public void sameGroupUniqueIdentifiers_diffNotificationKey_shouldReturnFalse() {
        mNotificationGroup4 = new NotificationGroup();
        mNotificationGroupList4 = new ArrayList<>();
        mNotification4 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotificationGroup4.addNotification(mNotification4);
        mNotificationGroup4.setGroupSummaryNotification(mNotification4);

        assertThat(mNotificationGroup1.getGroupKey()).isEqualTo(mNotificationGroup4.getGroupKey());
        assertThat(mNotificationGroup1.getChildCount()).isEqualTo(
                mNotificationGroup4.getChildCount());
        assertThat(CarNotificationDiff.sameGroupUniqueIdentifiers(mNotificationGroup1,
                mNotificationGroup4)).isFalse();
    }

    @Test
    public void sameGroupUniqueIdentifiers_sameChildrenNotification_shouldReturnTrue() {
        mNotificationGroup4 = new NotificationGroup();
        mNotificationGroupList4 = new ArrayList<>();
        mNotification4 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotificationGroup4.addNotification(mNotification4);

        assertThat(mNotificationGroup1.getGroupKey()).isEqualTo(mNotificationGroup4.getGroupKey());
        assertThat(mNotificationGroup1.getChildCount()).isEqualTo(
                mNotificationGroup4.getChildCount());
        assertThat(CarNotificationDiff.sameGroupUniqueIdentifiers(mNotificationGroup1,
                mNotificationGroup4)).isTrue();
    }

    /**
     * Test that the CarNotificationDiff's sameGroupUniqueIdentifiers should return false.
     */
    @Test
    public void differentGroupUniqueIdentifiers_shouldReturnFalse() {
        assertThat(CarNotificationDiff.sameGroupUniqueIdentifiers(mNotificationGroup1,
                mNotificationGroup3)).isFalse();
    }

    /**
     * Test that the CarNotificationDiff's areItemsTheSame should return true.
     */
    @Test
    public void sameItems_shouldReturnTrue() {
        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                mNotificationGroupList1, mNotificationGroupList1);
        assertThat(carNotificationDiff.areItemsTheSame(0, 0)).isTrue();
    }

    @Test
    public void areContentsTheSame_shouldReturnTrue() {
        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                mNotificationGroupList1, mNotificationGroupList1);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isTrue();
    }

    @Test
    public void areContentsTheSame_shouldReturnFalse() {
        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                mNotificationGroupList1, mNotificationGroupList3);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isFalse();
    }

    @Test
    public void getOldListSize_shouldReturnOne() {
        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                mNotificationGroupList1, mNotificationGroupList3);
        assertThat(carNotificationDiff.getOldListSize()).isEqualTo(1);
    }

    @Test
    public void getNewListSize_shouldReturnOne() {
        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                mNotificationGroupList1, mNotificationGroupList2);
        assertThat(carNotificationDiff.getNewListSize()).isEqualTo(1);
    }

    @Test
    public void areBundleEqual_sameSize_shouldReturnTrue() {
        Notification.Builder oldNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(new Bundle())
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder newNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(new Bundle())
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        StatusBarNotification oldStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, oldNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        StatusBarNotification newStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, newNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        NotificationGroup oldNotificationGroup = new NotificationGroup();
        oldNotificationGroup.setGroupSummaryNotification(oldStatusBarNotification);
        List<NotificationGroup> oldNotificationGroupList = new ArrayList<>();

        NotificationGroup newNotificationGroup = new NotificationGroup();
        newNotificationGroup.setGroupSummaryNotification(newStatusBarNotification);
        List<NotificationGroup> newNotificationGroupList = new ArrayList<>();

        oldNotificationGroup.addNotification(oldStatusBarNotification);
        oldNotificationGroupList.add(oldNotificationGroup);

        newNotificationGroup.addNotification(newStatusBarNotification);
        newNotificationGroupList.add(newNotificationGroup);


        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                oldNotificationGroupList, newNotificationGroupList);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isTrue();
    }

    @Test
    public void areBundleEqual_diffSize_shouldReturnFalse() {
        Bundle bundle_1 = new Bundle();
        bundle_1.putInt("test", 1);
        Notification.Builder oldNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_1)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Bundle bundle_2 = new Bundle();
        bundle_2.putInt("test", 1);
        bundle_2.putInt("test_1", 1);
        Notification.Builder newNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_2)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        StatusBarNotification oldStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, oldNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        StatusBarNotification newStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, newNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        NotificationGroup oldNotificationGroup = new NotificationGroup();
        oldNotificationGroup.setGroupSummaryNotification(oldStatusBarNotification);
        List<NotificationGroup> oldNotificationGroupList = new ArrayList<>();

        NotificationGroup newNotificationGroup = new NotificationGroup();
        newNotificationGroup.setGroupSummaryNotification(newStatusBarNotification);
        List<NotificationGroup> newNotificationGroupList = new ArrayList<>();

        oldNotificationGroup.addNotification(oldStatusBarNotification);
        oldNotificationGroupList.add(oldNotificationGroup);

        newNotificationGroup.addNotification(newStatusBarNotification);
        newNotificationGroupList.add(newNotificationGroup);


        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                oldNotificationGroupList, newNotificationGroupList);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isFalse();
    }

    @Test
    public void areBundleEqual_sameKeySetWithSameValue_shouldReturnTrue() {
        Bundle bundle_1 = new Bundle();
        bundle_1.putInt("test", 1);
        Notification.Builder oldNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_1)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Bundle bundle_2 = new Bundle();
        bundle_2.putInt("test", 1);
        Notification.Builder newNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_2)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        StatusBarNotification oldStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, oldNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        StatusBarNotification newStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, newNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        NotificationGroup oldNotificationGroup = new NotificationGroup();
        oldNotificationGroup.setGroupSummaryNotification(oldStatusBarNotification);
        List<NotificationGroup> oldNotificationGroupList = new ArrayList<>();

        NotificationGroup newNotificationGroup = new NotificationGroup();
        newNotificationGroup.setGroupSummaryNotification(newStatusBarNotification);
        List<NotificationGroup> newNotificationGroupList = new ArrayList<>();

        oldNotificationGroup.addNotification(oldStatusBarNotification);
        oldNotificationGroupList.add(oldNotificationGroup);

        newNotificationGroup.addNotification(newStatusBarNotification);
        newNotificationGroupList.add(newNotificationGroup);


        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                oldNotificationGroupList, newNotificationGroupList);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isTrue();
    }

    @Test
    public void areBundleEqual_sameKeySetWithDiffValue_shouldReturnFalse() {
        Bundle bundle_1 = new Bundle();
        bundle_1.putInt("test", 1);
        Notification.Builder oldNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_1)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Bundle bundle_2 = new Bundle();
        bundle_2.putInt("test", 2);
        Notification.Builder newNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_2)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        StatusBarNotification oldStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, oldNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        StatusBarNotification newStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, newNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        NotificationGroup oldNotificationGroup = new NotificationGroup();
        oldNotificationGroup.setGroupSummaryNotification(oldStatusBarNotification);
        List<NotificationGroup> oldNotificationGroupList = new ArrayList<>();

        NotificationGroup newNotificationGroup = new NotificationGroup();
        newNotificationGroup.setGroupSummaryNotification(newStatusBarNotification);
        List<NotificationGroup> newNotificationGroupList = new ArrayList<>();

        oldNotificationGroup.addNotification(oldStatusBarNotification);
        oldNotificationGroupList.add(oldNotificationGroup);

        newNotificationGroup.addNotification(newStatusBarNotification);
        newNotificationGroupList.add(newNotificationGroup);


        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                oldNotificationGroupList, newNotificationGroupList);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isFalse();
    }

    @Test
    public void areBundleEqual_diffKeySet_shouldReturnFalse() {
        Bundle bundle_1 = new Bundle();
        bundle_1.putInt("test", 1);
        Notification.Builder oldNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_1)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Bundle bundle_2 = new Bundle();
        bundle_2.putInt("test_2", 2);
        Notification.Builder newNotification = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setExtras(bundle_2)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        StatusBarNotification oldStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, oldNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        StatusBarNotification newStatusBarNotification = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, newNotification.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);

        NotificationGroup oldNotificationGroup = new NotificationGroup();
        oldNotificationGroup.setGroupSummaryNotification(oldStatusBarNotification);
        List<NotificationGroup> oldNotificationGroupList = new ArrayList<>();

        NotificationGroup newNotificationGroup = new NotificationGroup();
        newNotificationGroup.setGroupSummaryNotification(newStatusBarNotification);
        List<NotificationGroup> newNotificationGroupList = new ArrayList<>();

        oldNotificationGroup.addNotification(oldStatusBarNotification);
        oldNotificationGroupList.add(oldNotificationGroup);

        newNotificationGroup.addNotification(newStatusBarNotification);
        newNotificationGroupList.add(newNotificationGroup);


        CarNotificationDiff carNotificationDiff = new CarNotificationDiff(mContext,
                oldNotificationGroupList, newNotificationGroupList);
        assertThat(carNotificationDiff.areContentsTheSame(0, 0)).isFalse();
    }
}
