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
 * limitations under the License.
 */

package com.android.car.user;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.app.IActivityManager;
import android.car.settings.CarSettings;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.location.LocationManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;

/**
 * This class contains unit tests for the {@link CarUserService}.
 *
 * The following mocks are used:
 * <ol>
 * <li> {@link Context} provides system services and resources.
 * <li> {@link CarUserManagerHelper} provides user info and actions.
 * <ol/>
 */
@RunWith(AndroidJUnit4.class)
public class CarUserServiceTest {
    private CarUserService mCarUserService;

    @Mock
    private Context mMockContext;

    @Mock
    private Context mApplicationContext;

    @Mock
    private LocationManager mLocationManager;

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    private static final String DEFAULT_ADMIN_NAME = "defaultName";

    @Mock
    private IActivityManager mMockedIActivityManager;

    @Mock
    private UserManager mMockedUserManager;

    private boolean mUser0TaskExecuted;


    /**
     * Initialize all of the objects with the @Mock annotation.
     */
    @Before
    public void setUpMocks() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mApplicationContext).when(mMockContext).getApplicationContext();
        doReturn(mLocationManager).when(mMockContext).getSystemService(Context.LOCATION_SERVICE);
        doReturn(InstrumentationRegistry.getTargetContext().getContentResolver())
                .when(mMockContext).getContentResolver();
        doReturn(mMockedUserManager).when(mMockContext).getSystemService(Context.USER_SERVICE);
        doReturn(false).when(mMockedUserManager).isUserUnlockingOrUnlocked(anyInt());
        mCarUserService = new CarUserService(mMockContext, mCarUserManagerHelper,
                mMockedIActivityManager, 3);

        doReturn(new ArrayList<>()).when(mCarUserManagerHelper).getAllUsers();

        // Restore default value at the beginning of each test.
        putSettingsInt(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 0);
    }

    /**
     * Test that the {@link CarUserService} registers to receive the locked boot completed
     * intent.
     */
    @Test
    public void testRegistersToReceiveEvents() {
        ArgumentCaptor<IntentFilter> argument = ArgumentCaptor.forClass(IntentFilter.class);
        mCarUserService.init();
        verify(mMockContext).registerReceiver(eq(mCarUserService), argument.capture());
        IntentFilter intentFilter = argument.getValue();
        assertThat(intentFilter.countActions()).isEqualTo(1);

        assertThat(intentFilter.getAction(0)).isEqualTo(Intent.ACTION_USER_SWITCHED);
    }

    /**
     * Test that the {@link CarUserService} unregisters its event receivers.
     */
    @Test
    public void testUnregistersEventReceivers() {
        mCarUserService.release();
        verify(mMockContext).unregisterReceiver(mCarUserService);
    }

    /**
     * Test that the {@link CarUserService} does set the disable modify account permission for
     * user 0 upon user 0 unlock when user 0 is headless.
     */
    @Test
    public void testDisableModifyAccountsForHeadlessSystemUserOnFirstRun() {
        // Mock system user.
        UserInfo systemUser = new UserInfo();
        systemUser.id = UserHandle.USER_SYSTEM;
        doReturn(systemUser).when(mCarUserManagerHelper).getSystemUserInfo();
        doReturn(true).when(mCarUserManagerHelper).isHeadlessSystemUser();

        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);

        verify(mCarUserManagerHelper)
                .setUserRestriction(systemUser, UserManager.DISALLOW_MODIFY_ACCOUNTS, true);
    }

    /**
     * Test that the {@link CarUserService} does not set the disable modify account permission for
     * user 0 upon user 0 unlock when user 0 is not headless.
     */
    @Test
    public void testDisableModifyAccountsForRegularSystemUserOnFirstRun() {
        // Mock system user.
        UserInfo systemUser = new UserInfo();
        systemUser.id = UserHandle.USER_SYSTEM;
        doReturn(systemUser).when(mCarUserManagerHelper).getSystemUserInfo();
        doReturn(false).when(mCarUserManagerHelper).isHeadlessSystemUser();

        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);

        verify(mCarUserManagerHelper, never())
                .setUserRestriction(systemUser, UserManager.DISALLOW_MODIFY_ACCOUNTS, true);
    }

    /**
     * Test that the {@link CarUserService} does not set restrictions on user 0 if they have already
     * been set.
     */
    @Test
    public void testDoesNotSetSystemUserRestrictions_IfRestrictionsAlreadySet() {
        // Mock system user.
        UserInfo systemUser = new UserInfo();
        systemUser.id = UserHandle.USER_SYSTEM;
        doReturn(systemUser).when(mCarUserManagerHelper).getSystemUserInfo();

        putSettingsInt(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 1);
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);

        verify(mCarUserManagerHelper, never())
                .setUserRestriction(systemUser, UserManager.DISALLOW_MODIFY_ACCOUNTS, true);
    }

    /**
     * Test that the {@link CarUserService} disables the location service for headless user 0 upon
     * first run.
     */
    @Test
    public void testDisableLocationForHeadlessSystemUserOnFirstRun() {
        doReturn(true).when(mCarUserManagerHelper).isHeadlessSystemUser();
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);

        verify(mLocationManager).setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.of(UserHandle.USER_SYSTEM));
    }

    /**
     * Test that the {@link CarUserService} does not disable the location service for regular user 0
     * upon first run.
     */
    @Test
    public void testDisableLocationForRegularSystemUserOnFirstRun() {
        doReturn(false).when(mCarUserManagerHelper).isHeadlessSystemUser();
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);

        verify(mLocationManager, never()).setLocationEnabledForUser(
                /* enabled= */ false, UserHandle.of(UserHandle.USER_SYSTEM));
    }

    /**
     * Test that the {@link CarUserService} updates last active user on user switch intent.
     */
    @Test
    public void testLastActiveUserUpdatedOnUserSwitch() {
        int lastActiveUserId = 11;

        Intent intent = new Intent(Intent.ACTION_USER_SWITCHED);
        intent.putExtra(Intent.EXTRA_USER_HANDLE, lastActiveUserId);

        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(lastActiveUserId);

        mCarUserService.onReceive(mMockContext, intent);

        verify(mCarUserManagerHelper).setLastActiveUser(lastActiveUserId);
    }

    /**
     * Test that the {@link CarUserService} sets default guest restrictions on first boot.
     */
    @Test
    public void testInitializeGuestRestrictions_IfNotAlreadySet() {
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        verify(mCarUserManagerHelper).initDefaultGuestRestrictions();
        assertThat(getSettingsInt(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET)).isEqualTo(1);
    }

    /**
     * Test that the {@link CarUserService} does not set restrictions after they have been set once.
     */
    @Test
    public void test_DoesNotInitializeGuestRestrictions_IfAlreadySet() {
        putSettingsInt(CarSettings.Global.DEFAULT_USER_RESTRICTIONS_SET, 1);
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        verify(mCarUserManagerHelper, never()).initDefaultGuestRestrictions();
    }

    @Test
    public void testRunOnUser0UnlockImmediate() {
        mUser0TaskExecuted = false;
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        mCarUserService.runOnUser0Unlock(() -> {
            mUser0TaskExecuted = true;
        });
        assertTrue(mUser0TaskExecuted);
    }

    @Test
    public void testRunOnUser0UnlockLater() {
        mUser0TaskExecuted = false;
        mCarUserService.runOnUser0Unlock(() -> {
            mUser0TaskExecuted = true;
        });
        assertFalse(mUser0TaskExecuted);
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        assertTrue(mUser0TaskExecuted);
    }

    /**
     * Test is lengthy as it is testing LRU logic.
     */
    @Test
    public void testBackgroundUserList() {
        final int user1 = 101;
        final int user2 = 102;
        final int user3 = 103;
        final int user4Guest = 104;
        final int user5 = 105;
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user1);
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user2);
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user3);
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user5);

        doReturn(user1).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        // user 0 should never go to that list.
        assertTrue(mCarUserService.getBackgroundUsersToRestart().isEmpty());

        mCarUserService.setUserLockStatus(user1, true);
        assertEquals(new Integer[]{user1},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        // user 2 background, ignore in restart list
        mCarUserService.setUserLockStatus(user2, true);
        mCarUserService.setUserLockStatus(user1, false);
        assertEquals(new Integer[]{user1},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        doReturn(user3).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(user3, true);
        mCarUserService.setUserLockStatus(user2, false);
        assertEquals(new Integer[]{user3, user1},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        doReturn(user4Guest).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(user4Guest, true);
        mCarUserService.setUserLockStatus(user3, false);
        assertEquals(new Integer[]{user3, user1},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        doReturn(user5).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(user5, true);
        mCarUserService.setUserLockStatus(user4Guest, false);
        assertEquals(new Integer[]{user5, user3},
                mCarUserService.getBackgroundUsersToRestart().toArray());
    }

    /**
     * Test is lengthy as it is testing LRU logic.
     */
    @Test
    public void testBackgroundUsersStartStopKeepBackgroundUserList() throws Exception {
        final int user1 = 101;
        final int user2 = 102;
        final int user3 = 103;

        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user1);
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user2);
        doReturn(true).when(mCarUserManagerHelper).isPersistentUser(user3);
        doReturn(user1).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(UserHandle.USER_SYSTEM, true);
        mCarUserService.setUserLockStatus(user1, true);
        doReturn(user2).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(user2, true);
        mCarUserService.setUserLockStatus(user1, false);
        doReturn(user3).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        mCarUserService.setUserLockStatus(user3, true);
        mCarUserService.setUserLockStatus(user2, false);

        assertEquals(new Integer[]{user3, user2},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        doReturn(true).when(mMockedIActivityManager).startUserInBackground(user2);
        doReturn(true).when(mMockedIActivityManager).unlockUser(user2,
                null, null, null);
        assertEquals(new Integer[]{user2},
                mCarUserService.startAllBackgroundUsers().toArray());
        mCarUserService.setUserLockStatus(user2, true);
        assertEquals(new Integer[]{user3, user2},
                mCarUserService.getBackgroundUsersToRestart().toArray());

        doReturn(ActivityManager.USER_OP_SUCCESS).when(mMockedIActivityManager).stopUser(user2,
                true, null);
        // should not stop the current fg user
        assertFalse(mCarUserService.stopBackgroundUser(user3));
        assertTrue(mCarUserService.stopBackgroundUser(user2));
        assertEquals(new Integer[]{user3, user2},
                mCarUserService.getBackgroundUsersToRestart().toArray());
        mCarUserService.setUserLockStatus(user2, false);
        assertEquals(new Integer[]{user3, user2},
                mCarUserService.getBackgroundUsersToRestart().toArray());
    }

    @Test
    public void testStopBackgroundUserForSystemUser() {
        assertFalse(mCarUserService.stopBackgroundUser(UserHandle.USER_SYSTEM));
    }

    @Test
    public void testStopBackgroundUserForFgUser() {
        final int user1 = 101;
        doReturn(user1).when(mCarUserManagerHelper).getCurrentForegroundUserId();
        assertFalse(mCarUserService.stopBackgroundUser(UserHandle.USER_SYSTEM));
    }

    private void putSettingsInt(String key, int value) {
        Settings.Global.putInt(InstrumentationRegistry.getTargetContext().getContentResolver(),
                key, value);
    }

    private int getSettingsInt(String key) {
        return Settings.Global.getInt(
                InstrumentationRegistry.getTargetContext().getContentResolver(),
                key, /* default= */ 0);
    }

    private UserInfo mockAdmin(int adminId) {
        UserInfo admin = new UserInfo(adminId, DEFAULT_ADMIN_NAME,
                UserInfo.FLAG_ADMIN);
        doReturn(admin).when(mCarUserManagerHelper).createNewAdminUser();

        return admin;
    }
}
