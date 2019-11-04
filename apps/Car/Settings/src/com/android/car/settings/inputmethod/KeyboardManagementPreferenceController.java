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

package com.android.car.settings.inputmethod;

import android.app.admin.DevicePolicyManager;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.pm.PackageManager;
import android.view.inputmethod.InputMethodInfo;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.SwitchPreference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Updates the available keyboard list. */
public class KeyboardManagementPreferenceController extends
        PreferenceController<PreferenceGroup> {
    @VisibleForTesting
    static final String DIRECT_BOOT_WARN_DIALOG_TAG = "DirectBootWarnDialog";
    @VisibleForTesting
    static final String SECURITY_WARN_DIALOG_TAG = "SecurityWarnDialog";
    private static final String KEY_INPUT_METHOD_INFO = "INPUT_METHOD_INFO";
    private final InputMethodManager mInputMethodManager;
    private final DevicePolicyManager mDevicePolicyManager;
    private final PackageManager mPackageManager;
    private final ConfirmationDialogFragment.ConfirmListener mDirectBootWarnConfirmListener =
            args -> {
                InputMethodInfo inputMethodInfo = args.getParcelable(KEY_INPUT_METHOD_INFO);
                InputMethodUtil.enableInputMethod(getContext().getContentResolver(),
                        inputMethodInfo);
                refreshUi();
            };
    private final ConfirmationDialogFragment.RejectListener mRejectListener = args ->
            refreshUi();
    private final ConfirmationDialogFragment.ConfirmListener mSecurityWarnDialogConfirmListener =
            args -> {
                InputMethodInfo inputMethodInfo = args.getParcelable(KEY_INPUT_METHOD_INFO);
                // The user confirmed to enable a 3rd party IME, but we might need to prompt if
                // it's not
                // Direct Boot aware.
                if (inputMethodInfo.getServiceInfo().directBootAware) {
                    InputMethodUtil.enableInputMethod(getContext().getContentResolver(),
                            inputMethodInfo);
                    refreshUi();
                } else {
                    showDirectBootWarnDialog(inputMethodInfo);
                }
            };

    public KeyboardManagementPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mPackageManager = context.getPackageManager();
        mDevicePolicyManager =
                (DevicePolicyManager) context.getSystemService(Context.DEVICE_POLICY_SERVICE);
        mInputMethodManager =
                (InputMethodManager) context.getSystemService(Context.INPUT_METHOD_SERVICE);
    }

    @Override
    protected void onCreateInternal() {
        super.onCreateInternal();

        ConfirmationDialogFragment dialogFragment = (ConfirmationDialogFragment)
                getFragmentController().findDialogByTag(DIRECT_BOOT_WARN_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(dialogFragment,
                mDirectBootWarnConfirmListener,
                mRejectListener);

        dialogFragment = (ConfirmationDialogFragment) getFragmentController()
                .findDialogByTag(SECURITY_WARN_DIALOG_TAG);
        ConfirmationDialogFragment.resetListeners(dialogFragment,
                mSecurityWarnDialogConfirmListener,
                mRejectListener);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<String> permittedInputMethods = mDevicePolicyManager
                .getPermittedInputMethodsForCurrentUser();
        Set<String> permittedInputMethodsSet = permittedInputMethods == null ? null : new HashSet<>(
                permittedInputMethods);

        preferenceGroup.removeAll();

        List<InputMethodInfo> inputMethodInfos = mInputMethodManager.getInputMethodList();
        if (inputMethodInfos == null || inputMethodInfos.size() == 0) {
            return;
        }

        Collections.sort(inputMethodInfos, Comparator.comparing(
                (InputMethodInfo a) -> InputMethodUtil.getPackageLabel(mPackageManager, a))
                .thenComparing((InputMethodInfo a) -> InputMethodUtil.getSummaryString(getContext(),
                        mInputMethodManager, a)));

        for (InputMethodInfo inputMethodInfo : inputMethodInfos) {
            if (!isInputMethodAllowedByOrganization(permittedInputMethodsSet, inputMethodInfo)) {
                continue;
            }

            Preference preference = createSwitchPreference(inputMethodInfo);

            preference.setEnabled(!isOnlyEnabledDefaultInputMethod(inputMethodInfo));

            preferenceGroup.addPreference(preference);
        }
    }

    private boolean isInputMethodAllowedByOrganization(Set<String> permittedList,
            InputMethodInfo inputMethodInfo) {
        // permittedList is null means that all input methods are allowed.
        return (permittedList == null) || permittedList.contains(inputMethodInfo.getPackageName());
    }

    /**
     * Check if given input method is the only enabled input method that can be a default system
     * input method.
     *
     * @return {@code true} if input method is the only input method that can be a default system
     * input method.
     */
    private boolean isOnlyEnabledDefaultInputMethod(InputMethodInfo inputMethodInfo) {
        if (!inputMethodInfo.isDefault(getContext())) {
            return false;
        }

        List<InputMethodInfo> inputMethodInfos = mInputMethodManager.getEnabledInputMethodList();

        for (InputMethodInfo imi : inputMethodInfos) {
            if (!imi.isDefault(getContext())) {
                continue;
            }

            if (!imi.getId().equals(inputMethodInfo.getId())) {
                return false;
            }
        }

        return true;
    }

    /**
     * Create a SwitchPreference to enable/disable an input method.
     *
     * @return {@code SwitchPreference} which allows a user to enable/disable an input method.
     */
    private SwitchPreference createSwitchPreference(InputMethodInfo inputMethodInfo) {
        SwitchPreference switchPreference = new SwitchPreference(getContext());
        switchPreference.setKey(String.valueOf(inputMethodInfo.getId()));
        switchPreference.setIcon(InputMethodUtil.getPackageIcon(mPackageManager, inputMethodInfo));
        switchPreference.setTitle(InputMethodUtil.getPackageLabel(mPackageManager,
                inputMethodInfo));
        switchPreference.setChecked(InputMethodUtil.isInputMethodEnabled(getContext()
                .getContentResolver(), inputMethodInfo));
        switchPreference.setSummary(InputMethodUtil.getSummaryString(getContext(),
                mInputMethodManager, inputMethodInfo));

        switchPreference.setOnPreferenceChangeListener((switchPref, newValue) -> {
            boolean enable = (boolean) newValue;
            if (enable) {
                showSecurityWarnDialog(inputMethodInfo);
            } else {
                InputMethodUtil.disableInputMethod(getContext(), mInputMethodManager,
                        inputMethodInfo);
                refreshUi();
            }
            return false;
        });
        return switchPreference;
    }

    private void showDirectBootWarnDialog(InputMethodInfo inputMethodInfo) {
        ConfirmationDialogFragment dialog = new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(getContext().getString(R.string.direct_boot_unaware_dialog_message_car))
                .setPositiveButton(android.R.string.ok, mDirectBootWarnConfirmListener)
                .setNegativeButton(android.R.string.cancel, mRejectListener)
                .addArgumentParcelable(KEY_INPUT_METHOD_INFO, inputMethodInfo)
                .build();

        getFragmentController().showDialog(dialog, DIRECT_BOOT_WARN_DIALOG_TAG);
    }

    private void showSecurityWarnDialog(InputMethodInfo inputMethodInfo) {
        CharSequence label = inputMethodInfo.loadLabel(mPackageManager);

        ConfirmationDialogFragment dialog = new ConfirmationDialogFragment.Builder(getContext())
                .setTitle(android.R.string.dialog_alert_title)
                .setMessage(getContext().getString(R.string.ime_security_warning, label))
                .setPositiveButton(android.R.string.ok, mSecurityWarnDialogConfirmListener)
                .setNegativeButton(android.R.string.cancel, mRejectListener)
                .addArgumentParcelable(KEY_INPUT_METHOD_INFO, inputMethodInfo)
                .build();

        getFragmentController().showDialog(dialog, SECURITY_WARN_DIALOG_TAG);
    }
}
