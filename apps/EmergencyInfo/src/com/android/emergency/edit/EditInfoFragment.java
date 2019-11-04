/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.emergency.edit;

import android.app.Activity;
import android.app.DialogFragment;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import androidx.preference.Preference.OnPreferenceChangeListener;
import androidx.preference.PreferenceFragment;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import android.util.Log;
import android.widget.Toast;

import com.android.emergency.PreferenceKeys;
import com.android.emergency.R;
import com.android.emergency.ReloadablePreferenceInterface;
import com.android.emergency.preferences.EmergencyContactsPreference;
import com.android.emergency.preferences.EmergencyNamePreference;
import com.android.emergency.util.PreferenceUtils;
import com.android.internal.annotations.VisibleForTesting;
import com.android.settingslib.CustomDialogPreference;
import com.android.settingslib.CustomEditTextPreference;

import java.util.HashMap;
import java.util.Map;

/** Fragment for editing emergency info, including medical info and emergency contacts. */
public class EditInfoFragment extends PreferenceFragment {
    private static final String TAG = "EditInfoFragment";

    /** Result code for contact picker */
    private static final int CONTACT_PICKER_RESULT = 1001;

    private static final String DIALOG_PREFERENCE_TAG = "dialog_preference";

    private final Map<String, Preference> mMedicalInfoPreferences =
            new HashMap<String, Preference>();

    /** The category that holds the emergency contacts. */
    private EmergencyContactsPreference mEmergencyContactsPreferenceCategory;

    private EmergencyNamePreference mEmergencyNamePreference;

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        setPreferencesFromResource(R.xml.edit_emergency_info, rootKey);

        for (String preferenceKey : PreferenceKeys.KEYS_EDIT_EMERGENCY_INFO) {
            Preference preference = findPreference(preferenceKey);
            mMedicalInfoPreferences.put(preferenceKey, preference);

            preference.setOnPreferenceChangeListener(new PreferenceChangeListener(getActivity()));

            if (((ReloadablePreferenceInterface) preference).isNotSet()) {
                getMedicalInfoParent().removePreference(preference);
            }
        }

        mEmergencyNamePreference = (EmergencyNamePreference) findPreference(
                PreferenceKeys.KEY_NAME);

        // Fill in emergency contacts.
        mEmergencyContactsPreferenceCategory = (EmergencyContactsPreference)
                findPreference(PreferenceKeys.KEY_EMERGENCY_CONTACTS);

        Preference addEmergencyContact = findPreference(PreferenceKeys.KEY_ADD_EMERGENCY_CONTACT);
        addEmergencyContact.setOnPreferenceClickListener(new Preference
                .OnPreferenceClickListener() {
            @Override
            public boolean onPreferenceClick(Preference preference) {
                // By using ContactsContract.CommonDataKinds.Phone.CONTENT_URI, the user is
                // presented with a list of contacts, with one entry per phone number.
                // The selected contact is guaranteed to have a name and phone number.
                Intent contactPickerIntent = new Intent(Intent.ACTION_PICK,
                        ContactsContract.CommonDataKinds.Phone.CONTENT_URI);
                try {
                    startActivityForResult(contactPickerIntent, CONTACT_PICKER_RESULT);
                    return true;
                } catch (ActivityNotFoundException e) {
                    Log.w(TAG, "No contact app available to display the contacts", e);
                    Toast.makeText(getContext(),
                                   getContext().getString(R.string.fail_load_contact_picker),
                                   Toast.LENGTH_LONG).show();
                    return false;
                }
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        reloadFromPreference();
        mEmergencyNamePreference.reloadFromUserManager();
    }

    /** Reloads the contacts by reading the value from the shared preferences. */
    public void reloadFromPreference() {
        for (Preference preference : mMedicalInfoPreferences.values()) {
            ReloadablePreferenceInterface reloadablePreference =
                    (ReloadablePreferenceInterface) preference;
            reloadablePreference.reloadFromPreference();
            if (reloadablePreference.isNotSet()) {
                getMedicalInfoParent().removePreference(preference);
            } else {
                // Note: this preference won't be added it if it already exists.
                getMedicalInfoParent().addPreference(preference);
            }
        }
        mEmergencyContactsPreferenceCategory.reloadFromPreference();
    }

    @Override
    public void onDisplayPreferenceDialog(Preference preference) {
        DialogFragment fragment = null;
        if (preference instanceof CustomEditTextPreference) {
            fragment = CustomEditTextPreference.CustomPreferenceDialogFragment
                    .newInstance(preference.getKey());
        } else if (preference instanceof CustomDialogPreference) {
            fragment = EmergencyNamePreference.EmergencyNamePreferenceDialogFragment
                    .newInstance(preference.getKey());
        }
        if (fragment != null) {
            fragment.setTargetFragment(this, 0);
            fragment.show(getFragmentManager(), DIALOG_PREFERENCE_TAG);
        } else {
            super.onDisplayPreferenceDialog(preference);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == CONTACT_PICKER_RESULT && resultCode == Activity.RESULT_OK) {
            Uri phoneUri = data.getData();
            mEmergencyContactsPreferenceCategory.addNewEmergencyContact(phoneUri);
        }
    }

    @VisibleForTesting
    public PreferenceGroup getMedicalInfoParent() {
        return (PreferenceGroup) findPreference(PreferenceKeys.KEY_MEDICAL_INFO);
    }

    @VisibleForTesting
    public Preference getMedicalInfoPreference(String key) {
        return mMedicalInfoPreferences.get(key);
    }

    @VisibleForTesting
    static class PreferenceChangeListener implements OnPreferenceChangeListener {
        private final Context mContext;

        public PreferenceChangeListener(Context context) {
            mContext = context;
        }

        @Override
        public boolean onPreferenceChange(Preference preferenceItem, Object value) {
            // Enable or disable settings suggestion, as appropriate.
            PreferenceUtils.updateSettingsSuggestionState(mContext);
            // If the preference implements OnPreferenceChangeListener, notify it of the
            // change as well.
            if (Preference.OnPreferenceChangeListener.class.isInstance(preferenceItem)) {
                return ((Preference.OnPreferenceChangeListener) preferenceItem)
                    .onPreferenceChange(preferenceItem, value);
            }
            return true;
        }
    }
}
