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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import android.app.admin.DevicePolicyManager;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.os.RemoteException;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.setupservice.InitialLockSetupService;
import com.android.car.settings.testutils.ShadowLockPatternUtils;
import com.android.car.setupwizardlib.IInitialLockSetupService;
import com.android.car.setupwizardlib.InitialLockSetupConstants.LockTypes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.SetLockCodes;
import com.android.car.setupwizardlib.InitialLockSetupConstants.ValidateLockFlags;
import com.android.car.setupwizardlib.InitialLockSetupHelper;
import com.android.car.setupwizardlib.LockConfig;
import com.android.internal.widget.LockPatternUtils;
import com.android.internal.widget.LockPatternView;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowContextWrapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Tests that the {@link InitialLockSetupService} properly handles connections and lock requests.
 */
@Config(shadows = ShadowLockPatternUtils.class)
@RunWith(CarSettingsRobolectricTestRunner.class)
public class InitialLockSetupServiceTest {

    private static final String LOCK_PERMISSION = "com.android.car.settings.SET_INITIAL_LOCK";

    private InitialLockSetupService mInitialLockSetupService;
    private Context mContext;

    @Before
    public void setupService() {
        ShadowLockPatternUtils.reset();
        mInitialLockSetupService = Robolectric.buildService(InitialLockSetupService.class)
                .create()
                .get();
        mContext = RuntimeEnvironment.application;
        ShadowContextWrapper shadowContextWrapper = Shadows.shadowOf((ContextWrapper) mContext);
        shadowContextWrapper.grantPermissions(LOCK_PERMISSION);
    }

    @Test
    public void testBindReturnsNull_ifLockSet() {
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        assertThat(mInitialLockSetupService.onBind(new Intent())).isNull();
    }

    @Test
    public void testBindReturnsInstanceOfServiceInterface_ifLockNotSet() throws RemoteException {
        assertThat(mInitialLockSetupService.onBind(
                new Intent()) instanceof IInitialLockSetupService.Stub).isTrue();
    }

    @Test
    public void testGetLockConfig_returnsCorrectConfig() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        LockConfig pinConfig = service.getLockConfig(LockTypes.PIN);
        assertThat(pinConfig.enabled).isTrue();
        assertThat(pinConfig.minLockLength).isEqualTo(LockPatternUtils.MIN_LOCK_PASSWORD_SIZE);
        LockConfig patternConfig = service.getLockConfig(LockTypes.PATTERN);
        assertThat(patternConfig.enabled).isTrue();
        assertThat(patternConfig.minLockLength).isEqualTo(LockPatternUtils.MIN_LOCK_PATTERN_SIZE);
        LockConfig passwordConfig = service.getLockConfig(LockTypes.PASSWORD);
        assertThat(passwordConfig.enabled).isTrue();
        assertThat(passwordConfig.minLockLength).isEqualTo(LockPatternUtils.MIN_LOCK_PASSWORD_SIZE);
    }

    @Test
    public void testCheckValidLock_tooShort() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        int result = service.checkValidLock(LockTypes.PASSWORD, "hi".getBytes());
        assertThat(result & ValidateLockFlags.INVALID_LENGTH)
                .isEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    @Test
    public void testCheckValidLock_longEnough() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        int result = service.checkValidLock(LockTypes.PASSWORD, "password".getBytes());
        assertThat(result & ValidateLockFlags.INVALID_LENGTH)
                .isNotEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    @Test
    public void testCheckValidLockPin_withLetters() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        int result = service.checkValidLock(LockTypes.PIN, "12a3".getBytes());
        assertThat(result & ValidateLockFlags.INVALID_BAD_SYMBOLS)
                .isEqualTo(ValidateLockFlags.INVALID_BAD_SYMBOLS);
    }

    @Test
    public void testCheckValidLockPattern_tooShort() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        byte[] pattern = new byte[LockPatternUtils.MIN_LOCK_PATTERN_SIZE - 1];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = (byte) i;
        }
        int result = service.checkValidLock(LockTypes.PATTERN, pattern);
        assertThat(result & ValidateLockFlags.INVALID_LENGTH)
                .isEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    @Test
    public void testCheckValidLockPattern_longEnough() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        byte[] pattern = new byte[LockPatternUtils.MIN_LOCK_PATTERN_SIZE + 1];
        for (int i = 0; i < pattern.length; i++) {
            pattern[i] = (byte) i;
        }
        int result = service.checkValidLock(LockTypes.PATTERN, pattern);
        assertThat(result & ValidateLockFlags.INVALID_LENGTH)
                .isNotEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    @Test
    public void testSetLockPassword_doesNotWorkWithExistingPassword() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        ShadowLockPatternUtils.setPasswordQuality(DevicePolicyManager.PASSWORD_QUALITY_NUMERIC);
        int result = service.setLock(LockTypes.PASSWORD, "password".getBytes());
        assertThat(result).isEqualTo(SetLockCodes.FAIL_LOCK_EXISTS);
    }

    @Test
    public void testSetLockPassword_doesNotWorkWithInvalidPassword() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        int result = service.setLock(LockTypes.PASSWORD, "hi".getBytes());
        assertThat(result).isEqualTo(SetLockCodes.FAIL_LOCK_INVALID);
    }

    @Test
    public void testSetLockPassword_setsDevicePassword() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        byte[] password = "password".getBytes();
        // Need copy since password is cleared.
        byte[] expectedPassword = Arrays.copyOf(password, password.length);
        int result = service.setLock(LockTypes.PASSWORD, password);
        assertThat(result).isEqualTo(SetLockCodes.SUCCESS);
        assertThat(Arrays.equals(ShadowLockPatternUtils.getSavedPassword(),
                expectedPassword)).isTrue();
    }

    @Test
    public void testSetLockPin_setsDevicePin() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        byte[] password = "1580".getBytes();
        byte[] expectedPassword = Arrays.copyOf(password, password.length);
        int result = service.setLock(LockTypes.PIN, password);
        assertThat(result).isEqualTo(SetLockCodes.SUCCESS);
        assertThat(Arrays.equals(ShadowLockPatternUtils.getSavedPassword(),
                expectedPassword)).isTrue();
    }

    @Test
    public void testSetLockPattern_setsDevicePattern() throws RemoteException {
        IInitialLockSetupService service = IInitialLockSetupService.Stub.asInterface(
                mInitialLockSetupService.onBind(new Intent()));
        List<LockPatternView.Cell> pattern = new ArrayList<>();
        pattern.add(LockPatternView.Cell.of(0, 0));
        pattern.add(LockPatternView.Cell.of(1, 0));
        pattern.add(LockPatternView.Cell.of(2, 0));
        pattern.add(LockPatternView.Cell.of(0, 1));
        byte[] patternBytes = new byte[pattern.size()];
        for (int i = 0; i < patternBytes.length; i++) {
            LockPatternView.Cell cell = pattern.get(i);
            patternBytes[i] = InitialLockSetupHelper.getByteFromPatternCell(cell.getRow(),
                    cell.getColumn());
        }
        int result = service.setLock(LockTypes.PATTERN, patternBytes);
        assertThat(result).isEqualTo(SetLockCodes.SUCCESS);
        List<LockPatternView.Cell> savedPattern = ShadowLockPatternUtils.getSavedPattern();
        assertThat(savedPattern).containsExactlyElementsIn(pattern);
    }

    @Test
    public void testBindFails_ifNoPermissionGranted() {
        ShadowContextWrapper shadowContextWrapper = Shadows.shadowOf((ContextWrapper) mContext);
        shadowContextWrapper.denyPermissions(LOCK_PERMISSION);
        assertThat(mInitialLockSetupService.onBind(new Intent())).isNull();
    }

}
