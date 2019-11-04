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

package com.android.car.dialer.ui.common;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Resources;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.PhoneNumber;
import com.android.car.telephony.common.TelecomUtils;

import java.util.ArrayList;
import java.util.List;

/** Ui Utilities for dialer */
public class DialerUtils {

    private static final String TAG = "CD.DialerUtils";

    /**
     * Callback interface for
     * {@link DialerUtils#showPhoneNumberSelector(Context, List, PhoneNumberSelectionCallback)} and
     * {@link DialerUtils#promptForPrimaryNumber(Context, Contact, PhoneNumberSelectionCallback)}
     */
    public interface PhoneNumberSelectionCallback {
        /**
         * Called when a phone number is chosen.
         * @param phoneNumber The phone number
         * @param always Whether the user pressed "aways" or "just once"
         */
        void onPhoneNumberSelected(PhoneNumber phoneNumber, boolean always);
    }

    /**
     * Shows a dialog asking the user to pick a phone number.
     * Has buttons for selecting always or just once.
     */
    public static void showPhoneNumberSelector(Context context,
            List<PhoneNumber> numbers,
            PhoneNumberSelectionCallback callback) {
        final List<PhoneNumber> selectedPhoneNumber = new ArrayList<>();
        new AlertDialog.Builder(context)
                .setTitle(R.string.select_number_dialog_title)
                .setSingleChoiceItems(
                        new PhoneNumberListAdapter(context, numbers),
                        -1,
                        ((dialog, which) -> {
                            selectedPhoneNumber.clear();
                            selectedPhoneNumber.add(numbers.get(which));
                        }))
                .setNeutralButton(R.string.select_number_dialog_just_once_button,
                        (dialog, which) -> {
                            if (!selectedPhoneNumber.isEmpty()) {
                                callback.onPhoneNumberSelected(selectedPhoneNumber.get(0), false);
                            }
                        })
                .setPositiveButton(R.string.select_number_dialog_always_button,
                        (dialog, which) -> {
                            if (!selectedPhoneNumber.isEmpty()) {
                                callback.onPhoneNumberSelected(selectedPhoneNumber.get(0), true);
                            }
                        })
                .show();
    }

    /**
     * Gets the primary phone number from the contact.
     * If no primary number is set, a dialog will pop up asking the user to select one.
     * If the user presses the "always" button, the phone number will become their
     * primary phone number. The "always" parameter of the callback will always be false
     * using this method.
     */
    public static void promptForPrimaryNumber(
            Context context,
            Contact contact,
            PhoneNumberSelectionCallback callback) {
        if (contact.hasPrimaryPhoneNumber()) {
            callback.onPhoneNumberSelected(contact.getPrimaryPhoneNumber(), false);
        } else if (contact.getNumbers().size() == 1) {
            callback.onPhoneNumberSelected(contact.getNumbers().get(0), false);
        } else if (contact.getNumbers().size() > 0) {
            showPhoneNumberSelector(context, contact.getNumbers(), (phoneNumber, always) -> {
                if (always) {
                    TelecomUtils.setAsPrimaryPhoneNumber(context, phoneNumber);
                }

                callback.onPhoneNumberSelected(phoneNumber, false);
            });
        } else {
            L.w(TAG, "contact %s doesn't have any phone number", contact.getDisplayName());
        }
    }

    /** Returns true if this a short height screen */
    public static boolean isShortScreen(Context context) {
        Resources resources = context.getResources();
        return resources.getBoolean(R.bool.screen_size_short);
    }

    /** Returns true if this a tall height screen */
    public static boolean isTallScreen(Context context) {
        Resources resources = context.getResources();
        return resources.getBoolean(R.bool.screen_size_tall);
    }
}
