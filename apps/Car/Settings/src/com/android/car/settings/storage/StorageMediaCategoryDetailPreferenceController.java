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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.preference.Preference;

import com.android.car.settings.R;
import com.android.car.settings.common.FragmentController;
import com.android.settingslib.applications.ApplicationsState;

import java.util.ArrayList;

/**
 * Controller extends the {@link StorageApplicationListPreferenceController} which adds all the
 * application in the parent controller and in addition one more application specific for listing
 * audio files.
 */
public class StorageMediaCategoryDetailPreferenceController extends
        StorageApplicationListPreferenceController {

    private long mExternalAudioBytes;

    public StorageMediaCategoryDetailPreferenceController(Context context,
            String preferenceKey, FragmentController fragmentController,
            CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
    }

    @Override
    public void onDataLoaded(ArrayList<ApplicationsState.AppEntry> apps) {
        super.onDataLoaded(apps);
        Preference preference = createPreference(
                getContext().getString(R.string.storage_audio_files_title),
                Long.toString(mExternalAudioBytes),
                getContext().getDrawable(R.drawable.ic_headset),
                getContext().getString(R.string.pk_storage_music_audio_files));
        // remove the onClickListener which was set above with null key. This preference should
        // do nothing on click.
        preference.setOnPreferenceClickListener(null);
        getPreference().addPreference(preference);
    }

    /**
     * Sets the external audio bytes
     */
    public void setExternalAudioBytes(long externalAudioBytes) {
        mExternalAudioBytes = externalAudioBytes;
    }
}
