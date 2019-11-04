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

package com.android.car.settings.datausage;

import android.content.Context;
import android.net.NetworkPolicyManager;
import android.net.NetworkTemplate;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.Nullable;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.NetworkPolicyEditor;

import java.util.Arrays;
import java.util.List;

/** Screen to set data warning and limit thresholds. */
public class DataWarningAndLimitFragment extends SettingsFragment {

    private TelephonyManager mTelephonyManager;
    private SubscriptionManager mSubscriptionManager;
    private NetworkPolicyEditor mPolicyEditor;
    private NetworkTemplate mNetworkTemplate;

    /**
     * Creates a new instance of {@link DataWarningAndLimitFragment} with the given template. If the
     * template is {@code null}, the fragment will use the default data network template.
     */
    public static DataWarningAndLimitFragment newInstance(@Nullable NetworkTemplate template) {
        DataWarningAndLimitFragment fragment = new DataWarningAndLimitFragment();
        Bundle args = new Bundle();
        args.putParcelable(NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE, template);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.data_warning_and_limit_fragment;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        mPolicyEditor = new NetworkPolicyEditor(NetworkPolicyManager.from(context));
        mNetworkTemplate = getArguments().getParcelable(
                NetworkPolicyManager.EXTRA_NETWORK_TEMPLATE);
        if (mNetworkTemplate == null) {
            mTelephonyManager = context.getSystemService(TelephonyManager.class);
            mSubscriptionManager = context.getSystemService(SubscriptionManager.class);
            mNetworkTemplate = DataUsageUtils.getMobileNetworkTemplate(mTelephonyManager,
                    DataUsageUtils.getDefaultSubscriptionId(mSubscriptionManager));
        }

        // Loads the current policies to the policy editor cache.
        mPolicyEditor.read();

        List<DataWarningAndLimitBasePreferenceController> preferenceControllers =
                Arrays.asList(
                        use(CycleResetDayOfMonthPickerPreferenceController.class,
                                R.string.pk_data_usage_cycle),
                        use(DataWarningPreferenceController.class, R.string.pk_data_warning_group),
                        use(DataLimitPreferenceController.class, R.string.pk_data_limit_group));

        for (DataWarningAndLimitBasePreferenceController preferenceController :
                preferenceControllers) {
            preferenceController.setNetworkPolicyEditor(mPolicyEditor);
            preferenceController.setNetworkTemplate(mNetworkTemplate);
        }
    }
}
