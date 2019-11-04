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

package com.android.car.settings.testutils;

import android.app.admin.DevicePolicyManager;

import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.Resetter;

import java.util.ArrayList;
import java.util.List;

/**
 * Shadow for LockPatternUtils.
 */
@Implements(LockPatternUtils.class)
public class ShadowLockPatternUtils {

    public static final int NO_USER = -1;

    private static int sPasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
    private static byte[] sSavedPassword;
    private static List<LockPatternView.Cell> sSavedPattern;
    private static byte[] sClearLockCredential;
    private static int sClearLockUser = NO_USER;


    @Resetter
    public static void reset() {
        sPasswordQuality = DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED;
        sSavedPassword = null;
        sSavedPattern = null;
        sClearLockCredential = null;
        sClearLockUser = NO_USER;
    }

    /**
     * Sets the current password quality that is returned by
     * {@link LockPatternUtils#getKeyguardStoredPasswordQuality}.
     */
    public static void setPasswordQuality(int passwordQuality) {
        sPasswordQuality = passwordQuality;
    }

    /**
     * Returns the password saved by a call to {@link LockPatternUtils#saveLockPassword}.
     */
    public static byte[] getSavedPassword() {
        return sSavedPassword;
    }

    /**
     * Returns the saved credential passed in to clear the lock, null if it has not been cleared.
     */
    public static byte[] getClearLockCredential() {
        return sClearLockCredential;
    }

    /**
     * Returns the user passed in to clear the lock, {@link #NO_USER} if it has not been cleared.
     */
    public static int getClearLockUser() {
        return sClearLockUser;
    }


    /**
     * Returns the pattern saved by a call to {@link LockPatternUtils#saveLockPattern}.
     */
    public static List<LockPatternView.Cell> getSavedPattern() {
        return sSavedPattern;
    }

    @Implementation
    protected void clearLock(byte[] savedCredential, int userHandle) {
        sClearLockCredential = savedCredential;
        sClearLockUser = userHandle;
    }

    @Implementation
    protected int getKeyguardStoredPasswordQuality(int userHandle) {
        return sPasswordQuality;
    }

    @Implementation
    public void saveLockPassword(byte[] password, byte[] savedPassword, int requestedQuality,
            int userHandler) {
        sSavedPassword = password;
    }

    @Implementation
    public void saveLockPattern(List<LockPatternView.Cell> pattern, byte[] savedPassword,
            int userId, boolean allowUntrustedChanges) {
        sSavedPattern = new ArrayList<>(pattern);
    }
}
