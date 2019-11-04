/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.settings.system;

import android.accounts.AccountManager;
import android.annotation.SuppressLint;
import android.app.Fragment;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.DrawableRes;
import androidx.annotation.IntDef;
import androidx.annotation.Keep;
import androidx.leanback.preference.LeanbackSettingsFragment;
import androidx.localbroadcastmanager.content.LocalBroadcastManager;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.widget.ILockSettings;
import com.android.internal.widget.LockPatternUtils;
import com.android.tv.settings.R;
import com.android.tv.settings.SettingsPreferenceFragment;
import com.android.tv.settings.dialog.PinDialogFragment;
import com.android.tv.settings.users.AppRestrictionsFragment;
import com.android.tv.settings.users.RestrictedProfileModel;
import com.android.tv.settings.users.RestrictedProfilePinDialogFragment;
import com.android.tv.settings.users.UserSwitchListenerService;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.List;

/**
 * The security settings screen in Tv settings.
 */
@Keep
public class SecurityFragment extends SettingsPreferenceFragment
        implements RestrictedProfilePinDialogFragment.Callback {

    private static final String TAG = "SecurityFragment";

    private static final String KEY_UNKNOWN_SOURCES = "unknown_sources";
    private static final String KEY_VERIFY_APPS = "verify_apps";
    private static final String KEY_RESTRICTED_PROFILE_GROUP = "restricted_profile_group";
    private static final String KEY_RESTRICTED_PROFILE_ENTER = "restricted_profile_enter";
    private static final String KEY_RESTRICTED_PROFILE_EXIT = "restricted_profile_exit";
    private static final String KEY_RESTRICTED_PROFILE_APPS = "restricted_profile_apps";
    private static final String KEY_RESTRICTED_PROFILE_PIN = "restricted_profile_pin";
    private static final String KEY_RESTRICTED_PROFILE_CREATE = "restricted_profile_create";
    private static final String KEY_RESTRICTED_PROFILE_DELETE = "restricted_profile_delete";

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    private static final String ACTION_RESTRICTED_PROFILE_CREATED =
            "SecurityFragment.RESTRICTED_PROFILE_CREATED";
    private static final String EXTRA_RESTRICTED_PROFILE_INFO =
            "SecurityFragment.RESTRICTED_PROFILE_INFO";
    private static final String SAVESTATE_CREATING_RESTRICTED_PROFILE =
            "SecurityFragment.CREATING_RESTRICTED_PROFILE";

    @Retention(RetentionPolicy.SOURCE)
    @IntDef({PIN_MODE_CHOOSE_LOCKSCREEN,
            PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT,
            PIN_MODE_RESTRICTED_PROFILE_CHANGE_PASSWORD,
            PIN_MODE_RESTRICTED_PROFILE_DELETE})
    private @interface PinMode {}
    private static final int PIN_MODE_CHOOSE_LOCKSCREEN = 1;
    private static final int PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT = 2;
    private static final int PIN_MODE_RESTRICTED_PROFILE_CHANGE_PASSWORD = 3;
    private static final int PIN_MODE_RESTRICTED_PROFILE_DELETE = 4;

    private Preference mUnknownSourcesPref;
    private TwoStatePreference mVerifyAppsPref;
    private PreferenceGroup mRestrictedProfileGroup;
    private Preference mRestrictedProfileEnterPref;
    private Preference mRestrictedProfileExitPref;
    private Preference mRestrictedProfileAppsPref;
    private Preference mRestrictedProfilePinPref;
    private Preference mRestrictedProfileCreatePref;
    private Preference mRestrictedProfileDeletePref;

    private ILockSettings mLockSettingsService;
    private RestrictedProfileModel mRestrictedProfile;

    private boolean mCreatingRestrictedProfile;
    @SuppressLint("StaticFieldLeak")
    private static CreateRestrictedProfileTask sCreateRestrictedProfileTask;
    private final BroadcastReceiver mRestrictedProfileReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            UserInfo result = intent.getParcelableExtra(EXTRA_RESTRICTED_PROFILE_INFO);
            if (isResumed()) {
                onRestrictedUserCreated(result);
            }
        }
    };

    private final Handler mHandler = new Handler();

    public static SecurityFragment newInstance() {
        return new SecurityFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mRestrictedProfile = new RestrictedProfileModel(getContext());

        super.onCreate(savedInstanceState);
        mCreatingRestrictedProfile = savedInstanceState != null
                && savedInstanceState.getBoolean(SAVESTATE_CREATING_RESTRICTED_PROFILE);
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        LocalBroadcastManager.getInstance(getActivity())
                .registerReceiver(mRestrictedProfileReceiver,
                        new IntentFilter(ACTION_RESTRICTED_PROFILE_CREATED));
        if (mCreatingRestrictedProfile) {
            UserInfo userInfo = mRestrictedProfile.getUser();
            if (userInfo != null) {
                onRestrictedUserCreated(userInfo);
            }
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        LocalBroadcastManager.getInstance(getActivity())
                .unregisterReceiver(mRestrictedProfileReceiver);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean(SAVESTATE_CREATING_RESTRICTED_PROFILE, mCreatingRestrictedProfile);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.security, null);

        mUnknownSourcesPref = findPreference(KEY_UNKNOWN_SOURCES);
        mVerifyAppsPref = (TwoStatePreference) findPreference(KEY_VERIFY_APPS);
        mRestrictedProfileGroup = (PreferenceGroup) findPreference(KEY_RESTRICTED_PROFILE_GROUP);
        mRestrictedProfileEnterPref = findPreference(KEY_RESTRICTED_PROFILE_ENTER);
        mRestrictedProfileExitPref = findPreference(KEY_RESTRICTED_PROFILE_EXIT);
        mRestrictedProfileAppsPref = findPreference(KEY_RESTRICTED_PROFILE_APPS);
        mRestrictedProfilePinPref = findPreference(KEY_RESTRICTED_PROFILE_PIN);
        mRestrictedProfileCreatePref = findPreference(KEY_RESTRICTED_PROFILE_CREATE);
        mRestrictedProfileDeletePref = findPreference(KEY_RESTRICTED_PROFILE_DELETE);
    }

    private void refresh() {
        if (mRestrictedProfile.isCurrentUser()) {
            // We are in restricted profile
            mUnknownSourcesPref.setVisible(false);
            mVerifyAppsPref.setVisible(false);

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(true);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(false);
        } else if (mRestrictedProfile.getUser() != null) {
            // Not in restricted profile, but it exists
            mUnknownSourcesPref.setVisible(true);
            mVerifyAppsPref.setVisible(shouldShowVerifierSetting());

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(true);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(true);
            mRestrictedProfilePinPref.setVisible(true);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(true);

            AppRestrictionsFragment.prepareArgs(mRestrictedProfileAppsPref.getExtras(),
                    mRestrictedProfile.getUser().id, false);
        } else if (UserManager.supportsMultipleUsers()) {
            // Not in restricted profile, and it doesn't exist
            mUnknownSourcesPref.setVisible(true);
            mVerifyAppsPref.setVisible(shouldShowVerifierSetting());

            mRestrictedProfileGroup.setVisible(true);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(true);
            mRestrictedProfileDeletePref.setVisible(false);
        } else {
            // Not in restricted profile, and can't create one either
            mUnknownSourcesPref.setVisible(true);
            mVerifyAppsPref.setVisible(shouldShowVerifierSetting());

            mRestrictedProfileGroup.setVisible(false);
            mRestrictedProfileEnterPref.setVisible(false);
            mRestrictedProfileExitPref.setVisible(false);
            mRestrictedProfileAppsPref.setVisible(false);
            mRestrictedProfilePinPref.setVisible(false);
            mRestrictedProfileCreatePref.setVisible(false);
            mRestrictedProfileDeletePref.setVisible(false);
        }

        mRestrictedProfileCreatePref.setEnabled(sCreateRestrictedProfileTask == null);

        mUnknownSourcesPref.setEnabled(!isUnknownSourcesBlocked());
        mVerifyAppsPref.setChecked(isVerifyAppsEnabled());
        mVerifyAppsPref.setEnabled(isVerifierInstalled());
    }

    @Override
    public boolean onPreferenceTreeClick(Preference preference) {
        final String key = preference.getKey();
        if (TextUtils.isEmpty(key)) {
            return super.onPreferenceTreeClick(preference);
        }
        switch (key) {
            case KEY_VERIFY_APPS:
                setVerifyAppsEnabled(mVerifyAppsPref.isChecked());
                return true;
            case KEY_RESTRICTED_PROFILE_ENTER:
                if (mRestrictedProfile.enterUser()) {
                    getActivity().finish();
                }
                return true;
            case KEY_RESTRICTED_PROFILE_EXIT:
                if (hasLockscreenSecurity()) {
                    launchPinDialog(PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT);
                } else {
                    pinFragmentDone(PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT, /* success= */ true);
                }
                return true;
            case KEY_RESTRICTED_PROFILE_PIN:
                launchPinDialog(PIN_MODE_RESTRICTED_PROFILE_CHANGE_PASSWORD);
                return true;
            case KEY_RESTRICTED_PROFILE_CREATE:
                if (hasLockscreenSecurity()) {
                    addRestrictedUser();
                } else {
                    launchPinDialog(PIN_MODE_CHOOSE_LOCKSCREEN);
                }
                return true;
            case KEY_RESTRICTED_PROFILE_DELETE:
                launchPinDialog(PIN_MODE_RESTRICTED_PROFILE_DELETE);
                return true;
        }
        return super.onPreferenceTreeClick(preference);
    }

    private boolean isUnknownSourcesBlocked() {
        final UserManager um = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        return um.hasUserRestriction(UserManager.DISALLOW_INSTALL_UNKNOWN_SOURCES);
    }

    private boolean isVerifyAppsEnabled() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.PACKAGE_VERIFIER_ENABLE, 1) > 0 && isVerifierInstalled();
    }

    private void setVerifyAppsEnabled(boolean enable) {
        Settings.Global.putInt(getContext().getContentResolver(),
                Settings.Global.PACKAGE_VERIFIER_ENABLE, enable ? 1 : 0);
    }

    private boolean isVerifierInstalled() {
        final PackageManager pm = getContext().getPackageManager();
        final Intent verification = new Intent(Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
        verification.setType(PACKAGE_MIME_TYPE);
        verification.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        final List<ResolveInfo> receivers = pm.queryBroadcastReceivers(verification, 0);
        return receivers.size() > 0;
    }

    private boolean shouldShowVerifierSetting() {
        return Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.PACKAGE_VERIFIER_SETTING_VISIBLE, 1) > 0;
    }

    private void launchPinDialog(@PinMode int pinMode) {
        @PinDialogFragment.PinDialogType
        int pinDialogMode;

        switch (pinMode) {
            case PIN_MODE_CHOOSE_LOCKSCREEN:
                pinDialogMode = PinDialogFragment.PIN_DIALOG_TYPE_NEW_PIN;
                break;
            case PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT:
                pinDialogMode = PinDialogFragment.PIN_DIALOG_TYPE_ENTER_PIN;
                break;
            case PIN_MODE_RESTRICTED_PROFILE_CHANGE_PASSWORD:
                pinDialogMode = PinDialogFragment.PIN_DIALOG_TYPE_NEW_PIN;
                break;
            case PIN_MODE_RESTRICTED_PROFILE_DELETE:
                pinDialogMode = PinDialogFragment.PIN_DIALOG_TYPE_DELETE_PIN;
                break;
            default:
                throw new IllegalArgumentException("Unknown pin mode: " + pinMode);
        }

        RestrictedProfilePinDialogFragment restrictedProfilePinDialogFragment =
                RestrictedProfilePinDialogFragment.newInstance(pinDialogMode);
        restrictedProfilePinDialogFragment.setTargetFragment(this, pinMode);
        restrictedProfilePinDialogFragment.show(getFragmentManager(),
                PinDialogFragment.DIALOG_TAG);
    }

    @Override
    public void saveLockPassword(String pin, String originalPin, int quality) {
        new LockPatternUtils(getActivity())
                .saveLockPassword(pin, originalPin, quality, getContext().getUserId());
    }

    @Override
    public void clearLockPassword(String oldPin) {
        byte[] oldPinBytes = oldPin != null ? oldPin.getBytes() : null;
        new LockPatternUtils(getActivity()).clearLock(oldPinBytes, getContext().getUserId());
    }

    @Override
    public boolean checkPassword(String password) {
        return mRestrictedProfile.checkPassword(password);
    }

    @Override
    public boolean hasLockscreenSecurity() {
        return mRestrictedProfile.hasLockscreenSecurity();
    }

    private ILockSettings getLockSettings() {
        if (mLockSettingsService == null) {
            mLockSettingsService = ILockSettings.Stub.asInterface(
                    ServiceManager.getService("lock_settings"));
        }
        return mLockSettingsService;
    }

    @Override
    public void pinFragmentDone(int requestCode, boolean success) {
        switch (requestCode) {
            case PIN_MODE_CHOOSE_LOCKSCREEN:
                if (success) {
                    addRestrictedUser();
                }
                break;
            case PIN_MODE_RESTRICTED_PROFILE_SWITCH_OUT:
                if (success) {
                    mRestrictedProfile.exitUser();
                    getActivity().finish();
                }
                break;
            case PIN_MODE_RESTRICTED_PROFILE_CHANGE_PASSWORD:
                // do nothing
                break;
            case PIN_MODE_RESTRICTED_PROFILE_DELETE:
                if (success) {
                    mHandler.post(() -> {
                        mRestrictedProfile.removeUser();
                        UserSwitchListenerService.updateLaunchPoint(getActivity(), false);
                        refresh();
                    });

                }
                break;
        }
    }

    private void addRestrictedUser() {
        if (sCreateRestrictedProfileTask == null) {
            sCreateRestrictedProfileTask = new CreateRestrictedProfileTask(getContext());
            sCreateRestrictedProfileTask.execute();
            mCreatingRestrictedProfile = true;
        }
        refresh();
    }

    /**
      * Called by other Fragments to decide whether to show or hide profile-related views.
      */
    public static boolean isRestrictedProfileInEffect(Context context) {
        return new RestrictedProfileModel(context).isCurrentUser();
    }

    private void onRestrictedUserCreated(UserInfo result) {
        int userId = result.id;
        if (result.isRestricted()
                && result.restrictedProfileParentId == UserHandle.myUserId()) {
            final AppRestrictionsFragment restrictionsFragment =
                    AppRestrictionsFragment.newInstance(userId, true);
            final Fragment settingsFragment = getCallbackFragment();
            if (settingsFragment instanceof LeanbackSettingsFragment) {
                ((LeanbackSettingsFragment) settingsFragment)
                        .startPreferenceFragment(restrictionsFragment);
            } else {
                throw new IllegalStateException("Didn't find fragment of expected type: "
                        + settingsFragment);
            }
        }
        mCreatingRestrictedProfile = false;
        refresh();
    }

    private static class CreateRestrictedProfileTask extends AsyncTask<Void, Void, UserInfo> {
        private final Context mContext;
        private final UserManager mUserManager;

        CreateRestrictedProfileTask(Context context) {
            mContext = context.getApplicationContext();
            mUserManager = (UserManager) mContext.getSystemService(Context.USER_SERVICE);
        }

        @Override
        protected UserInfo doInBackground(Void... params) {
            UserInfo restrictedUserInfo = mUserManager.createProfileForUser(
                    mContext.getString(R.string.user_new_profile_name),
                    UserInfo.FLAG_RESTRICTED, UserHandle.myUserId());
            if (restrictedUserInfo == null) {
                final UserInfo existingUserInfo = new RestrictedProfileModel(mContext).getUser();
                if (existingUserInfo == null) {
                    Log.wtf(TAG, "Got back a null user handle!");
                }
                return existingUserInfo;
            }
            int userId = restrictedUserInfo.id;
            UserHandle user = new UserHandle(userId);
            mUserManager.setUserRestriction(UserManager.DISALLOW_MODIFY_ACCOUNTS, true, user);
            Bitmap bitmap = createBitmapFromDrawable(R.drawable.ic_avatar_default);
            mUserManager.setUserIcon(userId, bitmap);
            // Add shared accounts
            AccountManager.get(mContext).addSharedAccountsFromParentUser(
                    UserHandle.of(UserHandle.myUserId()), user);
            return restrictedUserInfo;
        }

        @Override
        protected void onPostExecute(UserInfo result) {
            sCreateRestrictedProfileTask = null;
            if (result == null) {
                return;
            }
            UserSwitchListenerService.updateLaunchPoint(mContext, true);
            LocalBroadcastManager.getInstance(mContext).sendBroadcast(
                    new Intent(ACTION_RESTRICTED_PROFILE_CREATED)
                            .putExtra(EXTRA_RESTRICTED_PROFILE_INFO, result));
        }

        private Bitmap createBitmapFromDrawable(@DrawableRes int resId) {
            Drawable icon = mContext.getDrawable(resId);
            if (icon == null) {
                throw new IllegalArgumentException("Drawable is missing!");
            }
            icon.setBounds(0, 0, icon.getIntrinsicWidth(), icon.getIntrinsicHeight());
            Bitmap bitmap = Bitmap.createBitmap(icon.getIntrinsicWidth(), icon.getIntrinsicHeight(),
                    Bitmap.Config.ARGB_8888);
            icon.draw(new Canvas(bitmap));
            return bitmap;
        }
    }

    @Override
    public int getMetricsCategory() {
        return MetricsProto.MetricsEvent.SECURITY;
    }
}
