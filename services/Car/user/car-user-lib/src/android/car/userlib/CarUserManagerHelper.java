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

import android.Manifest;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.app.ActivityManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.sysprop.CarProperties;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.os.RoSystemProperties;
import com.android.internal.util.UserIcons;

import com.google.android.collect.Sets;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * Helper class for {@link UserManager}, this is meant to be used by builds that support
 * Multi-user model with headless user 0. User 0 is not associated with a real person, and
 * can not be brought to foreground.
 *
 * <p>This class provides method for user management, including creating, removing, adding
 * and switching users. Methods related to get users will exclude system user by default.
 *
 * @hide
 */
public final class CarUserManagerHelper {
    private static final String TAG = "CarUserManagerHelper";

    private static final int BOOT_USER_NOT_FOUND = -1;

    /**
     * Default set of restrictions for Non-Admin users.
     */
    private static final Set<String> DEFAULT_NON_ADMIN_RESTRICTIONS = Sets.newArraySet(
            UserManager.DISALLOW_FACTORY_RESET
    );

    /**
     * Additional optional set of restrictions for Non-Admin users. These are the restrictions
     * configurable via Settings.
     */
    public static final Set<String> OPTIONAL_NON_ADMIN_RESTRICTIONS = Sets.newArraySet(
            UserManager.DISALLOW_ADD_USER,
            UserManager.DISALLOW_OUTGOING_CALLS,
            UserManager.DISALLOW_SMS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_UNINSTALL_APPS
    );

    /**
     * Default set of restrictions for Guest users.
     */
    private static final Set<String> DEFAULT_GUEST_RESTRICTIONS = Sets.newArraySet(
            UserManager.DISALLOW_FACTORY_RESET,
            UserManager.DISALLOW_REMOVE_USER,
            UserManager.DISALLOW_MODIFY_ACCOUNTS,
            UserManager.DISALLOW_INSTALL_APPS,
            UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES,
            UserManager.DISALLOW_UNINSTALL_APPS
    );

    private final Context mContext;
    private final UserManager mUserManager;
    private final ActivityManager mActivityManager;
    private final TestableFrameworkWrapper mTestableFrameworkWrapper;
    private String mDefaultAdminName;
    private Bitmap mDefaultGuestUserIcon;
    private ArrayList<OnUsersUpdateListener> mUpdateListeners;
    private final BroadcastReceiver mUserChangeReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            ArrayList<OnUsersUpdateListener> copyOfUpdateListeners;
            synchronized (mUpdateListeners) {
                copyOfUpdateListeners = new ArrayList(mUpdateListeners);
            }

            for (OnUsersUpdateListener listener : copyOfUpdateListeners) {
                listener.onUsersUpdate();
            }
        }
    };

    /**
     * Initializes with a default name for admin users.
     *
     * @param context Application Context
     */
    public CarUserManagerHelper(Context context) {
        this(context, new TestableFrameworkWrapper());
    }

    @VisibleForTesting
    CarUserManagerHelper(Context context, TestableFrameworkWrapper testableFrameworkWrapper) {
        mUpdateListeners = new ArrayList<>();
        mContext = context.getApplicationContext();
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mTestableFrameworkWrapper = testableFrameworkWrapper;
    }

    /**
     * Registers a listener for updates to all users - removing, adding users or changing user info.
     *
     * @param listener Instance of {@link OnUsersUpdateListener}.
     */
    public void registerOnUsersUpdateListener(OnUsersUpdateListener listener) {
        if (listener == null) {
            return;
        }

        synchronized (mUpdateListeners) {
            if (mUpdateListeners.isEmpty()) {
                // First listener being added, register receiver.
                registerReceiver();
            }

            if (!mUpdateListeners.contains(listener)) {
                mUpdateListeners.add(listener);
            }
        }
    }

    /**
     * Unregisters on user update listener.
     * Unregisters {@code BroadcastReceiver} if no listeners remain.
     *
     * @param listener Instance of {@link OnUsersUpdateListener} to unregister.
     */
    public void unregisterOnUsersUpdateListener(OnUsersUpdateListener listener) {
        synchronized (mUpdateListeners) {
            if (mUpdateListeners.contains(listener)) {
                mUpdateListeners.remove(listener);

                if (mUpdateListeners.isEmpty()) {
                    // No more listeners, unregister broadcast receiver.
                    unregisterReceiver();
                }
            }
        }
    }

    /**
     * Set last active user.
     *
     * @param userId last active user id.
     */
    public void setLastActiveUser(int userId) {
        Settings.Global.putInt(
                mContext.getContentResolver(), Settings.Global.LAST_ACTIVE_USER_ID, userId);
    }

    /**
     * Set last active user.
     *
     * @param userId last active user id.
     * @param skipGlobalSetting whether to skip set the global settings value.
     * @deprecated Use {@link #setLastActiveUser(int)} instead.
     */
    @Deprecated
    public void setLastActiveUser(int userId, boolean skipGlobalSetting) {
        if (!skipGlobalSetting) {
            Settings.Global.putInt(
                    mContext.getContentResolver(), Settings.Global.LAST_ACTIVE_USER_ID, userId);
        }
    }

    /**
     * Get user id for the last active user.
     *
     * @return user id of the last active user.
     */
    public int getLastActiveUser() {
        return Settings.Global.getInt(
            mContext.getContentResolver(), Settings.Global.LAST_ACTIVE_USER_ID,
            /* default user id= */ UserHandle.USER_SYSTEM);
    }

    /**
     * Gets the user id for the initial user to boot into. This is only applicable for headless
     * system user model. This method checks for a system property and will only work for system
     * apps.
     *
     * This method checks for the initial user via three mechanisms in this order:
     * <ol>
     *     <li>Check for a boot user override via {@link CarProperties#boot_user_override_id()}</li>
     *     <li>Check for the last active user in the system</li>
     *     <li>Fallback to the smallest user id that is not {@link UserHandle.USER_SYSTEM}</li>
     * </ol>
     *
     * If any step fails to retrieve the stored id or the retrieved id does not exist on device,
     * then it will move onto the next step.
     *
     * @return user id of the initial user to boot into on the device.
     */
    @SystemApi
    public int getInitialUser() {
        List<Integer> allUsers = userInfoListToUserIdList(getAllPersistentUsers());

        int bootUserOverride = mTestableFrameworkWrapper.getBootUserOverrideId(BOOT_USER_NOT_FOUND);

        // If an override user is present and a real user, return it
        if (bootUserOverride != BOOT_USER_NOT_FOUND
                && allUsers.contains(bootUserOverride)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Boot user id override found for initial user, user id: "
                        + bootUserOverride);
            }
            return bootUserOverride;
        }

        // If the last active user is not the SYSTEM user and is a real user, return it
        int lastActiveUser = getLastActiveUser();
        if (lastActiveUser != UserHandle.USER_SYSTEM
                && allUsers.contains(lastActiveUser)) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "Last active user loaded for initial user, user id: "
                        + lastActiveUser);
            }
            return lastActiveUser;
        }

        // If all else fails, return the smallest user id
        int returnId = Collections.min(allUsers);
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "Saved ids were invalid. Returning smallest user id, user id: "
                    + returnId);
        }
        return returnId;
    }

    private List<Integer> userInfoListToUserIdList(List<UserInfo> allUsers) {
        ArrayList<Integer> list = new ArrayList<>(allUsers.size());
        for (UserInfo userInfo : allUsers) {
            list.add(userInfo.id);
        }
        return list;
    }

    /**
     * Sets default guest restrictions that will be applied every time a Guest user is created.
     *
     * <p> Restrictions are written to disk and persistent across boots.
     */
    public void initDefaultGuestRestrictions() {
        Bundle defaultGuestRestrictions = new Bundle();
        for (String restriction : DEFAULT_GUEST_RESTRICTIONS) {
            defaultGuestRestrictions.putBoolean(restriction, true);
        }
        mUserManager.setDefaultGuestRestrictions(defaultGuestRestrictions);
    }

    /**
     * Returns {@code true} if the system is in the headless user 0 model.
     *
     * @return {@boolean true} if headless system user.
     */
    public boolean isHeadlessSystemUser() {
        return RoSystemProperties.MULTIUSER_HEADLESS_SYSTEM_USER;
    }

    /**
     * Gets UserInfo for the system user.
     *
     * @return {@link UserInfo} for the system user.
     */
    public UserInfo getSystemUserInfo() {
        return mUserManager.getUserInfo(UserHandle.USER_SYSTEM);
    }

    /**
     * Gets UserInfo for the current foreground user.
     *
     * Concept of foreground user is relevant for the multi-user deployment. Foreground user
     * corresponds to the currently "logged in" user.
     *
     * @return {@link UserInfo} for the foreground user.
     */
    public UserInfo getCurrentForegroundUserInfo() {
        return mUserManager.getUserInfo(getCurrentForegroundUserId());
    }

    /**
     * @return Id of the current foreground user.
     */
    public int getCurrentForegroundUserId() {
        return mActivityManager.getCurrentUser();
    }

    /**
     * Gets UserInfo for the user running the caller process.
     *
     * <p>Differentiation between foreground user and current process user is relevant for
     * multi-user deployments.
     *
     * <p>Some multi-user aware components (like SystemUI) needs to run a singleton component
     * in system user. Current process user is always the same for that component, even when
     * the foreground user changes.
     *
     * @return {@link UserInfo} for the user running the current process.
     */
    public UserInfo getCurrentProcessUserInfo() {
        return mUserManager.getUserInfo(getCurrentProcessUserId());
    }

    /**
     * @return Id for the user running the current process.
     */
    public int getCurrentProcessUserId() {
        return UserHandle.myUserId();
    }

    /**
     * Gets all the existing users on the system that are not currently running as
     * the foreground user.
     * These are all the users that can be switched to from the foreground user.
     *
     * @return List of {@code UserInfo} for each user that is not the foreground user.
     */
    public List<UserInfo> getAllSwitchableUsers() {
        if (isHeadlessSystemUser()) {
            return getAllUsersExceptSystemUserAndSpecifiedUser(getCurrentForegroundUserId());
        } else {
            return getAllUsersExceptSpecifiedUser(getCurrentForegroundUserId());
        }
    }

    /**
     * Gets all the users that can be brought to the foreground on the system.
     *
     * @return List of {@code UserInfo} for users that associated with a real person.
     */
    public List<UserInfo> getAllUsers() {
        if (isHeadlessSystemUser()) {
            return getAllUsersExceptSystemUserAndSpecifiedUser(UserHandle.USER_SYSTEM);
        } else {
            return mUserManager.getUsers(/* excludeDying= */ true);
        }
    }

    /**
     * Gets all the users that are non-ephemeral and can be brought to the foreground on the system.
     *
     * @return List of {@code UserInfo} for non-ephemeral users that associated with a real person.
     */
    public List<UserInfo> getAllPersistentUsers() {
        List<UserInfo> users = getAllUsers();
        for (Iterator<UserInfo> iterator = users.iterator(); iterator.hasNext(); ) {
            UserInfo userInfo = iterator.next();
            if (userInfo.isEphemeral()) {
                // Remove user that is ephemeral.
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * Gets all the users that can be brought to the foreground on the system that have admin roles.
     *
     * @return List of {@code UserInfo} for admin users that associated with a real person.
     */
    public List<UserInfo> getAllAdminUsers() {
        List<UserInfo> users = getAllUsers();

        for (Iterator<UserInfo> iterator = users.iterator(); iterator.hasNext(); ) {
            UserInfo userInfo = iterator.next();
            if (!userInfo.isAdmin()) {
                // Remove user that is not admin.
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * Gets all users that are not guests.
     *
     * @return List of {@code UserInfo} for all users who are not guest users.
     */
    public List<UserInfo> getAllUsersExceptGuests() {
        List<UserInfo> users = getAllUsers();

        for (Iterator<UserInfo> iterator = users.iterator(); iterator.hasNext(); ) {
            UserInfo userInfo = iterator.next();
            if (userInfo.isGuest()) {
                // Remove guests.
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * Get all the users except the one with userId passed in.
     *
     * @param userId of the user not to be returned.
     * @return All users other than user with userId.
     */
    private List<UserInfo> getAllUsersExceptSpecifiedUser(int userId) {
        List<UserInfo> users = mUserManager.getUsers(/* excludeDying= */true);

        for (Iterator<UserInfo> iterator = users.iterator(); iterator.hasNext(); ) {
            UserInfo userInfo = iterator.next();
            if (userInfo.id == userId) {
                // Remove user with userId from the list.
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * Get all the users except system user and the one with userId passed in.
     *
     * @param userId of the user not to be returned.
     * @return All users other than system user and user with userId.
     */
    private List<UserInfo> getAllUsersExceptSystemUserAndSpecifiedUser(int userId) {
        List<UserInfo> users = mUserManager.getUsers(/* excludeDying= */true);

        for (Iterator<UserInfo> iterator = users.iterator(); iterator.hasNext(); ) {
            UserInfo userInfo = iterator.next();
            if (userInfo.id == userId || userInfo.id == UserHandle.USER_SYSTEM) {
                // Remove user with userId from the list.
                iterator.remove();
            }
        }
        return users;
    }

    /**
     * Maximum number of users allowed on the device. This includes real users, managed profiles
     * and restricted users, but excludes guests.
     *
     * <p> It excludes system user in headless system user model.
     *
     * @return Maximum number of users that can be present on the device.
     */
    public int getMaxSupportedUsers() {
        if (isHeadlessSystemUser()) {
            return mTestableFrameworkWrapper.userManagerGetMaxSupportedUsers() - 1;
        }
        return mTestableFrameworkWrapper.userManagerGetMaxSupportedUsers();
    }

    /**
     * Get the maximum number of real (non-guest, non-managed profile) users that can be created on
     * the device. This is a dynamic value and it decreases with the increase of the number of
     * managed profiles on the device.
     *
     * <p> It excludes system user in headless system user model.
     *
     * @return Maximum number of real users that can be created.
     */
    public int getMaxSupportedRealUsers() {
        return getMaxSupportedUsers() - getManagedProfilesCount();
    }

    /**
     * Returns true if the maximum number of users on the device has been reached, false otherwise.
     */
    public boolean isUserLimitReached() {
        int countNonGuestUsers = getAllUsersExceptGuests().size();
        int maxSupportedUsers = getMaxSupportedUsers();

        if (countNonGuestUsers > maxSupportedUsers) {
            Log.e(TAG, "There are more users on the device than allowed.");
            return true;
        }

        return getAllUsersExceptGuests().size() == maxSupportedUsers;
    }

    private int getManagedProfilesCount() {
        List<UserInfo> users = getAllUsers();

        // Count all users that are managed profiles of another user.
        int managedProfilesCount = 0;
        for (UserInfo user : users) {
            if (user.isManagedProfile()) {
                managedProfilesCount++;
            }
        }
        return managedProfilesCount;
    }

    // User information accessors

    /**
     * Checks whether the user is system user.
     *
     * @param userInfo User to check against system user.
     * @return {@code true} if system user, {@code false} otherwise.
     */
    public boolean isSystemUser(UserInfo userInfo) {
        return userInfo.id == UserHandle.USER_SYSTEM;
    }

    /**
     * Checks whether the user is last active user.
     *
     * @param userInfo User to check against last active user.
     * @return {@code true} if is last active user, {@code false} otherwise.
     */
    public boolean isLastActiveUser(UserInfo userInfo) {
        return userInfo.id == getLastActiveUser();
    }

    /**
     * Checks whether passed in user is the foreground user.
     *
     * @param userInfo User to check.
     * @return {@code true} if foreground user, {@code false} otherwise.
     */
    public boolean isForegroundUser(UserInfo userInfo) {
        return getCurrentForegroundUserId() == userInfo.id;
    }

    /**
     * Checks whether passed in user is the user that's running the current process.
     *
     * @param userInfo User to check.
     * @return {@code true} if user running the process, {@code false} otherwise.
     */
    public boolean isCurrentProcessUser(UserInfo userInfo) {
        return getCurrentProcessUserId() == userInfo.id;
    }

    // Foreground user information accessors.

    /**
     * Checks if the foreground user is a guest user.
     */
    public boolean isForegroundUserGuest() {
        return getCurrentForegroundUserInfo().isGuest();
    }

    /**
     * Checks if the foreground user is a demo user.
     */
    public boolean isForegroundUserDemo() {
        return getCurrentForegroundUserInfo().isDemo();
    }

    /**
     * Checks if the foreground user is ephemeral.
     */
    public boolean isForegroundUserEphemeral() {
        return getCurrentForegroundUserInfo().isEphemeral();
    }

    /**
     * Checks if the given user is non-ephemeral.
     *
     * @param userId User to check
     * @return {@code true} if given user is persistent user.
     */
    public boolean isPersistentUser(int userId) {
        UserInfo user = mUserManager.getUserInfo(userId);
        return !user.isEphemeral();
    }

    /**
     * Returns whether this user can be removed from the system.
     *
     * @param userInfo User to be removed
     * @return {@code true} if they can be removed, {@code false} otherwise.
     */
    public boolean canUserBeRemoved(UserInfo userInfo) {
        return !isSystemUser(userInfo);
    }

    /**
     * Returns whether a user has a restriction.
     *
     * @param restriction Restriction to check. Should be a UserManager.* restriction.
     * @param userInfo the user whose restriction is to be checked
     */
    public boolean hasUserRestriction(String restriction, UserInfo userInfo) {
        return mUserManager.hasUserRestriction(restriction, userInfo.getUserHandle());
    }

    /**
     * Return whether the foreground user has a restriction.
     *
     * @param restriction Restriction to check. Should be a UserManager.* restriction.
     * @return Whether that restriction exists for the foreground user.
     */
    public boolean foregroundUserHasUserRestriction(String restriction) {
        return hasUserRestriction(restriction, getCurrentForegroundUserInfo());
    }

    /**
     * Checks if the foreground user can add new users.
     */
    public boolean canForegroundUserAddUsers() {
        return !foregroundUserHasUserRestriction(UserManager.DISALLOW_ADD_USER);
    }

    /**
     * Checks if the current process user can modify accounts. Demo and Guest users cannot modify
     * accounts even if the DISALLOW_MODIFY_ACCOUNTS restriction is not applied.
     */
    public boolean canForegroundUserModifyAccounts() {
        return !foregroundUserHasUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)
            && !isForegroundUserDemo()
            && !isForegroundUserGuest();
    }

    /**
     * Returns whether the foreground user can switch to other users.
     *
     * <p>For instance switching users is not allowed if the current user is in a phone call,
     * or {@link #{UserManager.DISALLOW_USER_SWITCH} is set.
     */
    public boolean canForegroundUserSwitchUsers() {
        boolean inIdleCallState = TelephonyManager.getDefault().getCallState()
                == TelephonyManager.CALL_STATE_IDLE;
        boolean disallowUserSwitching =
                foregroundUserHasUserRestriction(UserManager.DISALLOW_USER_SWITCH);
        return (inIdleCallState && !disallowUserSwitching);
    }

    // Current process user information accessors

    /**
     * Checks whether this process is running under the system user.
     */
    public boolean isCurrentProcessSystemUser() {
        return mUserManager.isSystemUser();
    }

    /**
     * Checks if the calling app is running in a demo user.
     */
    public boolean isCurrentProcessDemoUser() {
        return mUserManager.isDemoUser();
    }

    /**
     * Checks if the calling app is running as an admin user.
     */
    public boolean isCurrentProcessAdminUser() {
        return mUserManager.isAdminUser();
    }

    /**
     * Checks if the calling app is running as a guest user.
     */
    public boolean isCurrentProcessGuestUser() {
        return mUserManager.isGuestUser();
    }

    /**
     * Check is the calling app is running as a restricted profile user (ie. a LinkedUser).
     * Restricted profiles are only available when {@link #isHeadlessSystemUser()} is false.
     */
    public boolean isCurrentProcessRestrictedProfileUser() {
        return mUserManager.isRestrictedProfile();
    }

    // Current process user restriction accessors

    /**
     * Return whether the user running the current process has a restriction.
     *
     * @param restriction Restriction to check. Should be a UserManager.* restriction.
     * @return Whether that restriction exists for the user running the process.
     */
    public boolean isCurrentProcessUserHasRestriction(String restriction) {
        return mUserManager.hasUserRestriction(restriction);
    }

    /**
     * Checks if the current process user can modify accounts. Demo and Guest users cannot modify
     * accounts even if the DISALLOW_MODIFY_ACCOUNTS restriction is not applied.
     */
    public boolean canCurrentProcessModifyAccounts() {
        return !isCurrentProcessUserHasRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS)
            && !isCurrentProcessDemoUser()
            && !isCurrentProcessGuestUser();
    }

    /**
     * Checks if the user running the current process can add new users.
     */
    public boolean canCurrentProcessAddUsers() {
        return !isCurrentProcessUserHasRestriction(UserManager.DISALLOW_ADD_USER);
    }

    /**
     * Checks if the user running the current process can remove users.
     */
    public boolean canCurrentProcessRemoveUsers() {
        return !isCurrentProcessUserHasRestriction(UserManager.DISALLOW_REMOVE_USER);
    }

    /**
     * Returns whether the current process user can switch to other users.
     *
     * <p>For instance switching users is not allowed if the user is in a phone call,
     * or {@link #{UserManager.DISALLOW_USER_SWITCH} is set.
     */
    public boolean canCurrentProcessSwitchUsers() {
        boolean inIdleCallState = TelephonyManager.getDefault().getCallState()
                == TelephonyManager.CALL_STATE_IDLE;
        boolean disallowUserSwitching =
                isCurrentProcessUserHasRestriction(UserManager.DISALLOW_USER_SWITCH);
        return (inIdleCallState && !disallowUserSwitching);
    }

    /**
     * Grants admin permissions to the user.
     *
     * @param user User to be upgraded to Admin status.
     */
    @RequiresPermission(allOf = {
            Manifest.permission.INTERACT_ACROSS_USERS_FULL,
            Manifest.permission.MANAGE_USERS
    })
    public void grantAdminPermissions(UserInfo user) {
        if (!isCurrentProcessAdminUser()) {
            Log.w(TAG, "Only admin users can assign admin permissions.");
            return;
        }

        mUserManager.setUserAdmin(user.id);

        // Remove restrictions imposed on non-admins.
        setDefaultNonAdminRestrictions(user, /* enable= */ false);
        setOptionalNonAdminRestrictions(user, /* enable= */ false);
    }

    /**
     * Creates a new user on the system with a default user name. This user name is set during
     * constrution. The created user would be granted admin role. Only admins can create other
     * admins.
     *
     * @return Newly created admin user, null if failed to create a user.
     */
    @Nullable
    public UserInfo createNewAdminUser() {
        return createNewAdminUser(getDefaultAdminName());
    }

    /**
     * Creates a new user on the system, the created user would be granted admin role.
     * Only admins can create other admins.
     *
     * @param userName Name to give to the newly created user.
     * @return Newly created admin user, null if failed to create a user.
     */
    @Nullable
    public UserInfo createNewAdminUser(String userName) {
        if (!(isCurrentProcessAdminUser() || isCurrentProcessSystemUser())) {
            // Only Admins or System user can create other privileged users.
            Log.e(TAG, "Only admin users and system user can create other admins.");
            return null;
        }

        UserInfo user = mUserManager.createUser(userName, UserInfo.FLAG_ADMIN);
        if (user == null) {
            // Couldn't create user, most likely because there are too many.
            Log.w(TAG, "can't create admin user.");
            return null;
        }
        assignDefaultIcon(user);

        return user;
    }

    /**
     * Creates a new non-admin user on the system.
     *
     * @param userName Name to give to the newly created user.
     * @return Newly created non-admin user, null if failed to create a user.
     */
    @Nullable
    public UserInfo createNewNonAdminUser(String userName) {
        UserInfo user = mUserManager.createUser(userName, 0);
        if (user == null) {
            // Couldn't create user, most likely because there are too many.
            Log.w(TAG, "can't create non-admin user.");
            return null;
        }
        setDefaultNonAdminRestrictions(user, /* enable= */ true);

        // Each non-admin has sms and outgoing call restrictions applied by the UserManager on
        // creation. We want to enable these permissions by default in the car.
        setUserRestriction(user, UserManager.DISALLOW_SMS, /* enable= */ false);
        setUserRestriction(user, UserManager.DISALLOW_OUTGOING_CALLS, /* enable= */ false);

        assignDefaultIcon(user);
        return user;
    }

    /**
     * Sets the values of default Non-Admin restrictions to the passed in value.
     *
     * @param userInfo User to set restrictions on.
     * @param enable If true, restriction is ON, If false, restriction is OFF.
     */
    private void setDefaultNonAdminRestrictions(UserInfo userInfo, boolean enable) {
        for (String restriction : DEFAULT_NON_ADMIN_RESTRICTIONS) {
            setUserRestriction(userInfo, restriction, enable);
        }
    }

    /**
     * Sets the values of settings controllable restrictions to the passed in value.
     *
     * @param userInfo User to set restrictions on.
     * @param enable If true, restriction is ON, If false, restriction is OFF.
     */
    private void setOptionalNonAdminRestrictions(UserInfo userInfo, boolean enable) {
        for (String restriction : OPTIONAL_NON_ADMIN_RESTRICTIONS) {
            setUserRestriction(userInfo, restriction, enable);
        }
    }

    /**
     * Sets the value of the specified restriction for the specified user.
     *
     * @param userInfo the user whose restriction is to be changed
     * @param restriction the key of the restriction
     * @param enable the value for the restriction. if true, turns the restriction ON, if false,
     *               turns the restriction OFF.
     */
    public void setUserRestriction(UserInfo userInfo, String restriction, boolean enable) {
        UserHandle userHandle = UserHandle.of(userInfo.id);
        mUserManager.setUserRestriction(restriction, enable, userHandle);
    }

    /**
     * Tries to remove the user that's passed in. System user cannot be removed.
     * If the user to be removed is user currently running the process,
     * it switches to the guest user first, and then removes the user.
     * If the user being removed is the last admin user, this will create a new admin user.
     *
     * @param userInfo User to be removed
     * @param guestUserName User name to use for the guest user if we need to switch to it
     * @return {@code true} if user is successfully removed, {@code false} otherwise.
     */
    public boolean removeUser(UserInfo userInfo, String guestUserName) {
        if (isSystemUser(userInfo)) {
            Log.w(TAG, "User " + userInfo.id + " is system user, could not be removed.");
            return false;
        }

        // Try to create a new admin before deleting the current one.
        if (userInfo.isAdmin() && getAllAdminUsers().size() <= 1) {
            return removeLastAdmin(userInfo);
        }

        if (!isCurrentProcessAdminUser() && !isCurrentProcessUser(userInfo)) {
            // If the caller is non-admin, they can only delete themselves.
            Log.e(TAG, "Non-admins cannot remove other users.");
            return false;
        }

        if (userInfo.id == getCurrentForegroundUserId()) {
            if (!canCurrentProcessSwitchUsers()) {
                // If we can't switch to a different user, we can't exit this one and therefore
                // can't delete it.
                Log.w(TAG, "User switching is not allowed. Current user cannot be deleted");
                return false;
            }
            startGuestSession(guestUserName);
        }

        return mUserManager.removeUser(userInfo.id);
    }

    private boolean removeLastAdmin(UserInfo userInfo) {
        if (Log.isLoggable(TAG, Log.INFO)) {
            Log.i(TAG, "User " + userInfo.id
                    + " is the last admin user on device. Creating a new admin.");
        }

        UserInfo newAdmin = createNewAdminUser(getDefaultAdminName());
        if (newAdmin == null) {
            Log.w(TAG, "Couldn't create another admin, cannot delete current user.");
            return false;
        }

        switchToUser(newAdmin);
        return mUserManager.removeUser(userInfo.id);
    }

    /**
     * Switches (logs in) to another user given user id.
     *
     * @param id User id to switch to.
     * @return {@code true} if user switching succeed.
     */
    public boolean switchToUserId(int id) {
        if (id == UserHandle.USER_SYSTEM && isHeadlessSystemUser()) {
            // System User doesn't associate with real person, can not be switched to.
            return false;
        }
        if (!canCurrentProcessSwitchUsers()) {
            return false;
        }
        if (id == getCurrentForegroundUserId()) {
            return false;
        }
        return mActivityManager.switchUser(id);
    }

    /**
     * Switches (logs in) to another user.
     *
     * @param userInfo User to switch to.
     * @return {@code true} if user switching succeed.
     */
    public boolean switchToUser(UserInfo userInfo) {
        return switchToUserId(userInfo.id);
    }

    /**
     * Creates a new guest or finds the existing one, and switches into it.
     *
     * @param guestName Username for the guest user.
     * @return {@code true} if switch to guest user succeed.
     */
    public boolean startGuestSession(String guestName) {
        UserInfo guest = createNewOrFindExistingGuest(guestName);
        if (guest == null) {
            return false;
        }
        return switchToUserId(guest.id);
    }

    /**
     * Creates and returns a new guest user or returns the existing one.
     * Returns null if it fails to create a new guest.
     *
     * @param guestName Username for guest if new guest is being created.
     */
    @Nullable
    public UserInfo createNewOrFindExistingGuest(String guestName) {
        // CreateGuest will return null if a guest already exists.
        UserInfo newGuest = mUserManager.createGuest(mContext, guestName);
        if (newGuest != null) {
            assignDefaultIcon(newGuest);
            return newGuest;
        }

        UserInfo existingGuest = findExistingGuestUser();
        if (existingGuest == null) {
            // Most likely a guest got removed just before we tried to look for it.
            Log.w(TAG, "Couldn't create a new guest and couldn't find an existing one.");
        }

        return existingGuest;
    }

    /**
     * Returns UserInfo for the existing guest user, or null if there are no guests on the device.
     */
    @Nullable
    private UserInfo findExistingGuestUser() {
        for (UserInfo userInfo : getAllUsers()) {
            if (userInfo.isGuest() && !userInfo.guestToRemove) {
                return userInfo;
            }
        }
        return null;
    }

    /**
     * Gets a bitmap representing the user's default avatar.
     *
     * @param userInfo User whose avatar should be returned.
     * @return Default user icon
     */
    public Bitmap getUserDefaultIcon(UserInfo userInfo) {
        return UserIcons.convertToBitmap(
            UserIcons.getDefaultUserIcon(mContext.getResources(), userInfo.id, false));
    }

    /**
     * Gets a bitmap representing the default icon for a Guest user.
     *
     * @return Default guest user icon
     */
    public Bitmap getGuestDefaultIcon() {
        if (mDefaultGuestUserIcon == null) {
            mDefaultGuestUserIcon = UserIcons.convertToBitmap(UserIcons.getDefaultUserIcon(
                mContext.getResources(), UserHandle.USER_NULL, false));
        }
        return mDefaultGuestUserIcon;
    }

    /**
     * Gets an icon for the user.
     *
     * @param userInfo User for which we want to get the icon.
     * @return a Bitmap for the icon
     */
    public Bitmap getUserIcon(UserInfo userInfo) {
        Bitmap picture = mUserManager.getUserIcon(userInfo.id);

        if (picture == null) {
            return assignDefaultIcon(userInfo);
        }

        return picture;
    }

    /**
     * Method for scaling a Bitmap icon to a desirable size.
     *
     * @param icon Bitmap to scale.
     * @param desiredSize Wanted size for the icon.
     * @return Drawable for the icon, scaled to the new size.
     */
    public Drawable scaleUserIcon(Bitmap icon, int desiredSize) {
        Bitmap scaledIcon = Bitmap.createScaledBitmap(
                icon, desiredSize, desiredSize, true /* filter */);
        return new BitmapDrawable(mContext.getResources(), scaledIcon);
    }

    /**
     * Sets new Username for the user.
     *
     * @param user User whose name should be changed.
     * @param name New username.
     */
    public void setUserName(UserInfo user, String name) {
        mUserManager.setUserName(user.id, name);
    }

    private void registerReceiver() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(Intent.ACTION_USER_REMOVED);
        filter.addAction(Intent.ACTION_USER_ADDED);
        filter.addAction(Intent.ACTION_USER_INFO_CHANGED);
        filter.addAction(Intent.ACTION_USER_SWITCHED);
        filter.addAction(Intent.ACTION_USER_STOPPED);
        filter.addAction(Intent.ACTION_USER_UNLOCKED);
        mContext.registerReceiverAsUser(mUserChangeReceiver, UserHandle.ALL, filter, null, null);
    }

    // Assigns a default icon to a user according to the user's id.
    private Bitmap assignDefaultIcon(UserInfo userInfo) {
        Bitmap bitmap = userInfo.isGuest()
                ? getGuestDefaultIcon() : getUserDefaultIcon(userInfo);
        mUserManager.setUserIcon(userInfo.id, bitmap);
        return bitmap;
    }

    private void unregisterReceiver() {
        mContext.unregisterReceiver(mUserChangeReceiver);
    }

    private String getDefaultAdminName() {
        if (TextUtils.isEmpty(mDefaultAdminName)) {
            mDefaultAdminName = mContext.getString(com.android.internal.R.string.owner_name);
        }
        return mDefaultAdminName;
    }

    @VisibleForTesting
    void setDefaultAdminName(String defaultAdminName) {
        mDefaultAdminName = defaultAdminName;
    }

    /**
     * Interface for listeners that want to register for receiving updates to changes to the users
     * on the system including removing and adding users, and changing user info.
     */
    public interface OnUsersUpdateListener {
        /**
         * Method that will get called when users list has been changed.
         */
        void onUsersUpdate();
    }
}
