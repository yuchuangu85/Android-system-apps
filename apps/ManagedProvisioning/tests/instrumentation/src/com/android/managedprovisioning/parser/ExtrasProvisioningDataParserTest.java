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
package com.android.managedprovisioning.parser;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE;
import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_USER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION;

import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMERS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_CONTENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_DISCLAIMER_HEADER;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ORGANIZATION_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_SETUP;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SUPPORT_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_TIME_ZONE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_USE_MOBILE_DATA;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_DOMAIN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_EAP_METHOD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_HIDDEN;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_IDENTITY;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PAC_URL;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PASSWORD;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PHASE2_AUTH;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_HOST;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_PROXY_PORT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_SSID;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE;
import static android.nfc.NfcAdapter.ACTION_NDEF_DISCOVERED;
import static com.android.managedprovisioning.TestUtils.createTestAdminExtras;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMERS_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCALE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCAL_TIME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOGO_URI_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_MAIN_COLOR_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SKIP_USER_CONSENT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SKIP_USER_SETUP_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_SUPPORT_URL_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_TIME_ZONE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_SSID_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.accounts.Account;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;
import android.test.AndroidTestCase;
import android.test.suitebuilder.annotation.SmallTest;
import android.util.Base64;
import com.android.managedprovisioning.common.Globals;
import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Stream;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Tests for {@link ExtrasProvisioningDataParser}. */
@SmallTest
public class ExtrasProvisioningDataParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final ComponentName TEST_COMPONENT_NAME_2 =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc2/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final boolean TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = true;
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_SKIP_USER_CONSENT = true;
    private static final boolean TEST_KEEP_ACCOUNT_MIGRATED = true;
    private static final boolean TEST_SKIP_USER_SETUP = true;
    private static final long TEST_PROVISIONING_ID = 1000L;
    private static final Account TEST_ACCOUNT_TO_MIGRATE =
            new Account("user@gmail.com", "com.google");
    private static final String TEST_SHARED_PREFERENCE = "ExtrasProvisioningDataParserTest";
    private static final String TEST_DEVICE_ADMIN_PACKAGE_LABEL = "TestPackage";
    private static final String TEST_ORGANIZATION_NAME = "TestOrganizationName";
    private static final String TEST_SUPPORT_URL = "https://www.support.url/";
    private static final String TEST_ILL_FORMED_LOCALE = "aaa_";

    // Wifi info
    private static final String TEST_SSID = "TestWifi";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_SECURITY_TYPE_EAP = "EAP";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
    private static final String TEST_EAP_METHOD = "TTLS";
    private static final String TEST_PHASE2_AUTH = "PAP";
    private static final String TEST_CA_CERT = "certificate";
    private static final String TEST_USER_CERT = "certificate";
    private static final String TEST_IDENTITY = "TestUser";
    private static final String TEST_ANONYMOUS_IDENTITY = "TestAUser";
    private static final String TEST_DOMAIN = "google.com";
    private static final WifiInfo TEST_WIFI_INFO = WifiInfo.Builder.builder()
            .setSsid(TEST_SSID)
            .setHidden(TEST_HIDDEN)
            .setSecurityType(TEST_SECURITY_TYPE)
            .setPassword(TEST_PASSWORD)
            .setProxyHost(TEST_PROXY_HOST)
            .setProxyPort(TEST_PROXY_PORT)
            .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
            .setPacUrl(TEST_PAC_URL)
            .build();

    // Device admin package download info
    private static final String TEST_DOWNLOAD_LOCATION =
            "http://example/dpc.apk";
    private static final String TEST_COOKIE_HEADER =
            "Set-Cookie: sessionToken=foobar; Expires=Thu, 18 Feb 2016 23:59:59 GMT";
    private static final byte[] TEST_PACKAGE_CHECKSUM = new byte[] { '1', '2', '3', '4', '5' };
    private static final byte[] TEST_SIGNATURE_CHECKSUM = new byte[] { '5', '4', '3', '2', '1' };
    private static final int TEST_MIN_SUPPORT_VERSION = 17689;
    private static final PackageDownloadInfo TEST_DOWNLOAD_INFO =
            PackageDownloadInfo.Builder.builder()
                    .setLocation(TEST_DOWNLOAD_LOCATION)
                    .setCookieHeader(TEST_COOKIE_HEADER)
                    .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                    .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                    .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                    .build();
    private static final boolean TEST_USE_MOBILE_DATA = true;
    private static final Uri TEST_URI = Uri.parse("https://www.google.com/");
    private static final String TEST_DISCLAMER_HEADER = "Google";

    @Mock
    private Context mContext;

    @Mock
    private DevicePolicyManager mDpm;

    @Mock
    private ManagedProvisioningSharedPreferences mSharedPreferences;

    private ExtrasProvisioningDataParser mExtrasProvisioningDataParser;

    private Utils mUtils;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);
        when(mContext.getSystemServiceName(DevicePolicyManager.class))
                .thenReturn(Context.DEVICE_POLICY_SERVICE);
        when(mContext.getSystemService(Context.DEVICE_POLICY_SERVICE)).thenReturn(mDpm);
        when(mContext.getContentResolver()).thenReturn(getContext().getContentResolver());
        when(mContext.getFilesDir()).thenReturn(getContext().getFilesDir());
        when(mSharedPreferences.incrementAndGetProvisioningId()).thenReturn(TEST_PROVISIONING_ID);
        mUtils = spy(new Utils());
        mExtrasProvisioningDataParser = new ExtrasProvisioningDataParser(mContext, mUtils,
                mSharedPreferences);
    }

    public void testParse_trustedSourceProvisioningIntent() throws Exception {
        // GIVEN a ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL,
                        TEST_DEVICE_ADMIN_PACKAGE_LABEL)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE is translated to
                        // ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminDownloadInfo(TEST_DOWNLOAD_INFO)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        // THEN customizable color is not supported.
                        .setMainColor(ProvisioningParams.DEFAULT_MAIN_COLOR)
                        // THEN the trusted source is set to true.
                        .setStartedByTrustedSource(true)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN skipping user consent flag is ignored
                        .setSkipUserConsent(false)
                        // THEN keep account migrated flag is ignored
                        .setKeepAccountMigrated(false)
                        .setLeaveAllSystemAppsEnabled(true)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .setDeviceAdminLabel(TEST_DEVICE_ADMIN_PACKAGE_LABEL)
                        .setOrganizationName(TEST_ORGANIZATION_NAME)
                        .setSupportUrl(TEST_SUPPORT_URL)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_resumeProvisioningIntent() throws Exception {
        // GIVEN a ProvisioningParams stored in an intent
        ProvisioningParams expected = ProvisioningParams.Builder.builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .build();
        Intent intent = new Intent(Globals.ACTION_RESUME_PROVISIONING)
                .putExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS, expected);
        // WHEN the intent is parsed by the parser
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
        // THEN we get back the original ProvisioningParams.
        assertThat(expected).isEqualTo(params);
    }

    public void testParse_managedProfileIntent() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForTestPackageName();

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_PROFILE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN leave all system apps flag is ignored
                        .setLeaveAllSystemAppsEnabled(false)
                        // THEN skipping user consent flag is ignored
                        .setSkipUserConsent(false)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedProfileIntent_CompProvisioning() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = buildTestManagedProfileIntent();

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForTestPackageName();

        // GIVEN the device admin is also device owner in primary user.
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(TEST_COMPONENT_NAME);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_PROFILE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setSkipUserConsent(TEST_SKIP_USER_CONSENT)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedProfileIntent_DeviceOwnerWithByodProvisioning() throws Exception {
        // GIVEN a managed profile provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED);

        // GIVEN the device admin is installed.
        mockInstalledDeviceAdminForNullPackageName();

        // GIVEN a different device admin is a device owner in primary user.
        when(mDpm.getDeviceOwnerComponentOnCallingUser()).thenReturn(TEST_COMPONENT_NAME_2);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN skipping user consent flag is ignored
                        .setSkipUserConsent(false)
                        .setKeepAccountMigrated(TEST_KEEP_ACCOUNT_MIGRATED)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedUserIntent() throws Exception {
        // GIVEN a managed user provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_USER)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_USER
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_USER)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported in Managed User
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN device admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN wifi info is not supported.
                        .setWifiInfo(null)
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_shortExtras_sameAsLongExtras() throws Exception {
        assertThat(mExtrasProvisioningDataParser.parse(buildIntentWithAllLongExtras()))
            .isEqualTo(mExtrasProvisioningDataParser.parse(buildIntentWithAllShortExtras()));
    }

    public void testParse_managedDeviceIntent() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN Device Admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN time, time zone and locale are not supported.
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setLeaveAllSystemAppsEnabled(true)
                        // THEN wifi configuration is not supported.
                        .setWifiInfo(null)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_managedSharableDeviceIntent() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN provisioning action is ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        // THEN device admin package name is not supported in Device Owner
                        // provisioning.
                        .setDeviceAdminPackageName(null)
                        // THEN Device Admin download info is not supported.
                        .setDeviceAdminDownloadInfo(null)
                        // THEN time, time zone and locale are not supported.
                        .setMainColor(TEST_MAIN_COLOR)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        // THEN wifi configuration is not supported.
                        .setWifiInfo(null)
                        .setAdminExtrasBundle(createTestAdminExtras())
                        .setAccountToMigrate(TEST_ACCOUNT_TO_MIGRATE)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_nfcProvisioningIntentThrowsException() {
        // GIVEN a NFC provisioning intent and other extras.
        Intent intent = new Intent(ACTION_NDEF_DISCOVERED)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
            fail("ExtrasProvisioningDataParser doesn't support NFC intent. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testParse_illFormedLocaleThrowsException() throws Exception {
        // GIVEN a managed device provisioning intent and other extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                // GIVEN a device admin package name and component name
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                // GIVEN a ill formed locale string.
                .putExtras(getTestTimeTimeZoneAndLocaleExtras(TEST_ILL_FORMED_LOCALE))
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);
            fail("ExtrasProvisioningDataParser parsing an ill formed locale string. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testSetUseMobileData_forManagedProfile_alwaysFalse() throws Exception {
        Intent intent =
                buildTestManagedProfileIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testSetUseMobileData_fromTrustedSource_toFalse() throws Exception {
        Intent intent =
                buildTestTrustedSourceIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isTrue();
    }

    public void testSetUseMobileData_fromTrustedSource_toTrue() throws Exception {
        Intent intent =
                buildTestTrustedSourceIntent().putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, true);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isTrue();
    }

    public void testSetUseMobileData_fromTrustedSource_defaultsToFalse() throws Exception {
        Intent intent = buildTestTrustedSourceIntent();
        intent.removeExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA);
        mockInstalledDeviceAdminForTestPackageName();

        assertThat(mExtrasProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testParse_WifiInfoWithCertificates() throws Exception {
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID)
                .putExtra(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN)
                .putExtra(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE_EAP)
                .putExtra(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD)
                .putExtra(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD)
                .putExtra(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH)
                .putExtra(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT)
                .putExtra(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT)
                .putExtra(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY)
                .putExtra(EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY)
                .putExtra(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT)
                .putExtra(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS)
                .putExtra(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);

        ProvisioningParams params = mExtrasProvisioningDataParser.parse(intent);

        assertThat(params).isEqualTo(createTestProvisioningParamsBuilder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setStartedByTrustedSource(true)
                .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                .setWifiInfo(WifiInfo.Builder.builder()
                        .setSsid(TEST_SSID)
                        .setHidden(TEST_HIDDEN)
                        .setSecurityType(TEST_SECURITY_TYPE_EAP)
                        .setPassword(TEST_PASSWORD)
                        .setEapMethod(TEST_EAP_METHOD)
                        .setPhase2Auth(TEST_PHASE2_AUTH)
                        .setCaCertificate(TEST_CA_CERT)
                        .setUserCertificate(TEST_USER_CERT)
                        .setIdentity(TEST_IDENTITY)
                        .setAnonymousIdentity(TEST_ANONYMOUS_IDENTITY)
                        .setDomain(TEST_DOMAIN)
                        .setProxyHost(TEST_PROXY_HOST)
                        .setProxyPort(TEST_PROXY_PORT)
                        .setProxyBypassHosts(TEST_PROXY_BYPASS_HOSTS)
                        .setPacUrl(TEST_PAC_URL)
                        .build())
                .build());
    }

    public void testShortNamesOfExtrasAreUnique() {
        assertEquals(buildAllShortExtras().distinct().count(), buildAllShortExtras().count());
    }

    private Stream<Field> buildAllShortExtras() {
        Field[] fields = ExtrasProvisioningDataParser.class.getDeclaredFields();
        return Arrays.stream(fields)
                .filter(field -> field.getName().startsWith("EXTRA_")
                        && field.getName().endsWith("_SHORT"));
    }

    private ProvisioningParams.Builder createTestProvisioningParamsBuilder() {
        return ProvisioningParams.Builder.builder().setProvisioningId(TEST_PROVISIONING_ID);
    }

    private Intent buildIntentWithAllShortExtras() {
        Bundle bundleShort = new Bundle();
        bundleShort.putString(
                EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT, TEST_DISCLAMER_HEADER);
        bundleShort.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT, TEST_URI);
        Parcelable[] parcelablesShort = {bundleShort};
        return new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT,
                        TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT,
                        TEST_COMPONENT_NAME)
                .putExtras(getShortTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getAllShortTestWifiInfoExtras())
                .putExtras(getShortTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT,
                        createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR_SHORT, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT,
                        TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT,
                        TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL_SHORT, TEST_SUPPORT_URL)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL_SHORT,
                        TEST_DEVICE_ADMIN_PACKAGE_LABEL)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT_SHORT,
                        TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT,
                        TEST_USE_MOBILE_DATA)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_SETUP_SHORT,
                        ExtrasProvisioningDataParserTest.TEST_SKIP_USER_SETUP)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI_SHORT, TEST_URI)
                .putExtra(EXTRA_PROVISIONING_LOGO_URI_SHORT, TEST_URI)
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS_SHORT, parcelablesShort);
    }

    private Intent buildIntentWithAllLongExtras() {
        Bundle bundleLong = new Bundle();
        bundleLong.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, TEST_DISCLAMER_HEADER);
        bundleLong.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, TEST_URI);
        Parcelable[] parcelablesLong = {bundleLong};
        return new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getAllTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL,
                        TEST_DEVICE_ADMIN_PACKAGE_LABEL)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_USE_MOBILE_DATA, TEST_USE_MOBILE_DATA)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_SETUP,
                        ExtrasProvisioningDataParserTest.TEST_SKIP_USER_SETUP)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI, TEST_URI)
                .putExtra(EXTRA_PROVISIONING_LOGO_URI, TEST_URI)
                .putExtra(EXTRA_PROVISIONING_DISCLAIMERS, parcelablesLong);
    }

    private static Intent buildTestManagedProfileIntent() {
        return new Intent(ACTION_PROVISION_MANAGED_PROFILE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);
    }

    private static Intent buildTestTrustedSourceIntent() {
        return  new Intent(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtras(getTestTimeTimeZoneAndLocaleExtras())
                .putExtras(getTestWifiInfoExtras())
                .putExtras(getTestDeviceAdminDownloadExtras())
                .putExtra(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, createTestAdminExtras())
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT)
                .putExtra(EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED)
                .putExtra(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                        TEST_LEAVE_ALL_SYSTEM_APP_ENABLED)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL,
                        TEST_DEVICE_ADMIN_PACKAGE_LABEL)
                .putExtra(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME)
                .putExtra(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);
    }

    private static Bundle getTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private static Bundle getAllTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private static Bundle getAllShortTestWifiInfoExtras() {
        Bundle wifiInfoExtras = new Bundle();
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_SSID_SHORT, TEST_SSID);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT, TEST_SECURITY_TYPE);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT, TEST_PASSWORD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT, TEST_EAP_METHOD);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT, TEST_PHASE2_AUTH);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT, TEST_CA_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT, TEST_USER_CERT);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT, TEST_IDENTITY);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT, TEST_ANONYMOUS_IDENTITY);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT, TEST_DOMAIN);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT, TEST_PROXY_HOST);
        wifiInfoExtras.putString(
                EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT, TEST_PROXY_BYPASS_HOSTS);
        wifiInfoExtras.putString(EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT, TEST_PAC_URL);
        wifiInfoExtras.putInt(EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT, TEST_PROXY_PORT);
        wifiInfoExtras.putBoolean(EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT, TEST_HIDDEN);
        return wifiInfoExtras;
    }

    private void mockInstalledDeviceAdminForTestPackageName()
            throws IllegalProvisioningArgumentException {
        mockInstalledDeviceAdmin(TEST_PACKAGE_NAME);
    }

    private void mockInstalledDeviceAdminForNullPackageName()
            throws IllegalProvisioningArgumentException {
        mockInstalledDeviceAdmin(null);
    }

    private void mockInstalledDeviceAdmin(String packageName)
            throws IllegalProvisioningArgumentException {
        doReturn(TEST_COMPONENT_NAME)
                .when(mUtils)
                .findDeviceAdmin(packageName, TEST_COMPONENT_NAME, mContext, UserHandle.myUserId());
    }

    private static String buildTestLocaleString() {
        return StoreUtils.localeToString(TEST_LOCALE);
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtras() {
        return getTestTimeTimeZoneAndLocaleExtrasInternal(buildTestLocaleString());
    }

    private static Bundle getShortTestTimeTimeZoneAndLocaleExtras() {
        return getShortTestTimeTimeZoneAndLocaleExtrasInternal(buildTestLocaleString());
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtras(String locale) {
        return getTestTimeTimeZoneAndLocaleExtrasInternal(locale);
    }

    private static Bundle getTestTimeTimeZoneAndLocaleExtrasInternal(String locale){
        Bundle timeTimezoneAndLocaleExtras = new Bundle();
        timeTimezoneAndLocaleExtras.putLong(EXTRA_PROVISIONING_LOCAL_TIME, TEST_LOCAL_TIME);
        timeTimezoneAndLocaleExtras.putString(EXTRA_PROVISIONING_TIME_ZONE, TEST_TIME_ZONE);
        timeTimezoneAndLocaleExtras.putString(EXTRA_PROVISIONING_LOCALE, locale);
        return timeTimezoneAndLocaleExtras;
    }

    private static Bundle getShortTestTimeTimeZoneAndLocaleExtrasInternal(String locale){
        Bundle timeTimezoneAndLocaleExtras = new Bundle();
        timeTimezoneAndLocaleExtras.putLong(
                EXTRA_PROVISIONING_LOCAL_TIME_SHORT, TEST_LOCAL_TIME);
        timeTimezoneAndLocaleExtras.putString(
                EXTRA_PROVISIONING_TIME_ZONE_SHORT, TEST_TIME_ZONE);
        timeTimezoneAndLocaleExtras.putString(
                EXTRA_PROVISIONING_LOCALE_SHORT, locale);
        return timeTimezoneAndLocaleExtras;
    }

    private static Bundle getTestDeviceAdminDownloadExtras() {
        Bundle downloadInfoExtras = new Bundle();
        downloadInfoExtras.putInt(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE, TEST_MIN_SUPPORT_VERSION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION, TEST_DOWNLOAD_LOCATION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER, TEST_COOKIE_HEADER);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                buildTestPackageChecksum());
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                buildTestSignatureChecksum());
        return downloadInfoExtras;
    }

    private static String buildTestPackageChecksum() {
        return Base64.encodeToString(TEST_PACKAGE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static Bundle getShortTestDeviceAdminDownloadExtras() {
        Bundle downloadInfoExtras = new Bundle();
        downloadInfoExtras.putInt(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT,
                TEST_MIN_SUPPORT_VERSION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT,
                TEST_DOWNLOAD_LOCATION);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT,
                TEST_COOKIE_HEADER);
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT,
                buildTestPackageChecksum());
        downloadInfoExtras.putString(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT,
                buildTestSignatureChecksum());
        return downloadInfoExtras;
    }

    private static String buildTestSignatureChecksum() {
        return Base64.encodeToString(TEST_SIGNATURE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
