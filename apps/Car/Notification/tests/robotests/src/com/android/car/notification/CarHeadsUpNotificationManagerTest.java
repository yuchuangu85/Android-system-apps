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

package com.android.car.notification;

import static android.content.pm.ApplicationInfo.FLAG_INSTALLED;
import static android.content.pm.ApplicationInfo.FLAG_SYSTEM;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_PRIVILEGED;
import static android.content.pm.ApplicationInfo.PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.os.UserHandle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.view.View;

import com.android.car.notification.testutils.ShadowApplicationPackageManager;
import com.android.car.notification.testutils.ShadowCarAssistUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowLooper;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.HashMap;
import java.util.Map;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowCarAssistUtils.class, ShadowApplicationPackageManager.class})
public class CarHeadsUpNotificationManagerTest {

    private Context mContext;

    @Mock
    NotificationListenerService.RankingMap mRankingMapMock;

    @Mock
    NotificationListenerService.Ranking mRankingMock;

    @Mock
    NotificationChannel mNotificationChannelMock;

    @Mock
    NotificationClickHandlerFactory mClickHandlerFactory;

    @Mock
    NotificationDataManager mNotificationDataManager;

    @Spy
    StatusBarNotification mStatusBarNotificationSpy;

    private CarHeadsUpNotificationManager mManager;

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

    private StatusBarNotification mNotification1;
    private StatusBarNotification mNotification2;
    private StatusBarNotification mNotification_carInformationHeadsUp;
    private StatusBarNotification mNotification_messageHeadsUp;
    private StatusBarNotification mNotification_navigationHeadsUp;
    private StatusBarNotification mNotification_callHeadsUp;
    private StatusBarNotification mNotification_inboxHeadsUp;
    private Map<String, StatusBarNotification> mActiveNotifications;

    @Before
    public void setupBaseActivityAndLayout() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        ShadowApplicationPackageManager.setResources(mContext.getResources());
        when(mRankingMock.getChannel()).thenReturn(mNotificationChannelMock);

        when(mClickHandlerFactory.getClickHandler(any())).thenReturn(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
            }
        });
        Notification.Builder mNotificationBuilder1 = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CALL)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder mNotificationBuilder2 = new Notification.Builder(mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder mNotificationBuilder_carInformationHeadsUp = new Notification.Builder(
                mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CAR_INFORMATION)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder mNotificationBuilder_messageHeadsUp = new Notification.Builder(
                mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder mNotificationBuilder_navigationHeadsUp = new Notification.Builder(
                mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);
        Notification.Builder mNotificationBuilder_callHeadsUp = new Notification.Builder(
                mContext, CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_CALL)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);

        Bundle bundle = new Bundle();
        bundle.putString(Notification.EXTRA_BIG_TEXT, "EXTRA_BIG_TEXT");
        bundle.putString(Notification.EXTRA_SUMMARY_TEXT, "EXTRA_SUMMARY_TEXT");
        Notification.Builder mNotificationBuilder_inboxHeadsUp = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(Notification.CATEGORY_MESSAGE)
                .setExtras(bundle)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);

        mNotification1 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification2 = new StatusBarNotification(PKG_2, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder2.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification_carInformationHeadsUp = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder_carInformationHeadsUp.build(),
                USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification_messageHeadsUp = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder_messageHeadsUp.build(),
                USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification_navigationHeadsUp = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder_navigationHeadsUp.build(),
                USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification_callHeadsUp = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder_callHeadsUp.build(),
                USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME);
        mNotification_inboxHeadsUp = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder_inboxHeadsUp.build(),
                USER_HANDLE, OVERRIDE_GROUP_KEY, POST_TIME);

        mActiveNotifications = new HashMap<>();
        initializeWithFactory();
    }

    /**
     * Resets the state of the shadow after every test is run.
     */
    @After
    public void resetShadow() {
        mManager = null;
        mContext = null;
        ShadowCarAssistUtils.reset();
        ShadowApplicationPackageManager.reset();
    }

    /**
     * Heads up notification not be shown when is not important.
     */
    @Test
    public void maybeShowHeadsUp_isNotImportant_returnsNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_DEFAULT);

        setPackageInfo(PKG_2);
        mManager.maybeShowHeadsUp(mNotification2, mRankingMapMock, mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification2.getKey()));

        assertThat(notificationView).isNull();
    }

    @Test
    public void maybeRemoveHeadsUp_noCurrentNotifications_shouldNotCallClearView() {
        mManager.maybeRemoveHeadsUp(mStatusBarNotificationSpy);

        verify(mStatusBarNotificationSpy, times(1)).getKey();
    }

    /**
     * Test that Heads up notification should be shown when notification is IMPORTANCE_HIGH.
     */
    @Test
    public void maybeShowHeadsUp_isImportanceHigh_returnsNotNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification1.getKey()));

        assertThat(notificationView).isNotNull();
    }

    @Test
    public void getActiveHeadsUpNotifications_shouldReturnOne() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);

        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void getActiveHeadsUpNotifications_diffNotifications_shouldReturnTwo() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        setPackageInfo(PKG_2);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);
        mManager.maybeShowHeadsUp(mNotification2, mRankingMapMock, mActiveNotifications);

        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(2);
    }

    @Test
    public void getActiveHeadsUpNotifications_sameNotifications_shouldReturnOne() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);

        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_categoryCarInformation_returnsNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification_carInformationHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(
                        mNotification_carInformationHeadsUp.getKey()));

        assertThat(notificationView).isNull();
    }

    @Test
    public void maybeShowHeadsUp_categoryMessage_returnsNotNull() {
        initializeWithFactory();
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        ShadowCarAssistUtils.addMessageNotification(mNotification_inboxHeadsUp.getKey());

        mManager.maybeShowHeadsUp(mNotification_messageHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(
                        mNotification_messageHeadsUp.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_categoryCall_returnsNotNull() {
        initializeWithFactory();
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification_callHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(
                        mNotification_callHeadsUp.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_categoryNavigation_returnsNotNull() {
        initializeWithFactory();
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification_navigationHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(
                        mNotification_navigationHeadsUp.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_InboxHeadsUp_returnsNotNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        ShadowCarAssistUtils.addMessageNotification(mNotification_inboxHeadsUp.getKey());

        mManager.maybeShowHeadsUp(mNotification_inboxHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification_inboxHeadsUp.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_isSignedWithPlatformKey_returnsNotNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        PackageInfo pkgInfo = new PackageInfo();
        pkgInfo.packageName = PKG_1;

        pkgInfo.applicationInfo = new ApplicationInfo();
        pkgInfo.applicationInfo.packageName = PKG_1;
        pkgInfo.applicationInfo.flags = PRIVATE_FLAG_SIGNED_WITH_PLATFORM_KEY | FLAG_INSTALLED;

        getShadowPackageManager().removePackage(PKG_1);
        getShadowPackageManager().addPackage(pkgInfo);
        ShadowApplicationPackageManager.setPackageInfo(pkgInfo);

        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification1.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_isSystemApp_returnsNotNull() {
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        PackageInfo pkgInfo = new PackageInfo();
        pkgInfo.packageName = PKG_1;

        pkgInfo.applicationInfo = new ApplicationInfo();
        pkgInfo.applicationInfo.packageName = PKG_1;
        pkgInfo.applicationInfo.flags = PRIVATE_FLAG_PRIVILEGED | FLAG_SYSTEM | FLAG_INSTALLED;

        getShadowPackageManager().removePackage(PKG_1);
        getShadowPackageManager().addPackage(pkgInfo);
        ShadowApplicationPackageManager.setPackageInfo(pkgInfo);

        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification1.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void getActiveHeadsUpNotifications_clearViewCalled_shouldReturnZero() {
        initializeWithFactory();
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification1, mRankingMapMock, mActiveNotifications);

        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);

        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();
    }

    @Test
    public void maybeShowHeadsUp_nonMutedNotification_headsUpShown() {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(false);
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        ShadowCarAssistUtils.addMessageNotification(mNotification_inboxHeadsUp.getKey());

        mManager.maybeShowHeadsUp(mNotification_inboxHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification_inboxHeadsUp.getKey()));

        assertThat(notificationView).isNotNull();
        assertThat(mManager.getActiveHeadsUpNotifications().size()).isEqualTo(1);
    }

    @Test
    public void maybeShowHeadsUp_mutedNotification_headsUpNotShown() {
        when(mNotificationDataManager.isMessageNotificationMuted(any())).thenReturn(true);
        when(mRankingMapMock.getRanking(any(), any())).thenReturn(true);
        when(mRankingMock.getImportance()).thenReturn(NotificationManager.IMPORTANCE_HIGH);

        setPackageInfo(PKG_1);
        mManager.maybeShowHeadsUp(mNotification_inboxHeadsUp, mRankingMapMock,
                mActiveNotifications);
        View notificationView = getNotificationView(
                mManager.getActiveHeadsUpNotifications().get(mNotification_inboxHeadsUp.getKey()));

        assertThat(notificationView).isNull();
    }

    private void initializeWithFactory() {
        mManager = new CarHeadsUpNotificationManager(mContext, mClickHandlerFactory,
                mNotificationDataManager) {
            @Override
            protected NotificationListenerService.Ranking getRanking() {
                return mRankingMock;
            }
        };
    }

    private View getNotificationView(HeadsUpEntry currentNotification) {
        return currentNotification == null ? null : currentNotification.getNotificationView();
    }

    private void setPackageInfo(String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;

        ApplicationInfo applicationInfo = new ApplicationInfo();
        packageInfo.applicationInfo = applicationInfo;

        ShadowApplicationPackageManager.setPackageInfo(packageInfo);
        getShadowPackageManager().addPackage(packageInfo);

    }

    private ShadowPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
