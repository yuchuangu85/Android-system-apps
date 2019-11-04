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
package com.google.android.car.kitchensink.storagevolumes;

import android.annotation.Nullable;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.storage.StorageManager;
import android.os.storage.VolumeInfo;
import android.provider.MediaStore;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.List;
import java.util.Objects;

/**
 * Listens for changes in content and displays updated list of volumes
 */
public class StorageVolumesFragment extends Fragment {
    private static final String TAG = "CAR.STORAGEVOLUMES.KS";

    private ListView mStorageVolumesList;
    private StorageManager mStorageManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        mStorageManager = Objects.requireNonNull(getContext())
                .getSystemService(StorageManager.class);

        super.onCreate(savedInstanceState);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        mStorageVolumesList = (ListView) inflater.inflate(R.layout.storagevolumes,
                container, false);
        return mStorageVolumesList;
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
        Objects.requireNonNull(getContext()).getContentResolver()
                .registerContentObserver(MediaStore.AUTHORITY_URI, true, mContentObserver);
    }

    @Override
    public void onPause() {
        Objects.requireNonNull(getContext()).getContentResolver()
                .unregisterContentObserver(mContentObserver);
        super.onPause();
    }

    private void refresh() {
        final List<VolumeInfo> volumes = mStorageManager.getVolumes();
        volumes.sort(VolumeInfo.getDescriptionComparator());

        mStorageVolumesList.setAdapter(new ArrayAdapter<>(getContext(),
                android.R.layout.simple_list_item_1, volumes.toArray()));
    }

    private final ContentObserver mContentObserver = new ContentObserver(new Handler()) {
        @Override
        public void onChange(boolean selfChange, Uri uri) {
            super.onChange(selfChange, uri);
            if (isResumed()) {
                Log.d(TAG, "Content has changed for URI " + uri);
                refresh();
            }
        }
    };
}
