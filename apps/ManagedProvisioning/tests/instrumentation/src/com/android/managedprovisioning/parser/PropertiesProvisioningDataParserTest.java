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
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
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
import static android.app.admin.DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC;

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
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCALE_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOCAL_TIME_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_LOGO_URI_SHORT;
import static com.android.managedprovisioning.parser.ExtrasProvisioningDataParser.EXTRA_PROVISIONING_MAIN_COLOR_SHORT;
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

import static org.mockito.Mockito.when;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.nfc.NdefMessage;
import android.nfc.NdefRecord;
import android.nfc.NfcAdapter;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.PersistableBundle;
import android.test.AndroidTestCase;
import android.util.Base64;

import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Locale;
import java.util.Properties;

/** Tests for {@link PropertiesProvisioningDataParser} */
@SmallTest
public class PropertiesProvisioningDataParserTest extends AndroidTestCase {
    private static final String TEST_PACKAGE_NAME = "com.afwsamples.testdpc";
    private static final ComponentName TEST_COMPONENT_NAME =
            ComponentName.unflattenFromString(
                    "com.afwsamples.testdpc/com.afwsamples.testdpc.DeviceAdminReceiver");
    private static final long TEST_LOCAL_TIME = 1456939524713L;
    private static final Locale TEST_LOCALE = Locale.UK;
    private static final String TEST_TIME_ZONE = "GMT";
    private static final Integer TEST_MAIN_COLOR = 65280;
    private static final boolean TEST_STARTED_BY_TRUSTED_SOURCE = true;
    private static final String TEST_LEAVE_ALL_SYSTEM_APP_ENABLED = "true";
    private static final boolean TEST_SKIP_ENCRYPTION = true;
    private static final boolean TEST_USE_MOBILE_DATA = false;
    private static final boolean TEST_SKIP_USER_SETUP = true;
    private static final long TEST_PROVISIONING_ID = 2000L;
    private static final String TEST_ACCOUNT_TO_MIGRATE ="user@gmail.com";
    private static final String INVALID_MIME_TYPE = "invalid-mime-type";

    // Wifi info
    private static final String TEST_SSID = "TestWifi";
    private static final boolean TEST_HIDDEN = true;
    private static final String TEST_SECURITY_TYPE = "WPA2";
    private static final String TEST_SECURITY_TYPE_EAP = "EAP";
    private static final String TEST_PASSWORD = "GoogleRock";
    private static final String TEST_EAP_METHOD = "TTLS";
    private static final String TEST_PHASE2_AUTH = "PAP";
    private static final String TEST_CA_CERT = "caCertificate";
    private static final String TEST_USER_CERT = "userCertificate";
    private static final String TEST_IDENTITY = "TestUser";
    private static final String TEST_ANONYMOUS_IDENTITY = "TestAUser";
    private static final String TEST_DOMAIN = "google.com";
    private static final String TEST_PROXY_HOST = "testhost.com";
    private static final int TEST_PROXY_PORT = 7689;
    private static final String TEST_PROXY_BYPASS_HOSTS = "http://host1.com;https://host2.com";
    private static final String TEST_PAC_URL = "pac.test.com";
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
    private static final String TEST_KEEP_ACCOUNT_MIGRATED = "true";
    private static final String TEST_ORGANIZATION_NAME = "TestOrganizationName";
    private static final String TEST_SUPPORT_URL = "https://www.support.url/";
    private static final String TEST_DEVICE_ADMIN_PACKAGE_LABEL = "TestPackage";
    private static final String TEST_SKIP_USER_CONSENT = "true";
    private static final Uri TEST_URI = Uri.parse("https://www.google.com/");
    private static final String TEST_URI_STRING = "https://www.google.com/";
    private static final String TEST_DISCLAMER_HEADER = "Google";

    @Mock
    private Context mContext;

    @Mock
    private ManagedProvisioningSharedPreferences mSharedPreferences;

    private PropertiesProvisioningDataParser mPropertiesProvisioningDataParser;

    @Override
    public void setUp() {
        // this is necessary for mockito to work
        System.setProperty("dexmaker.dexcache", getContext().getCacheDir().toString());

        MockitoAnnotations.initMocks(this);

        when(mSharedPreferences.incrementAndGetProvisioningId()).thenReturn(TEST_PROVISIONING_ID);
        mPropertiesProvisioningDataParser = new PropertiesProvisioningDataParser(mContext,
                new Utils(), mSharedPreferences);
    }

    // TODO(alexkershaw): split this huge test into individual tests using
    // buildNfcProvisioningProperties and buildNfcProvisioningIntent.
    public void testParse_nfcProvisioningIntent() throws Exception {
        // GIVEN a NFC provisioning intent with all supported data.
        Properties props = new Properties();
        props.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        setTestWifiInfo(props);
        setTestTimeTimeZoneAndLocale(props);
        setTestDeviceAdminDownload(props);
        props.setProperty(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, getTestAdminExtrasString());
        props.setProperty(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                Boolean.toString(TEST_SKIP_ENCRYPTION));
        // GIVEN main color is supplied even though it is not supported in NFC provisioning.
        props.setProperty(EXTRA_PROVISIONING_MAIN_COLOR, Integer.toString(TEST_MAIN_COLOR));

        Intent intent = buildNfcProvisioningIntent(props);

        // WHEN the intent is parsed by the parser.
        ProvisioningParams params = mPropertiesProvisioningDataParser.parse(intent);


        // THEN ProvisionParams is constructed as expected.
        assertThat(
                ProvisioningParams.Builder.builder()
                        // THEN NFC provisioning is translated to ACTION_PROVISION_MANAGED_DEVICE
                        // action.
                        .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                        .setDeviceAdminComponentName(TEST_COMPONENT_NAME)
                        .setDeviceAdminPackageName(TEST_PACKAGE_NAME)
                        .setProvisioningId(TEST_PROVISIONING_ID)
                        .setDeviceAdminDownloadInfo(
                                PackageDownloadInfo.Builder.builder()
                                        .setLocation(TEST_DOWNLOAD_LOCATION)
                                        .setCookieHeader(TEST_COOKIE_HEADER)
                                        .setPackageChecksum(TEST_PACKAGE_CHECKSUM)
                                        .setSignatureChecksum(TEST_SIGNATURE_CHECKSUM)
                                        .setMinVersion(TEST_MIN_SUPPORT_VERSION)
                                        .build())
                        .setLocalTime(TEST_LOCAL_TIME)
                        .setLocale(TEST_LOCALE)
                        .setTimeZone(TEST_TIME_ZONE)
                        // THEN main color is not supported in NFC intent.
                        .setMainColor(null)
                        .setSkipEncryption(TEST_SKIP_ENCRYPTION)
                        .setWifiInfo(TEST_WIFI_INFO)
                        .setAdminExtrasBundle(getTestAdminExtrasPersistableBundle())
                        .setStartedByTrustedSource(true)
                        .setIsNfc(true)
                        .setIsOrganizationOwnedProvisioning(true)
                        .build())
                .isEqualTo(params);
    }

    public void testParse_shortExtras_sameAsLongExtras() throws Exception {
        assertThat(mPropertiesProvisioningDataParser.parse(buildIntentWithAllLongExtras()))
            .isEqualTo(mPropertiesProvisioningDataParser.parse(buildIntentWithAllShortExtras()));
    }

    public void testParse_nfcProvisioningIntent_setUseMobileDataTrue()
            throws Exception {
        Properties properties = buildNfcProvisioningProperties();
        properties.setProperty(EXTRA_PROVISIONING_USE_MOBILE_DATA, Boolean.toString(true));
        Intent intent = buildNfcProvisioningIntent(properties);

        assertThat(mPropertiesProvisioningDataParser.parse(intent).useMobileData).isTrue();
    }

    public void testParse_nfcProvisioningIntent_setUseMobileDataFalse()
            throws Exception {
        Properties properties = buildNfcProvisioningProperties();
        properties.setProperty(EXTRA_PROVISIONING_USE_MOBILE_DATA, Boolean.toString(false));
        Intent intent = buildNfcProvisioningIntent(properties);

        assertThat(mPropertiesProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testParse_nfcProvisioningIntent_useMobileDataDefaultsToFalse() throws Exception {
        Properties properties = buildNfcProvisioningProperties();
        properties.remove(EXTRA_PROVISIONING_USE_MOBILE_DATA);
        Intent intent = buildNfcProvisioningIntent(properties);

        assertThat(mPropertiesProvisioningDataParser.parse(intent).useMobileData).isFalse();
    }

    public void testParse_nfcProvisioning_wifiWithCertificates() throws Exception {
        Properties props = new Properties();
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        props.setProperty(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        props.setProperty(EXTRA_PROVISIONING_WIFI_HIDDEN, Boolean.toString(TEST_HIDDEN));
        props.setProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE_EAP);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH);
        props.setProperty(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY);
        props.setProperty(EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY);
        props.setProperty(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT, Integer.toString(TEST_PROXY_PORT));
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        Intent intent = buildNfcProvisioningIntent(props);

        ProvisioningParams params = mPropertiesProvisioningDataParser.parse(intent);

        assertThat(params).isEqualTo(createTestProvisioningParamsBuilder()
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

    private ProvisioningParams.Builder createTestProvisioningParamsBuilder() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningId(TEST_PROVISIONING_ID)
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE)
                .setStartedByTrustedSource(true)
                .setIsNfc(true)
                .setIsOrganizationOwnedProvisioning(true);
    }

    public void testParse_OtherIntentsThrowsException() {
        // GIVEN a managed device provisioning intent and some extras.
        Intent intent = new Intent(ACTION_PROVISION_MANAGED_DEVICE)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME)
                .putExtra(EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME, TEST_COMPONENT_NAME)
                .putExtra(EXTRA_PROVISIONING_SKIP_ENCRYPTION, TEST_SKIP_ENCRYPTION)
                .putExtra(EXTRA_PROVISIONING_MAIN_COLOR, TEST_MAIN_COLOR)
                .putExtra(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        try {
            // WHEN the intent is parsed by the parser.
            ProvisioningParams params = mPropertiesProvisioningDataParser.parse(intent);
            fail("PropertiesProvisioningDataParser doesn't support intent other than NFC. "
                    + "IllegalProvisioningArgumentException should be thrown");
        } catch (IllegalProvisioningArgumentException e) {
            // THEN IllegalProvisioningArgumentException is thrown.
        }
    }

    public void testGetFirstNdefRecord_nullNdefMessages() {
        // GIVEN nfc intent with no ndef messages
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED);
        // WHEN getting first ndef record
        // THEN it should return null
        assertThat(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent)).isNull();
    }

    public void testGetFirstNdefRecord_noNfcMimeType() {
        // GIVEN nfc intent with no ndf message with a record with a valid mime type.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NdefRecord record = NdefRecord.createMime(INVALID_MIME_TYPE, stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});
        // WHEN getting first ndef record
        // THEN it should return null
        assertThat(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent)).isNull();
    }

    public void testGetFirstNdefRecord() {
        // GIVEN nfc intent with valid ndf message with a record with mime type nfc.
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        NdefRecord record = NdefRecord.createMime(
                DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC,
                stream.toByteArray());
        NdefMessage ndfMsg = new NdefMessage(new NdefRecord[]{record});
        Intent nfcIntent = new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, new NdefMessage[]{ndfMsg});
        // WHEN getting first ndef record
        // THEN valid record should be returned
        assertThat(PropertiesProvisioningDataParser.getFirstNdefRecord(nfcIntent))
                .isEqualTo(record);
    }

    private Intent buildIntentWithAllShortExtras() throws Exception {
        Properties propsShort = new Properties();
        Bundle bundleShort = new Bundle();
        bundleShort.putString(
                EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT, TEST_DISCLAMER_HEADER);
        bundleShort.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT, TEST_URI);
        Parcelable[] parcelablesShort = {bundleShort};
        propsShort.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT, TEST_PACKAGE_NAME);
        propsShort.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT,
                TEST_COMPONENT_NAME.flattenToString());
        setAllShortTestWifiInfo(propsShort);
        setShortTestTimeTimeZoneAndLocale(propsShort);
        setShortTestDeviceAdminDownload(propsShort);
        propsShort.setProperty(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT, getTestAdminExtrasString());
        propsShort.setProperty(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT,
                Boolean.toString(TEST_SKIP_ENCRYPTION));
        propsShort.setProperty(
                EXTRA_PROVISIONING_MAIN_COLOR_SHORT, Integer.toString(TEST_MAIN_COLOR));
        propsShort.setProperty(EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT,
                TEST_LEAVE_ALL_SYSTEM_APP_ENABLED);
        propsShort.setProperty(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT, TEST_ACCOUNT_TO_MIGRATE);
        propsShort.setProperty(
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT,
                TEST_KEEP_ACCOUNT_MIGRATED);
        propsShort.setProperty(
                EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT, TEST_ORGANIZATION_NAME);
        propsShort.setProperty(EXTRA_PROVISIONING_SUPPORT_URL_SHORT, TEST_SUPPORT_URL);
        propsShort.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL_SHORT,
                TEST_DEVICE_ADMIN_PACKAGE_LABEL);
        propsShort.setProperty(
                EXTRA_PROVISIONING_SKIP_USER_CONSENT_SHORT, TEST_SKIP_USER_CONSENT);
        propsShort.setProperty(
                EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT,
                Boolean.toString(TEST_USE_MOBILE_DATA));
        propsShort.setProperty(
                EXTRA_PROVISIONING_SKIP_USER_SETUP_SHORT,
                Boolean.toString(TEST_SKIP_USER_SETUP));
        propsShort.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI_SHORT, TEST_URI_STRING);
        propsShort.setProperty(EXTRA_PROVISIONING_LOGO_URI_SHORT, TEST_URI_STRING);

        Intent intentShort = buildNfcProvisioningIntent(propsShort);
        intentShort.putExtra(EXTRA_PROVISIONING_DISCLAIMERS_SHORT, parcelablesShort);
        return intentShort;
    }

    private Intent buildIntentWithAllLongExtras() throws Exception {
        Properties propsLong = new Properties();
        Bundle bundleLong = new Bundle();
        bundleLong.putString(EXTRA_PROVISIONING_DISCLAIMER_HEADER, TEST_DISCLAMER_HEADER);
        bundleLong.putParcelable(EXTRA_PROVISIONING_DISCLAIMER_CONTENT, TEST_URI);
        Parcelable[] parcelablesLong = {bundleLong};
        propsLong.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME);
        propsLong.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        setAllTestWifiInfo(propsLong);
        setTestTimeTimeZoneAndLocale(propsLong);
        setTestDeviceAdminDownload(propsLong);
        propsLong.setProperty(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, getTestAdminExtrasString());
        propsLong.setProperty(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                Boolean.toString(TEST_SKIP_ENCRYPTION));
        propsLong.setProperty(EXTRA_PROVISIONING_MAIN_COLOR, Integer.toString(TEST_MAIN_COLOR));
        propsLong.setProperty(
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                TEST_LEAVE_ALL_SYSTEM_APP_ENABLED);
        propsLong.setProperty(EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, TEST_ACCOUNT_TO_MIGRATE);

        propsLong.setProperty(
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION, TEST_KEEP_ACCOUNT_MIGRATED);
        propsLong.setProperty(EXTRA_PROVISIONING_ORGANIZATION_NAME, TEST_ORGANIZATION_NAME);
        propsLong.setProperty(EXTRA_PROVISIONING_SUPPORT_URL, TEST_SUPPORT_URL);
        propsLong.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL, TEST_DEVICE_ADMIN_PACKAGE_LABEL);
        propsLong.setProperty(EXTRA_PROVISIONING_SKIP_USER_CONSENT, TEST_SKIP_USER_CONSENT);
        propsLong.setProperty(
                EXTRA_PROVISIONING_USE_MOBILE_DATA, Boolean.toString(TEST_USE_MOBILE_DATA));
        propsLong.setProperty(
                EXTRA_PROVISIONING_SKIP_USER_SETUP, Boolean.toString(TEST_SKIP_USER_SETUP));
        propsLong.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI, TEST_URI_STRING);
        propsLong.setProperty(EXTRA_PROVISIONING_LOGO_URI, TEST_URI_STRING);

        Intent intentLong = buildNfcProvisioningIntent(propsLong);
        intentLong.putExtra(EXTRA_PROVISIONING_DISCLAIMERS, parcelablesLong);
        return intentLong;
    }

    private static Properties setTestWifiInfo(Properties props) {
        props.setProperty(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        props.setProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT, Integer.toString(TEST_PROXY_PORT));
        props.setProperty(EXTRA_PROVISIONING_WIFI_HIDDEN, Boolean.toString(TEST_HIDDEN));
        return props;
    }

    private static Properties setAllTestWifiInfo(Properties props) {
        props.setProperty(EXTRA_PROVISIONING_WIFI_SSID, TEST_SSID);
        props.setProperty(EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, TEST_SECURITY_TYPE);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PASSWORD, TEST_PASSWORD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_EAP_METHOD, TEST_EAP_METHOD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, TEST_PHASE2_AUTH);
        props.setProperty(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE, TEST_CA_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE, TEST_USER_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_IDENTITY, TEST_IDENTITY);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY, TEST_ANONYMOUS_IDENTITY);
        props.setProperty(EXTRA_PROVISIONING_WIFI_DOMAIN, TEST_DOMAIN);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_HOST, TEST_PROXY_HOST);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, TEST_PROXY_BYPASS_HOSTS);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PAC_URL, TEST_PAC_URL);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PROXY_PORT, Integer.toString(TEST_PROXY_PORT));
        props.setProperty(EXTRA_PROVISIONING_WIFI_HIDDEN, Boolean.toString(TEST_HIDDEN));
        return props;
    }

    private static Properties setAllShortTestWifiInfo(Properties props) {
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_SSID_SHORT, TEST_SSID);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT, TEST_SECURITY_TYPE);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT, TEST_PASSWORD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT, TEST_EAP_METHOD);
        props.setProperty(EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT, TEST_PHASE2_AUTH);
        props.setProperty(EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT, TEST_CA_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT, TEST_USER_CERT);
        props.setProperty(EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT, TEST_IDENTITY);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT, TEST_ANONYMOUS_IDENTITY);
        props.setProperty(EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT, TEST_DOMAIN);

        props.setProperty(
                EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT, TEST_PROXY_HOST);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT, TEST_PROXY_BYPASS_HOSTS);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT, TEST_PAC_URL);
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT,
                Integer.toString(TEST_PROXY_PORT));
        props.setProperty(
                EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT, Boolean.toString(TEST_HIDDEN));
        return props;
    }

    private static Properties setTestTimeTimeZoneAndLocale(Properties props) {
        props.setProperty(EXTRA_PROVISIONING_LOCAL_TIME, Long.toString(TEST_LOCAL_TIME));
        props.setProperty(EXTRA_PROVISIONING_TIME_ZONE, TEST_TIME_ZONE);
        props.setProperty(EXTRA_PROVISIONING_LOCALE, StoreUtils.localeToString(TEST_LOCALE));
        return props;
    }

    private static Properties setShortTestTimeTimeZoneAndLocale(Properties props) {
        props.setProperty(
                EXTRA_PROVISIONING_LOCAL_TIME_SHORT, Long.toString(TEST_LOCAL_TIME));
        props.setProperty(
                EXTRA_PROVISIONING_TIME_ZONE_SHORT, TEST_TIME_ZONE);
        props.setProperty(
                EXTRA_PROVISIONING_LOCALE_SHORT, StoreUtils.localeToString(TEST_LOCALE));
        return props;
    }

    private static Properties setTestDeviceAdminDownload(Properties props) {
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                Integer.toString(TEST_MIN_SUPPORT_VERSION));
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                TEST_DOWNLOAD_LOCATION);
        props.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                TEST_COOKIE_HEADER);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM, buildTestPackageChecksum());
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM, buildTestSignatureChecksum());
        return props;
    }

    private static Properties setShortTestDeviceAdminDownload(Properties props) {
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT,
                Integer.toString(TEST_MIN_SUPPORT_VERSION));
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT,
                TEST_DOWNLOAD_LOCATION);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT,
                TEST_COOKIE_HEADER);
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT, buildTestPackageChecksum());
        props.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT,
                buildTestSignatureChecksum());
        return props;
    }

    private static String getTestAdminExtrasString() throws Exception {
        Properties props = new Properties();
        ByteArrayOutputStream stream = new ByteArrayOutputStream();

        PersistableBundle bundle = getTestAdminExtrasPersistableBundle();
        for (String key : bundle.keySet()) {
            props.setProperty(key, bundle.getString(key));
        }
        props.store(stream, "ADMIN_EXTRAS_BUNDLE" /* data description */);

        return stream.toString();
    }

    private static PersistableBundle getTestAdminExtrasPersistableBundle() {
        PersistableBundle bundle = new PersistableBundle();
        bundle.putString("key1", "val1");
        bundle.putString("key2", "val2");
        bundle.putString("key3", "val3");
        return bundle;
    }

    private Properties buildNfcProvisioningProperties() throws Exception {
        Properties properties = new Properties();
        properties.setProperty(EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME, TEST_PACKAGE_NAME);
        properties.setProperty(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                TEST_COMPONENT_NAME.flattenToString());
        setTestWifiInfo(properties);
        setTestTimeTimeZoneAndLocale(properties);
        setTestDeviceAdminDownload(properties);
        properties.setProperty(EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE, getTestAdminExtrasString());
        properties.setProperty(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                Boolean.toString(TEST_SKIP_ENCRYPTION));
        properties.setProperty(
                EXTRA_PROVISIONING_USE_MOBILE_DATA, Boolean.toString(TEST_USE_MOBILE_DATA));
        properties.setProperty(EXTRA_PROVISIONING_MAIN_COLOR, Integer.toString(TEST_MAIN_COLOR));
        return properties;
    }

    private Intent buildNfcProvisioningIntent(Properties properties) throws IOException {
        NdefMessage[] ndefMessages = new NdefMessage[] {
            new NdefMessage(new NdefRecord[] {
                NdefRecord.createMime(
                        DevicePolicyManager.MIME_TYPE_PROVISIONING_NFC,
                        buildNfcProvisioningMimeData(properties))
            })
        };
        return new Intent(NfcAdapter.ACTION_NDEF_DISCOVERED)
                .setType(MIME_TYPE_PROVISIONING_NFC)
                .putExtra(NfcAdapter.EXTRA_NDEF_MESSAGES, ndefMessages);

    }

    private byte[] buildNfcProvisioningMimeData(Properties properties)
            throws IOException {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        properties.store(stream, /* comments= */ "NFC provisioning intent");
        return stream.toByteArray();
    }

    private static String buildTestPackageChecksum() {
        return Base64.encodeToString(TEST_PACKAGE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }

    private static String buildTestSignatureChecksum() {
        return Base64.encodeToString(TEST_SIGNATURE_CHECKSUM,
                Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP);
    }
}
