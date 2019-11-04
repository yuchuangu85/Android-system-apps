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

package com.android.car.settings.storage;

import android.app.ActivityManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.UserHandle;
import android.os.UserManager;
import android.view.View;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;
import androidx.loader.app.LoaderManager;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.RestrictedLockUtils;
import com.android.settingslib.RestrictedLockUtilsInternal;
import com.android.settingslib.applications.ApplicationsState;
import com.android.settingslib.applications.StorageStatsSource;

import java.util.Arrays;
import java.util.List;

/**
 * Fragment to display the applications storage information. Also provide buttons to clear the
 * applications cache data and user data.
 */
public class AppStorageSettingsDetailsFragment extends SettingsFragment implements
        AppsStorageStatsManager.Callback {
    private static final Logger LOG = new Logger(AppStorageSettingsDetailsFragment.class);

    @VisibleForTesting
    static final String CONFIRM_CLEAR_STORAGE_DIALOG_TAG =
            "com.android.car.settings.storage.ConfirmClearStorageDialog";

    @VisibleForTesting
    static final String CONFIRM_CANNOT_CLEAR_STORAGE_DIALOG_TAG =
            "com.android.car.settings.storage.ConfirmCannotClearStorageDialog";

    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";
    // Result code identifiers
    public static final int REQUEST_MANAGE_SPACE = 2;

    // Internal constants used in Handler
    private static final int OP_SUCCESSFUL = 1;
    private static final int OP_FAILED = 2;

    // Constant used in handler to determine when the user data is cleared.
    private static final int MSG_CLEAR_USER_DATA = 1;
    // Constant used in handler to determine when the cache is cleared.
    private static final int MSG_CLEAR_CACHE = 2;

    // Keys to save the instance values.
    private static final String KEY_CACHE_CLEARED = "cache_cleared";
    private static final String KEY_DATA_CLEARED = "data_cleared";

    // Package information
    protected PackageManager mPackageManager;
    private String mPackageName;

    // Application state info
    private ApplicationsState.AppEntry mAppEntry;
    private ApplicationsState mAppState;
    private ApplicationInfo mInfo;
    private AppsStorageStatsManager mAppsStorageStatsManager;

    // User info
    private int mUserId;
    private CarUserManagerHelper mCarUserManagerHelper;

    //  An observer callback to get notified when the cache file deletion is complete.
    private ClearCacheObserver mClearCacheObserver;
    //  An observer callback to get notified when the user data deletion is complete.
    private ClearUserDataObserver mClearDataObserver;

    // The restriction enforced by admin.
    private RestrictedLockUtils.EnforcedAdmin mAppsControlDisallowedAdmin;
    private boolean mAppsControlDisallowedBySystem;

    // Clear user data and cache buttons and state.
    private Button mClearStorageButton;
    private Button mClearCacheButton;
    private boolean mCanClearData = true;
    private boolean mCacheCleared;
    private boolean mDataCleared;

    private final ConfirmationDialogFragment.ConfirmListener mConfirmClearStorageDialog =
            arguments -> initiateClearUserData();


    private final ConfirmationDialogFragment.ConfirmListener mConfirmCannotClearStorageDialog =
            arguments -> mClearStorageButton.setEnabled(false);

    /** Creates an instance of this fragment, passing packageName as an argument. */
    public static AppStorageSettingsDetailsFragment getInstance(String packageName) {
        AppStorageSettingsDetailsFragment applicationDetailFragment =
                new AppStorageSettingsDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PACKAGE_NAME, packageName);
        applicationDetailFragment.setArguments(bundle);
        return applicationDetailFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.app_storage_settings_details_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
        mUserId = mCarUserManagerHelper.getCurrentProcessUserId();
        mPackageName = getArguments().getString(EXTRA_PACKAGE_NAME);
        mAppState = ApplicationsState.getInstance(requireActivity().getApplication());
        mAppEntry = mAppState.getEntry(mPackageName, mUserId);
        StorageStatsSource storageStatsSource = new StorageStatsSource(context);
        StorageStatsSource.AppStorageStats stats = null;
        mPackageManager = context.getPackageManager();
        try {
            stats = storageStatsSource.getStatsForPackage(/* volumeUuid= */ null, mPackageName,
                    UserHandle.of(mUserId));
        } catch (Exception e) {
            // This may happen if the package was removed during our calculation.
            LOG.w("App unexpectedly not found", e);
        }
        mAppsStorageStatsManager = new AppsStorageStatsManager(context);
        mAppsStorageStatsManager.registerListener(this);
        use(StorageApplicationPreferenceController.class,
                R.string.pk_storage_application_details)
                .setAppEntry(mAppEntry)
                .setAppState(mAppState);

        List<StorageSizeBasePreferenceController> preferenceControllers = Arrays.asList(
                use(StorageApplicationSizePreferenceController.class,
                        R.string.pk_storage_application_size),
                use(StorageApplicationTotalSizePreferenceController.class,
                        R.string.pk_storage_application_total_size),
                use(StorageApplicationUserDataPreferenceController.class,
                        R.string.pk_storage_application_data_size),
                use(StorageApplicationCacheSizePreferenceController.class,
                        R.string.pk_storage_application_cache_size)
        );

        for (StorageSizeBasePreferenceController pc : preferenceControllers) {
            pc.setAppsStorageStatsManager(mAppsStorageStatsManager);
            pc.setAppStorageStats(stats);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(KEY_CACHE_CLEARED, mCacheCleared);
        outState.putBoolean(KEY_DATA_CLEARED, mDataCleared);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (savedInstanceState != null) {
            mCacheCleared = savedInstanceState.getBoolean(KEY_CACHE_CLEARED, false);
            mDataCleared = savedInstanceState.getBoolean(KEY_DATA_CLEARED, false);
            mCacheCleared = mCacheCleared || mDataCleared;
        }
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) findDialogByTag(CONFIRM_CLEAR_STORAGE_DIALOG_TAG),
                mConfirmClearStorageDialog, /* rejectListener= */ null);
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) findDialogByTag(
                        CONFIRM_CANNOT_CLEAR_STORAGE_DIALOG_TAG),
                mConfirmCannotClearStorageDialog, /* rejectListener= */ null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mClearStorageButton = requireActivity().findViewById(R.id.action_button1);
        mClearStorageButton.setVisibility(View.VISIBLE);
        mClearStorageButton.setEnabled(false);
        mClearStorageButton.setText(R.string.storage_clear_user_data_text);

        mClearCacheButton = requireActivity().findViewById(R.id.action_button2);
        mClearCacheButton.setVisibility(View.VISIBLE);
        mClearCacheButton.setEnabled(false);
        mClearCacheButton.setText(R.string.storage_clear_cache_btn_text);
    }

    @Override
    public void onResume() {
        super.onResume();
        mAppsControlDisallowedAdmin = RestrictedLockUtilsInternal.checkIfRestrictionEnforced(
                getActivity(), UserManager.DISALLOW_APPS_CONTROL, mUserId);
        mAppsControlDisallowedBySystem = RestrictedLockUtilsInternal.hasBaseUserRestriction(
                getActivity(), UserManager.DISALLOW_APPS_CONTROL, mUserId);
        updateSize();
    }

    @Override
    public void onDataLoaded(StorageStatsSource.AppStorageStats data, boolean cacheCleared,
            boolean dataCleared) {
        if (data == null) {
            mClearStorageButton.setEnabled(false);
            mClearCacheButton.setEnabled(false);
        } else {
            long cacheSize = data.getCacheBytes();
            long dataSize = data.getDataBytes() - cacheSize;

            if (dataSize <= 0 || !mCanClearData || mDataCleared) {
                mClearStorageButton.setEnabled(false);
            } else {
                mClearStorageButton.setEnabled(true);
                mClearStorageButton.setOnClickListener(v -> handleClearDataClick());
            }
            if (cacheSize <= 0 || mCacheCleared) {
                mClearCacheButton.setEnabled(false);
            } else {
                mClearCacheButton.setEnabled(true);
                mClearCacheButton.setOnClickListener(v -> handleClearCacheClick());
            }
        }
        if (mAppsControlDisallowedBySystem) {
            mClearStorageButton.setEnabled(false);
            mClearCacheButton.setEnabled(false);
        }
    }

    private void handleClearCacheClick() {
        if (mAppsControlDisallowedAdmin != null && !mAppsControlDisallowedBySystem) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(
                    getActivity(), mAppsControlDisallowedAdmin);
            return;
        }
        // Lazy initialization of observer.
        if (mClearCacheObserver == null) {
            mClearCacheObserver = new ClearCacheObserver();
        }
        mPackageManager.deleteApplicationCacheFiles(mPackageName, mClearCacheObserver);
    }

    private void handleClearDataClick() {
        if (mAppsControlDisallowedAdmin != null && !mAppsControlDisallowedBySystem) {
            RestrictedLockUtils.sendShowAdminSupportDetailsIntent(
                    getActivity(), mAppsControlDisallowedAdmin);
        } else if (mAppEntry.info.manageSpaceActivityName != null) {
            Intent intent = new Intent(Intent.ACTION_DEFAULT);
            intent.setClassName(mAppEntry.info.packageName,
                    mAppEntry.info.manageSpaceActivityName);
            startActivityForResult(intent, REQUEST_MANAGE_SPACE);
        } else {
            showClearDataDialog();
        }
    }

    /*
     * Private method to initiate clearing user data when the user clicks the clear data
     * button for a system package
     */
    private void initiateClearUserData() {
        mClearStorageButton.setEnabled(false);
        // Invoke uninstall or clear user data based on sysPackage
        String packageName = mAppEntry.info.packageName;
        LOG.i("Clearing user data for package : " + packageName);
        if (mClearDataObserver == null) {
            mClearDataObserver = new ClearUserDataObserver();
        }
        ActivityManager am = (ActivityManager)
                getActivity().getSystemService(Context.ACTIVITY_SERVICE);
        boolean res = am.clearApplicationUserData(packageName, mClearDataObserver);
        if (!res) {
            // Clearing data failed for some obscure reason. Just log error for now
            LOG.i("Couldn't clear application user data for package:" + packageName);
            showCannotClearDataDialog();
        }
    }

    /*
     * Private method to handle clear message notification from observer when
     * the async operation from PackageManager is complete
     */
    private void processClearMsg(Message msg) {
        int result = msg.arg1;
        String packageName = mAppEntry.info.packageName;
        if (result == OP_SUCCESSFUL) {
            LOG.i("Cleared user data for package : " + packageName);
            updateSize();
        } else {
            mClearStorageButton.setEnabled(true);
        }
    }

    private void updateSize() {
        PackageManager packageManager = getActivity().getPackageManager();
        try {
            mInfo = packageManager.getApplicationInfo(mPackageName, 0);
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Could not find package", e);
        }
        if (mInfo == null) {
            return;
        }
        LoaderManager loaderManager = LoaderManager.getInstance(this);
        mAppsStorageStatsManager.startLoading(loaderManager, mInfo, mUserId, mCacheCleared,
                mDataCleared);
    }

    private void showClearDataDialog() {
        ConfirmationDialogFragment confirmClearStorageDialog =
                new ConfirmationDialogFragment.Builder(getContext())
                        .setTitle(R.string.storage_clear_user_data_text)
                        .setMessage(getString(R.string.storage_clear_data_dlg_text))
                        .setPositiveButton(R.string.okay, mConfirmClearStorageDialog)
                        .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                        .build();
        showDialog(confirmClearStorageDialog, CONFIRM_CLEAR_STORAGE_DIALOG_TAG);
    }

    private void showCannotClearDataDialog() {
        ConfirmationDialogFragment dialogFragment =
                new ConfirmationDialogFragment.Builder(getContext())
                        .setTitle(R.string.storage_clear_data_dlg_title)
                        .setMessage(getString(R.string.storage_clear_failed_dlg_text))
                        .setPositiveButton(R.string.okay, mConfirmCannotClearStorageDialog)
                        .build();
        showDialog(dialogFragment, CONFIRM_CANNOT_CLEAR_STORAGE_DIALOG_TAG);
    }

    private final Handler mHandler = new Handler() {
        public void handleMessage(Message msg) {
            if (getView() == null) {
                return;
            }
            switch (msg.what) {
                case MSG_CLEAR_USER_DATA:
                    mDataCleared = true;
                    mCacheCleared = true;
                    processClearMsg(msg);
                    break;
                case MSG_CLEAR_CACHE:
                    mCacheCleared = true;
                    // Refresh size info
                    updateSize();
                    break;
            }
        }
    };

    class ClearCacheObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
            Message msg = mHandler.obtainMessage(MSG_CLEAR_CACHE);
            msg.arg1 = succeeded ? OP_SUCCESSFUL : OP_FAILED;
            mHandler.sendMessage(msg);
        }
    }

    class ClearUserDataObserver extends IPackageDataObserver.Stub {
        public void onRemoveCompleted(final String packageName, final boolean succeeded) {
            Message msg = mHandler.obtainMessage(MSG_CLEAR_USER_DATA);
            msg.arg1 = succeeded ? OP_SUCCESSFUL : OP_FAILED;
            mHandler.sendMessage(msg);
        }
    }
}
