/*
 * Copyright 2016, The Android Open Source Project
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
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static com.android.managedprovisioning.finalization.SendDpcBroadcastService.EXTRA_PROVISIONING_PARAMS;

import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.PersistableBundle;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;

import com.android.managedprovisioning.TestUtils;
import com.android.managedprovisioning.analytics.DeferredMetricsReader;
import com.android.managedprovisioning.common.NotificationHelper;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static com.google.common.truth.Truth.assertThat;

/**
 * Unit tests for {@link FinalizationController}.
 */
public class FinalizationControllerTest extends AndroidTestCase {
    private static final UserHandle MANAGED_PROFILE_USER_HANDLE = UserHandle.of(123);
    private static final String TEST_MDM_PACKAGE_NAME = "mdm.package.name";
    private static final String TEST_MDM_ADMIN_RECEIVER = TEST_MDM_PACKAGE_NAME + ".AdminReceiver";
    private static final ComponentName TEST_MDM_ADMIN = new ComponentName(TEST_MDM_PACKAGE_NAME,
            TEST_MDM_ADMIN_RECEIVER);
    private static final PersistableBundle TEST_MDM_EXTRA_BUNDLE = createTestAdminExtras();

    @Mock private Context mContext;
    @Mock private Utils mUtils;
    @Mock private SettingsFacade mSettingsFacade;
    @Mock private UserProvisioningStateHelper mHelper;
    @Mock private NotificationHelper mNotificationHelper;
    @Mock private DeferredMetricsReader mDeferredMetricsReader;

    private FinalizationController mController;

    @Override
    public void setUp() throws Exception {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());
        MockitoAnnotations.initMocks(this);
        when(mUtils.canResolveIntentAsUser(any(Context.class), any(Intent.class), anyInt()))
                .thenReturn(true);
        when(mContext.getFilesDir()).thenReturn(getContext().getFilesDir());

        mController = new FinalizationController(
                mContext, mUtils, mSettingsFacade, mHelper, mNotificationHelper,
                mDeferredMetricsReader);
    }

    @Override
    public void tearDown() throws Exception {
        mController.loadProvisioningParamsAndClearFile();
    }

    @SmallTest
    public void testInitiallyDone_alreadyCalled() {
        // GIVEN that provisioningInitiallyDone has already been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN nothing should happen
        verify(mHelper, never()).markUserProvisioningStateInitiallyDone(params);
        verify(mHelper, never()).markUserProvisioningStateFinalized(params);
        verifyZeroInteractions(mDeferredMetricsReader);
    }

    @SmallTest
    public void testFinalized_alreadyCalled() {
        // GIVEN that provisioningInitiallyDone has already been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN deferred metrics are written
        verify(mDeferredMetricsReader).scheduleDumpMetrics(any(Context.class));
        verifyNoMoreInteractions(mDeferredMetricsReader);

        // THEN nothing should happen
        verify(mHelper, never()).markUserProvisioningStateInitiallyDone(params);
        verify(mHelper, never()).markUserProvisioningStateFinalized(params);
    }

    @SmallTest
    public void testFinalized_noParamsStored() {
        // GIVEN that the user provisioning state is correct
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN deferred metrics are written
        verify(mDeferredMetricsReader).scheduleDumpMetrics(any(Context.class));
        verifyNoMoreInteractions(mDeferredMetricsReader);

        // THEN nothing should happen
        verify(mHelper, never())
                .markUserProvisioningStateInitiallyDone(any(ProvisioningParams.class));
        verify(mHelper, never()).markUserProvisioningStateFinalized(any(ProvisioningParams.class));
    }

    @SmallTest
    public void testManagedProfileAfterSuw() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(true);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(false);
        when(mUtils.getManagedProfile(mContext))
                .thenReturn(MANAGED_PROFILE_USER_HANDLE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);

        // THEN the service which starts the DPC is started.
        verifySendDpcServiceStarted();

        verifyZeroInteractions(mDeferredMetricsReader);
    }

    @SmallTest
    public void testManagedProfileDuringSuw() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_PROFILE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);
        when(mUtils.getManagedProfile(mContext))
                .thenReturn(MANAGED_PROFILE_USER_HANDLE);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);
        // THEN the provisioning params have been stored and will be read in provisioningFinalized

        // GIVEN that the provisioning state is now incomplete
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN deferred metrics are written
        verify(mDeferredMetricsReader).scheduleDumpMetrics(any(Context.class));
        verifyNoMoreInteractions(mDeferredMetricsReader);

        // THEN the user provisioning state is finalized
        verify(mHelper).markUserProvisioningStateFinalized(params);

        // THEN the service which starts the DPC, is be started.
        verifySendDpcServiceStarted();
    }

    @SmallTest
    public void testDeviceOwner() {
        // GIVEN that provisioningInitiallyDone has never been called
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(true);
        // GIVEN that we've provisioned a managed profile after SUW
        final ProvisioningParams params = createProvisioningParams(
                ACTION_PROVISION_MANAGED_DEVICE);
        when(mSettingsFacade.isUserSetupCompleted(mContext)).thenReturn(false);
        when(mSettingsFacade.isDuringSetupWizard(mContext)).thenReturn(true);

        // WHEN calling provisioningInitiallyDone
        mController.provisioningInitiallyDone(params);

        // THEN the user provisioning state should be marked as initially done
        verify(mHelper).markUserProvisioningStateInitiallyDone(params);
        // THEN the provisioning params have been stored and will be read in provisioningFinalized

        // GIVEN that the provisioning state is now incomplete
        when(mHelper.isStateUnmanagedOrFinalized()).thenReturn(false);

        // WHEN calling provisioningFinalized
        mController.provisioningFinalized();

        // THEN deferred metrics are written
        verify(mDeferredMetricsReader).scheduleDumpMetrics(any(Context.class));
        verifyNoMoreInteractions(mDeferredMetricsReader);

        // THEN the user provisioning state is finalized
        verify(mHelper).markUserProvisioningStateFinalized(params);

        // THEN provisioning successful intent should be sent to the dpc.
        verifyDpcLaunchedForUser(UserHandle.of(UserHandle.myUserId()));

        // THEN a broadcast was sent to the primary user
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).sendBroadcast(intentCaptor.capture());

        verify(mNotificationHelper).showPrivacyReminderNotification(eq(mContext), anyInt());

        // THEN the intent should be ACTION_PROFILE_PROVISIONING_COMPLETE
        assertEquals(ACTION_PROFILE_PROVISIONING_COMPLETE, intentCaptor.getValue().getAction());
        // THEN the intent should be sent to the admin receiver
        assertEquals(TEST_MDM_ADMIN, intentCaptor.getValue().getComponent());
        // THEN the admin extras bundle should contain mdm extras
        assertExtras(intentCaptor.getValue());
    }

    private void verifyDpcLaunchedForUser(UserHandle userHandle) {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startActivityAsUser(intentCaptor.capture(), eq(userHandle));
        // THEN the intent should be ACTION_PROVISIONING_SUCCESSFUL
        assertEquals(ACTION_PROVISIONING_SUCCESSFUL, intentCaptor.getValue().getAction());
        // THEN the intent should only be sent to the dpc
        assertEquals(TEST_MDM_PACKAGE_NAME, intentCaptor.getValue().getPackage());
        // THEN the admin extras bundle should contain mdm extras
        assertExtras(intentCaptor.getValue());
    }

    private void verifySendDpcServiceStarted() {
        ArgumentCaptor<Intent> intentCaptor = ArgumentCaptor.forClass(Intent.class);
        verify(mContext).startService(intentCaptor.capture());
        // THEN the intent should launch the SendDpcBroadcastService
        assertEquals(SendDpcBroadcastService.class.getName(),
                intentCaptor.getValue().getComponent().getClassName());
        // THEN the service extras should contain mdm extras
        assertSendDpcBroadcastServiceParams(intentCaptor.getValue());
    }

    private void assertExtras(Intent intent) {
        assertTrue(TestUtils.bundleEquals(TEST_MDM_EXTRA_BUNDLE,
                (PersistableBundle) intent.getExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE)));
    }

    private void assertSendDpcBroadcastServiceParams(Intent intent) {
        final ProvisioningParams expectedParams =
                createProvisioningParams(ACTION_PROVISION_MANAGED_PROFILE);
        final ProvisioningParams actualParams =
                intent.getParcelableExtra(EXTRA_PROVISIONING_PARAMS);
        assertThat(actualParams).isEqualTo(expectedParams);
    }

    private ProvisioningParams createProvisioningParams(String action) {
        return new ProvisioningParams.Builder()
                .setDeviceAdminComponentName(TEST_MDM_ADMIN)
                .setProvisioningAction(action)
                .setAdminExtrasBundle(TEST_MDM_EXTRA_BUNDLE)
                .build();
    }
}
