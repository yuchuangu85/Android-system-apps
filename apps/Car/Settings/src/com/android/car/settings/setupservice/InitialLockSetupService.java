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

package com.android.car.settings.setupservice;


import android.app.Service;
import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.IBinder;

import com.android.car.settings.common.Logger;
import com.android.car.settings.security.PasswordHelper;
import com.android.car.setupwizardlib.IInitialLockSetupService;
import com.android.car.setupwizardlib.InitialLockSetupConstants;
import com.android.car.setupwizardlib.InitialLockSetupConstants.LockTypes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.SetLockCodes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.ValidateLockFlags;
import com.android.car.setupwizardlib.InitialLockSetupHelper;
import com.android.car.setupwizardlib.LockConfig;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Service that is used by Setup Wizard (exclusively) to set the initial lock screen.
 *
 * <p>This service provides functionality to get the lock config state, check if a password is
 * valid based on the Settings defined password criteria, and save a lock if there is not one
 * already saved. The interface for these operations is found in the {@link
 * IInitialLockSetupService}.
 */
public class InitialLockSetupService extends Service {

    private static final Logger LOG = new Logger(InitialLockSetupService.class);
    private static final String SET_LOCK_PERMISSION = "com.android.car.settings.SET_INITIAL_LOCK";

    private final InitialLockSetupServiceImpl mIInitialLockSetupService =
            new InitialLockSetupServiceImpl();

    /**
     * Will return an {@link IBinder} for the service unless either the caller does not have the
     * appropriate permissions or a lock has already been set on the device. In this case, the
     * service will return {@code null}.
     */
    @Override
    public IBinder onBind(Intent intent) {
        LOG.v("onBind");
        if (checkCallingOrSelfPermission(SET_LOCK_PERMISSION)
                != PackageManager.PERMISSION_GRANTED) {
            // Check permission as a failsafe.
            return null;
        }
        int userId = new CarUserManagerHelper(getApplicationContext()).getCurrentProcessUserId();
        LockPatternUtils lockPatternUtils = new LockPatternUtils(getApplicationContext());
        // Deny binding if there is an existing lock.
        if (lockPatternUtils.getKeyguardStoredPasswordQuality(userId)
                != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
            LOG.v("Rejecting binding, lock exists");
            return null;
        }
        return mIInitialLockSetupService;
    }

    // Translates the byte[] pattern received into the List<LockPatternView.Cell> that is
    // recognized by LockPatternUtils.
    private List<LockPatternView.Cell> toSettingsPattern(byte[] pattern) {
        List<LockPatternView.Cell> outputList = new ArrayList<>();
        for (int i = 0; i < pattern.length; i++) {
            outputList.add(LockPatternView.Cell.of(
                    InitialLockSetupHelper.getPatternCellRowFromByte(pattern[i]),
                    InitialLockSetupHelper.getPatternCellColumnFromByte(pattern[i])));
        }
        return outputList;
    }

    // Implementation of the service binder interface.
    private class InitialLockSetupServiceImpl extends IInitialLockSetupService.Stub {

        @Override
        public int getServiceVersion() {
            return InitialLockSetupConstants.LIBRARY_VERSION;
        }

        @Override
        public LockConfig getLockConfig(@LockTypes int lockType) {
            // All lock types currently are configured the same.
            switch (lockType) {
                case LockTypes.PASSWORD:
                    // fall through
                case LockTypes.PIN:
                    // fall through
                case LockTypes.PATTERN:
                    return new LockConfig(/* enabled= */true, PasswordHelper.MIN_LENGTH);
            }
            return null;
        }

        @Override
        @ValidateLockFlags
        public int checkValidLock(@LockTypes int lockType, byte[] password) {
            PasswordHelper passwordHelper;
            switch (lockType) {
                case LockTypes.PASSWORD:
                    passwordHelper = new PasswordHelper(/* isPin= */ false);
                    return passwordHelper.validateSetupWizard(password);
                case LockTypes.PIN:
                    passwordHelper = new PasswordHelper(/* isPin= */ true);
                    return passwordHelper.validateSetupWizard(password);
                case LockTypes.PATTERN:
                    return password.length >= LockPatternUtils.MIN_LOCK_PATTERN_SIZE
                            ? 0 : ValidateLockFlags.INVALID_LENGTH;
                default:
                    LOG.e("other lock type, returning generic error");
                    return ValidateLockFlags.INVALID_GENERIC;
            }
        }

        @Override
        @SetLockCodes
        public int setLock(@LockTypes int lockType, byte[] password) {
            int userId = new CarUserManagerHelper(
                    InitialLockSetupService.this.getApplicationContext())
                    .getCurrentProcessUserId();
            LockPatternUtils lockPatternUtils = new LockPatternUtils(
                    InitialLockSetupService.this.getApplicationContext());
            int currentPassword = lockPatternUtils.getKeyguardStoredPasswordQuality(userId);
            if (currentPassword != DevicePolicyManager.PASSWORD_QUALITY_UNSPECIFIED) {
                LOG.v("Password already set, rejecting call to setLock");
                return SetLockCodes.FAIL_LOCK_EXISTS;
            }
            if (!InitialLockSetupHelper.isValidLockResultCode(checkValidLock(lockType, password))) {
                LOG.v("Password is not valid, rejecting call to setLock");
                return SetLockCodes.FAIL_LOCK_INVALID;
            }

            boolean success = false;
            try {
                switch (lockType) {
                    case LockTypes.PASSWORD:
                        // Need to remove setup wizard lib byte array encoding and use the
                        // LockPatternUtils encoding.
                        byte[] encodedPassword = LockPatternUtils.charSequenceToByteArray(
                                InitialLockSetupHelper.byteArrayToCharSequence(password));
                        lockPatternUtils.saveLockPassword(encodedPassword,
                                /* savedPassword= */ null,
                                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC, userId);
                        success = true;
                        break;
                    case LockTypes.PIN:
                        // Need to remove setup wizard lib byte array encoding and use the
                        // LockPatternUtils encoding.
                        byte[] encodedPin = LockPatternUtils.charSequenceToByteArray(
                                InitialLockSetupHelper.byteArrayToCharSequence(password));
                        lockPatternUtils.saveLockPassword(encodedPin, /* savedPassword= */ null,
                                DevicePolicyManager.PASSWORD_QUALITY_NUMERIC, userId);
                        success = true;
                        break;
                    case LockTypes.PATTERN:
                        // Need to remove the setup wizard lib pattern encoding and use the
                        // LockPatternUtils pattern format.
                        List<LockPatternView.Cell> pattern = toSettingsPattern(password);
                        lockPatternUtils.saveLockPattern(pattern, /* savedPattern =*/ null,
                                userId, /* allowUntrustedChange =*/ false);
                        pattern.clear();
                        success = true;
                        break;
                    default:
                        LOG.e("Unknown lock type, returning a failure");
                }
            } catch (Exception e) {
                LOG.e("Save lock exception", e);
                success = false;
            }
            Arrays.fill(password, (byte) 0);
            return success ? SetLockCodes.SUCCESS : SetLockCodes.FAIL_LOCK_GENERIC;
        }
    }
}
