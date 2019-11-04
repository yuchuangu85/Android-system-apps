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

package com.android.car.settings.security;

import static com.google.common.truth.Truth.assertThat;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.setupwizardlib.InitialLockSetupConstants.ValidateLockFlags;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Tests for PasswordHelper class.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class PasswordHelperTest {

    private PasswordHelper mPasswordHelper;
    private PasswordHelper mPinHelper;

    @Before
    public void initObjects() {
        mPasswordHelper = new PasswordHelper(/* isPin= */ false);
        mPinHelper = new PasswordHelper(/* isPin= */ true);
    }

    /**
     * A test to check validate works as expected for alphanumeric password
     * that are too short.
     */
    @Test
    public void testValidatePasswordTooShort() {
        byte[] password = "lov".getBytes();
        assertThat(mPasswordHelper.validate(password))
                .isEqualTo(PasswordHelper.TOO_SHORT);
    }

    /**
     * A test to check validate works when alphanumeric password contains white space.
     */
    @Test
    public void testValidatePasswordWhiteSpace() {
        byte[] password = "pass wd".getBytes();
        assertThat(mPasswordHelper.validate(password))
                .isEqualTo(PasswordHelper.NO_ERROR);
    }

    /**
     * A test to check validate works as expected for alphanumeric password
     * that contains invalid character.
     */
    @Test
    public void testValidatePasswordNonAscii() {
        byte[] password = "1passwýd".getBytes();
        assertThat(mPasswordHelper.validate(password))
                .isEqualTo(PasswordHelper.CONTAINS_INVALID_CHARACTERS);
    }

    /**
     * A test to check validate works as expected for pin that contains non digits.
     */
    @Test
    public void testValidatePinContainingNonDigits() {
        byte[] password = "1a34".getBytes();
        assertThat(mPinHelper.validate(password))
                .isEqualTo(PasswordHelper.CONTAINS_NON_DIGITS);
    }

    /**
     * A test to check validate works as expected for pin with too few digits
     */
    @Test
    public void testValidatePinWithTooFewDigits() {
        byte[] password = "12".getBytes();
        assertThat(mPinHelper.validate(password))
                .isEqualTo(PasswordHelper.TOO_SHORT);
    }

    /**
     * A test to check that validate will work as expected for setup wizard passwords that are
     * too short.
     */
    @Test
    public void testValidatePasswordTooShort_setupWizard() {
        byte[] password = "lov".getBytes();
        assertThat(mPasswordHelper.validateSetupWizard(password) & ValidateLockFlags.INVALID_LENGTH)
                .isEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    /**
     * A test to check that validate works when setup wizard alphanumeric passwords contain white
     * space.
     */
    @Test
    public void testValidatePasswordWhiteSpace_setupWizard() {
        byte[] password = "pass wd".getBytes();
        assertThat(mPasswordHelper.validateSetupWizard(password)).isEqualTo(0);
    }

    /**
     * A test to check validate works as expected for setup wizard alphanumeric password
     * that contains an invalid character.
     */
    @Test
    public void testValidatePasswordNonAscii_setupWizard() {
        byte[] password = "1passwýd".getBytes();
        assertThat(mPasswordHelper.validateSetupWizard(password)
                & ValidateLockFlags.INVALID_BAD_SYMBOLS)
                .isEqualTo(ValidateLockFlags.INVALID_BAD_SYMBOLS);
    }

    /**
     * A test to check validate works as expected for setup wizard pin that contains non digits.
     */
    @Test
    public void testValidatePinContainingNonDigits_setupWizard() {
        byte[] password = "1a34".getBytes();
        assertThat(mPinHelper.validateSetupWizard(password)
                & ValidateLockFlags.INVALID_BAD_SYMBOLS)
                .isEqualTo(ValidateLockFlags.INVALID_BAD_SYMBOLS);
    }

    /**
     * A test to check validate works as expected for setup wizard pin with too few digits.
     */
    @Test
    public void testValidatePinWithTooFewDigits_setupWizard() {
        byte[] password = "12".getBytes();
        assertThat(mPinHelper.validateSetupWizard(password) & ValidateLockFlags.INVALID_LENGTH)
                .isEqualTo(ValidateLockFlags.INVALID_LENGTH);
    }

    /**
     * A test to check that validate works as expected for a valid setup wizard pin.
     */
    @Test
    public void testValidatePinWithValidPin_setupWizard() {
        byte[] password = "1234".getBytes();
        assertThat(mPinHelper.validateSetupWizard(password)).isEqualTo(0);
    }

}
