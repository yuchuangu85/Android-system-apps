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

import android.widget.Button;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;

/**
 * Helper methods for DialogFragment testing.
 */
public class DialogTestUtils {
    private DialogTestUtils() {
    }

    /**
     * Invokes onClick on the dialog's positive button.
     */
    public static void clickPositiveButton(DialogFragment dialogFragment) {
        Button positiveButton = dialogFragment.getDialog().getWindow().findViewById(
                com.android.internal.R.id.button1);
        positiveButton.callOnClick();
    }

    /**
     * Invokes onClick on the dialog's negative button.
     */
    public static void clickNegativeButton(DialogFragment dialogFragment) {
        Button negativeButton = dialogFragment.getDialog().getWindow().findViewById(
                com.android.internal.R.id.button2);
        negativeButton.callOnClick();
    }

    /**
     * Gets dialog's title.
     */
    public static String getTitle(DialogFragment dialogFragment) {
        TextView titleView = dialogFragment.getDialog().getWindow().findViewById(
                com.android.internal.R.id.alertTitle);
        return titleView.getText().toString();
    }
}
