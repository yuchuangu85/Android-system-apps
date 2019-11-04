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

package com.android.car.settings.applications.specialaccess;

import android.app.AppOpsManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;

import androidx.annotation.CallSuper;
import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.car.settings.R;
import com.android.car.settings.applications.specialaccess.AppStateAppOpsBridge.PermissionState;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.ApplicationsState.AppEntry;
import com.android.settingslib.applications.ApplicationsState.AppFilter;
import com.android.settingslib.applications.ApplicationsState.CompoundFilter;

import java.util.List;

/**
 * Displays a list of toggles for applications requesting permission to perform the operation with
 * which this controller was initialized. {@link #init(int, String, int)} should be called when
 * this controller is instantiated to specify the {@link AppOpsManager} operation code to control
 * access for.
 */
public class AppOpsPreferenceController extends PreferenceController<PreferenceGroup> {

    private static final AppFilter FILTER_HAS_INFO = new AppFilter() {
        @Override
        public void init() {
            // No op.
        }

        @Override
        public boolean filterApp(AppEntry info) {
            return info.extraInfo != null;
        }
    };

    private final AppOpsManager mAppOpsManager;

    private final Preference.OnPreferenceChangeListener mOnPreferenceChangeListener =
            new Preference.OnPreferenceChangeListener() {
                @Override
                public boolean onPreferenceChange(Preference preference, Object newValue) {
                    AppOpPreference appOpPreference = (AppOpPreference) preference;
                    AppEntry entry = appOpPreference.mEntry;
                    PermissionState extraInfo = (PermissionState) entry.extraInfo;
                    boolean allowOp = (Boolean) newValue;
                    if (allowOp != extraInfo.isPermissible()) {
                        mAppOpsManager.setMode(mAppOpsOpCode, entry.info.uid,
                                entry.info.packageName,
                                allowOp ? AppOpsManager.MODE_ALLOWED : mNegativeOpMode);
                        // Update the extra info of this entry so that it reflects the new mode.
                        mAppEntryListManager.forceUpdate(entry);
                        return true;
                    }
                    return false;
                }
            };

    private final AppEntryListManager.Callback mCallback = new AppEntryListManager.Callback() {
        @Override
        public void onAppEntryListChanged(List<AppEntry> entries) {
            mEntries = entries;
            refreshUi();
        }
    };

    private int mAppOpsOpCode = AppOpsManager.OP_NONE;
    private String mPermission;
    private int mNegativeOpMode = -1;

    @VisibleForTesting
    AppEntryListManager mAppEntryListManager;
    private List<AppEntry> mEntries;

    private boolean mShowSystem;

    public AppOpsPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mAppOpsManager = (AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE);
        mAppEntryListManager = new AppEntryListManager(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    /**
     * Initializes this controller with the {@code appOpsOpCode} (selected from the operations in
     * {@link AppOpsManager}) to control access for.
     *
     * @param permission     the {@link android.Manifest.permission} apps must hold to perform the
     *                       operation.
     * @param negativeOpMode the operation mode that will be passed to {@link
     *                       AppOpsManager#setMode(int, int, String, int)} when access for a app is
     *                       revoked.
     */
    public void init(int appOpsOpCode, String permission, int negativeOpMode) {
        mAppOpsOpCode = appOpsOpCode;
        mPermission = permission;
        mNegativeOpMode = negativeOpMode;
    }

    /**
     * Rebuilds the preference list to show system applications if {@code showSystem} is true.
     * System applications will be hidden otherwise.
     */
    public void setShowSystem(boolean showSystem) {
        if (mShowSystem != showSystem) {
            mShowSystem = showSystem;
            mAppEntryListManager.forceUpdate();
        }
    }

    @Override
    protected void checkInitialized() {
        if (mAppOpsOpCode == AppOpsManager.OP_NONE) {
            throw new IllegalStateException("App operation code must be initialized");
        }
        if (mPermission == null) {
            throw new IllegalStateException("Manifest permission must be initialized");
        }
        if (mNegativeOpMode == -1) {
            throw new IllegalStateException("Negative case app operation mode must be initialized");
        }
    }

    @Override
    protected void onCreateInternal() {
        AppStateAppOpsBridge extraInfoBridge = new AppStateAppOpsBridge(getContext(), mAppOpsOpCode,
                mPermission);
        mAppEntryListManager.init(extraInfoBridge, this::getAppFilter, mCallback);
    }

    @Override
    protected void onStartInternal() {
        mAppEntryListManager.start();
    }

    @Override
    protected void onStopInternal() {
        mAppEntryListManager.stop();
    }

    @Override
    protected void onDestroyInternal() {
        mAppEntryListManager.destroy();
    }

    @Override
    protected void updateState(PreferenceGroup preference) {
        if (mEntries == null) {
            // Still loading.
            return;
        }
        preference.removeAll();
        for (AppEntry entry : mEntries) {
            Preference appOpPreference = new AppOpPreference(getContext(), entry);
            appOpPreference.setOnPreferenceChangeListener(mOnPreferenceChangeListener);
            preference.addPreference(appOpPreference);
        }
    }

    @CallSuper
    protected AppFilter getAppFilter() {
        AppFilter filterObj = new CompoundFilter(FILTER_HAS_INFO,
                ApplicationsState.FILTER_NOT_HIDE);
        if (!mShowSystem) {
            filterObj = new CompoundFilter(filterObj,
                    ApplicationsState.FILTER_DOWNLOADED_AND_LAUNCHER);
        }
        return filterObj;
    }

    private static class AppOpPreference extends SwitchPreference {

        private final AppEntry mEntry;

        AppOpPreference(Context context, AppEntry entry) {
            super(context);
            String key = entry.info.packageName + "|" + entry.info.uid;
            setKey(key);
            setTitle(entry.label);
            setIcon(entry.icon);
            setSummary(getAppStateText(entry.info));
            setPersistent(false);
            PermissionState extraInfo = (PermissionState) entry.extraInfo;
            setChecked(extraInfo.isPermissible());
            mEntry = entry;
        }

        private String getAppStateText(ApplicationInfo info) {
            if ((info.flags & ApplicationInfo.FLAG_INSTALLED) == 0) {
                return getContext().getString(R.string.not_installed);
            } else if (!info.enabled || info.enabledSetting
                    == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED) {
                return getContext().getString(R.string.disabled);
            }
            return null;
        }
    }
}
