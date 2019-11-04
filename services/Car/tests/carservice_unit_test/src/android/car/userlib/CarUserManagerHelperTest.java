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

package android.car.userlib;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

/**
 * This class contains unit tests for the {@link CarUserManagerHelper}.
 * It tests that {@link CarUserManagerHelper} does the right thing for user management flows.
 *
 * The following mocks are used:
 * 1. {@link Context} provides system services and resources.
 * 2. {@link UserManager} provides dummy users and user info.
 * 3. {@link ActivityManager} to verify user switch is invoked.
 * 4. {@link CarUserManagerHelper.OnUsersUpdateListener} registers a listener for user updates.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class CarUserManagerHelperTest {
    @Mock
    private Context mContext;
    @Mock
    private UserManager mUserManager;
    @Mock
    private ActivityManager mActivityManager;
    @Mock
    private CarUserManagerHelper.OnUsersUpdateListener mTestListener;
    @Mock
    private TestableFrameworkWrapper mTestableFrameworkWrapper;

    private static final String GUEST_USER_NAME = "testGuest";
    private static final String TEST_USER_NAME = "testUser";
    private static final String DEFAULT_ADMIN_NAME = "defaultAdminName";

    private CarUserManagerHelper mCarUserManagerHelper;
    private UserInfo mCurrentProcessUser;
    private UserInfo mSystemUser;
    private int mForegroundUserId;
    private UserInfo mForegroundUser;

    @Before
    public void setUpMocksAndVariables() throws Exception {
        MockitoAnnotations.initMocks(this);
        doReturn(mUserManager).when(mContext).getSystemService(Context.USER_SERVICE);
        doReturn(mActivityManager).when(mContext).getSystemService(Context.ACTIVITY_SERVICE);
        doReturn(InstrumentationRegistry.getTargetContext().getResources())
                .when(mContext).getResources();
        doReturn(InstrumentationRegistry.getTargetContext().getContentResolver())
                .when(mContext).getContentResolver();
        doReturn(mContext).when(mContext).getApplicationContext();
        mCarUserManagerHelper = new CarUserManagerHelper(mContext, mTestableFrameworkWrapper);
        mCarUserManagerHelper.setDefaultAdminName(DEFAULT_ADMIN_NAME);

        mCurrentProcessUser = createUserInfoForId(UserHandle.myUserId());
        mSystemUser = createUserInfoForId(UserHandle.USER_SYSTEM);
        doReturn(mCurrentProcessUser).when(mUserManager).getUserInfo(UserHandle.myUserId());

        // Get the ID of the foreground user running this test.
        // We cannot mock the foreground user since getCurrentUser is static.
        // We cannot rely on foreground_id != system_id, they could be the same user.
        mForegroundUserId = ActivityManager.getCurrentUser();
        mForegroundUser = createUserInfoForId(mForegroundUserId);

        // Clear boot override for every test by returning the default value passed to the method
        when(mTestableFrameworkWrapper.getBootUserOverrideId(anyInt()))
                .thenAnswer(stub -> stub.getArguments()[0]);
    }

    @Test
    public void checkHeadlessSystemUserFlag() {
        // Make sure the headless system user flag is on.
        assertThat(mCarUserManagerHelper.isHeadlessSystemUser()).isTrue();
    }

    @Test
    public void checkIsSystemUser() {
        UserInfo testInfo = new UserInfo();

        testInfo.id = UserHandle.USER_SYSTEM;
        assertThat(mCarUserManagerHelper.isSystemUser(testInfo)).isTrue();

        testInfo.id = UserHandle.USER_SYSTEM + 2; // Make it different than system id.
        assertThat(mCarUserManagerHelper.isSystemUser(testInfo)).isFalse();
    }

    // System user will not be returned when calling get all users.
    @Test
    public void testHeadlessUser0GetAllUsers_NotReturnSystemUser() {
        UserInfo otherUser1 = createUserInfoForId(10);
        UserInfo otherUser2 = createUserInfoForId(11);
        UserInfo otherUser3 = createUserInfoForId(12);

        mockGetUsers(mSystemUser, otherUser1, otherUser2, otherUser3);

        assertThat(mCarUserManagerHelper.getAllUsers())
                .containsExactly(otherUser1, otherUser2, otherUser3);
    }

    @Test
    public void testGetAllSwitchableUsers() {
        // Create two non-foreground users.
        UserInfo user1 = createUserInfoForId(mForegroundUserId + 1);
        UserInfo user2 = createUserInfoForId(mForegroundUserId + 2);

        mockGetUsers(mForegroundUser, user1, user2);

        // Should return all non-foreground users.
        assertThat(mCarUserManagerHelper.getAllSwitchableUsers()).containsExactly(user1, user2);
    }

    @Test
    public void testGetAllPersistentUsers() {
        // Create two non-ephemeral users.
        UserInfo user1 = createUserInfoForId(mForegroundUserId);
        UserInfo user2 = createUserInfoForId(mForegroundUserId + 1);
        // Create two ephemeral users.
        UserInfo user3 = new UserInfo(
                /* id= */mForegroundUserId + 2, /* name = */ "user3", UserInfo.FLAG_EPHEMERAL);
        UserInfo user4 = new UserInfo(
                /* id= */mForegroundUserId + 3, /* name = */ "user4", UserInfo.FLAG_EPHEMERAL);

        mockGetUsers(user1, user2, user3, user4);

        // Should return all non-ephemeral users.
        assertThat(mCarUserManagerHelper.getAllPersistentUsers()).containsExactly(user1, user2);
    }

    @Test
    public void testGetAllAdminUsers() {
        // Create two admin, and two non-admin users.
        UserInfo user1 = new UserInfo(/* id= */ 10, /* name = */ "user10", UserInfo.FLAG_ADMIN);
        UserInfo user2 = createUserInfoForId(11);
        UserInfo user3 = createUserInfoForId(12);
        UserInfo user4 = new UserInfo(/* id= */ 13, /* name = */ "user13", UserInfo.FLAG_ADMIN);

        mockGetUsers(user1, user2, user3, user4);

        // Should return only admin users.
        assertThat(mCarUserManagerHelper.getAllAdminUsers()).containsExactly(user1, user4);
    }

    @Test
    public void testGetAllUsersExceptGuests() {
        // Create two users and a guest user.
        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 = createUserInfoForId(12);
        UserInfo user3 = new UserInfo(/* id= */ 13, /* name = */ "user13", UserInfo.FLAG_GUEST);

        mockGetUsers(user1, user2, user3);

        // Should not return guests.
        assertThat(mCarUserManagerHelper.getAllUsersExceptGuests())
                .containsExactly(user1, user2);
    }

    @Test
    public void testUserCanBeRemoved() {
        UserInfo testInfo = new UserInfo();

        // System user cannot be removed.
        testInfo.id = UserHandle.USER_SYSTEM;
        assertThat(mCarUserManagerHelper.canUserBeRemoved(testInfo)).isFalse();

        testInfo.id = UserHandle.USER_SYSTEM + 2; // Make it different than system id.
        assertThat(mCarUserManagerHelper.canUserBeRemoved(testInfo)).isTrue();
    }

    @Test
    public void testCurrentProcessCanAddUsers() {
        doReturn(false).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_ADD_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessAddUsers()).isTrue();

        doReturn(true).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_ADD_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessAddUsers()).isFalse();
    }

    @Test
    public void testCurrentProcessCanRemoveUsers() {
        doReturn(false).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_REMOVE_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessRemoveUsers()).isTrue();

        doReturn(true).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_REMOVE_USER);
        assertThat(mCarUserManagerHelper.canCurrentProcessRemoveUsers()).isFalse();
    }

    @Test
    public void testCurrentProcessCanSwitchUsers() {
        doReturn(false).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        assertThat(mCarUserManagerHelper.canCurrentProcessSwitchUsers()).isTrue();

        doReturn(true).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        assertThat(mCarUserManagerHelper.canCurrentProcessSwitchUsers()).isFalse();
    }

    @Test
    public void testCurrentGuestProcessCannotModifyAccounts() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager).isGuestUser();

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testCurrentDemoProcessCannotModifyAccounts() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager).isDemoUser();

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testCurrentDisallowModifyAccountsProcessIsEnforced() {
        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isTrue();

        doReturn(true).when(mUserManager)
                .hasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS);

        assertThat(mCarUserManagerHelper.canCurrentProcessModifyAccounts()).isFalse();
    }

    @Test
    public void testGetMaxSupportedUsers() {
        setMaxSupportedUsers(11);

        // Max users - headless system user.
        assertThat(mCarUserManagerHelper.getMaxSupportedUsers()).isEqualTo(10);
    }

    @Test
    public void testGetMaxSupportedRealUsers() {
        setMaxSupportedUsers(7);

        // Create three managed profiles, and two normal users.
        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 =
                new UserInfo(/* id= */ 11, /* name = */ "user11", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user3 =
                new UserInfo(/* id= */ 12, /* name = */ "user12", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user4 = createUserInfoForId(13);
        UserInfo user5 =
                new UserInfo(/* id= */ 14, /* name = */ "user14", UserInfo.FLAG_MANAGED_PROFILE);

        mockGetUsers(user1, user2, user3, user4, user5);

        // Max users - # managed profiles - headless system user.
        assertThat(mCarUserManagerHelper.getMaxSupportedRealUsers()).isEqualTo(3);
    }

    @Test
    public void testHeadlessSystemUser_IsUserLimitReached() {
        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 =
                new UserInfo(/* id= */ 11, /* name = */ "user11", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user3 =
                new UserInfo(/* id= */ 12, /* name = */ "user12", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user4 = createUserInfoForId(13);

        mockGetUsers(mSystemUser, user1, user2, user3, user4);

        setMaxSupportedUsers(6);
        assertThat(mCarUserManagerHelper.isUserLimitReached()).isFalse();

        setMaxSupportedUsers(5);
        assertThat(mCarUserManagerHelper.isUserLimitReached()).isTrue();
    }

    @Test
    public void testIsUserLimitReachedIgnoresGuests() {
        setMaxSupportedUsers(6);

        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 =
                new UserInfo(/* id= */ 11, /* name = */ "user11", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user3 =
                new UserInfo(/* id= */ 12, /* name = */ "user12", UserInfo.FLAG_MANAGED_PROFILE);
        UserInfo user4 = createUserInfoForId(13);
        UserInfo user5 = new UserInfo(/* id= */ 14, /* name = */ "user14", UserInfo.FLAG_GUEST);
        UserInfo user6 = createUserInfoForId(15);

        mockGetUsers(user1, user2, user3, user4);
        assertThat(mCarUserManagerHelper.isUserLimitReached()).isFalse();

        // Add guest user. Verify it doesn't affect the limit.
        mockGetUsers(user1, user2, user3, user4, user5);
        assertThat(mCarUserManagerHelper.isUserLimitReached()).isFalse();

        // Add normal user. Limit is reached
        mockGetUsers(user1, user2, user3, user4, user5, user6);
        assertThat(mCarUserManagerHelper.isUserLimitReached()).isTrue();
    }

    @Test
    public void testCreateNewAdminUserCallsCreateUser() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        mCarUserManagerHelper.createNewAdminUser(TEST_USER_NAME);
        verify(mUserManager).createUser(TEST_USER_NAME, UserInfo.FLAG_ADMIN);
    }

    @Test
    public void testCreateNewAdminUserReturnsNullUsers() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        doReturn(null).when(mUserManager).createUser(TEST_USER_NAME, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser(TEST_USER_NAME)).isNull();
    }

    @Test
    public void testCreateNewAdminUserReturnsCreatedUser() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        UserInfo newUser = new UserInfo();
        newUser.name = TEST_USER_NAME;
        doReturn(newUser).when(mUserManager).createUser(TEST_USER_NAME, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser(TEST_USER_NAME)).isEqualTo(newUser);
    }

    @Test
    public void testCreateNewAdminUserWithDefaultUserNameCallsCreateUser() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        mCarUserManagerHelper.createNewAdminUser();
        verify(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
    }

    @Test
    public void testCreateNewAdminUserWithDefaultUserNameReturnsNullUsers() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        doReturn(null).when(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser(DEFAULT_ADMIN_NAME)).isNull();
    }

    @Test
    public void testCreateNewAdminUserWithDefaultUserNameReturnsCreatedUser() {
        // Make sure current user is admin, since only admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();

        UserInfo newUser = new UserInfo();
        newUser.name = DEFAULT_ADMIN_NAME;
        doReturn(newUser).when(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        assertThat(mCarUserManagerHelper.createNewAdminUser()).isEqualTo(newUser);
    }

    @Test
    public void testAdminsCanCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;
        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // Admins can create other admins.
        doReturn(true).when(mUserManager).isAdminUser();
        UserInfo actualAdmin = mCarUserManagerHelper.createNewAdminUser(newAdminName);
        assertThat(actualAdmin).isEqualTo(expectedAdmin);
    }

    @Test
    public void testNonAdminsCanNotCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;
        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // Test that non-admins cannot create new admins.
        doReturn(false).when(mUserManager).isAdminUser(); // Current user non-admin.
        assertThat(mCarUserManagerHelper.createNewAdminUser(newAdminName)).isNull();
    }

    @Test
    public void testSystemUserCanCreateAdmins() {
        String newAdminName = "Test new admin";
        UserInfo expectedAdmin = new UserInfo();
        expectedAdmin.name = newAdminName;

        doReturn(expectedAdmin).when(mUserManager).createUser(newAdminName, UserInfo.FLAG_ADMIN);

        // System user can create admins.
        doReturn(true).when(mUserManager).isSystemUser();
        UserInfo actualAdmin = mCarUserManagerHelper.createNewAdminUser(newAdminName);
        assertThat(actualAdmin).isEqualTo(expectedAdmin);
    }

    @Test
    public void testCreateNewNonAdminUser() {
        // Verify createUser on UserManager gets called.
        mCarUserManagerHelper.createNewNonAdminUser(TEST_USER_NAME);
        verify(mUserManager).createUser(TEST_USER_NAME, 0);

        doReturn(null).when(mUserManager).createUser(TEST_USER_NAME, 0);
        assertThat(mCarUserManagerHelper.createNewNonAdminUser(TEST_USER_NAME)).isNull();

        UserInfo newUser = new UserInfo();
        newUser.name = TEST_USER_NAME;
        doReturn(newUser).when(mUserManager).createUser(TEST_USER_NAME, 0);
        assertThat(mCarUserManagerHelper.createNewNonAdminUser(TEST_USER_NAME)).isEqualTo(newUser);
    }

    @Test
    public void testCannotRemoveSystemUser() {
        assertThat(mCarUserManagerHelper.removeUser(mSystemUser, GUEST_USER_NAME)).isFalse();
    }

    @Test
    public void testAdminsCanRemoveOtherUsers() {
        int idToRemove = mCurrentProcessUser.id + 2;
        UserInfo userToRemove = createUserInfoForId(idToRemove);

        doReturn(true).when(mUserManager).removeUser(idToRemove);

        // If Admin is removing non-current, non-system user, simply calls removeUser.
        doReturn(true).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.removeUser(userToRemove, GUEST_USER_NAME)).isTrue();
        verify(mUserManager).removeUser(idToRemove);
    }

    @Test
    public void testNonAdminsCanNotRemoveOtherUsers() {
        UserInfo otherUser = createUserInfoForId(mCurrentProcessUser.id + 2);

        // Make current user non-admin.
        doReturn(false).when(mUserManager).isAdminUser();

        // Mock so that removeUser always pretends it's successful.
        doReturn(true).when(mUserManager).removeUser(anyInt());

        // If Non-Admin is trying to remove someone other than themselves, they should fail.
        assertThat(mCarUserManagerHelper.removeUser(otherUser, GUEST_USER_NAME)).isFalse();
        verify(mUserManager, never()).removeUser(otherUser.id);
    }

    @Test
    public void testRemoveLastActiveUser() {
        // Cannot remove system user.
        assertThat(mCarUserManagerHelper.removeUser(mSystemUser, GUEST_USER_NAME)).isFalse();

        UserInfo adminInfo = new UserInfo(/* id= */10, "admin", UserInfo.FLAG_ADMIN);
        mockGetUsers(adminInfo);

        assertThat(mCarUserManagerHelper.removeUser(adminInfo, GUEST_USER_NAME))
                .isEqualTo(false);
    }

    @Test
    public void testRemoveLastAdminUser() {
        // Make current user admin.
        doReturn(true).when(mUserManager).isAdminUser();

        UserInfo adminInfo = new UserInfo(/* id= */10, "admin", UserInfo.FLAG_ADMIN);
        UserInfo nonAdminInfo = new UserInfo(/* id= */11, "non-admin", 0);
        mockGetUsers(adminInfo, nonAdminInfo);

        UserInfo newAdminInfo = new UserInfo(/* id= */12, DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        doReturn(newAdminInfo)
                .when(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);

        mCarUserManagerHelper.removeUser(adminInfo, GUEST_USER_NAME);
        verify(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        verify(mActivityManager).switchUser(newAdminInfo.id);
        verify(mUserManager).removeUser(adminInfo.id);
    }

    @Test
    public void testRemoveLastAdminUserFailsToCreateNewUser() {
        // Make current user admin.
        doReturn(true).when(mUserManager).isAdminUser();

        UserInfo adminInfo = new UserInfo(/* id= */10, "admin", UserInfo.FLAG_ADMIN);
        UserInfo nonAdminInfo = new UserInfo(/* id= */11, "non-admin", 0);
        mockGetUsers(adminInfo, nonAdminInfo);

        UserInfo newAdminInfo = new UserInfo(/* id= */12, DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        doReturn(newAdminInfo)
                .when(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);

        // Fail to create a new user to force a failure case
        doReturn(null)
                .when(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);

        mCarUserManagerHelper.removeUser(adminInfo, GUEST_USER_NAME);
        verify(mUserManager).createUser(DEFAULT_ADMIN_NAME, UserInfo.FLAG_ADMIN);
        verify(mActivityManager, never()).switchUser(anyInt());
        verify(mUserManager, never()).removeUser(adminInfo.id);
    }

    @Test
    public void testSwitchToGuest() {
        mCarUserManagerHelper.startGuestSession(GUEST_USER_NAME);
        verify(mUserManager).createGuest(mContext, GUEST_USER_NAME);

        UserInfo guestInfo = new UserInfo(/* id= */21, GUEST_USER_NAME, UserInfo.FLAG_GUEST);
        doReturn(guestInfo).when(mUserManager).createGuest(mContext, GUEST_USER_NAME);
        mCarUserManagerHelper.startGuestSession(GUEST_USER_NAME);
        verify(mActivityManager).switchUser(21);
    }

    @Test
    public void testSwitchToId() {
        int userIdToSwitchTo = mForegroundUserId + 2;
        doReturn(true).when(mActivityManager).switchUser(userIdToSwitchTo);

        assertThat(mCarUserManagerHelper.switchToUserId(userIdToSwitchTo)).isTrue();
        verify(mActivityManager).switchUser(userIdToSwitchTo);
    }

    @Test
    public void testSwitchToForegroundIdExitsEarly() {
        doReturn(true).when(mActivityManager).switchUser(mForegroundUserId);

        assertThat(mCarUserManagerHelper.switchToUserId(mForegroundUserId)).isFalse();
        verify(mActivityManager, never()).switchUser(mForegroundUserId);
    }

    @Test
    public void testCannotSwitchIfSwitchingNotAllowed() {
        int userIdToSwitchTo = mForegroundUserId + 2;
        doReturn(true).when(mActivityManager).switchUser(userIdToSwitchTo);
        doReturn(true).when(mUserManager).hasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        assertThat(mCarUserManagerHelper.switchToUserId(userIdToSwitchTo)).isFalse();
        verify(mActivityManager, never()).switchUser(userIdToSwitchTo);
    }

    @Test
    public void testGetUserIcon() {
        mCarUserManagerHelper.getUserIcon(mCurrentProcessUser);
        verify(mUserManager).getUserIcon(mCurrentProcessUser.id);
    }

    @Test
    public void testScaleUserIcon() {
        Bitmap fakeIcon = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888);
        Drawable scaledIcon = mCarUserManagerHelper.scaleUserIcon(fakeIcon, 300);
        assertThat(scaledIcon.getIntrinsicWidth()).isEqualTo(300);
        assertThat(scaledIcon.getIntrinsicHeight()).isEqualTo(300);
    }

    @Test
    public void testSetUserName() {
        UserInfo testInfo = createUserInfoForId(mCurrentProcessUser.id + 3);
        String newName = "New Test Name";
        mCarUserManagerHelper.setUserName(testInfo, newName);
        verify(mUserManager).setUserName(mCurrentProcessUser.id + 3, newName);
    }

    @Test
    public void testIsCurrentProcessSystemUser() {
        doReturn(true).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.isCurrentProcessAdminUser()).isTrue();

        doReturn(false).when(mUserManager).isAdminUser();
        assertThat(mCarUserManagerHelper.isCurrentProcessAdminUser()).isFalse();
    }

    @Test
    public void testGrantAdminPermissions() {
        int userId = 30;
        UserInfo testInfo = createUserInfoForId(userId);

        // Test that non-admins cannot grant admin permissions.
        doReturn(false).when(mUserManager).isAdminUser(); // Current user non-admin.
        mCarUserManagerHelper.grantAdminPermissions(testInfo);
        verify(mUserManager, never()).setUserAdmin(userId);

        // Admins can grant admin permissions.
        doReturn(true).when(mUserManager).isAdminUser();
        mCarUserManagerHelper.grantAdminPermissions(testInfo);
        verify(mUserManager).setUserAdmin(userId);
    }

    @Test
    public void testSetUserRestriction() {
        int userId = 20;
        UserInfo testInfo = createUserInfoForId(userId);

        mCarUserManagerHelper.setUserRestriction(
                testInfo, UserManager.DISALLOW_ADD_USER, /* enable= */ true);
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_ADD_USER, true, UserHandle.of(userId));

        mCarUserManagerHelper.setUserRestriction(
                testInfo, UserManager.DISALLOW_REMOVE_USER, /* enable= */ false);
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_REMOVE_USER, false, UserHandle.of(userId));
    }

    @Test
    public void testDefaultNonAdminRestrictions() {
        String testUserName = "Test User";
        int userId = 20;
        UserInfo newNonAdmin = createUserInfoForId(userId);

        doReturn(newNonAdmin).when(mUserManager).createUser(testUserName, /* flags= */ 0);

        mCarUserManagerHelper.createNewNonAdminUser(testUserName);

        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET, /* enable= */ true, UserHandle.of(userId));
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_SMS, /* enable= */ false, UserHandle.of(userId));
        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_OUTGOING_CALLS, /* enable= */ false, UserHandle.of(userId));
    }

    @Test
    public void testDefaultGuestRestrictions() {
        int guestRestrictionsExpectedCount = 6;

        ArgumentCaptor<Bundle> bundleCaptor = ArgumentCaptor.forClass(Bundle.class);
        mCarUserManagerHelper.initDefaultGuestRestrictions();

        verify(mUserManager).setDefaultGuestRestrictions(bundleCaptor.capture());
        Bundle guestRestrictions = bundleCaptor.getValue();

        assertThat(guestRestrictions.keySet()).hasSize(guestRestrictionsExpectedCount);
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_FACTORY_RESET)).isTrue();
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_REMOVE_USER)).isTrue();
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_MODIFY_ACCOUNTS)).isTrue();
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_INSTALL_APPS)).isTrue();
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES))
                .isTrue();
        assertThat(guestRestrictions.getBoolean(UserManager.DISALLOW_UNINSTALL_APPS)).isTrue();
    }

    @Test
    public void testGrantingAdminPermissionsRemovesNonAdminRestrictions() {
        int testUserId = 30;
        boolean restrictionEnabled = false;
        UserInfo testInfo = createUserInfoForId(testUserId);

        // Only admins can grant permissions.
        doReturn(true).when(mUserManager).isAdminUser();

        mCarUserManagerHelper.grantAdminPermissions(testInfo);

        verify(mUserManager).setUserRestriction(
                UserManager.DISALLOW_FACTORY_RESET, restrictionEnabled, UserHandle.of(testUserId));
    }

    @Test
    public void testRegisterUserChangeReceiver() {
        mCarUserManagerHelper.registerOnUsersUpdateListener(mTestListener);

        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);
        ArgumentCaptor<UserHandle> handleCaptor = ArgumentCaptor.forClass(UserHandle.class);
        ArgumentCaptor<IntentFilter> filterCaptor = ArgumentCaptor.forClass(IntentFilter.class);
        ArgumentCaptor<String> permissionCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Handler> handlerCaptor = ArgumentCaptor.forClass(Handler.class);

        verify(mContext).registerReceiverAsUser(
                receiverCaptor.capture(),
                handleCaptor.capture(),
                filterCaptor.capture(),
                permissionCaptor.capture(),
                handlerCaptor.capture());

        // Verify we're listening to Intents from ALL users.
        assertThat(handleCaptor.getValue()).isEqualTo(UserHandle.ALL);

        // Verify the presence of each intent in the filter.
        // Verify the exact number of filters. Every time a new intent is added, this test should
        // get updated.
        assertThat(filterCaptor.getValue().countActions()).isEqualTo(6);
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_REMOVED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_ADDED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_INFO_CHANGED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_SWITCHED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_STOPPED)).isTrue();
        assertThat(filterCaptor.getValue().hasAction(Intent.ACTION_USER_UNLOCKED)).isTrue();

        // Verify that calling the receiver calls the listener.
        receiverCaptor.getValue().onReceive(mContext, new Intent());
        verify(mTestListener).onUsersUpdate();

        assertThat(permissionCaptor.getValue()).isNull();
        assertThat(handlerCaptor.getValue()).isNull();

        // Unregister the receiver.
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(mTestListener);
        verify(mContext).unregisterReceiver(receiverCaptor.getValue());
    }

    @Test
    public void testMultipleRegistrationsOfSameListener() {
        CarUserManagerHelper.OnUsersUpdateListener listener =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);

        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        mCarUserManagerHelper.registerOnUsersUpdateListener(listener);
        mCarUserManagerHelper.registerOnUsersUpdateListener(listener);
        // Even for multiple registrations of the same listener, broadcast receiver registered once.
        verify(mContext, times(1))
                .registerReceiverAsUser(receiverCaptor.capture(), any(), any(), any(), any());

        // Verify that calling the receiver calls the listener.
        receiverCaptor.getValue().onReceive(mContext, new Intent());
        verify(listener).onUsersUpdate();

        // Verify that a single removal unregisters the listener.
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener);
        verify(mContext).unregisterReceiver(any());
    }

    @Test
    public void testMultipleUnregistrationsOfTheSameListener() {
        CarUserManagerHelper.OnUsersUpdateListener listener =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        mCarUserManagerHelper.registerOnUsersUpdateListener(listener);

        // Verify that a multiple unregistrations cause only one unregister for broadcast receiver.
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener);
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener);
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener);
        verify(mContext, times(1)).unregisterReceiver(any());
    }

    @Test
    public void testUnregisterReceiverCalledAfterAllListenersUnregister() {
        CarUserManagerHelper.OnUsersUpdateListener listener1 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        CarUserManagerHelper.OnUsersUpdateListener listener2 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);

        mCarUserManagerHelper.registerOnUsersUpdateListener(listener1);
        mCarUserManagerHelper.registerOnUsersUpdateListener(listener2);

        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener1);
        verify(mContext, never()).unregisterReceiver(any());

        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener2);
        verify(mContext, times(1)).unregisterReceiver(any());
    }

    @Test
    public void testRegisteringMultipleListeners() {
        CarUserManagerHelper.OnUsersUpdateListener listener1 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        CarUserManagerHelper.OnUsersUpdateListener listener2 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        mCarUserManagerHelper.registerOnUsersUpdateListener(listener1);
        mCarUserManagerHelper.registerOnUsersUpdateListener(listener2);
        verify(mContext, times(1))
                .registerReceiverAsUser(receiverCaptor.capture(), any(), any(), any(), any());

        // Verify that calling the receiver calls both listeners.
        receiverCaptor.getValue().onReceive(mContext, new Intent());
        verify(listener1).onUsersUpdate();
        verify(listener2).onUsersUpdate();
    }

    @Test
    public void testUnregisteringListenerStopsUpdatesForListener() {
        CarUserManagerHelper.OnUsersUpdateListener listener1 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        CarUserManagerHelper.OnUsersUpdateListener listener2 =
                Mockito.mock(CarUserManagerHelper.OnUsersUpdateListener.class);
        ArgumentCaptor<BroadcastReceiver> receiverCaptor =
                ArgumentCaptor.forClass(BroadcastReceiver.class);

        mCarUserManagerHelper.registerOnUsersUpdateListener(listener1);
        mCarUserManagerHelper.registerOnUsersUpdateListener(listener2);
        verify(mContext, times(1))
                .registerReceiverAsUser(receiverCaptor.capture(), any(), any(), any(), any());

        // Unregister listener2
        mCarUserManagerHelper.unregisterOnUsersUpdateListener(listener2);

        // Verify that calling the receiver calls only one listener.
        receiverCaptor.getValue().onReceive(mContext, new Intent());
        verify(listener1).onUsersUpdate();
        verify(listener2, never()).onUsersUpdate();
    }

    @Test
    public void test_GetInitialUserWithValidLastActiveUser_ReturnsLastActiveUser() {
        int lastActiveUserId = 12;

        UserInfo user10 = createUserInfoForId(10);
        UserInfo user11 = createUserInfoForId(11);
        UserInfo user12 = createUserInfoForId(12);

        setLastActiveUser(lastActiveUserId);
        mockGetUsers(mSystemUser, user10, user11, user12);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(lastActiveUserId);
    }

    @Test
    public void test_GetInitialUserWithNonExistLastActiveUser_ReturnsSmallestUserId() {
        int lastActiveUserId = 12;
        int minimumUserId = 10;

        UserInfo smallestUser = createUserInfoForId(minimumUserId);
        UserInfo notSmallestUser = createUserInfoForId(minimumUserId + 1);

        setLastActiveUser(lastActiveUserId);
        mockGetUsers(mSystemUser, smallestUser, notSmallestUser);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(minimumUserId);
    }

    @Test
    public void test_GetInitialUserWithOverrideId_ReturnsOverrideId() {
        int lastActiveUserId = 12;
        int overrideUserId = 11;

        UserInfo user10 = createUserInfoForId(10);
        UserInfo user11 = createUserInfoForId(11);
        UserInfo user12 = createUserInfoForId(12);

        setDefaultBootUserOverride(overrideUserId);
        setLastActiveUser(lastActiveUserId);
        mockGetUsers(mSystemUser, user10, user11, user12);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(overrideUserId);
    }

    @Test
    public void test_GetInitialUserWithInvalidOverrideId_ReturnsLastActiveUserId() {
        int lastActiveUserId = 12;
        int overrideUserId = 15;

        UserInfo user10 = createUserInfoForId(10);
        UserInfo user11 = createUserInfoForId(11);
        UserInfo user12 = createUserInfoForId(12);

        setDefaultBootUserOverride(overrideUserId);
        setLastActiveUser(lastActiveUserId);
        mockGetUsers(mSystemUser, user10, user11, user12);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(lastActiveUserId);
    }

    @Test
    public void test_GetInitialUserWithInvalidOverrideAndLastActiveUserIds_ReturnsSmallestUserId() {
        int minimumUserId = 10;
        int invalidLastActiveUserId = 14;
        int invalidOverrideUserId = 15;

        UserInfo minimumUser = createUserInfoForId(minimumUserId);
        UserInfo user11 = createUserInfoForId(minimumUserId + 1);
        UserInfo user12 = createUserInfoForId(minimumUserId + 2);

        setDefaultBootUserOverride(invalidOverrideUserId);
        setLastActiveUser(invalidLastActiveUserId);
        mockGetUsers(mSystemUser, minimumUser, user11, user12);

        assertThat(mCarUserManagerHelper.getInitialUser()).isEqualTo(minimumUserId);
    }

    @Test
    public void test_CreateNewOrFindExistingGuest_ReturnsExistingGuest() {
        // Create two users and a guest user.
        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 = createUserInfoForId(12);
        UserInfo user3 = new UserInfo(/* id= */ 13, /* name = */ "user13", UserInfo.FLAG_GUEST);

        mockGetUsers(user1, user2, user3);
        doReturn(null).when(mUserManager).createGuest(any(), any());

        UserInfo guest = mCarUserManagerHelper.createNewOrFindExistingGuest(GUEST_USER_NAME);
        assertThat(guest).isEqualTo(user3);
    }

    @Test
    public void test_CreateNewOrFindExistingGuest_CreatesNewGuest_IfNoExisting() {
        // Create two users.
        UserInfo user1 = createUserInfoForId(10);
        UserInfo user2 = createUserInfoForId(12);

        mockGetUsers(user1, user2);

        // Create a user for the "new guest" user.
        UserInfo guestInfo = new UserInfo(/* id= */21, GUEST_USER_NAME, UserInfo.FLAG_GUEST);
        doReturn(guestInfo).when(mUserManager).createGuest(mContext, GUEST_USER_NAME);

        UserInfo guest = mCarUserManagerHelper.createNewOrFindExistingGuest(GUEST_USER_NAME);
        verify(mUserManager).createGuest(mContext, GUEST_USER_NAME);
        assertThat(guest).isEqualTo(guestInfo);
    }

    private UserInfo createUserInfoForId(int id) {
        UserInfo userInfo = new UserInfo();
        userInfo.id = id;
        return userInfo;
    }

    private void mockGetUsers(UserInfo... users) {
        List<UserInfo> testUsers = new ArrayList<>();
        for (UserInfo user : users) {
            testUsers.add(user);
        }
        doReturn(testUsers).when(mUserManager).getUsers(true);
    }

    private void setLastActiveUser(int userId) {
        Settings.Global.putInt(InstrumentationRegistry.getTargetContext().getContentResolver(),
                Settings.Global.LAST_ACTIVE_USER_ID, userId);
    }

    private void setDefaultBootUserOverride(int userId) {
        doReturn(userId).when(mTestableFrameworkWrapper)
                .getBootUserOverrideId(anyInt());
    }

    private void setMaxSupportedUsers(int maxValue) {
        doReturn(maxValue).when(mTestableFrameworkWrapper).userManagerGetMaxSupportedUsers();
    }
}