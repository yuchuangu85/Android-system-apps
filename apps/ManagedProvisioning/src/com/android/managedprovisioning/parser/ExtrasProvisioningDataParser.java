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
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCALE;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOCAL_TIME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_LOGO_URI;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_MAIN_COLOR;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_ORGANIZATION_NAME;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_ENCRYPTION;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_USER_CONSENT;
import static android.app.admin.DevicePolicyManager.EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
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

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.common.Globals.ACTION_PROVISION_MANAGED_DEVICE_SILENTLY;
import static com.android.managedprovisioning.common.Globals.ACTION_RESUME_PROVISIONING;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS;
import static com.android.managedprovisioning.model.ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_USE_MOBILE_DATA;
import static com.android.managedprovisioning.model.ProvisioningParams.inferStaticDeviceAdminPackageName;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Parcelable;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.managedprovisioning.common.IllegalProvisioningArgumentException;
import com.android.managedprovisioning.common.LogoUtils;
import com.android.managedprovisioning.common.ManagedProvisioningSharedPreferences;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.StoreUtils;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.DisclaimersParam;
import com.android.managedprovisioning.model.PackageDownloadInfo;
import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.model.WifiInfo;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IllformedLocaleException;
import java.util.Map;
import java.util.Set;

/**
 * A parser which parses provisioning data from intent which stores in {@link Bundle} extras.
 */

@VisibleForTesting
public class ExtrasProvisioningDataParser implements ProvisioningDataParser {
    private static final Set<String> PROVISIONING_ACTIONS_SUPPORT_ALL_PROVISIONING_DATA =
            new HashSet<>(Collections.singletonList(
                    ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE));

    private static final Set<String> PROVISIONING_ACTIONS_SUPPORT_MIN_PROVISIONING_DATA =
            new HashSet<>(Arrays.asList(
                    ACTION_PROVISION_MANAGED_DEVICE,
                    ACTION_PROVISION_MANAGED_SHAREABLE_DEVICE,
                    ACTION_PROVISION_MANAGED_USER,
                    ACTION_PROVISION_MANAGED_PROFILE,
                    ACTION_PROVISION_MANAGED_DEVICE_SILENTLY));

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT = "a.a.e.PAEB";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT = "a.a.e.PDAPN";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT = "a.a.e.PDACN";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT = "a.a.e.PATM";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT = "a.a.e.PKAOM";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_MAIN_COLOR_SHORT = "a.a.e.PMC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT = "a.a.e.PLASAE";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_TIME_ZONE_SHORT = "a.a.e.PTZ";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_LOCAL_TIME_SHORT = "a.a.e.PLT";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_LOCALE_SHORT = "a.a.e.PL";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_SSID_SHORT = "a.a.e.PWS";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT = "a.a.e.PWH";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT = "a.a.e.PWST";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT = "a.a.e.PWP";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT = "a.a.e.PWEM";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT = "a.a.e.PWPA";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT = "a.a.e.PWCC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT = "a.a.e.PWUC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT = "a.a.e.PWI";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT = "a.a.e.PWAI";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT = "a.a.e.PWD";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT = "a.a.e.PWPH";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT = "a.a.e.PWPRP";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT = "a.a.e.PWPB";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT = "a.a.e.PWPU";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT =
            "a.a.e.PDAPDL";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT = "a.a.e.PON";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_SUPPORT_URL_SHORT = "a.a.e.PSU";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL_SHORT = "a.a.e.PDAPL";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI_SHORT = "a.a.e.PDAPIU";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT = "a.a.e.PDAMVC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT =
            "a.a.e.PDAPDCH";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT = "a.a.e.PDAPC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT = "a.a.e.PDASC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT = "a.a.e.PSE";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_LOGO_URI_SHORT = "a.a.e.PLU";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DISCLAIMERS_SHORT = "a.a.e.PD";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT = "a.a.e.PDH";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT = "a.a.e.PDC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_SKIP_USER_SETUP_SHORT = "a.a.e.PSUS";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_SKIP_USER_CONSENT_SHORT = "a.a.e.PSUC";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS_SHORT = "a.a.e.PSES";

    @VisibleForTesting
    static final String EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT = "a.a.e.PUMD";

    private static final Map<String, String> SHORTER_EXTRAS = buildShorterExtrasMap();

    private static Map<String, String> buildShorterExtrasMap() {
        Map<String, String> shorterExtras = new HashMap<>();
        shorterExtras.put(
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE,
                EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME,
                EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE, EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION,
                EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_MAIN_COLOR, EXTRA_PROVISIONING_MAIN_COLOR_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_TIME_ZONE, EXTRA_PROVISIONING_TIME_ZONE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_LOCAL_TIME, EXTRA_PROVISIONING_LOCAL_TIME_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_LOCALE, EXTRA_PROVISIONING_LOCALE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_SSID, EXTRA_PROVISIONING_WIFI_SSID_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_HIDDEN, EXTRA_PROVISIONING_WIFI_HIDDEN_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_SECURITY_TYPE, EXTRA_PROVISIONING_WIFI_SECURITY_TYPE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PASSWORD, EXTRA_PROVISIONING_WIFI_PASSWORD_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_EAP_METHOD, EXTRA_PROVISIONING_WIFI_EAP_METHOD_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PHASE2_AUTH, EXTRA_PROVISIONING_WIFI_PHASE2_AUTH_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE,
                EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE,
                EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_IDENTITY, EXTRA_PROVISIONING_WIFI_IDENTITY_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY,
                EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_DOMAIN, EXTRA_PROVISIONING_WIFI_DOMAIN_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PROXY_HOST, EXTRA_PROVISIONING_WIFI_PROXY_HOST_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PROXY_PORT, EXTRA_PROVISIONING_WIFI_PROXY_PORT_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PROXY_BYPASS, EXTRA_PROVISIONING_WIFI_PROXY_BYPASS_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_WIFI_PAC_URL, EXTRA_PROVISIONING_WIFI_PAC_URL_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_ORGANIZATION_NAME, EXTRA_PROVISIONING_ORGANIZATION_NAME_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_SUPPORT_URL, EXTRA_PROVISIONING_SUPPORT_URL_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM,
                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM,
                EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_SKIP_ENCRYPTION, EXTRA_PROVISIONING_SKIP_ENCRYPTION_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DISCLAIMERS, EXTRA_PROVISIONING_DISCLAIMERS_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_LOGO_URI, EXTRA_PROVISIONING_LOGO_URI_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DISCLAIMER_HEADER, EXTRA_PROVISIONING_DISCLAIMER_HEADER_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_DISCLAIMER_CONTENT, EXTRA_PROVISIONING_DISCLAIMER_CONTENT_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_SKIP_USER_SETUP, EXTRA_PROVISIONING_SKIP_USER_SETUP_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_SKIP_USER_CONSENT, EXTRA_PROVISIONING_SKIP_USER_CONSENT_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS,
                EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS_SHORT);
        shorterExtras.put(
                EXTRA_PROVISIONING_USE_MOBILE_DATA, EXTRA_PROVISIONING_USE_MOBILE_DATA_SHORT);
        return shorterExtras;
    }

    private final Utils mUtils;
    private final Context mContext;
    private final ManagedProvisioningSharedPreferences mSharedPreferences;

    ExtrasProvisioningDataParser(Context context, Utils utils) {
        this(context, utils, new ManagedProvisioningSharedPreferences(context));
    }

    @VisibleForTesting
    ExtrasProvisioningDataParser(Context context, Utils utils,
            ManagedProvisioningSharedPreferences sharedPreferences) {
        mContext = checkNotNull(context);
        mUtils = checkNotNull(utils);
        mSharedPreferences = checkNotNull(sharedPreferences);
    }

    @Override
    public ProvisioningParams parse(Intent provisioningIntent)
            throws IllegalProvisioningArgumentException{
        String provisioningAction = provisioningIntent.getAction();
        if (ACTION_RESUME_PROVISIONING.equals(provisioningAction)) {
            return getParcelableExtraFromLongName(provisioningIntent,
                    ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        }
        if (PROVISIONING_ACTIONS_SUPPORT_MIN_PROVISIONING_DATA.contains(provisioningAction)) {
            ProvisionLogger.logi("Processing mininalist extras intent.");
            return parseMinimalistSupportedProvisioningDataInternal(provisioningIntent, mContext)
                    .build();
        } else if (PROVISIONING_ACTIONS_SUPPORT_ALL_PROVISIONING_DATA.contains(
                provisioningAction)) {
            return parseAllSupportedProvisioningData(provisioningIntent, mContext);
        } else {
            throw new IllegalProvisioningArgumentException("Unsupported provisioning action: "
                    + provisioningAction);
        }
    }

    /**
     * Returns a short version of the requested extra.
     */
    static String getShortExtraNames(String extraName) {
        return SHORTER_EXTRAS.get(extraName);
    }

    private boolean hasExtraFromLongName(Intent intent, String longName) {
        return intent.hasExtra(longName) || intent.hasExtra(getShortExtraNames(longName));
    }

    @Nullable
    private <T extends Parcelable> T getParcelableExtraFromLongName(
            Intent intent, String longName) {
        if (intent.hasExtra(longName)) {
            return intent.getParcelableExtra(longName);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.hasExtra(shortName)) {
            return intent.getParcelableExtra(shortName);
        }
        return null;
    }

    @Nullable
    private Parcelable[] getParcelableArrayExtraFromLongName(Intent intent, String longName) {
        if (intent.hasExtra(longName)) {
            return intent.getParcelableArrayExtra(longName);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.hasExtra(shortName)) {
            return intent.getParcelableArrayExtra(shortName);
        }
        return null;
    }

    private int getIntExtraFromLongName(Intent intent, String longName, int defaultValue) {
        if (intent.hasExtra(longName)) {
            return intent.getIntExtra(longName, defaultValue);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.hasExtra(shortName)) {
            return intent.getIntExtra(shortName, defaultValue);
        }
        return defaultValue;
    }

    private boolean getBooleanExtraFromLongName(
            Intent intent, String longName, boolean defaultValue) {
        if (intent.hasExtra(longName)) {
            return intent.getBooleanExtra(longName, defaultValue);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.hasExtra(shortName)) {
            return intent.getBooleanExtra(shortName, defaultValue);
        }
        return defaultValue;
    }

    private long getParcelableExtraFromLongName(Intent intent, String longName, long defaultValue) {
        if (intent.hasExtra(longName)) {
            return intent.getLongExtra(longName, defaultValue);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.hasExtra(shortName)) {
            return intent.getLongExtra(shortName, defaultValue);
        }
        return defaultValue;
    }

    @Nullable
    private String getStringExtraFromLongName(Intent intent, String longName) {
        if (intent.getStringExtra(longName) != null) {
            return intent.getStringExtra(longName);
        }
        String shortName = getShortExtraNames(longName);
        if (intent.getStringExtra(shortName) != null) {
            return intent.getStringExtra(shortName);
        }
        return null;
    }

    /**
     * Parses minimal supported set of parameters from bundle extras of a provisioning intent.
     *
     * <p>Here is the list of supported parameters.
     * <ul>
     *     <li>{@link EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME}</li>
     *     <li>
     *         {@link EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME} only in
     *         {@link ACTION_PROVISION_MANAGED_PROFILE}.
     *     </li>
     *     <li>{@link EXTRA_PROVISIONING_LOGO_URI}</li>
     *     <li>{@link EXTRA_PROVISIONING_MAIN_COLOR}</li>
     *     <li>
     *         {@link EXTRA_PROVISIONING_SKIP_USER_SETUP} only in
     *         {@link ACTION_PROVISION_MANAGED_USER} and {@link ACTION_PROVISION_MANAGED_DEVICE}.
     *     </li>
     *     <li>{@link EXTRA_PROVISIONING_SKIP_ENCRYPTION}</li>
     *     <li>{@link EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED}</li>
     *     <li>{@link EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE}</li>
     *     <li>{@link EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE}</li>
     *     <li>{@link EXTRA_PROVISIONING_SKIP_USER_CONSENT}</li>
     * </ul>
     */
    private ProvisioningParams.Builder parseMinimalistSupportedProvisioningDataInternal(
            Intent intent, Context context)
            throws IllegalProvisioningArgumentException {
        final DevicePolicyManager dpm = context.getSystemService(DevicePolicyManager.class);
        boolean isProvisionManagedDeviceFromTrustedSourceIntent =
                ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(intent.getAction());
        try {
            final long provisioningId = mSharedPreferences.incrementAndGetProvisioningId();
            String provisioningAction = mUtils.mapIntentToDpmAction(intent);
            final boolean isManagedProfileAction =
                    ACTION_PROVISION_MANAGED_PROFILE.equals(provisioningAction);

            // Parse device admin package name and component name.
            ComponentName deviceAdminComponentName = getParcelableExtraFromLongName(
                    intent, EXTRA_PROVISIONING_DEVICE_ADMIN_COMPONENT_NAME);
            // Device admin package name is deprecated. It is only supported in Profile Owner
            // provisioning and when resuming NFC provisioning.
            String deviceAdminPackageName = null;
            if (isManagedProfileAction) {
                // In L, we only support package name. This means some DPC may still send us the
                // device admin package name only. Attempts to obtain the package name from extras.
                deviceAdminPackageName = getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_NAME);
                // For profile owner, the device admin package should be installed. Verify the
                // device admin package.
                deviceAdminComponentName = mUtils.findDeviceAdmin(
                        deviceAdminPackageName,
                        deviceAdminComponentName,
                        context,
                        UserHandle.myUserId());
                // Since the device admin package must be installed at this point and its component
                // name has been obtained, it should be safe to set the deprecated package name
                // value to null.
                deviceAdminPackageName = null;
            }

            // Parse skip user setup in ACTION_PROVISION_MANAGED_USER and
            // ACTION_PROVISION_MANAGED_DEVICE (sync auth) only. This extra is not supported if
            // provisioning was started by trusted source, as it is not clear where SUW should
            // continue from.
            boolean skipUserSetup = ProvisioningParams.DEFAULT_SKIP_USER_SETUP;
            if (!isProvisionManagedDeviceFromTrustedSourceIntent
                    && (provisioningAction.equals(ACTION_PROVISION_MANAGED_USER)
                            || provisioningAction.equals(ACTION_PROVISION_MANAGED_DEVICE))) {
                skipUserSetup = getBooleanExtraFromLongName(
                        intent, EXTRA_PROVISIONING_SKIP_USER_SETUP,
                        ProvisioningParams.DEFAULT_SKIP_USER_SETUP);
            }

            // Only current DeviceOwner can specify EXTRA_PROVISIONING_SKIP_USER_CONSENT when
            // provisioning PO with ACTION_PROVISION_MANAGED_PROFILE
            final boolean skipUserConsent = isManagedProfileAction
                            && getBooleanExtraFromLongName(intent,
                                EXTRA_PROVISIONING_SKIP_USER_CONSENT,
                                ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SKIP_USER_CONSENT)
                            && mUtils.isPackageDeviceOwner(dpm, inferStaticDeviceAdminPackageName(
                                    deviceAdminComponentName, deviceAdminPackageName));

            final boolean skipEducationScreens = shouldSkipEducationScreens(intent);

            // Only when provisioning PO with ACTION_PROVISION_MANAGED_PROFILE
            final boolean keepAccountMigrated = isManagedProfileAction
                            && getBooleanExtraFromLongName(
                                intent, EXTRA_PROVISIONING_KEEP_ACCOUNT_ON_MIGRATION,
                                ProvisioningParams
                                        .DEFAULT_EXTRA_PROVISIONING_KEEP_ACCOUNT_MIGRATED);

            // Parse main color and organization's logo. This is not supported in managed device
            // from trusted source provisioning because, currently, there is no way to send
            // organization logo to the device at this stage.
            Integer mainColor = ProvisioningParams.DEFAULT_MAIN_COLOR;
            if (!isProvisionManagedDeviceFromTrustedSourceIntent) {
                if (hasExtraFromLongName(intent, EXTRA_PROVISIONING_MAIN_COLOR)) {
                    mainColor = getIntExtraFromLongName(
                            intent, EXTRA_PROVISIONING_MAIN_COLOR, 0 /* not used */);
                }
                parseOrganizationLogoUrlFromExtras(context, intent);
            }

            DisclaimersParam disclaimersParam = new DisclaimersParser(context, provisioningId)
                    .parse(getParcelableArrayExtraFromLongName(
                            intent, EXTRA_PROVISIONING_DISCLAIMERS));

            String deviceAdminLabel = null;
            String organizationName = null;
            String supportUrl = null;
            String deviceAdminIconFilePath = null;
            if (isProvisionManagedDeviceFromTrustedSourceIntent) {
                deviceAdminLabel = getStringExtraFromLongName(intent,
                        EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_LABEL);
                organizationName = getStringExtraFromLongName(
                        intent,EXTRA_PROVISIONING_ORGANIZATION_NAME);
                supportUrl = getStringExtraFromLongName(intent,EXTRA_PROVISIONING_SUPPORT_URL);
                deviceAdminIconFilePath = new DeviceAdminIconParser(context, provisioningId).parse(
                        getParcelableExtraFromLongName(intent,
                                EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_ICON_URI));
            }

            final boolean leaveAllSystemAppsEnabled = isManagedProfileAction
                    ? false
                    : getBooleanExtraFromLongName(
                            intent, EXTRA_PROVISIONING_LEAVE_ALL_SYSTEM_APPS_ENABLED,
                            ProvisioningParams.DEFAULT_LEAVE_ALL_SYSTEM_APPS_ENABLED);

            return ProvisioningParams.Builder.builder()
                    .setProvisioningId(provisioningId)
                    .setProvisioningAction(provisioningAction)
                    .setDeviceAdminComponentName(deviceAdminComponentName)
                    .setDeviceAdminPackageName(deviceAdminPackageName)
                    .setSkipEncryption(
                            getBooleanExtraFromLongName(
                                    intent, EXTRA_PROVISIONING_SKIP_ENCRYPTION,
                                    ProvisioningParams.DEFAULT_EXTRA_PROVISIONING_SKIP_ENCRYPTION))
                    .setLeaveAllSystemAppsEnabled(leaveAllSystemAppsEnabled)
                    .setAdminExtrasBundle(getParcelableExtraFromLongName(
                            intent, EXTRA_PROVISIONING_ADMIN_EXTRAS_BUNDLE))
                    .setMainColor(mainColor)
                    .setDisclaimersParam(disclaimersParam)
                    .setSkipUserConsent(skipUserConsent)
                    .setKeepAccountMigrated(keepAccountMigrated)
                    .setSkipUserSetup(skipUserSetup)
                    .setSkipEducationScreens(skipEducationScreens)
                    .setAccountToMigrate(getParcelableExtraFromLongName(
                            intent, EXTRA_PROVISIONING_ACCOUNT_TO_MIGRATE))
                    .setDeviceAdminLabel(deviceAdminLabel)
                    .setOrganizationName(organizationName)
                    .setSupportUrl(supportUrl)
                    .setDeviceAdminIconFilePath(deviceAdminIconFilePath)
                    .setIsCloudEnrollment(mUtils.isCloudEnrollment(intent))
                    .setIsOrganizationOwnedProvisioning(
                            mUtils.isOrganizationOwnedProvisioning(intent));
        } catch (ClassCastException e) {
            throw new IllegalProvisioningArgumentException("Extra has invalid type", e);
        } catch (IllegalArgumentException e) {
            throw new IllegalProvisioningArgumentException("Invalid parameter found!", e);
        } catch (NullPointerException e) {
            throw new IllegalProvisioningArgumentException("Compulsory parameter not found!", e);
        }
    }

    /**
     * When {@link DevicePolicyManager#EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS} is passed as
     * a provisioning extra, we only process it for managed Google account enrollment and
     * persistent device owner.
     */
    private boolean shouldSkipEducationScreens(Intent intent) {
        if (!getBooleanExtraFromLongName(intent,
                EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS,
                DEFAULT_EXTRA_PROVISIONING_SKIP_EDUCATION_SCREENS)) {
            return false;
        }
        if (mUtils.isOrganizationOwnedProvisioning(intent)) {
            return false;
        }
        return isFullyManagedDeviceAction(intent);
    }

    private boolean isFullyManagedDeviceAction(Intent intent) {
        return ACTION_PROVISION_MANAGED_DEVICE.equals(intent.getAction())
                || ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE.equals(intent.getAction());
    }

    /**
     * Parses an intent and return a corresponding {@link ProvisioningParams} object.
     *
     * @param intent intent to be parsed.
     * @param context a context
     */
    private ProvisioningParams parseAllSupportedProvisioningData(Intent intent, Context context)
            throws IllegalProvisioningArgumentException {
        try {
            ProvisionLogger.logi("Processing all supported extras intent: " + intent.getAction());
            return parseMinimalistSupportedProvisioningDataInternal(intent, context)
                    // Parse time zone, local time and locale.
                    .setTimeZone(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_TIME_ZONE))
                    .setLocalTime(
                            getParcelableExtraFromLongName(
                                    intent, EXTRA_PROVISIONING_LOCAL_TIME,
                                    ProvisioningParams.DEFAULT_LOCAL_TIME))
                    .setLocale(StoreUtils.stringToLocale(
                            getStringExtraFromLongName(intent,EXTRA_PROVISIONING_LOCALE)))
                    .setUseMobileData(
                            getBooleanExtraFromLongName(
                                    intent, EXTRA_PROVISIONING_USE_MOBILE_DATA,
                                    DEFAULT_EXTRA_PROVISIONING_USE_MOBILE_DATA))
                    // Parse WiFi configuration.
                    .setWifiInfo(parseWifiInfoFromExtras(intent))
                    // Parse device admin package download info.
                    .setDeviceAdminDownloadInfo(parsePackageDownloadInfoFromExtras(intent))
                    // Cases where startedByTrustedSource can be true are
                    // 1. We are reloading a stored provisioning intent, either Nfc bump or
                    //    PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE, after encryption reboot,
                    //    which is a self-originated intent.
                    // 2. the intent is from a trusted source, for example QR provisioning.
                    .setStartedByTrustedSource(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE
                            .equals(intent.getAction()))
                    .build();
        }  catch (IllegalArgumentException e) {
            throw new IllegalProvisioningArgumentException("Invalid parameter found!", e);
        }  catch (IllformedLocaleException e) {
            throw new IllegalProvisioningArgumentException("Invalid locale format!", e);
        }  catch (NullPointerException e) {
            throw new IllegalProvisioningArgumentException("Compulsory parameter not found!", e);
        }
    }

    /**
     * Parses Wifi configuration from an Intent and returns the result in {@link WifiInfo}.
     */
    @Nullable
    private WifiInfo parseWifiInfoFromExtras(Intent intent) {
        if (getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_SSID) == null) {
            return null;
        }
        return WifiInfo.Builder.builder()
                .setSsid(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_SSID))
                .setSecurityType(
                        getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_SECURITY_TYPE))
                .setPassword(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_PASSWORD))
                .setProxyHost(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_PROXY_HOST))
                .setProxyBypassHosts(
                        getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_PROXY_BYPASS))
                .setPacUrl(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_PAC_URL))
                .setProxyPort(getIntExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_PROXY_PORT,
                        WifiInfo.DEFAULT_WIFI_PROXY_PORT))
                .setEapMethod(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_EAP_METHOD))
                .setPhase2Auth(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_PHASE2_AUTH))
                .setCaCertificate(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_CA_CERTIFICATE))
                .setUserCertificate(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_USER_CERTIFICATE))
                .setIdentity(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_IDENTITY))
                .setAnonymousIdentity(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_ANONYMOUS_IDENTITY))
                .setDomain(getStringExtraFromLongName(intent, EXTRA_PROVISIONING_WIFI_DOMAIN))
                .setHidden(getBooleanExtraFromLongName(
                        intent, EXTRA_PROVISIONING_WIFI_HIDDEN, WifiInfo.DEFAULT_WIFI_HIDDEN))
                .build();
    }

    /**
     * Parses device admin package download info configuration from an Intent and returns the result
     * in {@link PackageDownloadInfo}.
     */
    @Nullable
    private PackageDownloadInfo parsePackageDownloadInfoFromExtras(Intent intent) {
        if (getStringExtraFromLongName(
                intent, EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION) == null) {
            return null;
        }
        PackageDownloadInfo.Builder downloadInfoBuilder = PackageDownloadInfo.Builder.builder()
                .setMinVersion(getIntExtraFromLongName(
                        intent, EXTRA_PROVISIONING_DEVICE_ADMIN_MINIMUM_VERSION_CODE,
                        PackageDownloadInfo.DEFAULT_MINIMUM_VERSION))
                .setLocation(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_LOCATION))
                .setCookieHeader(getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_DOWNLOAD_COOKIE_HEADER));
        String packageHash =
                getStringExtraFromLongName(
                        intent, EXTRA_PROVISIONING_DEVICE_ADMIN_PACKAGE_CHECKSUM);
        if (packageHash != null) {
            downloadInfoBuilder.setPackageChecksum(StoreUtils.stringToByteArray(packageHash));
        }
        String sigHash = getStringExtraFromLongName(
                intent, EXTRA_PROVISIONING_DEVICE_ADMIN_SIGNATURE_CHECKSUM);
        if (sigHash != null) {
            downloadInfoBuilder.setSignatureChecksum(StoreUtils.stringToByteArray(sigHash));
        }
        return downloadInfoBuilder.build();
    }

    /**
     * Parses the organization logo url from intent.
     */
    private void parseOrganizationLogoUrlFromExtras(Context context, Intent intent) {
        Uri logoUri = getParcelableExtraFromLongName(intent, EXTRA_PROVISIONING_LOGO_URI);
        if (logoUri != null) {
            // If we go through encryption, and if the uri is a content uri:
            // We'll lose the grant to this uri. So we need to save it to a local file.
            LogoUtils.saveOrganisationLogo(context, logoUri);
        }
    }
}
