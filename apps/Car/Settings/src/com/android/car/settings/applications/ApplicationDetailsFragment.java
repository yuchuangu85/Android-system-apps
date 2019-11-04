/*
 * Copyright (C) 2018 The Android Open Source Project
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

import static android.app.Activity.RESULT_OK;

import static com.android.car.settings.applications.ApplicationsUtils.isKeepEnabledPackage;
import static com.android.car.settings.applications.ApplicationsUtils.isProfileOrDeviceOwner;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.admin.DevicePolicyManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.Bundle;
import android.os.UserHandle;
import android.os.UserManager;
import android.util.ArraySet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;
import com.android.settingslib.Utils;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Shows details about an application and action associated with that application, like uninstall,
 * forceStop.
 *
 * <p>To uninstall an app, it must <i>not</i> be:
 * <ul>
 * <li>a system bundled app
 * <li>system signed
 * <li>managed by an active admin from a device policy
 * <li>a device or profile owner
 * <li>the only home app
 * <li>the default home app
 * <li>for a user with the {@link UserManager#DISALLOW_APPS_CONTROL} restriction
 * <li>for a user with the {@link UserManager#DISALLOW_UNINSTALL_APPS} restriction
 * </ul>
 *
 * <p>For apps that cannot be uninstalled, a disable option is shown instead (or enable if the app
 * is already disabled).
 */
public class ApplicationDetailsFragment extends SettingsFragment implements ActivityResultCallback {
    private static final Logger LOG = new Logger(ApplicationDetailsFragment.class);
    public static final String EXTRA_PACKAGE_NAME = "extra_package_name";

    @VisibleForTesting
    static final String DISABLE_CONFIRM_DIALOG_TAG =
            "com.android.car.settings.applications.DisableConfirmDialog";
    @VisibleForTesting
    static final String FORCE_STOP_CONFIRM_DIALOG_TAG =
            "com.android.car.settings.applications.ForceStopConfirmDialog";
    @VisibleForTesting
    static final int UNINSTALL_REQUEST_CODE = 10;

    private DevicePolicyManager mDpm;
    private PackageManager mPm;
    private CarUserManagerHelper mCarUserManagerHelper;

    private String mPackageName;
    private PackageInfo mPackageInfo;
    private ApplicationsState mAppState;
    private ApplicationsState.Session mSession;
    private ApplicationsState.AppEntry mAppEntry;

    // The function of this button depends on which app is shown and the app's current state.
    // It is an application enable/disable toggle for apps bundled with the system image.
    private Button mUninstallButton;
    private Button mForceStopButton;

    /** Creates an instance of this fragment, passing packageName as an argument. */
    public static ApplicationDetailsFragment getInstance(String packageName) {
        ApplicationDetailsFragment applicationDetailFragment = new ApplicationDetailsFragment();
        Bundle bundle = new Bundle();
        bundle.putString(EXTRA_PACKAGE_NAME, packageName);
        applicationDetailFragment.setArguments(bundle);
        return applicationDetailFragment;
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.application_details_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mDpm = (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mPm = context.getPackageManager();
        mCarUserManagerHelper = new CarUserManagerHelper(context);

        // These should be loaded before onCreate() so that the controller operates as expected.
        mPackageName = getArguments().getString(EXTRA_PACKAGE_NAME);

        mAppState = ApplicationsState.getInstance(requireActivity().getApplication());
        mSession = mAppState.newSession(mApplicationStateCallbacks, getLifecycle());

        retrieveAppEntry();

        use(ApplicationPreferenceController.class,
                R.string.pk_application_details_app)
                .setAppEntry(mAppEntry).setAppState(mAppState);
        use(NotificationsPreferenceController.class,
                R.string.pk_application_details_notifications).setPackageInfo(mPackageInfo);
        use(PermissionsPreferenceController.class,
                R.string.pk_application_details_permissions).setPackageName(mPackageName);
        use(VersionPreferenceController.class,
                R.string.pk_application_details_version).setPackageInfo(mPackageInfo);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) findDialogByTag(DISABLE_CONFIRM_DIALOG_TAG),
                mDisableConfirmListener, /* rejectListener= */ null);
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) findDialogByTag(FORCE_STOP_CONFIRM_DIALOG_TAG),
                mForceStopConfirmListener, /* rejectListener= */ null);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mUninstallButton = requireActivity().findViewById(R.id.action_button1);
        mForceStopButton = requireActivity().findViewById(R.id.action_button2);
        mForceStopButton.setVisibility(View.VISIBLE);
        mForceStopButton.setEnabled(false);
        mForceStopButton.setText(R.string.force_stop);
        mForceStopButton.setOnClickListener(mForceStopClickListener);
    }

    @Override
    public void onStart() {
        super.onStart();
        // Resume the session earlier than the lifecycle so that cached information is updated
        // even if settings is not resumed (for example in multi-display).
        mSession.onResume();
        refresh();
    }

    @Override
    public void onStop() {
        super.onStop();
        // Since we resume early in onStart, make sure we clean up even if we don't receive onPause.
        mSession.onPause();
    }

    private void refresh() {
        retrieveAppEntry();
        if (mAppEntry == null) {
            goBack();
        }
        updateForceStopButton();
        updateUninstallButton();
    }

    private void retrieveAppEntry() {
        mAppEntry = mAppState.getEntry(mPackageName,
                mCarUserManagerHelper.getCurrentProcessUserId());
        if (mAppEntry != null) {
            try {
                mPackageInfo = mPm.getPackageInfo(mPackageName,
                        PackageManager.MATCH_DISABLED_COMPONENTS | PackageManager.MATCH_ANY_USER
                                | PackageManager.GET_SIGNATURES | PackageManager.GET_PERMISSIONS);
            } catch (PackageManager.NameNotFoundException e) {
                LOG.e("Exception when retrieving package:" + mPackageName, e);
                mPackageInfo = null;
            }
        } else {
            mPackageInfo = null;
        }
    }

    private void updateForceStopButton() {
        if (mDpm.packageHasActiveAdmins(mPackageName)) {
            updateForceStopButtonInner(/* enabled= */ false);
        } else if ((mAppEntry.info.flags & ApplicationInfo.FLAG_STOPPED) == 0) {
            // If the app isn't explicitly stopped, then always show the force stop button.
            updateForceStopButtonInner(/* enabled= */ true);
        } else {
            Intent intent = new Intent(Intent.ACTION_QUERY_PACKAGE_RESTART,
                    Uri.fromParts("package", mPackageName, /* fragment= */ null));
            intent.putExtra(Intent.EXTRA_PACKAGES, new String[]{mPackageName});
            intent.putExtra(Intent.EXTRA_UID, mAppEntry.info.uid);
            intent.putExtra(Intent.EXTRA_USER_HANDLE, UserHandle.getUserId(mAppEntry.info.uid));
            LOG.d("Sending broadcast to query restart status for " + mPackageName);
            requireContext().sendOrderedBroadcastAsUser(intent,
                    UserHandle.CURRENT,
                    /* receiverPermission= */ null,
                    mCheckKillProcessesReceiver,
                    /* scheduler= */ null,
                    Activity.RESULT_CANCELED,
                    /* initialData= */ null,
                    /* initialExtras= */ null);
        }
    }

    private void updateForceStopButtonInner(boolean enabled) {
        mForceStopButton.setEnabled(
                enabled && !mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                        UserManager.DISALLOW_APPS_CONTROL));
    }

    private void updateUninstallButton() {
        if (isBundledApp()) {
            if (isAppEnabled()) {
                mUninstallButton.setText(R.string.disable_text);
                mUninstallButton.setOnClickListener(mDisableClickListener);
            } else {
                mUninstallButton.setText(R.string.enable_text);
                mUninstallButton.setOnClickListener(mEnableClickListener);
            }
        } else {
            mUninstallButton.setText(R.string.uninstall_text);
            mUninstallButton.setOnClickListener(mUninstallClickListener);
        }

        mUninstallButton.setEnabled(!shouldDisableUninstallButton());
    }

    private boolean shouldDisableUninstallButton() {
        if (shouldDisableUninstallForHomeApp()) {
            LOG.d("Uninstall disabled for home app");
            return true;
        }

        if (isAppEnabled() && isKeepEnabledPackage(requireContext(), mPackageName)) {
            LOG.d("Disable button disabled for keep enabled package");
            return true;
        }

        if (Utils.isSystemPackage(getResources(), mPm, mPackageInfo)) {
            LOG.d("Uninstall disabled for system package");
            return true;
        }

        if (mDpm.packageHasActiveAdmins(mPackageName)) {
            LOG.d("Uninstall disabled because package has active admins");
            return true;
        }

        // We don't allow uninstalling profile/device owner on any user because if it's a system
        // app, "uninstall" is actually "downgrade to the system version + disable", and
        // "downgrade" will clear data on all users.
        if (isProfileOrDeviceOwner(mPackageName, mDpm, mCarUserManagerHelper)) {
            LOG.d("Uninstall disabled because package is profile or device owner");
            return true;
        }

        if (mDpm.isUninstallInQueue(mPackageName)) {
            LOG.d("Uninstall disabled because intent is already queued");
            return true;
        }

        if (mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_APPS_CONTROL)) {
            LOG.d("Uninstall disabled because user has DISALLOW_APPS_CONTROL restriction");
            return true;
        }

        if (mCarUserManagerHelper.isCurrentProcessUserHasRestriction(
                UserManager.DISALLOW_UNINSTALL_APPS)) {
            LOG.d("Uninstall disabled because user has DISALLOW_UNINSTALL_APPS restriction");
            return true;
        }

        return false;
    }

    /**
     * Returns {@code true} if the package is a Home app that should not be uninstalled. We don't
     * risk downgrading bundled home apps because that can interfere with home-key resolution. We
     * can't allow removal of the only home app, and we don't want to allow removal of an
     * explicitly preferred home app. The user can go to Home settings and pick a different app,
     * after which we'll permit removal of the now-not-default app.
     */
    private boolean shouldDisableUninstallForHomeApp() {
        Set<String> homePackages = new ArraySet<>();
        // Get list of "home" apps and trace through any meta-data references.
        List<ResolveInfo> homeActivities = new ArrayList<>();
        ComponentName currentDefaultHome = mPm.getHomeActivities(homeActivities);
        for (int i = 0; i < homeActivities.size(); i++) {
            ResolveInfo ri = homeActivities.get(i);
            String activityPkg = ri.activityInfo.packageName;
            homePackages.add(activityPkg);

            // Also make sure to include anything proxying for the home app.
            Bundle metadata = ri.activityInfo.metaData;
            if (metadata != null) {
                String metaPkg = metadata.getString(ActivityManager.META_HOME_ALTERNATE);
                if (signaturesMatch(metaPkg, activityPkg)) {
                    homePackages.add(metaPkg);
                }
            }
        }

        if (homePackages.contains(mPackageName)) {
            boolean isBundledApp = (mAppEntry.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
            if (isBundledApp) {
                // Don't risk a downgrade.
                return true;
            } else if (currentDefaultHome == null) {
                // No preferred default. Permit uninstall only when there is more than one
                // candidate.
                return (homePackages.size() == 1);
            } else {
                // Explicit default home app. Forbid uninstall of that one, but permit it for
                // installed-but-inactive ones.
                return mPackageName.equals(currentDefaultHome.getPackageName());
            }
        } else {
            // Not a home app.
            return false;
        }
    }

    private boolean signaturesMatch(String pkg1, String pkg2) {
        if (pkg1 != null && pkg2 != null) {
            try {
                int match = mPm.checkSignatures(pkg1, pkg2);
                if (match >= PackageManager.SIGNATURE_MATCH) {
                    return true;
                }
            } catch (Exception e) {
                // e.g. package not found during lookup. Possibly bad input.
                // Just return false as this isn't a reason to crash given the use case.
            }
        }
        return false;
    }

    private boolean isBundledApp() {
        return (mAppEntry.info.flags & ApplicationInfo.FLAG_SYSTEM) != 0;
    }

    private boolean isAppEnabled() {
        return mAppEntry.info.enabled && !(mAppEntry.info.enabledSetting
                == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == UNINSTALL_REQUEST_CODE) {
            if (resultCode == RESULT_OK) {
                goBack();
            } else {
                LOG.e("Uninstall failed with result " + resultCode);
            }
        }
    }

    private final View.OnClickListener mForceStopClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ConfirmationDialogFragment dialogFragment =
                    new ConfirmationDialogFragment.Builder(getContext())
                            .setTitle(R.string.force_stop_dialog_title)
                            .setMessage(R.string.force_stop_dialog_text)
                            .setPositiveButton(android.R.string.ok,
                                    mForceStopConfirmListener)
                            .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                            .build();
            showDialog(dialogFragment, FORCE_STOP_CONFIRM_DIALOG_TAG);
        }
    };

    private final ConfirmationDialogFragment.ConfirmListener mForceStopConfirmListener =
            new ConfirmationDialogFragment.ConfirmListener() {
                @Override
                public void onConfirm(@Nullable Bundle arguments) {
                    ActivityManager am = (ActivityManager) requireContext().getSystemService(
                            Context.ACTIVITY_SERVICE);
                    LOG.d("Stopping package " + mPackageName);
                    am.forceStopPackage(mPackageName);
                    int userId = UserHandle.getUserId(mAppEntry.info.uid);
                    mAppState.invalidatePackage(mPackageName, userId);
                }
            };

    private final BroadcastReceiver mCheckKillProcessesReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            boolean enabled = getResultCode() != Activity.RESULT_CANCELED;
            LOG.d("Got broadcast response: Restart status for " + mPackageName + " " + enabled);
            updateForceStopButtonInner(enabled);
        }
    };

    private final View.OnClickListener mDisableClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ConfirmationDialogFragment dialogFragment =
                    new ConfirmationDialogFragment.Builder(getContext())
                            .setMessage(getString(R.string.app_disable_dialog_text))
                            .setPositiveButton(R.string.app_disable_dialog_positive,
                                    mDisableConfirmListener)
                            .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                            .build();
            showDialog(dialogFragment, DISABLE_CONFIRM_DIALOG_TAG);
        }
    };

    private final ConfirmationDialogFragment.ConfirmListener mDisableConfirmListener =
            new ConfirmationDialogFragment.ConfirmListener() {
                @Override
                public void onConfirm(@Nullable Bundle arguments) {
                    mPm.setApplicationEnabledSetting(mPackageName,
                            PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER, /* flags= */ 0);
                }
            };

    private final View.OnClickListener mEnableClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            mPm.setApplicationEnabledSetting(mPackageName,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT, /* flags= */ 0);
        }
    };

    private final View.OnClickListener mUninstallClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            Uri packageUri = Uri.parse("package:" + mPackageName);

            Intent uninstallIntent = new Intent(Intent.ACTION_UNINSTALL_PACKAGE, packageUri);
            uninstallIntent.putExtra(Intent.EXTRA_UNINSTALL_ALL_USERS, true);
            uninstallIntent.putExtra(Intent.EXTRA_RETURN_RESULT, true);

            startActivityForResult(uninstallIntent, UNINSTALL_REQUEST_CODE, /* callback= */
                    ApplicationDetailsFragment.this);
        }
    };

    private final ApplicationsState.Callbacks mApplicationStateCallbacks =
            new ApplicationsState.Callbacks() {
                @Override
                public void onRunningStateChanged(boolean running) {
                }

                @Override
                public void onPackageListChanged() {
                    refresh();
                }

                @Override
                public void onRebuildComplete(ArrayList<ApplicationsState.AppEntry> apps) {
                }

                @Override
                public void onPackageIconChanged() {
                }

                @Override
                public void onPackageSizeChanged(String packageName) {
                }

                @Override
                public void onAllSizesComputed() {
                }

                @Override
                public void onLauncherInfoChanged() {
                }

                @Override
                public void onLoadEntriesCompleted() {
                }
            };
}
