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

package com.android.tv.settings.users;

import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.UserInfo;
import android.os.RemoteException;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.util.Log;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.VerifyCredentialResponse;

/**
 * Manipulate and list restricted profiles on the device.
 */
public class RestrictedProfileModel {
    private static final String TAG = "RestrictedProfile";

    private final Context mContext;
    private final boolean mApplyRestrictions;

    private final ActivityManager mActivityManager;
    private final UserManager mUserManager;

    /** Cache the UserInfo we're running as because, unlike other profiles, it won't change. */
    private final UserInfo mCurrentUserInfo;

    public RestrictedProfileModel(final Context context) {
        this(context, /* applyRestrictions= */ true);
    }

    RestrictedProfileModel(final Context context, final boolean applyRestrictions) {
        mContext = context;
        mApplyRestrictions = applyRestrictions;

        mActivityManager = (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);
        mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        mCurrentUserInfo = mUserManager.getUserInfo(mContext.getUserId());
    }

    /** Switch into the restricted profile. */
    public boolean enterUser() {
        if (isCurrentUser()) {
            Log.w(TAG, "Tried to switch into current user");
            return false;
        }
        final UserInfo restrictedUser = getUser();
        if (restrictedUser == null) {
            Log.e(TAG, "Tried to enter non-existent restricted user");
            return false;
        }
        updateBackgroundRestriction(restrictedUser);
        switchUserNow(restrictedUser.id);
        return true;
    }

    /** Switch out of the restricted profile, back into the primary user. */
    public void exitUser() {
        if (isCurrentUser()) {
            switchUserNow(getOwnerUserId());
        }
    }

    /**
     * Remove the restricted profile.
     *
     * Called from another user. Requires permission to MANAGE_USERS.
     */
    public void removeUser() {
        final UserInfo restrictedUser = getUser();
        if (restrictedUser == null) {
            Log.w(TAG, "No restricted user to remove?");
            return;
        }
        final int restrictedUserHandle = restrictedUser.id;
        mUserManager.removeUser(restrictedUserHandle);
    }

    /** @return {@code true} if the current user is the restricted profile. */
    public boolean isCurrentUser() {
        return mCurrentUserInfo.isRestricted();
    }

    /**
     * @return a @{link UserInfo} for the restricted profile, or {@code null} if there is no
     *         restricted profile on the device.
     */
    public UserInfo getUser() {
        if (mCurrentUserInfo.isRestricted()) {
            return mCurrentUserInfo;
        }
        for (UserInfo userInfo : mUserManager.getUsers()) {
            if (userInfo.isRestricted()) {
                return userInfo;
            }
        }
        return null;
    }

    /**
     * @return user ID for the current user, or parent of the current user if it exists.
     */
    private int getOwnerUserId() {
        if (!mCurrentUserInfo.isRestricted()) {
            return mCurrentUserInfo.id;
        } else if (mCurrentUserInfo.restrictedProfileParentId == UserInfo.NO_PROFILE_GROUP_ID) {
            return UserHandle.USER_OWNER;
        } else {
            return mCurrentUserInfo.restrictedProfileParentId;
        }
    }

    /** Switch to {@param userId} or log an exception if this fails. */
    private void switchUserNow(int userId) {
        try {
            mActivityManager.switchUser(userId);
        } catch (RuntimeException e) {
            Log.e(TAG, "Caught exception while switching user! ", e);
        }
    }

    /**
     * Profiles are allowed to run in the background by default, unless the device specifically
     * sets a config flag and/or has the global setting overridden by something on-device.
     */
    private void updateBackgroundRestriction(UserInfo user) {
        if (!mApplyRestrictions) {
            return;
        }
        final boolean allowedToRun = shouldAllowRunInBackground();
        mUserManager.setUserRestriction(
                UserManager.DISALLOW_RUN_IN_BACKGROUND, !allowedToRun, user.getUserHandle());
    }

    /**
     * @see #updateBackgroundRestriction(UserInfo)
     * @see Settings.Global#KEEP_PROFILE_IN_BACKGROUND
     */
    private boolean shouldAllowRunInBackground() {
        final boolean defaultValue = mContext.getResources().getBoolean(
                com.android.internal.R.bool.config_keepRestrictedProfilesInBackground);
        return Settings.Global.getInt(mContext.getContentResolver(),
                Settings.Global.KEEP_PROFILE_IN_BACKGROUND, defaultValue ? 1 : 0) > 0;
    }

    /**
     * @return {@code true} if {@code password} is a correct PIN for exiting the restricted user.
     */
    public boolean checkPassword(String password) {
        try {
            final int userId = getOwnerUserId();
            final byte[] passwordBytes = password != null ? password.getBytes() : null;
            return VerifyCredentialResponse.RESPONSE_OK
                    == new LockPatternUtils(mContext).getLockSettings().checkCredential(
                            passwordBytes, LockPatternUtils.CREDENTIAL_TYPE_PASSWORD, userId, null)
                                    .getResponseCode();
        } catch (RemoteException e) {
            Log.e(TAG, "Unable to check password for unlocking the user", e);
        }
        return false;
    }

    /**
     * @return {@code true} if the owner user has a PIN set to prevent access from the restricted
     * profile.
     */
    public boolean hasLockscreenSecurity() {
        final int userId = getOwnerUserId();
        final LockPatternUtils lpu = new LockPatternUtils(mContext);
        return lpu.isLockPasswordEnabled(userId) || lpu.isLockPatternEnabled(userId);
    }
}
