/*
 * Copyright 2019 The Android Open Source Project
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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.app.AlertDialog;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.Signature;
import android.content.pm.UserInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.UserHandle;
import android.os.UserManager;
import android.widget.Button;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowActivityManager;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;
import com.android.car.settings.testutils.ShadowDevicePolicyManager;
import com.android.car.settings.testutils.ShadowIconDrawableFactory;
import com.android.car.settings.testutils.ShadowPermissionControllerManager;
import com.android.car.settings.testutils.ShadowSmsApplication;
import com.android.car.settings.testutils.ShadowUserManager;
import com.android.car.settings.testutils.ShadowUtils;
import com.android.settingslib.Utils;
import com.android.settingslib.applications.ApplicationsState;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.annotation.Config;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.util.ReflectionHelpers;

import java.util.Arrays;
import java.util.Collections;

/** Unit test for {@link ApplicationDetailsFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {
        ShadowActivityManager.class,
        ShadowApplicationPackageManager.class,
        ShadowCarUserManagerHelper.class,
        ShadowDevicePolicyManager.class,
        ShadowIconDrawableFactory.class,
        ShadowPermissionControllerManager.class,
        ShadowSmsApplication.class,
        ShadowUserManager.class,
        ShadowUtils.class})
public class ApplicationDetailsFragmentTest {

    private static final String PACKAGE_NAME = "com.android.car.settings.test";

    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    private Context mContext;
    private TestActivity mActivity;
    private ActivityController<TestActivity> mController;
    private ApplicationDetailsFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        int userId = UserHandle.myUserId();
        UserInfo userInfo = new UserInfo();
        userInfo.id = userId;
        when(mCarUserManagerHelper.getCurrentProcessUserId()).thenReturn(userId);
        when(mCarUserManagerHelper.getAllUsers()).thenReturn(Collections.singletonList(userInfo));
        UserManager mockUserManager = mock(UserManager.class);
        when(mockUserManager.getUserInfo(userId)).thenReturn(userInfo);
        ShadowUserManager.setInstance(mockUserManager);

        mContext = RuntimeEnvironment.application;
        getShadowUserManager().addProfile(userId, userId, "profileName", /* profileFlags= */ 0);

        mActivity = new TestActivity();
        mController = ActivityController.of(mActivity);
        mController.create();

        mFragment = ApplicationDetailsFragment.getInstance(PACKAGE_NAME);
    }

    @After
    public void tearDown() {
        // Prevent caching from interfering across tests.
        ReflectionHelpers.setStaticField(ApplicationsState.class, "sInstance", null);
        ReflectionHelpers.setStaticField(Utils.class, "sSystemSignature", null);
        ShadowApplicationPackageManager.reset();
        ShadowCarUserManagerHelper.reset();
        ShadowDevicePolicyManager.reset();
        ShadowSmsApplication.reset();
        ShadowUserManager.reset();
        ShadowUtils.reset();
    }

    @Test
    public void onStart_packageNotExplicitlyStopped_enablesForceStopButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findForceStopButton(mActivity).isEnabled()).isTrue();
    }

    @Test
    public void onStart_packageHasActiveAdmins_disablesForceStopButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        getShadowDevicePolicyManager().setPackageHasActiveAdmins(
                PACKAGE_NAME, /* hasActiveAdmins= */ true);
        mController.start();

        assertThat(findForceStopButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_appsControlUserRestriction_disablesForceStopButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_APPS_CONTROL)).thenReturn(true);
        mController.start();

        assertThat(findForceStopButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_packageExplicitlyStopped_queriesPackageRestart() {
        int uid = 123;
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.uid = uid;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_STOPPED;

        mController.start();

        Intent broadcast = mActivity.getMostRecentOrderedBroadcastIntent();
        assertThat(broadcast).isNotNull();
        assertThat(broadcast.getAction()).isEqualTo(Intent.ACTION_QUERY_PACKAGE_RESTART);
        assertThat(broadcast.getStringArrayExtra(Intent.EXTRA_PACKAGES)).isEqualTo(
                new String[]{PACKAGE_NAME});
        assertThat(broadcast.getIntExtra(Intent.EXTRA_UID, 0)).isEqualTo(uid);
    }

    @Test
    public void onStart_packageExplicitlyStopped_success_enablesForceStopButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_STOPPED;
        mController.start();
        BroadcastReceiver receiver = mActivity.getMostRecentOrderedBroadcastResultReceiver();
        receiver.setPendingResult(
                new BroadcastReceiver.PendingResult(Activity.RESULT_OK,
                        "resultData",
                        /* resultExtras= */ null,
                        BroadcastReceiver.PendingResult.TYPE_UNREGISTERED,
                        /* ordered= */ true,
                        /* sticky= */ false,
                        /* token= */ null,
                        UserHandle.myUserId(),
                        /* flags= */ 0));
        receiver.onReceive(mContext, mActivity.getMostRecentOrderedBroadcastIntent());

        assertThat(findForceStopButton(mActivity).isEnabled()).isTrue();
    }

    @Test
    public void onStart_packageExplicitlyStopped_failure_disablesForceStopButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_STOPPED;
        mController.start();
        BroadcastReceiver receiver = mActivity.getMostRecentOrderedBroadcastResultReceiver();
        receiver.setPendingResult(
                new BroadcastReceiver.PendingResult(Activity.RESULT_CANCELED,
                        "resultData",
                        /* resultExtras= */ null,
                        BroadcastReceiver.PendingResult.TYPE_UNREGISTERED,
                        /* ordered= */ true,
                        /* sticky= */ false,
                        /* token= */ null,
                        UserHandle.myUserId(),
                        /* flags= */ 0));
        receiver.onReceive(mContext, mActivity.getMostRecentOrderedBroadcastIntent());

        assertThat(findForceStopButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_bundledApp_showsDisableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findDisableButton(mActivity).getText()).isEqualTo(
                mContext.getString(R.string.disable_text));
    }

    @Test
    public void onStart_bundledApp_notEnabled_showsEnableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        packageInfo.applicationInfo.enabled = false;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findDisableButton(mActivity).getText()).isEqualTo(
                mContext.getString(R.string.enable_text));
    }

    @Test
    public void onStart_bundledApp_enabled_disableUntilUsed_showsEnableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        packageInfo.applicationInfo.enabledSetting =
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findDisableButton(mActivity).getText()).isEqualTo(
                mContext.getString(R.string.enable_text));
    }

    @Test
    public void onStart_bundledApp_homePackage_disablesDisableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        getShadowPackageManager().addPackage(packageInfo);

        ResolveInfo homeActivity = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PACKAGE_NAME;
        homeActivity.activityInfo = activityInfo;

        getShadowPackageManager().setHomeActivities(Collections.singletonList(homeActivity));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findDisableButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_bundledApp_systemPackage_disablesDisableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        Signature[] signatures = new Signature[]{new Signature(PACKAGE_NAME.getBytes())};
        packageInfo.signatures = signatures;
        getShadowPackageManager().addPackage(packageInfo);

        PackageInfo systemPackage = createPackageInfoWithApplicationInfo("android");
        systemPackage.signatures = signatures;
        getShadowPackageManager().addPackage(systemPackage);

        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findDisableButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_bundledApp_enabledApp_keepEnabledPackage_disablesDisableButton() {
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);

        ShadowSmsApplication.setDefaultSmsApplication(new ComponentName(PACKAGE_NAME, "cls"));
        mController.start();

        assertThat(findDisableButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_appNotBundled_showsUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findUninstallButton(mActivity).getText()).isEqualTo(
                mContext.getString(R.string.uninstall_text));
    }

    @Test
    public void onStart_packageHasActiveAdmins_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        getShadowDevicePolicyManager().setPackageHasActiveAdmins(
                PACKAGE_NAME, /* hasActiveAdmins= */ true);
        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_deviceProvisioningPackage_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        ShadowUtils.setDeviceProvisioningPackage(PACKAGE_NAME);
        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_uninstallQueued_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        getShadowDevicePolicyManager().setIsUninstallInQueue(true);
        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_noDefaultHome_onlyHomeApp_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));

        ResolveInfo homeActivity = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PACKAGE_NAME;
        homeActivity.activityInfo = activityInfo;

        getShadowPackageManager().setHomeActivities(Collections.singletonList(homeActivity));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_noDefaultHome_multipleHomeApps_enablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));

        ResolveInfo homeActivity = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PACKAGE_NAME;
        homeActivity.activityInfo = activityInfo;

        ResolveInfo altHomeActivity = new ResolveInfo();
        ActivityInfo altActivityInfo = new ActivityInfo();
        altActivityInfo.packageName = PACKAGE_NAME + ".Someotherhome";
        altHomeActivity.activityInfo = altActivityInfo;

        getShadowPackageManager().setHomeActivities(Arrays.asList(homeActivity, altHomeActivity));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isTrue();
    }

    @Test
    public void onStart_defaultHomeApp_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));

        ResolveInfo homeActivity = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = PACKAGE_NAME;
        homeActivity.activityInfo = activityInfo;

        ResolveInfo altHomeActivity = new ResolveInfo();
        ActivityInfo altActivityInfo = new ActivityInfo();
        altActivityInfo.packageName = PACKAGE_NAME + ".Someotherhome";
        altHomeActivity.activityInfo = altActivityInfo;

        getShadowPackageManager().setHomeActivities(Arrays.asList(homeActivity, altHomeActivity));
        getShadowPackageManager().setDefaultHomeActivity(new ComponentName(PACKAGE_NAME, "cls"));
        mActivity.launchFragment(mFragment);

        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_appsControlUserRestriction_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_APPS_CONTROL)).thenReturn(true);
        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void onStart_uninstallAppsRestriction_disablesUninstallButton() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);

        when(mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_UNINSTALL_APPS)).thenReturn(true);
        mController.start();

        assertThat(findUninstallButton(mActivity).isEnabled()).isFalse();
    }

    @Test
    public void forceStopClicked_showsDialog() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);
        mController.start();

        findForceStopButton(mActivity).performClick();

        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                ApplicationDetailsFragment.FORCE_STOP_CONFIRM_DIALOG_TAG)).isInstanceOf(
                ConfirmationDialogFragment.class);
    }

    @Test
    public void forceStopDialogConfirmed_forceStopsPackage() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);
        mController.start();
        findForceStopButton(mActivity).performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertThat(getShadowActivityManager().getMostRecentlyStoppedPackage()).isEqualTo(
                PACKAGE_NAME);
    }

    @Test
    public void disableClicked_showsDialog() {
        // Use bundled app to get disable button.
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);
        mController.start();

        findDisableButton(mActivity).performClick();

        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                ApplicationDetailsFragment.DISABLE_CONFIRM_DIALOG_TAG)).isInstanceOf(
                ConfirmationDialogFragment.class);
    }

    @Test
    public void disableDialogConfirmed_disablesPackage() {
        // Use bundled app to get disable button.
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);
        mController.start();
        findDisableButton(mActivity).performClick();
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

        dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertThat(
                mContext.getPackageManager().getApplicationEnabledSetting(PACKAGE_NAME)).isEqualTo(
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
    }

    @Test
    public void enableClicked_enablesPackage() {
        // Use disabled bundled app to get enable button.
        PackageInfo packageInfo = createPackageInfoWithApplicationInfo(PACKAGE_NAME);
        packageInfo.applicationInfo.flags |= ApplicationInfo.FLAG_SYSTEM;
        packageInfo.applicationInfo.enabled = false;
        getShadowPackageManager().addPackage(packageInfo);
        mActivity.launchFragment(mFragment);
        mController.start();

        findDisableButton(mActivity).performClick();

        assertThat(
                mContext.getPackageManager().getApplicationEnabledSetting(PACKAGE_NAME)).isEqualTo(
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT);
    }

    @Test
    public void uninstallClicked_startsUninstallActivity() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);
        mController.start();

        findUninstallButton(mActivity).performClick();

        Intent intent = ShadowApplication.getInstance().getNextStartedActivity();
        assertThat(intent.getAction()).isEqualTo(Intent.ACTION_UNINSTALL_PACKAGE);
        assertThat(intent.getBooleanExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, false)).isTrue();
        assertThat(intent.getBooleanExtra(Intent.EXTRA_RETURN_RESULT, false)).isTrue();
        assertThat(intent.getData().toString()).isEqualTo("package:" + PACKAGE_NAME);
    }

    @Test
    public void processActivityResult_uninstall_resultOk_goesBack() {
        getShadowPackageManager().addPackage(createPackageInfoWithApplicationInfo(PACKAGE_NAME));
        mActivity.launchFragment(mFragment);
        mController.start();
        findUninstallButton(mActivity).performClick();

        mFragment.processActivityResult(ApplicationDetailsFragment.UNINSTALL_REQUEST_CODE,
                Activity.RESULT_OK, /* data= */ null);

        assertThat(mActivity.getOnBackPressedFlag()).isTrue();
    }

    private Button findForceStopButton(Activity activity) {
        return activity.findViewById(R.id.action_button2);
    }

    private Button findDisableButton(Activity activity) {
        // Same button is used with a different handler.  This method is purely for readability.
        return findUninstallButton(activity);
    }

    private Button findUninstallButton(Activity activity) {
        return activity.findViewById(R.id.action_button1);
    }

    private PackageInfo createPackageInfoWithApplicationInfo(String packageName) {
        PackageInfo packageInfo = new PackageInfo();
        packageInfo.packageName = packageName;
        packageInfo.applicationInfo = createApplicationInfo(packageName);
        return packageInfo;
    }

    private ApplicationInfo createApplicationInfo(String packageName) {
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.sourceDir =
                RuntimeEnvironment.getTempDirectory()
                        .createIfNotExists(applicationInfo.packageName + "-sourceDir")
                        .toAbsolutePath()
                        .toString();
        return applicationInfo;
    }

    private ShadowUserManager getShadowUserManager() {
        return Shadow.extract(UserManager.get(mContext));
    }

    private ShadowApplicationPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }

    private ShadowDevicePolicyManager getShadowDevicePolicyManager() {
        return Shadow.extract(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE));
    }

    private ShadowActivityManager getShadowActivityManager() {
        return Shadow.extract(mContext.getSystemService(Context.ACTIVITY_SERVICE));
    }

    /** We extend the test activity here to add functionality that isn't useful elsewhere. */
    private static class TestActivity extends BaseTestActivity {

        private Intent mOrderedBroadcastIntent;
        private BroadcastReceiver mOrderedBroadcastResultReceiver;

        @Override
        public void sendOrderedBroadcastAsUser(Intent intent, UserHandle user,
                String receiverPermission, BroadcastReceiver resultReceiver, Handler scheduler,
                int initialCode, String initialData, Bundle initialExtras) {
            mOrderedBroadcastIntent = intent;
            mOrderedBroadcastResultReceiver = resultReceiver;
        }

        Intent getMostRecentOrderedBroadcastIntent() {
            return mOrderedBroadcastIntent;
        }

        BroadcastReceiver getMostRecentOrderedBroadcastResultReceiver() {
            return mOrderedBroadcastResultReceiver;
        }
    }
}
