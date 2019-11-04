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

package com.android.car.settings.sound;

import android.app.Activity;
import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.UserHandle;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.Logger;
import com.android.car.settings.common.PreferenceController;

/** Business logic for changing the default ringtone. */
public class RingtonePreferenceController extends
        PreferenceController<RingtonePreference> implements ActivityResultCallback {

    private static final Logger LOG = new Logger(RingtonePreferenceController.class);
    @VisibleForTesting
    static final int REQUEST_CODE = 16;

    // We use a user context so that default ringtones can differ per user.
    private final Context mUserContext;

    public RingtonePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mUserContext = createPackageContextAsUser(getContext(), UserHandle.myUserId());
    }

    @Override
    protected Class<RingtonePreference> getPreferenceType() {
        return RingtonePreference.class;
    }

    @Override
    protected void updateState(RingtonePreference preference) {
        Uri ringtoneUri = RingtoneManager.getActualDefaultRingtoneUri(getContext(),
                getPreference().getRingtoneType());
        preference.setSummary(Ringtone.getTitle(getContext(), ringtoneUri, /* followSettingsUri= */
                false, /* allowRemote= */ true));
    }

    @Override
    protected boolean handlePreferenceClicked(RingtonePreference preference) {
        onPrepareRingtonePickerIntent(preference, preference.getIntent());
        getFragmentController().startActivityForResult(preference.getIntent(), REQUEST_CODE, this);
        return true;
    }

    @Override
    public void processActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_CODE) {
            if (resultCode != Activity.RESULT_OK || data == null) {
                return;
            }

            Uri ringtoneUri = data.getParcelableExtra(
                    RingtoneManager.EXTRA_RINGTONE_PICKED_URI);
            RingtoneManager.setActualDefaultRingtoneUri(mUserContext,
                    getPreference().getRingtoneType(), ringtoneUri);
            refreshUi();
        }
    }

    /**
     * Prepares the intent to launch the ringtone picker. This can be modified
     * to adjust the parameters of the ringtone picker.
     */
    private void onPrepareRingtonePickerIntent(RingtonePreference ringtonePreference,
            Intent ringtonePickerIntent) {
        Uri currentRingtone = RingtoneManager.getActualDefaultRingtoneUri(mUserContext,
                ringtonePreference.getRingtoneType());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI,
                currentRingtone);

        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TITLE,
                ringtonePreference.getTitle());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE,
                ringtonePreference.getRingtoneType());
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                ringtonePreference.getShowSilent());

        // Since we are picking the default ringtone, no need to show system default.
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_DEFAULT, false);
        // Allow playback in external activity.
        ringtonePickerIntent.putExtra(RingtoneManager.EXTRA_RINGTONE_AUDIO_ATTRIBUTES_FLAGS,
                AudioAttributes.FLAG_BYPASS_INTERRUPTION_POLICY);
    }

    /**
     * Returns a context created from the given context for the given user, or null if it fails.
     */
    private Context createPackageContextAsUser(Context context, int userId) {
        try {
            return context.createPackageContextAsUser(
                    context.getPackageName(), 0 /* flags */, UserHandle.of(userId));
        } catch (PackageManager.NameNotFoundException e) {
            LOG.e("Failed to create user context", e);
        }
        return null;
    }
}
