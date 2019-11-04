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

package com.android.car.settings.system;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.style.BulletSpan;

import androidx.annotation.StringRes;
import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Controller to determine which items appear as resetable within the reset network description. */
public class ResetNetworkItemsPreferenceController extends PreferenceController<Preference> {

    public ResetNetworkItemsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    @Override
    protected void updateState(Preference preference) {
        SpannableStringBuilder sb = new SpannableStringBuilder();
        sb.append(getContext().getString(R.string.reset_network_desc));
        sb.append(System.lineSeparator());
        if (hasFeature(PackageManager.FEATURE_WIFI)) {
            addBulletedValue(sb, R.string.reset_network_item_wifi);
        }
        if (hasFeature(PackageManager.FEATURE_TELEPHONY)) {
            addBulletedValue(sb, R.string.reset_network_item_mobile);
        }
        if (hasFeature(PackageManager.FEATURE_BLUETOOTH)) {
            addBulletedValue(sb, R.string.reset_network_item_bluetooth);
        }
        preference.setTitle(sb);
    }

    private boolean hasFeature(String feature) {
        return getContext().getPackageManager().hasSystemFeature(feature);
    }

    private void addBulletedValue(SpannableStringBuilder sb, @StringRes int resId) {
        sb.append(System.lineSeparator());
        SpannableString value = new SpannableString(getContext().getString(resId));
        // Match android.content.res.StringBlock which applies a 10 gapWidth BulletSpan as the <li>
        // style. This is a workaround for translation specific behavior in StringBlock which led
        // to multiple indents when building the list using getText(resId).
        value.setSpan(new BulletSpan(/* gapWidth= */ 10), /* start= */ 0,
                value.length(), /* flags= */ 0);
        sb.append(value);
    }
}
