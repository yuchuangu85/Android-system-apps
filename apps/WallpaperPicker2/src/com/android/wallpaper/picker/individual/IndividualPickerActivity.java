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
package com.android.wallpaper.picker.individual;

import android.app.Activity;
import android.app.WallpaperManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.res.Resources.NotFoundException;
import android.graphics.Insets;
import android.graphics.PorterDuff.Mode;
import android.graphics.drawable.Drawable;
import android.os.Build.VERSION;
import android.os.Bundle;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowInsets;
import android.widget.Toast;

import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.wallpaper.R;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.InlinePreviewIntentFactory;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.PickerIntentFactory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LiveWallpaperStatusChecker;
import com.android.wallpaper.module.NoBackupImageWallpaper;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.picker.BaseActivity;
import com.android.wallpaper.picker.PreviewActivity.PreviewActivityIntentFactory;
import com.android.wallpaper.util.ActivityUtils;
import com.android.wallpaper.util.DiskBasedLogger;

/**
 * Activity that can be launched from the Android wallpaper picker and allows users to pick from
 * various wallpapers and enter a preview mode for specific ones.
 */
public class IndividualPickerActivity extends BaseActivity {
    private static final String TAG = "IndividualPickerAct";
    private static final String EXTRA_CATEGORY_COLLECTION_ID =
            "com.android.wallpaper.category_collection_id";
    private static final int PREVIEW_WALLPAPER_REQUEST_CODE = 0;
    private static final int NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE = 1;
    private static final int PREVIEW_LIVEWALLPAPER_REQUEST_CODE = 2;
    private static final String KEY_CATEGORY_COLLECTION_ID = "key_category_collection_id";

    private InlinePreviewIntentFactory mPreviewIntentFactory;
    private WallpaperPersister mWallpaperPersister;
    private LiveWallpaperStatusChecker mLiveWallpaperStatusChecker;
    private Category mCategory;
    private String mCategoryCollectionId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_single_fragment_with_toolbar);

        // Set toolbar as the action bar.
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        mPreviewIntentFactory = new PreviewActivityIntentFactory();
        Injector injector = InjectorProvider.getInjector();
        mWallpaperPersister = injector.getWallpaperPersister(this);
        mLiveWallpaperStatusChecker = injector.getLiveWallpaperStatusChecker(this);

        FragmentManager fm = getSupportFragmentManager();
        Fragment fragment = fm.findFragmentById(R.id.fragment_container);

        mCategoryCollectionId = (savedInstanceState == null)
                ? getIntent().getStringExtra(EXTRA_CATEGORY_COLLECTION_ID)
                : savedInstanceState.getString(KEY_CATEGORY_COLLECTION_ID);
        mCategory = injector.getCategoryProvider(this).getCategory(mCategoryCollectionId);
        if (mCategory == null) {
            DiskBasedLogger.e(TAG, "Failed to find the category: " + mCategoryCollectionId, this);
            // We either were called with an invalid collection Id, or we're restarting with no
            // saved state, or with a collection id that doesn't exist anymore.
            // In those cases, we cannot continue, so let's just go back.
            finish();
            return;
        }

        setTitle(mCategory.getTitle());
        getSupportActionBar().setTitle(mCategory.getTitle());
        getSupportActionBar().setDisplayHomeAsUpEnabled(true);

        toolbar.getNavigationIcon().setTint(getColor(R.color.toolbar_icon_color));
        toolbar.getNavigationIcon().setAutoMirrored(true);

        getWindow().getDecorView().setSystemUiVisibility(
                getWindow().getDecorView().getSystemUiVisibility()
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        ((View) findViewById(R.id.fragment_container).getParent())
                .setOnApplyWindowInsetsListener((view, windowInsets) -> {
            view.setPadding(view.getPaddingLeft(), windowInsets.getSystemWindowInsetTop(),
                    view.getPaddingRight(), view.getBottom());
            // Consume only the top inset (status bar), to let other content in the Activity consume
            // the nav bar (ie, by using "fitSystemWindows")
            if (BuildCompat.isAtLeastQ()) {
                WindowInsets.Builder builder = new WindowInsets.Builder(windowInsets);
                builder.setSystemWindowInsets(Insets.of(windowInsets.getSystemWindowInsetLeft(),
                        0, windowInsets.getStableInsetRight(),
                        windowInsets.getSystemWindowInsetBottom()));
                return builder.build();
            } else {
                return windowInsets.replaceSystemWindowInsets(
                        windowInsets.getSystemWindowInsetLeft(),
                        0, windowInsets.getStableInsetRight(),
                        windowInsets.getSystemWindowInsetBottom());
            }
        });

        if (fragment == null) {
            fragment = injector.getIndividualPickerFragment(mCategoryCollectionId);
            fm.beginTransaction()
                    .add(R.id.fragment_container, fragment)
                    .commit();
        }
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // Handle Up as a Global back since the only entry point to IndividualPickerActivity is from
            // TopLevelPickerActivity.
            onBackPressed();
            return true;
        }
        return false;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        boolean shouldShowMessage = false;
        switch (requestCode) {
            case PREVIEW_LIVEWALLPAPER_REQUEST_CODE:
                shouldShowMessage = true;
            case PREVIEW_WALLPAPER_REQUEST_CODE:
                if (resultCode == Activity.RESULT_OK) {
                    mWallpaperPersister.onLiveWallpaperSet();

                    // The wallpaper was set, so finish this activity with result OK.
                    finishWithResultOk(shouldShowMessage);
                }
                break;

            case NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE:
                // User clicked "Set wallpaper" in live wallpaper preview UI.
                // NOTE: Don't check for the result code prior to KitKat MR2 because a bug on those versions
                // caused the result code to be discarded from LivePicker so we can't rely on it.
                if ((!BuildCompat.isAtLeastL() || resultCode == Activity.RESULT_OK)
                        && mLiveWallpaperStatusChecker.isNoBackupImageWallpaperSet()
                        && mCategory.getWallpaperRotationInitializer().startRotation(getApplicationContext())) {
                    finishWithResultOk(true);
                }
                break;

            default:
                Log.e(TAG, "Invalid request code: " + requestCode);
        }
    }

    /**
     * Shows the preview activity for the given wallpaper.
     */
    public void showPreview(WallpaperInfo wallpaperInfo) {
        mWallpaperPersister.setWallpaperInfoInPreview(wallpaperInfo);
        wallpaperInfo.showPreview(this, mPreviewIntentFactory,
                wallpaperInfo instanceof LiveWallpaperInfo ? PREVIEW_LIVEWALLPAPER_REQUEST_CODE
                        : PREVIEW_WALLPAPER_REQUEST_CODE);
    }

    /**
     * Shows the system live wallpaper preview for the {@link NoBackupImageWallpaper} which is used to
     * draw rotating wallpapers on pre-N Android builds.
     */
    public void showNoBackupImageWallpaperPreview() {
        Intent intent = new Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER);
        ComponentName componentName = new ComponentName(this, NoBackupImageWallpaper.class);
        intent.putExtra(WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT, componentName);
        ActivityUtils.startActivityForResultSafely(
                this, intent, NO_BACKUP_IMAGE_WALLPAPER_REQUEST_CODE);
    }

    private void finishWithResultOk(boolean shouldShowMessage) {
        if (shouldShowMessage) {
            try {
                Toast.makeText(this, R.string.wallpaper_set_successfully_message,
                        Toast.LENGTH_SHORT).show();
            } catch (NotFoundException e) {
                Log.e(TAG, "Could not show toast " + e);
            }
        }
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setResult(Activity.RESULT_OK);
        finish();
    }

    @Override
    protected void onSaveInstanceState(Bundle bundle) {
        super.onSaveInstanceState(bundle);

        bundle.putString(KEY_CATEGORY_COLLECTION_ID, mCategoryCollectionId);
    }

    /**
     * Default implementation of intent factory that provides an intent to start an
     * IndividualPickerActivity.
     */
    public static class IndividualPickerActivityIntentFactory implements PickerIntentFactory {
        @Override
        public Intent newIntent(Context ctx, String collectionId) {
            return new Intent(ctx, IndividualPickerActivity.class).putExtra(
                    EXTRA_CATEGORY_COLLECTION_ID, collectionId);
        }
    }
}
