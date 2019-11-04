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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link ProvisioningIntentProvider}.
 */
public class ProvisioningIntentProviderTest {

    private static final String DEVICE_ADMIN_PACKAGE_NAME = "com.example.package";
    private static final ComponentName DEVICE_ADMIN_COMPONENT_NAME =
            new ComponentName(DEVICE_ADMIN_PACKAGE_NAME, "com.android.AdminReceiver");
    private static final PersistableBundle ADMIN_EXTRAS_BUNDLE =
            PersistableBundle.forPair("test_key", "test_value");

    private ProvisioningIntentProvider mProvisioningIntentProvider;
    private ProvisioningParams mParams;
    @Mock private Context mContext;
    @Mock private Utils mUtils;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mProvisioningIntentProvider = new ProvisioningIntentProvider();
        mParams = new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(DEVICE_ADMIN_COMPONENT_NAME)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .setAdminExtrasBundle(ADMIN_EXTRAS_BUNDLE)
                .build();
    }

    @Test
    public void maybeLaunchDpc_success() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(true);

        mProvisioningIntentProvider.maybeLaunchDpc(mParams, 0, mUtils, mContext);

        verify(mContext).startActivityAsUser(any(), any());
    }

    @Test
    public void maybeLaunchDpc_cannotResolveIntent() {
        when(mUtils.canResolveIntentAsUser(any(), any(), anyInt())).thenReturn(false);

        mProvisioningIntentProvider.maybeLaunchDpc(mParams, 0, mUtils, mContext);

        verify(mContext, never()).startActivityAsUser(any(), any());
    }

    @Test
    public void createProvisioningIntent_success() {
        Intent intent = mProvisioningIntentProvider
                .createProvisioningCompleteIntent(mParams, 0, mUtils, mContext);

        assertThat(intent.getAction()).isEqualTo(ACTION_PROFILE_PROVISIONING_COMPLETE);
        assertThat(intent.getComponent()).isEqualTo(DEVICE_ADMIN_COMPONENT_NAME);
        assertThat(intent.<PersistableBundle>getParcelableExtra(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)).isEqualTo(ADMIN_EXTRAS_BUNDLE);
    }

    @Test
    public void createProvisioningIntent_cannotFindAdmin()
            throws IllegalProvisioningArgumentException {
        ProvisioningParams provisioningParams = new ProvisioningParams.Builder()
                .setDeviceAdminPackageName(DEVICE_ADMIN_PACKAGE_NAME)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                .build();
        when(mUtils.findDeviceAdmin(eq(DEVICE_ADMIN_PACKAGE_NAME), any(), any(), anyInt()))
                .thenThrow(IllegalProvisioningArgumentException.class);

        assertThat(mProvisioningIntentProvider.createProvisioningCompleteIntent(
                provisioningParams, 0, mUtils, mContext)).isNull();
    }
}
