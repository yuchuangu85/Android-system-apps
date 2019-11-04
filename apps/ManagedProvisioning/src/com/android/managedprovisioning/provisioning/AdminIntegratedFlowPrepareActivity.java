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
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.widget.ImageView;
import androidx.annotation.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.google.android.setupdesign.GlifLayout;

/**
 * Progress activity shown whilst network setup, downloading, verifying and installing the
 * admin package is ongoing.
 *
 * <p>This activity registers for updates of the provisioning process from the
 * {@link AdminIntegratedFlowPrepareManager}. It shows progress updates as provisioning
 * progresses and handles showing of cancel and error dialogs.</p>
 */
public class AdminIntegratedFlowPrepareActivity extends AbstractProvisioningActivity {

    private RepeatingVectorAnimation mRepeatingVectorAnimation;

    public AdminIntegratedFlowPrepareActivity() {
        this(new Utils());
    }

    public static boolean shouldRunPrepareActivity(
            Utils utils, Context context, ProvisioningParams params) {
        if (params.wifiInfo != null) {
            return true;
        }
        if (params.useMobileData) {
            return true;
        }
        if (params.deviceAdminDownloadInfo != null) {
            // Only prepare if a download is actually required
            if (utils.packageRequiresUpdate(
                    params.inferDeviceAdminPackageName(),
                    params.deviceAdminDownloadInfo.minVersion,
                    context)) {
                return true;
            }
        }
        return false;
    }

    @VisibleForTesting
    protected AdminIntegratedFlowPrepareActivity(Utils utils) {
        super(utils);
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.stop();
        }
    }

    @Override
    protected ProvisioningManagerInterface getProvisioningManager() {
        if (mProvisioningManager == null) {
            mProvisioningManager = AdminIntegratedFlowPrepareManager.getInstance(this);
        }
        return mProvisioningManager;
    }

    @Override
    public void preFinalizationCompleted() {
        ProvisionLogger.logi("AdminIntegratedFlowPrepareActivity pre-finalization completed");
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void decideCancelProvisioningDialog() {
        showCancelProvisioningDialog(/* resetRequired = */true);
    }

    @Override
    protected void initializeUi(ProvisioningParams params) {
        final int headerResId = R.string.downloading_administrator_header;
        final int titleResId = R.string.setup_device_progress;
        final CustomizationParams customizationParams =
                CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(R.layout.prepare_progress, headerResId, customizationParams);
        setTitle(titleResId);

        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        final ImageView imageView = layout.findViewById(R.id.animation);
        final AnimatedVectorDrawable animatedVectorDrawable =
                (AnimatedVectorDrawable) imageView.getDrawable();
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(animatedVectorDrawable);
        mRepeatingVectorAnimation.start();
    }
}