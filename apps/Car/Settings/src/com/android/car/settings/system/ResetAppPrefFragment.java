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

package com.android.car.settings.system;

import android.app.AppOpsManager;
import android.app.INotificationManager;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageManager;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.SettingsFragment;

import java.lang.ref.WeakReference;
import java.util.List;

/**
 * Presents the user with information about resetting app preferences.
 */
public class ResetAppPrefFragment extends SettingsFragment {

    private static final Logger LOG = new Logger(ResetAppPrefFragment.class);

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.reset_app_pref_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        Button resetAppsButton = requireActivity().findViewById(R.id.action_button1);
        resetAppsButton.setText(requireContext().getString(R.string.reset_app_pref_button_text));
        resetAppsButton.setOnClickListener(v -> resetAppPreferences());
    }

    private void resetAppPreferences() {
        new ResetTask(requireContext().getApplicationContext()).execute();
    }

    private static class ResetTask extends AsyncTask<Void, Void, Void> {

        private final WeakReference<Context> mContext;

        ResetTask(Context context) {
            mContext = new WeakReference<>(context);
        }

        @Override
        protected Void doInBackground(Void... unused) {
            Context context = mContext.get();
            if (context == null) {
                LOG.w("Unable to reset app preferences. Null context");
                return null;
            }
            PackageManager packageManager = context.getPackageManager();
            IBinder notificationManagerServiceBinder = ServiceManager.getService(
                    Context.NOTIFICATION_SERVICE);
            if (notificationManagerServiceBinder == null) {
                LOG.w("Unable to reset app preferences. Null notification manager service");
                return null;
            }
            INotificationManager notificationManagerService =
                    INotificationManager.Stub.asInterface(notificationManagerServiceBinder);

            // Reset app notifications.
            // Reset disabled apps.
            List<ApplicationInfo> apps = packageManager.getInstalledApplications(
                    PackageManager.MATCH_DISABLED_COMPONENTS);
            for (ApplicationInfo app : apps) {
                try {
                    notificationManagerService.setNotificationsEnabledForPackage(
                            app.packageName,
                            app.uid, true);
                } catch (RemoteException e) {
                    LOG.w("Unable to reset notification preferences for app: " + app.name, e);
                }
                if (!app.enabled) {
                    if (packageManager.getApplicationEnabledSetting(app.packageName)
                            == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER) {
                        packageManager.setApplicationEnabledSetting(app.packageName,
                                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                                PackageManager.DONT_KILL_APP);
                    }
                }
            }

            // Reset default applications for actions.
            // Reset background data restrictions for apps.
            // Reset permission restrictions.
            try {
                IBinder packageManagerServiceBinder = ServiceManager.getService("package");
                if (packageManagerServiceBinder == null) {
                    LOG.w("Unable to reset app preferences. Null package manager service");
                    return null;
                }
                IPackageManager.Stub.asInterface(
                        packageManagerServiceBinder).resetApplicationPreferences(
                        new CarUserManagerHelper(context).getCurrentForegroundUserId());
            } catch (RemoteException e) {
                LOG.w("Unable to reset app preferences", e);
            }

            // Cleanup.
            ((AppOpsManager) context.getSystemService(Context.APP_OPS_SERVICE)).resetAllModes();

            return null;
        }

        @Override
        protected void onPostExecute(Void unused) {
            super.onPostExecute(unused);
            Context context = mContext.get();
            if (context != null) {
                Toast.makeText(context, R.string.reset_app_pref_complete_toast,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }
}
