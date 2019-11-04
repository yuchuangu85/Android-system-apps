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
 * limitations under the License.
 */
package com.android.car.developeroptions.security;

import static com.android.car.developeroptions.security.EncryptionStatusPreferenceController.PREF_KEY_ENCRYPTION_SECURITY_PAGE;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.Intent;
import android.provider.SearchIndexableResource;

import com.android.car.developeroptions.R;
import com.android.car.developeroptions.biometrics.face.FaceProfileStatusPreferenceController;
import com.android.car.developeroptions.biometrics.face.FaceStatusPreferenceController;
import com.android.car.developeroptions.biometrics.fingerprint.FingerprintProfileStatusPreferenceController;
import com.android.car.developeroptions.biometrics.fingerprint.FingerprintStatusPreferenceController;
import com.android.car.developeroptions.dashboard.DashboardFragment;
import com.android.car.developeroptions.enterprise.EnterprisePrivacyPreferenceController;
import com.android.car.developeroptions.search.BaseSearchIndexProvider;
import com.android.car.developeroptions.widget.PreferenceCategoryController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.core.lifecycle.Lifecycle;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.List;

@SearchIndexable
public class SecuritySettings extends DashboardFragment {

    private static final String TAG = "SecuritySettings";
    private static final String SECURITY_CATEGORY = "security_category";
    private static final String WORK_PROFILE_SECURITY_CATEGORY = "security_category_profile";

    public static final int CHANGE_TRUST_AGENT_SETTINGS = 126;
    public static final int UNIFY_LOCK_CONFIRM_DEVICE_REQUEST = 128;
    public static final int UNIFY_LOCK_CONFIRM_PROFILE_REQUEST = 129;
    public static final int UNUNIFY_LOCK_CONFIRM_DEVICE_REQUEST = 130;

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SECURITY;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.security_dashboard_settings;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_security;
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context, getSettingsLifecycle(), this /* host*/);
    }

    /**
     * see confirmPatternThenDisableAndClear
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (use(LockUnificationPreferenceController.class)
                .handleActivityResult(requestCode, resultCode, data)) {
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    void startUnification() {
        use(LockUnificationPreferenceController.class).startUnification();
    }

    void updateUnificationPreference() {
        use(LockUnificationPreferenceController.class).updateState(null);
    }

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context,
            Lifecycle lifecycle, SecuritySettings host) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new EnterprisePrivacyPreferenceController(context));
        controllers.add(new ScreenPinningPreferenceController(context));
        controllers.add(new SimLockPreferenceController(context));
        controllers.add(new EncryptionStatusPreferenceController(context,
                PREF_KEY_ENCRYPTION_SECURITY_PAGE));

        final List<AbstractPreferenceController> securityPreferenceControllers = new ArrayList<>();
        securityPreferenceControllers.add(new FaceStatusPreferenceController(context));
        securityPreferenceControllers.add(new FingerprintStatusPreferenceController(context));
        securityPreferenceControllers.add(new ChangeScreenLockPreferenceController(context, host));
        controllers.add(new PreferenceCategoryController(context, SECURITY_CATEGORY)
                .setChildren(securityPreferenceControllers));
        controllers.addAll(securityPreferenceControllers);

        final List<AbstractPreferenceController> profileSecurityControllers = new ArrayList<>();
        profileSecurityControllers.add(new ChangeProfileScreenLockPreferenceController(
                context, host));
        profileSecurityControllers.add(new LockUnificationPreferenceController(context, host));
        profileSecurityControllers.add(new VisiblePatternProfilePreferenceController(
                context, lifecycle));
        profileSecurityControllers.add(new FaceProfileStatusPreferenceController(context));
        profileSecurityControllers.add(new FingerprintProfileStatusPreferenceController(context));
        controllers.add(new PreferenceCategoryController(context, WORK_PROFILE_SECURITY_CATEGORY)
                .setChildren(profileSecurityControllers));
        controllers.addAll(profileSecurityControllers);

        return controllers;
    }

    /**
     * For Search. Please keep it in sync when updating "createPreferenceHierarchy()"
     */
    public static final SearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {

                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final List<SearchIndexableResource> index = new ArrayList<>();
                    // Append the rest of the settings
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.security_dashboard_settings;
                    index.add(sir);
                    return index;
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(Context
                        context) {
                    return buildPreferenceControllers(context, null /* lifecycle */,
                            null /* host*/);
                }
            };
}
