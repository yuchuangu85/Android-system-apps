/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.provisioning;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.AccessibilityContextMenuMaker;
import com.android.managedprovisioning.common.ClickableSpanFactory;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.google.android.setupcompat.util.WizardManagerHelper;
import com.google.android.setupdesign.GlifLayout;

/**
 * The first activity shown during provisioning.
 */
public class LandingActivity extends SetupGlifLayoutActivity {
    private static final int ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE = 1;
    private final AccessibilityContextMenuMaker mContextMenuMaker;

    public LandingActivity() {
        this(new Utils(), null);
    }

    @VisibleForTesting
    LandingActivity(Utils utils, AccessibilityContextMenuMaker contextMenuMaker) {
        super(utils);
        mContextMenuMaker = contextMenuMaker != null
                ? contextMenuMaker
                : new AccessibilityContextMenuMaker(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        final ProvisioningParams params =
                getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        initializeUi(params);
    }

    private void initializeUi(ProvisioningParams params) {
        final int headerResId = R.string.brand_screen_header;
        final int titleResId = R.string.setup_device_progress;

        final CustomizationParams customizationParams =
                CustomizationParams.createInstance(params, this, mUtils);
        initializeLayoutParams(R.layout.landing_screen, headerResId, customizationParams);
        setTitle(titleResId);

        handleSupportUrl(customizationParams);
        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        Utils.addNextButton(layout, v -> onNextButtonClicked(params));

        if (Utils.isSilentProvisioning(this, params)) {
            onNextButtonClicked(params);
        }
    }

    private void onNextButtonClicked(ProvisioningParams params) {
        if (AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(mUtils, this, params)) {
            final Intent intent = new Intent(this, AdminIntegratedFlowPrepareActivity.class);
            WizardManagerHelper.copyWizardManagerExtras(getIntent(), intent);
            intent.putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, params);
            startActivityForResult(intent, ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE);
        } else {
            setResult(Activity.RESULT_OK);
            finish();
        }
    }

    private void handleSupportUrl(CustomizationParams customizationParams) {
        final TextView info = findViewById(R.id.provider_info);
        final String deviceProvider = getString(R.string.organization_admin);
        final String contactDeviceProvider =
                getString(R.string.contact_device_provider, deviceProvider);
        final ClickableSpanFactory clickableSpanFactory =
                new ClickableSpanFactory(getColor(R.color.blue_text));
        mUtils.handleSupportUrl(this, customizationParams, clickableSpanFactory,
                mContextMenuMaker, info, deviceProvider, contactDeviceProvider);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == ADMIN_INTEGRATED_FLOW_PREPARE_REQUEST_CODE) {
            setResult(resultCode);
            finish();
        }
    }
}
