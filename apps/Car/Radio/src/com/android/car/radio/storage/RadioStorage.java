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

package com.android.car.radio.storage;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.radio.ProgramSelector;
import android.net.Uri;
import android.os.AsyncTask;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LiveData;

import com.android.car.broadcastradio.support.Program;
import com.android.car.broadcastradio.support.platform.ProgramSelectorExt;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.util.Log;

import java.util.List;
import java.util.Objects;

/**
 * Manages persistent storage for broadcast radio application.
 */
public class RadioStorage {
    private static final String TAG = "BcRadioApp.storage";
    private static final String PREF_NAME = "RadioAppPrefs";

    private static final String PREF_KEY_RECENT_TYPE = "recentProgramType";
    private static final String PREF_KEY_RECENT_PROGRAM_PREFIX = "recentProgram-";

    private static RadioStorage sInstance;

    private final SharedPreferences mPrefs;
    private final RadioDatabase mDatabase;
    private final LiveData<List<Program>> mFavorites;

    private RadioStorage(Context context) {
        mPrefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        mDatabase = RadioDatabase.buildInstance(context);

        mFavorites = mDatabase.getAllFavorites();
    }

    /**
     * Returns singleton instance of {@link RadioStorage}.
     */
    public static @NonNull RadioStorage getInstance(Context context) {
        if (sInstance != null) return sInstance;
        synchronized (RadioStorage.class) {
            if (sInstance != null) return sInstance;
            sInstance = new RadioStorage(context.getApplicationContext());
            return sInstance;
        }
    }

    /**
     * Returns a list of all favorites added previously by the user.
     */
    @NonNull
    public LiveData<List<Program>> getFavorites() {
        return mFavorites;
    }

    /**
     * Checks, if a given program is favorite.
     *
     * @param favorites List of favorites.
     * @param selector Program to check.
     */
    public static boolean isFavorite(@NonNull List<Program> favorites,
            @NonNull ProgramSelector selector) {
        return favorites.contains(new Program(selector, ""));
    }

    /**
     * Checks, if a given program is favorite.
     *
     * @param selector Program to check.
     */
    public boolean isFavorite(@NonNull ProgramSelector selector) {
        List<Program> favorites = mFavorites.getValue();
        if (favorites == null) {
            Log.w(TAG, "Database is not ready yet");
            return false;
        }
        return isFavorite(favorites, selector);
    }

    private class AddFavoriteTask extends AsyncTask<Program, Void, Void> {
        @Override
        protected Void doInBackground(Program... programs) {
            mDatabase.insertFavorite(programs[0]);
            return null;
        }
    }

    private class RemoveFavoriteTask extends AsyncTask<ProgramSelector, Void, Void> {
        @Override
        protected Void doInBackground(ProgramSelector... selectors) {
            mDatabase.removeFavorite(selectors[0]);
            return null;
        }
    }

    /**
     * Adds a new program to the favorites list.
     *
     * After the operation succeeds, the list is refreshed via live object returned
     * from {@link #getFavorites}.
     *
     * @param favorite A program to add.
     */
    public void addFavorite(@NonNull Program favorite) {
        new AddFavoriteTask().execute(Objects.requireNonNull(favorite));
    }

    /**
     * Removes a program from the favorites list.
     *
     * After the operation succeeds, the list is refreshed via live object returned
     * from {@link #getFavorites}.
     *
     * @param favorite A program to remove.
     */
    public void removeFavorite(@NonNull ProgramSelector favorite) {
        new RemoveFavoriteTask().execute(Objects.requireNonNull(favorite));
    }

    /**
     * Stores recently selected program so it can be recalled on next app launch.
     *
     * @param sel Program to store as recently selected.
     */
    public void setRecentlySelected(@NonNull ProgramSelector sel) {
        ProgramType pt = ProgramType.fromSelector(sel);
        int ptid = pt == null ? 0 : pt.id;

        SharedPreferences.Editor editor = mPrefs.edit();
        boolean hasChanges = false;

        String prefName = PREF_KEY_RECENT_PROGRAM_PREFIX + ptid;
        Uri selUri = ProgramSelectorExt.toUri(sel);
        if (selUri == null) return;
        String selUriStr = selUri.toString();
        if (!mPrefs.getString(prefName, "").equals(selUriStr)) {
            editor.putString(prefName, selUriStr);
            hasChanges = true;
        }

        if (mPrefs.getInt(PREF_KEY_RECENT_TYPE, -1) != ptid) {
            editor.putInt(PREF_KEY_RECENT_TYPE, ptid);
            hasChanges = true;
        }

        if (hasChanges) editor.apply();
    }

    /**
     * Retrieves recently selected program.
     *
     * This function can either retrieve the recently selected program for a specific
     * {@link ProgramType} (band) or just the recently selected program in general.
     *
     * @param pt Program type to filter the result on, or {@code null} for general check
     * @return Selector of the recent program or {@code null}, if there was none saved
     */
    public @Nullable ProgramSelector getRecentlySelected(@Nullable ProgramType pt) {
        int ptid = pt != null ? pt.id : mPrefs.getInt(PREF_KEY_RECENT_TYPE, -1);
        if (ptid == -1) return null;

        String selUriStr = mPrefs.getString(PREF_KEY_RECENT_PROGRAM_PREFIX + ptid, "");
        if (selUriStr.equals("")) return null;

        return ProgramSelectorExt.fromUri(Uri.parse(selUriStr));
    }
}
