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

import android.Manifest;
import android.app.NotificationManager;
import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.os.AsyncTask;
import android.os.UserHandle;
import android.provider.Settings;
import android.service.notification.NotificationListenerService;
import android.util.IconDrawableFactory;

import androidx.annotation.VisibleForTesting;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.ServiceListing;

import java.util.List;

/**
 * Displays a list of notification listener services and provides toggles to allow the user to
 * grant/revoke permission for listening to notifications. Before changing the value of a
 * permission, the user is shown a confirmation dialog with information about the risks and
 * potential effects.
 */
public class NotificationAccessPreferenceController extends PreferenceController<PreferenceGroup> {

    private static final Logger LOG = new Logger(NotificationAccessPreferenceController.class);

    @VisibleForTesting
    static final String GRANT_CONFIRM_DIALOG_TAG =
            "com.android.car.settings.applications.specialaccess.GrantNotificationAccessDialog";
    @VisibleForTesting
    static final String REVOKE_CONFIRM_DIALOG_TAG =
            "com.android.car.settings.applications.specialaccess.RevokeNotificationAccessDialog";
    private static final String KEY_SERVICE = "service";

    private final NotificationManager mNm;
    private final ServiceListing mServiceListing;
    private final IconDrawableFactory mIconDrawableFactory;
    private final CarUserManagerHelper mCarUserManagerHelper;

    private final ServiceListing.Callback mCallback = this::onServicesReloaded;

    private final ConfirmationDialogFragment.ConfirmListener mGrantConfirmListener = arguments -> {
        ComponentName service = arguments.getParcelable(KEY_SERVICE);
        grantNotificationAccess(service);
    };
    private final ConfirmationDialogFragment.ConfirmListener mRevokeConfirmListener =
            arguments -> {
                ComponentName service = arguments.getParcelable(KEY_SERVICE);
                revokeNotificationAccess(service);
            };

    public NotificationAccessPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mNm = context.getSystemService(NotificationManager.class);
        mServiceListing = new ServiceListing.Builder(context)
                .setPermission(Manifest.permission.BIND_NOTIFICATION_LISTENER_SERVICE)
                .setIntentAction(NotificationListenerService.SERVICE_INTERFACE)
                .setSetting(Settings.Secure.ENABLED_NOTIFICATION_LISTENERS)
                .setTag(NotificationAccessPreferenceController.class.getSimpleName())
                .setNoun("notification listener") // For logging.
                .build();
        mIconDrawableFactory = IconDrawableFactory.newInstance(context);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        ConfirmationDialogFragment grantConfirmDialogFragment =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        GRANT_CONFIRM_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(grantConfirmDialogFragment,
                mGrantConfirmListener, /* rejectListener= */ null);

        ConfirmationDialogFragment revokeConfirmDialogFragment =
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        REVOKE_CONFIRM_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(revokeConfirmDialogFragment,
                mRevokeConfirmListener, /* rejectListener= */ null);

        mServiceListing.addCallback(mCallback);
    }

    @Override
    protected void onStartInternal() {
        mServiceListing.reload();
        mServiceListing.setListening(true);
    }

    @Override
    protected void onStopInternal() {
        mServiceListing.setListening(false);
    }

    @Override
    protected void onDestroyInternal() {
        mServiceListing.removeCallback(mCallback);
    }

    private void onServicesReloaded(List<ServiceInfo> services) {
        PackageManager packageManager = getContext().getPackageManager();
        services.sort(new PackageItemInfo.DisplayNameComparator(packageManager));
        getPreference().removeAll();
        for (ServiceInfo service : services) {
            ComponentName cn = new ComponentName(service.packageName, service.name);
            CharSequence title = null;
            try {
                title = packageManager.getApplicationInfoAsUser(service.packageName, /* flags= */ 0,
                        mCarUserManagerHelper.getCurrentProcessUserId()).loadLabel(packageManager);
            } catch (PackageManager.NameNotFoundException e) {
                LOG.e("can't find package name", e);
            }
            String summary = service.loadLabel(packageManager).toString();
            SwitchPreference pref = new SwitchPreference(getContext());
            pref.setPersistent(false);
            pref.setIcon(mIconDrawableFactory.getBadgedIcon(service, service.applicationInfo,
                    UserHandle.getUserId(service.applicationInfo.uid)));
            if (title != null && !title.equals(summary)) {
                pref.setTitle(title);
                pref.setSummary(summary);
            } else {
                pref.setTitle(summary);
            }
            pref.setKey(cn.flattenToString());
            pref.setChecked(isAccessGranted(cn));
            pref.setOnPreferenceChangeListener((preference, newValue) -> {
                boolean enable = (boolean) newValue;
                return promptUserToConfirmChange(cn, summary, enable);
            });
            getPreference().addPreference(pref);
        }
    }

    private boolean isAccessGranted(ComponentName service) {
        return mNm.isNotificationListenerAccessGranted(service);
    }

    private void grantNotificationAccess(ComponentName service) {
        mNm.setNotificationListenerAccessGranted(service, /* granted= */ true);
    }

    private void revokeNotificationAccess(ComponentName service) {
        mNm.setNotificationListenerAccessGranted(service, /* granted= */ false);
        AsyncTask.execute(() -> {
            if (!mNm.isNotificationPolicyAccessGrantedForPackage(service.getPackageName())) {
                mNm.removeAutomaticZenRules(service.getPackageName());
            }
        });
    }

    private boolean promptUserToConfirmChange(ComponentName service, String label,
            boolean grantAccess) {
        if (isAccessGranted(service) == grantAccess) {
            return true;
        }
        ConfirmationDialogFragment.Builder dialogFragment =
                grantAccess ? createConfirmGrantDialogFragment(label)
                        : createConfirmRevokeDialogFragment(label);
        dialogFragment.addArgumentParcelable(KEY_SERVICE, service);
        getFragmentController().showDialog(dialogFragment.build(),
                grantAccess ? GRANT_CONFIRM_DIALOG_TAG : REVOKE_CONFIRM_DIALOG_TAG);
        return false;
    }

    private ConfirmationDialogFragment.Builder createConfirmGrantDialogFragment(String label) {
        String title = getContext().getResources().getString(
                R.string.notification_listener_security_warning_title, label);
        String summary = getContext().getResources().getString(
                R.string.notification_listener_security_warning_summary, label);
        return new ConfirmationDialogFragment.Builder(getContext())
                .setTitle(title)
                .setMessage(summary)
                .setPositiveButton(R.string.allow, mGrantConfirmListener)
                .setNegativeButton(R.string.deny, /* rejectionListener= */ null);
    }

    private ConfirmationDialogFragment.Builder createConfirmRevokeDialogFragment(String label) {
        String summary = getContext().getResources().getString(
                R.string.notification_listener_revoke_warning_summary, label);
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(summary)
                .setPositiveButton(R.string.notification_listener_revoke_warning_confirm,
                        mRevokeConfirmListener)
                .setNegativeButton(R.string.notification_listener_revoke_warning_cancel,
                        /* rejectionListener= */ null);
    }
}
