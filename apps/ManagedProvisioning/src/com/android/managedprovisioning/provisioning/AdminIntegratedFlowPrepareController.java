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

import android.content.Context;

import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.finalization.FinalizationController;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask;
import com.android.managedprovisioning.task.AddWifiNetworkTask;
import com.android.managedprovisioning.task.ConnectMobileNetworkTask;
import com.android.managedprovisioning.task.DownloadPackageTask;
import com.android.managedprovisioning.task.InstallPackageTask;
import com.android.managedprovisioning.task.VerifyPackageTask;

/**
 * Controller for preparing admin integrated flow. This includes connecting to wi-fi and
 * mobile network, downloading, verifying and installing the admin app.
 */
public class AdminIntegratedFlowPrepareController extends AbstractProvisioningController {

    public AdminIntegratedFlowPrepareController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback) {
        this(context, params, userId, callback, new FinalizationController(context));
    }

    @VisibleForTesting
    AdminIntegratedFlowPrepareController(
            Context context,
            ProvisioningParams params,
            int userId,
            ProvisioningControllerCallback callback,
            FinalizationController finalizationController) {
        super(context, params, userId, callback, finalizationController);
    }

    @Override
    protected void setUpTasks() {
        if (mParams.wifiInfo != null) {
            addTasks(new AddWifiNetworkTask(mContext, mParams, this));
        } else if (mParams.useMobileData) {
            addTasks(new ConnectMobileNetworkTask(mContext, mParams, this));
        }

        if (mParams.deviceAdminDownloadInfo != null) {
            DownloadPackageTask downloadTask = new DownloadPackageTask(mContext, mParams, this);
            addTasks(downloadTask,
                    new VerifyPackageTask(downloadTask, mContext, mParams, this),
                    new InstallPackageTask(downloadTask, mContext, mParams, this));
        }
    }

    @Override
    protected int getErrorTitle() {
        return R.string.cant_set_up_device;
    }

    @Override
    protected int getErrorMsgId(AbstractProvisioningTask task, int errorCode) {
        if (task instanceof AddWifiNetworkTask) {
            return R.string.error_wifi;
        } else if (task instanceof DownloadPackageTask) {
            switch (errorCode) {
                case DownloadPackageTask.ERROR_DOWNLOAD_FAILED:
                    return R.string.error_download_failed;
                case DownloadPackageTask.ERROR_OTHER:
                    return R.string.cant_set_up_device;
            }
        } else if (task instanceof VerifyPackageTask) {
            switch (errorCode) {
                case VerifyPackageTask.ERROR_HASH_MISMATCH:
                    return R.string.error_hash_mismatch;
                case VerifyPackageTask.ERROR_DEVICE_ADMIN_MISSING:
                    return R.string.error_package_invalid;
            }
        } else if (task instanceof InstallPackageTask) {
            switch (errorCode) {
                case InstallPackageTask.ERROR_PACKAGE_INVALID:
                    return R.string.error_package_invalid;
                case InstallPackageTask.ERROR_INSTALLATION_FAILED:
                    return R.string.error_installation_failed;
            }
        }

        return R.string.cant_set_up_device;
    }

    @Override
    protected boolean getRequireFactoryReset(AbstractProvisioningTask task, int errorCode) {
        return !(task instanceof AddWifiNetworkTask);
    }

    @Override
    protected void performCleanup() {
        // Do nothing, because a factory reset will be triggered.
    }
}
