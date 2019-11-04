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

package com.android.car.settings.applications;

import android.car.drivingstate.CarUxRestrictions;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources;
import android.icu.text.ListFormatter;
import android.text.TextUtils;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.PermissionsSummaryHelper;

import java.util.ArrayList;
import java.util.List;

/** Business logic for the permissions entry in the application details settings. */
public class PermissionsPreferenceController extends PreferenceController<Preference> {

    private static final Logger LOG = new Logger(PermissionsPreferenceController.class);

    private String mPackageName;
    private String mSummary;

    public PermissionsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    protected Class<Preference> getPreferenceType() {
        return Preference.class;
    }

    /**
     * Set the packageName, which is used on the intent to open the permissions
     * selection screen.
     */
    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }

    @Override
    protected void checkInitialized() {
        if (mPackageName == null) {
            throw new IllegalStateException(
                    "PackageName should be set before calling this function");
        }
    }

    @Override
    protected void onStartInternal() {
        PermissionsSummaryHelper.getPermissionSummary(getContext(), mPackageName,
                mPermissionCallback);
    }

    @Override
    protected void updateState(Preference preference) {
        preference.setSummary(getSummary());
    }

    @Override
    protected boolean handlePreferenceClicked(Preference preference) {
        Intent intent = new Intent(Intent.ACTION_MANAGE_APP_PERMISSIONS);
        intent.putExtra(Intent.EXTRA_PACKAGE_NAME, mPackageName);
        try {
            getContext().startActivity(intent);
        } catch (ActivityNotFoundException e) {
            LOG.w("No app can handle android.intent.action.MANAGE_APP_PERMISSIONS");
        }
        return true;
    }

    private CharSequence getSummary() {
        if (TextUtils.isEmpty(mSummary)) {
            return getContext().getString(R.string.computing_size);
        }
        return mSummary;
    }

    private final PermissionsSummaryHelper.PermissionsResultCallback mPermissionCallback =
            new PermissionsSummaryHelper.PermissionsResultCallback() {
                @Override
                public void onPermissionSummaryResult(int standardGrantedPermissionCount,
                        int requestedPermissionCount, int additionalGrantedPermissionCount,
                        List<CharSequence> grantedGroupLabels) {
                    Resources res = getContext().getResources();

                    if (requestedPermissionCount == 0) {
                        mSummary = res.getString(
                                R.string.runtime_permissions_summary_no_permissions_requested);
                    } else {
                        ArrayList<CharSequence> list = new ArrayList<>(grantedGroupLabels);
                        if (additionalGrantedPermissionCount > 0) {
                            // N additional permissions.
                            list.add(res.getQuantityString(
                                    R.plurals.runtime_permissions_additional_count,
                                    additionalGrantedPermissionCount,
                                    additionalGrantedPermissionCount));
                        }
                        if (list.isEmpty()) {
                            mSummary = res.getString(
                                    R.string.runtime_permissions_summary_no_permissions_granted);
                        } else {
                            mSummary = ListFormatter.getInstance().format(list);
                        }
                    }
                    refreshUi();
                }
            };
}
