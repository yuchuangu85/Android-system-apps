/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.finalization;

import static android.app.admin.DeviceAdminReceiver.ACTION_PROFILE_PROVISIONING_COMPLETE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISIONING_SUCCESSFUL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;

import android.annotation.NonNull;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.UserHandle;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.google.android.setupcompat.util.WizardManagerHelper;

/**
 * Helper class for creating intents in finalization controller.
 */
class ProvisioningIntentProvider {
    void maybeLaunchDpc(ProvisioningParams params, int userId, Utils utils, Context context) {
        final Intent dpcLaunchIntent = createDpcLaunchIntent(params);
        if (utils.canResolveIntentAsUser(context, dpcLaunchIntent, userId)) {
            context.startActivityAsUser(createDpcLaunchIntent(params), UserHandle.of(userId));
            ProvisionLogger.logd("Dpc was launched for user: " + userId);
        }
    }

    boolean canLaunchDpc(ProvisioningParams params, int userId, Utils utils, Context context) {
        final Intent dpcLaunchIntent = createDpcLaunchIntent(params);
        return utils.canResolveIntentAsUser(context, dpcLaunchIntent, userId);
    }

    Intent createProvisioningCompleteIntent(
            @NonNull ProvisioningParams params, int userId, Utils utils, Context context) {
        Intent intent = new Intent(ACTION_PROFILE_PROVISIONING_COMPLETE);
        try {
            intent.setComponent(params.inferDeviceAdminComponentName(utils, context, userId));
        } catch (IllegalProvisioningArgumentException e) {
            ProvisionLogger.loge("Failed to infer the device admin component name", e);
            return null;
        }
        intent.addFlags(Intent.FLAG_INCLUDE_STOPPED_PACKAGES | Intent.FLAG_RECEIVER_FOREGROUND);
        addExtrasToIntent(intent, params);
        return intent;
    }

    private Intent createDpcLaunchIntent(@NonNull ProvisioningParams params) {
        Intent intent = new Intent(ACTION_PROVISIONING_SUCCESSFUL);
        final String packageName = params.inferDeviceAdminPackageName();
        if (packageName == null) {
            ProvisionLogger.loge("Device admin package name is null");
            return null;
        }
        intent.setPackage(packageName);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        addExtrasToIntent(intent, params);
        return intent;
    }

    private void addExtrasToIntent(Intent intent, ProvisioningParams params) {
        intent.putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, params.adminExtrasBundle);
    }

    void launchFinalizationScreen(Context context, ProvisioningParams params) {
        final Intent finalizationScreen = new Intent(context, FinalScreenActivity.class);
        if (context instanceof Activity) {
            final Intent intent = ((Activity) context).getIntent();
            if (intent != null) {
                WizardManagerHelper.copyWizardManagerExtras(intent, finalizationScreen);
            }
        }
        finalizationScreen.putExtra(FinalScreenActivity.EXTRA_PROVISIONING_PARAMS, params);
        context.startActivity(finalizationScreen);
    }
}
