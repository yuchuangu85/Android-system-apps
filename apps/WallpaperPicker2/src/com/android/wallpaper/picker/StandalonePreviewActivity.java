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
package com.android.wallpaper.picker;

import android.Manifest.permission;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Bundle;
import android.util.Log;

import com.android.wallpaper.R;
import com.android.wallpaper.model.ImageWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.UserEventLogger;

import androidx.annotation.NonNull;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

/**
 * Activity that displays a preview of a specific wallpaper and provides the ability to set the
 * wallpaper as the user's current wallpaper. It's "standalone" meaning it doesn't reside in the
 * app navigation hierarchy and can be launched directly via an explicit intent.
 */
public class StandalonePreviewActivity extends BasePreviewActivity {
    private static final String TAG = "StandalonePreview";
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE = 1;

    private UserEventLogger mUserEventLogger;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_preview);

        mUserEventLogger = InjectorProvider.getInjector().getUserEventLogger(getApplicationContext());
        mUserEventLogger.logStandalonePreviewLaunched();

        Intent cropAndSetWallpaperIntent = getIntent();
        Uri imageUri = cropAndSetWallpaperIntent.getData();

        if (imageUri == null) {
            Log.e(TAG, "No URI passed in intent; exiting StandalonePreviewActivity");
            finish();
            return;
        }

        // Check if READ_EXTERNAL_STORAGE permission is needed because the app invoking this activity
        // passed a file:// URI or a content:// URI without a flag to grant read permission.
        boolean isReadPermissionGrantedForImageUri = isReadPermissionGrantedForImageUri(imageUri);
        mUserEventLogger.logStandalonePreviewImageUriHasReadPermission(
                isReadPermissionGrantedForImageUri);

        // Request storage permission if necessary (i.e., on Android M and later if storage permission
        // has not already been granted) and delay loading the PreviewFragment until the permission is
        // granted.
        if (!isReadPermissionGrantedForImageUri && !isReadExternalStoragePermissionGrantedForApp()) {
            requestPermissions(
                    new String[]{permission.READ_EXTERNAL_STORAGE},
                    READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();

        FragmentManager fragmentManager = getSupportFragmentManager();
        Fragment fragment = fragmentManager.findFragmentById(R.id.fragment_container);

        if (fragment == null) {
            loadPreviewFragment();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // Load the preview fragment if the storage permission was granted.
        if (requestCode == READ_EXTERNAL_STORAGE_PERMISSION_REQUEST_CODE) {
            boolean isGranted = permissions.length > 0
                    && permissions[0].equals(permission.READ_EXTERNAL_STORAGE)
                    && grantResults.length > 0
                    && grantResults[0] == PackageManager.PERMISSION_GRANTED;

            mUserEventLogger.logStandalonePreviewStorageDialogApproved(isGranted);

            // Close the activity because we can't open the image without storage permission.
            if (!isGranted) {
                finish();
            }

            loadPreviewFragment();
        }
    }

    /**
     * Creates a new instance of {@link PreviewFragment} and loads the fragment into this activity's
     * fragment container so that it's shown to the user.
     */
    private void loadPreviewFragment() {
        Intent intent = getIntent();

        boolean testingModeEnabled = intent.getBooleanExtra(EXTRA_TESTING_MODE_ENABLED, false);
        WallpaperInfo wallpaper = new ImageWallpaperInfo(intent.getData());
        Fragment fragment = InjectorProvider.getInjector().getPreviewFragment(
                /* context */ this,
                wallpaper,
                PreviewFragment.MODE_CROP_AND_SET_WALLPAPER,
                testingModeEnabled);
        getSupportFragmentManager().beginTransaction()
                .add(R.id.fragment_container, fragment)
                .commit();
    }

    /**
     * Returns whether the user has granted READ_EXTERNAL_STORAGE permission to the app.
     */
    private boolean isReadExternalStoragePermissionGrantedForApp() {
        return getPackageManager().checkPermission(permission.READ_EXTERNAL_STORAGE,
                getPackageName()) == PackageManager.PERMISSION_GRANTED;
    }

    /**
     * Returns whether the provided image Uri is readable without requiring the app to have the user
     * grant READ_EXTERNAL_STORAGE permission.
     */
    private boolean isReadPermissionGrantedForImageUri(Uri imageUri) {
        return checkUriPermission(
                imageUri,
                Binder.getCallingPid(),
                Binder.getCallingUid(),
                Intent.FLAG_GRANT_READ_URI_PERMISSION) == PackageManager.PERMISSION_GRANTED;
    }
}
