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

package com.android.pump.fragment;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.android.pump.R;
import com.android.pump.activity.PumpActivity;
import com.android.pump.util.IntentUtils;

import java.util.Collection;
import java.util.HashSet;

@UiThread
public class PermissionFragment extends Fragment {
    private static final int REQUEST_REQUIRED_PERMISSIONS = 42;
    private static final String[] REQUIRED_PERMISSIONS = {
        Manifest.permission.INTERNET,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    private boolean mShowRationale;

    public static @NonNull Fragment newInstance() {
        return new PermissionFragment();
    }

    @Override
    public @NonNull View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_permission, container, false);

        view.findViewById(R.id.fragment_permission_button)
                .setOnClickListener((v) -> requestMissingRequiredPermissions());

        return view;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        if (requestCode == REQUEST_REQUIRED_PERMISSIONS) {
            boolean granted = true;
            boolean showRationale = false;
            for (int i = 0; i < grantResults.length; ++i) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    granted = false;
                    showRationale |= shouldShowRequestPermissionRationale(permissions[i]);
                }
            }
            if (granted) {
                // TODO We shouldn't reference PumpActivity from here
                ((PumpActivity) requireActivity()).initialize();
            } else if (!showRationale && !mShowRationale) {
                // If we were not supposed to show the rationale before requestPermissions(...) and
                // we still shouldn't show the rationale it means the user previously selected
                // "don't ask again" in the permission request dialog. In this case we bring up the
                // system permission settings for this package.
                Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                intent.setData(Uri.fromParts("package", requireActivity().getPackageName(), null));
                IntentUtils.startExternalActivity(requireContext(), intent);
            }
        } else {
            super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void requestMissingRequiredPermissions() {
        Collection<String> missing = new HashSet<>();

        mShowRationale = false;
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(requireContext(), permission)
                    != PackageManager.PERMISSION_GRANTED) {
                missing.add(permission);
                mShowRationale |= shouldShowRequestPermissionRationale(permission);
            }
        }

        if (!missing.isEmpty()) {
            requestPermissions(missing.toArray(new String[0]), REQUEST_REQUIRED_PERMISSIONS);
        }
    }
}
