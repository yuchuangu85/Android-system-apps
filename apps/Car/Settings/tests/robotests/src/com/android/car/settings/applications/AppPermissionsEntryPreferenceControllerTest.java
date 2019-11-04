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

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PermissionGroupInfo;
import android.content.pm.PermissionInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowPackageManager;

/** Unit test for {@link AppPermissionsEntryPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class AppPermissionsEntryPreferenceControllerTest {

    private Context mContext;
    private Preference mPreference;
    private AppPermissionsEntryPreferenceController mController;

    private PermissionInfo mPermLocation;
    private PermissionInfo mPermMic;
    private PermissionInfo mPermCamera;
    private PermissionInfo mPermSms;
    private PermissionInfo mPermContacts;
    private PermissionInfo mPermPhone;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        PreferenceControllerTestHelper<AppPermissionsEntryPreferenceController> controllerHelper =
                new PreferenceControllerTestHelper<>(mContext,
                        AppPermissionsEntryPreferenceController.class, mPreference);
        mController = controllerHelper.getController();
        controllerHelper.markState(Lifecycle.State.STARTED);

        mPermLocation = new PermissionInfo();
        mPermLocation.name = "Permission1";
        mPermLocation.group = "android.permission-group.LOCATION";
        PermissionGroupInfo groupLocation = new PermissionGroupInfo();
        groupLocation.name = mPermLocation.group;
        groupLocation.nonLocalizedLabel = "Location";
        getShadowPackageManager().addPermissionGroupInfo(groupLocation);

        mPermMic = new PermissionInfo();
        mPermMic.name = "Permission2";
        mPermMic.group = "android.permission-group.MICROPHONE";
        PermissionGroupInfo groupMic = new PermissionGroupInfo();
        groupMic.name = mPermMic.group;
        groupMic.nonLocalizedLabel = "Microphone";
        getShadowPackageManager().addPermissionGroupInfo(groupMic);

        mPermCamera = new PermissionInfo();
        mPermCamera.name = "Permission3";
        mPermCamera.group = "android.permission-group.CAMERA";
        PermissionGroupInfo groupCamera = new PermissionGroupInfo();
        groupCamera.name = mPermCamera.group;
        groupCamera.nonLocalizedLabel = "Camera";
        getShadowPackageManager().addPermissionGroupInfo(groupCamera);

        mPermSms = new PermissionInfo();
        mPermSms.name = "Permission4";
        mPermSms.group = "android.permission-group.SMS";
        PermissionGroupInfo groupSms = new PermissionGroupInfo();
        groupSms.name = mPermSms.group;
        groupSms.nonLocalizedLabel = "Sms";
        getShadowPackageManager().addPermissionGroupInfo(groupSms);

        mPermContacts = new PermissionInfo();
        mPermContacts.name = "Permission5";
        mPermContacts.group = "android.permission-group.CONTACTS";
        PermissionGroupInfo groupContacts = new PermissionGroupInfo();
        groupContacts.name = mPermContacts.group;
        groupContacts.nonLocalizedLabel = "Contacts";
        getShadowPackageManager().addPermissionGroupInfo(groupContacts);

        mPermPhone = new PermissionInfo();
        mPermPhone.name = "Permission6";
        mPermPhone.group = "android.permission-group.PHONE";
        PermissionGroupInfo groupPhone = new PermissionGroupInfo();
        groupPhone.name = mPermPhone.group;
        groupPhone.nonLocalizedLabel = "Phone";

        getShadowPackageManager().addPermissionGroupInfo(groupPhone);
    }


    @Test
    public void refreshUi_noGrantedPermissions_setsNullSummary() {
        mController.refreshUi();

        assertThat(mPreference.getSummary()).isNull();
    }

    @Test
    public void refreshUi_grantedPermissions_aboveMax_setsSummary_maxCountDefinedByController() {
        setupPackageWithPermissions(mPermLocation, mPermMic, mPermCamera, mPermSms);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo(
                "Apps using location, microphone, and camera");
    }

    @Test
    public void refreshUi_grantedPermissions_setsSummary_inOrderDefinedByController() {
        setupPackageWithPermissions(mPermPhone, mPermMic, mPermContacts, mPermSms);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo("Apps using microphone, sms, and contacts");
    }

    @Test
    public void refreshUi_grantedPermissions_onlyTwo_setsSummary() {
        setupPackageWithPermissions(mPermLocation, mPermCamera);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo("Apps using location and camera");
    }

    @Test
    public void refreshUi_grantedPermissions_onlyOne_setsSummary() {
        setupPackageWithPermissions(mPermCamera);

        mController.refreshUi();

        assertThat(mPreference.getSummary()).isEqualTo("Apps using camera");
    }

    private void setupPackageWithPermissions(PermissionInfo... permissions) {
        PackageInfo info = new PackageInfo();
        info.packageName = "fake.package.name";
        info.permissions = permissions;
        getShadowPackageManager().addPackage(info);
    }

    private ShadowPackageManager getShadowPackageManager() {
        return Shadow.extract(mContext.getPackageManager());
    }
}
