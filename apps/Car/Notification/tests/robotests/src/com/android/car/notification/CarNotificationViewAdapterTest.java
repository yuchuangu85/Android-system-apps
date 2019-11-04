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
 * limitations under the License
 */

package com.android.car.notification;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.internal.verification.VerificationModeFactory.times;
import static org.testng.Assert.assertThrows;

import android.app.Notification;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.os.UserHandle;
import android.service.notification.StatusBarNotification;
import android.view.View;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.template.BasicNotificationViewHolder;
import com.android.car.notification.template.GroupNotificationViewHolder;
import com.android.car.notification.template.GroupSummaryNotificationViewHolder;
import com.android.car.notification.template.InboxNotificationViewHolder;
import com.android.car.notification.template.MessageNotificationViewHolder;
import com.android.car.notification.template.ProgressNotificationViewHolder;
import com.android.car.notification.testutils.ShadowApplicationPackageManager;
import com.android.car.notification.testutils.ShadowStatusBarNotification;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class, ShadowStatusBarNotification.class})
public class CarNotificationViewAdapterTest {

    private static final String PKG_1 = "package_1";
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

    private Context mContext;

    @Mock
    NotificationClickHandlerFactory mClickHandlerFactoryMock;

    @Mock
    CarUxRestrictions mCarUxRestrictionsMock;

    private StatusBarNotification mNotification1;
    private Notification.Builder mNotificationBuilder1;
    private CarNotificationViewAdapter mCarNotificationViewAdapter;
    private NotificationGroup mNotificationGroup1;
    private NotificationGroup mNotificationGroup2;
    private List<NotificationGroup> mNotificationGroupList1;

    @Before
    public void setupBaseActivityAndLayout() {
        MockitoAnnotations.initMocks(this);

        mContext = RuntimeEnvironment.application;
        ShadowStatusBarNotification.setContext(mContext);

        when(mClickHandlerFactoryMock.getClickHandler(any())).thenReturn(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                    }
                });

        mNotificationBuilder1 = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);

        mNotification1 = new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, mNotificationBuilder1.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
        getShadowPackageManager().addPackage(PKG_1);

        mNotificationGroup1 = new NotificationGroup();
        mNotificationGroup1.addNotification(mNotification1);
        mNotificationGroup2 = new NotificationGroup();
        List<String> childTiles = new ArrayList<>();
        childTiles.add("child1");
        mNotificationGroup2.addNotification(mNotification1);
        mNotificationGroup2.setChildTitles(childTiles);
        mNotificationGroupList1 = new ArrayList<>();
        mNotificationGroupList1.add(mNotificationGroup1);
        mNotificationGroupList1.add(mNotificationGroup2);
    }

    private ShadowPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }

    @Test
    public void onCreateViewHolder_carWarningType_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_WARNING);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_groupExpandedType_shouldReturnObjectOfGroupNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.GROUP_EXPANDED);

        assertThat(vh.getClass()).isEqualTo(GroupNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_groupCollapsed_shouldReturnObjectOfGroupNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.GROUP_COLLAPSED);

        assertThat(vh.getClass()).isEqualTo(GroupNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_groupSummaryType_shouldReturnObjectOfGroupSummaryNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.GROUP_SUMMARY);

        assertThat(vh.getClass()).isEqualTo(GroupSummaryNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_carInformation_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_carInfoInGroup_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_shouldReturnObjectOfMessageNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(MessageNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_message_shouldReturnObjectOfMessageNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.MESSAGE);

        assertThat(vh.getClass()).isEqualTo(MessageNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_progressInGroupType_shouldReturnObjectOfProgressNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.PROGRESS_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(ProgressNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_progressInGroup_shouldReturnObjectOfProgressNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.PROGRESS);

        assertThat(vh.getClass()).isEqualTo(ProgressNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_inboxInGroupType_shouldReturnObjectOfInboxNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.INBOX_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(InboxNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_inbox_shouldReturnObjectOfInboxNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.INBOX);

        assertThat(vh.getClass()).isEqualTo(InboxNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_basicInGroup_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.BASIC_IN_GROUP);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_basic_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.BASIC);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onCreateViewHolder_default_shouldReturnObjectOfBasicNotificationViewHolder() {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                12345);

        assertThat(vh.getClass()).isEqualTo(BasicNotificationViewHolder.class);
    }

    @Test
    public void onBindViewHolder_default_shouldNotThrowError() {
        initializeWithFactory(false);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                12345);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_carWarning_shouldNotThrowError() {
        initializeWithFactory(false);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_WARNING);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_carInformation_shouldNotThrowError() {
        initializeWithFactory(false);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_basic_shouldNotThrowError() {
        initializeWithFactory(false);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.BASIC);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_groupExpanded_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP_EXPANDED);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_groupCollapsed_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP_COLLAPSED);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_message_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageWithRestriction_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        when(mCarUxRestrictionsMock.getActiveRestrictions()).thenReturn(
                CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE);
        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageInGroup_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_messageInGroupWithRestriction_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        when(mCarUxRestrictionsMock.getActiveRestrictions()).thenReturn(
                CarUxRestrictions.UX_RESTRICTIONS_NO_TEXT_MESSAGE);
        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.MESSAGE_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_progress_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.PROGRESS);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_progressInGroup_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.PROGRESS_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_inbox_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.INBOX);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_inboxInGroup_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.INBOX_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_groupSummary_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.GROUP_SUMMARY);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void onBindViewHolder_carInfo_shouldNotThrowError() {
        initializeWithFactory(false);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.onCreateViewHolder(null,
                NotificationViewType.CAR_INFORMATION);

        mCarNotificationViewAdapter.onBindViewHolder(vh, 0);
    }

    @Test
    public void onBindViewHolder_basicInGroup_shouldNotThrowError() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        RecyclerView.ViewHolder vh = mCarNotificationViewAdapter.createViewHolder(null,
                NotificationViewType.BASIC_IN_GROUP);
        mCarNotificationViewAdapter.onBindViewHolder(vh, 2);
    }

    @Test
    public void getItemViewType_shouldReturnGroupCollapsed() {
        initializeWithFactory(false);
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(), false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.GROUP_COLLAPSED);
    }

    @Test
    public void getItemViewType_shouldReturnGroupExpanded() {
        initializeWithFactory(false);
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(), true);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.GROUP_EXPANDED);
    }

    @Test
    public void getItemViewType_shouldReturnCarEmergency() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_CAR_EMERGENCY));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_EMERGENCY);
    }

    @Test
    public void getItemViewType_shouldReturnCarWarning() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_CAR_WARNING));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_WARNING);
    }

    @Test
    public void getItemViewType_shouldReturnCarInformation() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_CAR_INFORMATION));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_INFORMATION);
    }

    @Test
    public void getItemViewType_shouldReturnCarInformationInGroup() {
        initializeWithFactory(true);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_CAR_INFORMATION));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.CAR_INFORMATION_IN_GROUP);
    }

    @Test
    public void getItemViewType_shouldReturnMessageInGroup() {
        initializeWithFactory(true);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_MESSAGE));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.MESSAGE_IN_GROUP);
    }

    @Test
    public void getItemViewType_shouldReturnMessage() {
        initializeWithFactory(false);

        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.addNotification(
                getNotificationWithCategory(Notification.CATEGORY_MESSAGE));
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(2);

        assertThat(itemViewType).isEqualTo(NotificationViewType.MESSAGE);
    }

    @Test
    public void getItemViewType_shouldReturnGroupSummary() {
        initializeWithFactory(false);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(1);

        assertThat(itemViewType).isEqualTo(NotificationViewType.GROUP_SUMMARY);
    }

    @Test
    public void getItemViewType_shouldReturnBasic() {
        initializeWithFactory(false);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(0);

        assertThat(itemViewType).isEqualTo(NotificationViewType.BASIC);
    }

    @Test
    public void getItemViewType_shouldReturnBasicInGroup() {
        initializeWithFactory(true);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemViewType = mCarNotificationViewAdapter.getItemViewType(0);

        assertThat(itemViewType).isEqualTo(NotificationViewType.BASIC_IN_GROUP);
    }

    @Test
    public void getItemCount_shouldReturnTwo() {
        initializeWithFactory(true);

        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);

        int itemCount = mCarNotificationViewAdapter.getItemCount();

        assertThat(itemCount).isEqualTo(2);
    }

    @Test
    public void setCarUxRestrictions_shouldSetCarUxRestrictions() {
        initializeWithFactory(true);

        mCarNotificationViewAdapter.setCarUxRestrictions(mCarUxRestrictionsMock);

        assertThat(mCarNotificationViewAdapter.getCarUxRestrictions()).isNotNull();
    }

    @Test
    public void getViewPool_shouldReturnNotNull() {
        initializeWithFactory(false);

        assertThat(mCarNotificationViewAdapter.getViewPool()).isNotNull();
    }

    @Test
    public void getViewPool_shouldThrowError() {
        initializeWithFactory(true);

        assertThrows(IllegalStateException.class,
                () -> mCarNotificationViewAdapter.getViewPool());
    }

    @Test
    public void clearAllNotifications_verifyHandlerFactoryIsCalled() {
        initializeWithFactory(true);

        mCarNotificationViewAdapter.clearAllNotifications();

        verify(mClickHandlerFactoryMock, times(1)).clearAllNotifications();
    }

    @Test
    public void getItemId_shouldReturnHashCode() {
        initializeWithFactory(false);
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        notificationGroup.addNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(), true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo(notificationGroup.getGroupKey().hashCode());
    }

    @Test
    public void getItemId_shouldReturnSingleNotificationHashCode() {
        initializeWithFactory(false);
        NotificationGroup notificationGroup = new NotificationGroup();
        notificationGroup.setGroupSummaryNotification(mNotification1);
        mNotificationGroupList1.add(notificationGroup);
        mCarNotificationViewAdapter.setNotifications(
                mNotificationGroupList1, /* setRecyclerViewListHeaderAndFooter= */ false);
        mCarNotificationViewAdapter.setExpanded(notificationGroup.getGroupKey(), true);

        long itemId = mCarNotificationViewAdapter.getItemId(2);

        assertThat(itemId).isEqualTo(notificationGroup.getSingleNotification().getKey().hashCode());
    }

    private StatusBarNotification getNotificationWithCategory(String category) {
        Notification.Builder nb = new Notification.Builder(mContext,
                CHANNEL_ID)
                .setContentTitle(CONTENT_TITLE)
                .setCategory(category)
                .setSmallIcon(android.R.drawable.sym_def_app_icon);

        return new StatusBarNotification(PKG_1, OP_PKG,
                ID, TAG, UID, INITIAL_PID, nb.build(), USER_HANDLE,
                OVERRIDE_GROUP_KEY, POST_TIME);
    }


    private void initializeWithFactory(boolean isGroup) {
        mCarNotificationViewAdapter = new CarNotificationViewAdapter(mContext, isGroup);
        mCarNotificationViewAdapter.setClickHandlerFactory(mClickHandlerFactoryMock);
    }
}