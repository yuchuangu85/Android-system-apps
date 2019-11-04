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

import android.app.admin.DevicePolicyManager;
import android.app.admin.PasswordMetrics;
import android.content.Context;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.setupwizardlib.InitialLockSetupConstants.ValidateLockFlags;

import java.util.LinkedList;
import java.util.List;

/**
 * Helper used by ChooseLockPinPasswordFragment
 */
public class PasswordHelper {
    public static final String EXTRA_CURRENT_SCREEN_LOCK = "extra_current_screen_lock";
    /**
     * Required minimum length of PIN or password.
     */
    public static final int MIN_LENGTH = 4;
    // Error code returned from validate(String).
    static final int NO_ERROR = 0;
    static final int CONTAINS_INVALID_CHARACTERS = 1;
    static final int TOO_SHORT = 1 << 1;
    static final int CONTAINS_NON_DIGITS = 1 << 2;
    static final int CONTAINS_SEQUENTIAL_DIGITS = 1 << 3;
    private static final Logger LOG = new Logger(PasswordHelper.class);
    private final boolean mIsPin;

    public PasswordHelper(boolean isPin) {
        mIsPin = isPin;
    }

    /**
     * Returns one of the password quality values defined in {@link DevicePolicyManager}, such
     * as NUMERIC, ALPHANUMERIC etc.
     */
    public int getPasswordQuality() {
        return mIsPin ? DevicePolicyManager.PASSWORD_QUALITY_NUMERIC :
                DevicePolicyManager.PASSWORD_QUALITY_ALPHANUMERIC;
    }

    /**
     * Validates PIN/Password and returns the validation result.
     *
     * @param password the raw bytes for the password the user typed in
     * @return the error code which should be non-zero where there is error. Otherwise
     * {@link #NO_ERROR} should be returned.
     */
    public int validate(byte[] password) {
        return mIsPin ? validatePin(password) : validatePassword(password);
    }

    /**
     * Validates PIN/Password using the setup wizard {@link ValidateLockFlags}.
     *
     * @param password The password to validate.
     * @return The error code where 0 is no error.
     */
    public int validateSetupWizard(byte[] password) {
        return mIsPin ? translateSettingsToSuwError(validatePin(password))
                : translateSettingsToSuwError(validatePassword(password));
    }

    /**
     * Converts error code from validatePassword to an array of messages describing the errors with
     * important message comes first.  The messages are concatenated with a space in between.
     * Please make sure each message ends with a period.
     *
     * @param errorCode the code returned by {@link #validatePassword(byte[]) validatePassword}
     */
    public List<String> convertErrorCodeToMessages(Context context, int errorCode) {
        return mIsPin ? convertPinErrorCodeToMessages(context, errorCode) :
                convertPasswordErrorCodeToMessages(context, errorCode);
    }

    private int validatePassword(byte[] password) {
        int errorCode = NO_ERROR;

        if (password.length < MIN_LENGTH) {
            errorCode |= TOO_SHORT;
        }

        // Allow non-control Latin-1 characters only.
        for (int i = 0; i < password.length; i++) {
            char c = (char) password[i];
            if (c < 32 || c > 127) {
                errorCode |= CONTAINS_INVALID_CHARACTERS;
                break;
            }
        }

        return errorCode;
    }

    private int translateSettingsToSuwError(int error) {
        int output = 0;
        if ((error & CONTAINS_NON_DIGITS) > 0) {
            LOG.v("CONTAINS_NON_DIGITS");
            output |= ValidateLockFlags.INVALID_BAD_SYMBOLS;
        }
        if ((error & CONTAINS_INVALID_CHARACTERS) > 0) {
            LOG.v("INVALID_CHAR");
            output |= ValidateLockFlags.INVALID_BAD_SYMBOLS;
        }
        if ((error & TOO_SHORT) > 0) {
            LOG.v("TOO_SHORT");
            output |= ValidateLockFlags.INVALID_LENGTH;
        }
        if ((error & CONTAINS_SEQUENTIAL_DIGITS) > 0) {
            LOG.v("SEQUENTIAL_DIGITS");
            output |= ValidateLockFlags.INVALID_LACKS_COMPLEXITY;
        }
        return output;
    }

    private int validatePin(byte[] pin) {
        int errorCode = NO_ERROR;
        PasswordMetrics metrics = PasswordMetrics.computeForPassword(pin);
        int passwordQuality = getPasswordQuality();

        if (metrics.length < MIN_LENGTH) {
            errorCode |= TOO_SHORT;
        }

        if (metrics.letters > 0 || metrics.symbols > 0) {
            errorCode |= CONTAINS_NON_DIGITS;
        }

        if (passwordQuality == DevicePolicyManager.PASSWORD_QUALITY_NUMERIC_COMPLEX) {
            // Check for repeated characters or sequences (e.g. '1234', '0000', '2468')
            int sequence = PasswordMetrics.maxLengthSequence(pin);
            if (sequence > PasswordMetrics.MAX_ALLOWED_SEQUENCE) {
                errorCode |= CONTAINS_SEQUENTIAL_DIGITS;
            }
        }

        return errorCode;
    }

    private List<String> convertPasswordErrorCodeToMessages(Context context, int errorCode) {
        List<String> messages = new LinkedList<>();

        if ((errorCode & CONTAINS_INVALID_CHARACTERS) > 0) {
            messages.add(context.getString(R.string.lockpassword_illegal_character));
        }

        if ((errorCode & TOO_SHORT) > 0) {
            messages.add(context.getString(R.string.lockpassword_password_too_short, MIN_LENGTH));
        }

        return messages;
    }

    private List<String> convertPinErrorCodeToMessages(Context context, int errorCode) {
        List<String> messages = new LinkedList<>();

        if ((errorCode & CONTAINS_NON_DIGITS) > 0) {
            messages.add(context.getString(R.string.lockpassword_pin_contains_non_digits));
        }

        if ((errorCode & CONTAINS_SEQUENTIAL_DIGITS) > 0) {
            messages.add(context.getString(R.string.lockpassword_pin_no_sequential_digits));
        }

        if ((errorCode & TOO_SHORT) > 0) {
            messages.add(context.getString(R.string.lockpin_invalid_pin));
        }

        return messages;
    }
}
