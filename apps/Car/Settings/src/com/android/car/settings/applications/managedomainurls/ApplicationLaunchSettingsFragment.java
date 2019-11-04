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

package com.android.car.settings.applications.managedomainurls;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Bundle;

import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.applications.ApplicationsState;

import java.util.Arrays;
import java.util.List;

/** Settings screen to show details about launching a specific app. */
public class ApplicationLaunchSettingsFragment extends SettingsFragment {

    @VisibleForTesting
    static final String ARG_PACKAGE_NAME = "arg_package_name";

    private ApplicationsState mState;
    private ApplicationsState.AppEntry mAppEntry;
    private CarUserManagerHelper mCarUserManagerHelper;

    /** Creates a new instance of this fragment for the package specified in the arguments. */
    public static ApplicationLaunchSettingsFragment newInstance(String pkg) {
        ApplicationLaunchSettingsFragment fragment = new ApplicationLaunchSettingsFragment();
        Bundle args = new Bundle();
        args.putString(ARG_PACKAGE_NAME, pkg);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.application_launch_settings_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mState = ApplicationsState.getInstance(requireActivity().getApplication());

        String pkgName = getArguments().getString(ARG_PACKAGE_NAME);
        mAppEntry = mState.getEntry(pkgName, mCarUserManagerHelper.getCurrentProcessUserId());

        ApplicationWithVersionPreferenceController appController = use(
                ApplicationWithVersionPreferenceController.class,
                R.string.pk_opening_links_app_details);
        appController.setAppState(mState);
        appController.setAppEntry(mAppEntry);

        List<AppLaunchSettingsBasePreferenceController> preferenceControllers = Arrays.asList(
                use(AppLinkStatePreferenceController.class,
                        R.string.pk_opening_links_app_details_state),
                use(DomainUrlsPreferenceController.class,
                        R.string.pk_opening_links_app_details_urls),
                use(ClearDefaultsPreferenceController.class,
                        R.string.pk_opening_links_app_details_reset));

        for (AppLaunchSettingsBasePreferenceController controller : preferenceControllers) {
            controller.setAppEntry(mAppEntry);
        }
    }
}
