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
 * limitations under the License
 */

package com.android.managedprovisioning.provisioning;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.Shadows.shadowOf;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

/**
 * Robolectric tests for {@link AdminIntegratedFlowPrepareActivity}.
 */
@RunWith(RobolectricTestRunner.class)
public class AdminIntegratedFlowPrepareActivityTest {

    private static final ComponentName TEST_COMPONENT_NAME =
            new ComponentName("test", "test");
    private static final String TEST_PACKAGE_LOCATION = "http://test.location/test.apk";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[]{1};

    Utils mUtils = new Utils();
    Context mContext = RuntimeEnvironment.application;
    PackageManager mPackageManager = mContext.getPackageManager();

    @Test
    public void shouldRunPrepareActivity_returnsFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder().build();

        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isFalse();
    }

    @Test
    public void shouldRunPrepareActivity_wifi_returnsTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setWifiInfo(new WifiInfo.Builder()
                        .setSsid("ssid")
                        .build())
                .build();

        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isTrue();
    }

    @Test
    public void shouldRunPrepareActivity_mobileData_returnsTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setUseMobileData(true)
                .build();

        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isTrue();
    }

    @Test
    public void shouldRunPrepareActivity_download_notSideloaded_returnsTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setDeviceAdminDownloadInfo(
                        new PackageDownloadInfo.Builder()
                                .setLocation(TEST_PACKAGE_LOCATION)
                                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                .build())
                .build();


        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isTrue();
    }

    @Test
    public void shouldRunPrepareActivity_download_noMinimum_sideloaded_returnsTrue() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setDeviceAdminDownloadInfo(
                        new PackageDownloadInfo.Builder()
                                .setLocation(TEST_PACKAGE_LOCATION)
                                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                .build())
                .build();
        installTestPackage();


        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isTrue();
    }

    @Test
    public void shouldRunPrepareActivity_download_minimum_sideloaded_meetsMinimum_returnsFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setDeviceAdminDownloadInfo(
                        new PackageDownloadInfo.Builder()
                                .setLocation(TEST_PACKAGE_LOCATION)
                                .setMinVersion(1) // installed package is version 2
                                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                .build())
                .build();
        installTestPackage();

        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isFalse();
    }

    @Test
    public void shouldRunPrepareActivity_download_minimum_sideloaded_doesNotMeetMinimum_returnsFalse() {
        ProvisioningParams params = createDefaultProvisioningParamsBuilder()
                .setDeviceAdminDownloadInfo(
                        new PackageDownloadInfo.Builder()
                                .setLocation(TEST_PACKAGE_LOCATION)
                                .setMinVersion(3) // installed package is version 2
                                .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                .build())
                .build();
        installTestPackage();

        boolean shouldRun =
                AdminIntegratedFlowPrepareActivity.shouldRunPrepareActivity(
                        mUtils, mContext, params);

        assertThat(shouldRun).isTrue();
    }

    private void installTestPackage() {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = TEST_COMPONENT_NAME.getPackageName();
        packageInfo.versionCode = 2;
        shadowOf(mPackageManager).installPackage(packageInfo);
    }

    private static ProvisioningParams.Builder createDefaultProvisioningParamsBuilder() {
        return new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setProvisioningAction("");
    }
}
