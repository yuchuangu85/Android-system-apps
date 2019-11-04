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

package com.android.car.settings.applications.defaultapps;

import android.car.drivingstate.CarUxRestrictions;
import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.text.TextUtils;

import androidx.annotation.NonNull;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;
import com.android.settingslib.applications.DefaultAppInfo;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Defines the shared logic in picking a default application. */
public abstract class DefaultAppsPickerBasePreferenceController extends
        PreferenceController<PreferenceGroup> implements Preference.OnPreferenceClickListener {

    private static final Logger LOG = new Logger(DefaultAppsPickerBasePreferenceController.class);
    private static final String DIALOG_KEY_ARG = "key_arg";
    protected static final String NONE_PREFERENCE_KEY = "";

    private final CarUserManagerHelper mCarUserManagerHelper;
    private final Map<String, DefaultAppInfo> mDefaultAppInfoMap = new HashMap<>();
    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener = arguments -> {
        setCurrentDefault(arguments.getString(DIALOG_KEY_ARG));
        refreshUi();
    };
    private List<DefaultAppInfo> mCurrentCandidates;

    public DefaultAppsPickerBasePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarUserManagerHelper = new CarUserManagerHelper(context);
    }

    @Override
    protected Class<PreferenceGroup> getPreferenceType() {
        return PreferenceGroup.class;
    }

    @Override
    protected void onCreateInternal() {
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG),
                mConfirmListener,
                /* rejectListener= */ null);
    }

    @Override
    protected void updateState(PreferenceGroup preferenceGroup) {
        List<DefaultAppInfo> defaultAppInfos = getCandidates();
        if (!equalToCurrentCandidates(defaultAppInfos)) {
            mCurrentCandidates = defaultAppInfos;
            preferenceGroup.removeAll();
            if (includeNonePreference()) {
                preferenceGroup.addPreference(createNonePreference());
            }
            if (mCurrentCandidates != null) {
                for (DefaultAppInfo info : mCurrentCandidates) {
                    mDefaultAppInfoMap.put(info.getKey(), info);

                    Preference preference = new Preference(getContext());
                    bindPreference(preference, info);
                    getPreference().addPreference(preference);
                }
            } else {
                LOG.i("no candidate provided");
            }
        }

        // This is done separately from above, since the summary can change without changing the
        // list of candidates.
        for (int i = 0; i < preferenceGroup.getPreferenceCount(); i++) {
            Preference preference = preferenceGroup.getPreference(i);
            String newPreferenceSummary = TextUtils.equals(preference.getKey(),
                    getCurrentDefaultKey()) ? getContext().getString(
                    R.string.default_app_selected_app) : "";
            preference.setSummary(newPreferenceSummary);
        }
    }

    @Override
    public boolean onPreferenceClick(Preference preference) {
        String selectedKey = preference.getKey();
        if (TextUtils.equals(selectedKey, getCurrentDefaultKey())) {
            return false;
        }

        CharSequence message = getConfirmationMessage(mDefaultAppInfoMap.get(selectedKey));
        if (!TextUtils.isEmpty(message)) {
            ConfirmationDialogFragment dialogFragment =
                    new ConfirmationDialogFragment.Builder(getContext())
                            .setMessage(message.toString())
                            .setPositiveButton(android.R.string.ok, mConfirmListener)
                            .setNegativeButton(android.R.string.cancel, /* rejectListener= */ null)
                            .addArgumentString(DIALOG_KEY_ARG, selectedKey)
                            .build();
            getFragmentController().showDialog(dialogFragment, ConfirmationDialogFragment.TAG);
        } else {
            setCurrentDefault(selectedKey);
            refreshUi();
        }
        return true;
    }

    /** Modifies the preference based on the information provided. */
    protected void bindPreference(Preference preference, DefaultAppInfo info) {
        preference.setTitle(info.loadLabel());
        preference.setKey(info.getKey());
        preference.setEnabled(info.enabled);
        preference.setOnPreferenceClickListener(this);
        DefaultAppUtils.setSafeIcon(preference, info.loadIcon(),
                getContext().getResources().getInteger(R.integer.default_app_safe_icon_size));
    }

    /** Gets all of the candidates that should be considered when choosing a default application. */
    @NonNull
    protected abstract List<DefaultAppInfo> getCandidates();

    /** Gets the key of the currently selected candidate. */
    protected abstract String getCurrentDefaultKey();

    /**
     * Sets the key of the currently selected candidate.
     *
     * @param key represents the key from {@link DefaultAppInfo} which should mark the default
     *            application.
     */
    protected abstract void setCurrentDefault(String key);

    /**
     * Defines the warning dialog message to be shown when a default app is selected.
     */
    protected CharSequence getConfirmationMessage(DefaultAppInfo info) {
        return null;
    }

    /** Gets the current process user id. */
    protected int getCurrentProcessUserId() {
        return mCarUserManagerHelper.getCurrentProcessUserId();
    }

    /**
     * Determines whether the list of default apps should include "none". Implementation classes can
     * override this value to {@code false} in order to remove the "none" preference.
     */
    protected boolean includeNonePreference() {
        return true;
    }

    private Preference createNonePreference() {
        Preference nonePreference = new Preference(getContext());
        nonePreference.setKey(NONE_PREFERENCE_KEY);
        nonePreference.setTitle(R.string.app_list_preference_none);
        nonePreference.setOnPreferenceClickListener(this);
        nonePreference.setIcon(R.drawable.ic_remove_circle);
        return nonePreference;
    }

    /**
     * Check that the provided {@link DefaultAppInfo} list is equivalent to the current list of
     * candidates.
     */
    private boolean equalToCurrentCandidates(@NonNull List<DefaultAppInfo> defaultAppInfos) {
        if (mCurrentCandidates == null) {
            return false;
        }

        Set<String> keys = new HashSet<>();
        for (DefaultAppInfo info : mCurrentCandidates) {
            keys.add(info.getKey());
        }
        for (DefaultAppInfo info : defaultAppInfos) {
            if (!keys.remove(info.getKey())) {
                return false;
            }
        }
        return keys.isEmpty();
    }
}
