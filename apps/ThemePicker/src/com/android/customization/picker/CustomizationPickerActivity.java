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
package com.android.customization.picker;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;

import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.customization.model.clock.ClockManager;
import com.android.customization.model.clock.Clockface;
import com.android.customization.model.clock.ContentProviderClockProvider;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.grid.GridOptionsManager;
import com.android.customization.model.grid.LauncherGridOptionsProvider;
import com.android.customization.model.theme.DefaultThemeProvider;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.ThemeBundle;
import com.android.customization.model.theme.ThemeManager;
import com.android.customization.module.CustomizationInjector;
import com.android.customization.module.DefaultCustomizationPreferences;
import com.android.customization.module.ThemesUserEventLogger;
import com.android.customization.picker.clock.ClockFragment;
import com.android.customization.picker.clock.ClockFragment.ClockFragmentHost;
import com.android.customization.picker.grid.GridFragment;
import com.android.customization.picker.grid.GridFragment.GridFragmentHost;
import com.android.customization.picker.theme.ThemeFragment;
import com.android.customization.picker.theme.ThemeFragment.ThemeFragmentHost;
import com.android.customization.widget.NoTintDrawableWrapper;
import com.android.wallpaper.R;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.DailyLoggingAlarmScheduler;
import com.android.wallpaper.module.FormFactorChecker;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.picker.CategoryFragment;
import com.android.wallpaper.picker.CategoryFragment.CategoryFragmentHost;
import com.android.wallpaper.picker.MyPhotosStarter;
import com.android.wallpaper.picker.MyPhotosStarter.PermissionChangedListener;
import com.android.wallpaper.picker.TopLevelPickerActivity;
import com.android.wallpaper.picker.WallpaperPickerDelegate;
import com.android.wallpaper.picker.WallpapersUiContainer;

import com.google.android.material.bottomnavigation.BottomNavigationView;

import java.util.HashMap;
import java.util.Map;

/**
 *  Main Activity allowing containing a bottom nav bar for the user to switch between the different
 *  Fragments providing customization options.
 */
public class CustomizationPickerActivity extends FragmentActivity implements WallpapersUiContainer,
        CategoryFragmentHost, ThemeFragmentHost, GridFragmentHost, ClockFragmentHost {

    private static final String TAG = "CustomizationPickerActivity";
    private static final String WALLPAPER_FLAVOR_EXTRA = "com.android.launcher3.WALLPAPER_FLAVOR";
    private static final String WALLPAPER_FOCUS = "focus_wallpaper";
    private static final String WALLPAPER_ONLY = "wallpaper_only";

    private WallpaperPickerDelegate mDelegate;
    private UserEventLogger mUserEventLogger;
    private BottomNavigationView mBottomNav;

    private static final Map<Integer, CustomizationSection> mSections = new HashMap<>();
    private CategoryFragment mWallpaperCategoryFragment;
    private WallpaperSetter mWallpaperSetter;

    private boolean mWallpaperCategoryInitialized;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        Injector injector = InjectorProvider.getInjector();
        mDelegate = new WallpaperPickerDelegate(this, this, injector);
        mUserEventLogger = injector.getUserEventLogger(this);
        initSections();
        mWallpaperCategoryInitialized = false;

        // Restore this Activity's state before restoring contained Fragments state.
        super.onCreate(savedInstanceState);

        if (!supportsCustomization()) {
            Log.w(TAG, "Themes not supported, reverting to Wallpaper Picker");
            skipToWallpaperPicker();
        } else {
            setContentView(R.layout.activity_customization_picker_main);
            setUpBottomNavView();

            FragmentManager fm = getSupportFragmentManager();
            Fragment fragment = fm.findFragmentById(R.id.fragment_container);

            if (fragment == null) {
                // App launch specific logic: log the "app launched" event and set up daily logging.
                mUserEventLogger.logAppLaunched();
                DailyLoggingAlarmScheduler.setAlarm(getApplicationContext());

                // Navigate to the Wallpaper tab if we started directly from launcher, otherwise
                // start at the Styles tab
                int section = WALLPAPER_FOCUS.equals(getIntent()
                    .getStringExtra(WALLPAPER_FLAVOR_EXTRA))
                    ? mBottomNav.getMenu().size() - 1 : 0;
                navigateToSection(mBottomNav.getMenu().getItem(section).getItemId());
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        boolean wallpaperOnly =
                WALLPAPER_ONLY.equals(getIntent().getStringExtra(WALLPAPER_FLAVOR_EXTRA));
        boolean provisioned = Settings.Global.getInt(getContentResolver(),
                Settings.Global.DEVICE_PROVISIONED, 0) != 0;

        mUserEventLogger.logResumed(provisioned, wallpaperOnly);
        // refresh the sections as the preview may have changed
        initSections();
        if (mBottomNav == null) {
            return;
        }
        CustomizationSection section = mSections.get(mBottomNav.getSelectedItemId());
        if (section == null) {
            return;
        }
        // Keep CategoryFragment's design to load category within its fragment
        if (section instanceof WallpaperSection) {
            switchFragment(section);
            section.onVisible();
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (WALLPAPER_ONLY.equals(intent.getStringExtra(WALLPAPER_FLAVOR_EXTRA))) {
            Log.d(TAG, "WALLPAPER_ONLY intent, reverting to Wallpaper Picker");
            skipToWallpaperPicker();
        }
    }

    private void skipToWallpaperPicker() {
        Intent intent = new Intent(this, TopLevelPickerActivity.class);
        startActivity(intent);
        finish();
    }

    private boolean supportsCustomization() {
        return mDelegate.getFormFactor() == FormFactorChecker.FORM_FACTOR_MOBILE
                && mSections.size() > 1;
    }

    private void initSections() {
        mSections.clear();
        if (!BuildCompat.isAtLeastQ()) {
            Log.d(TAG, "Build version < Q detected");
            return;
        }
        if (WALLPAPER_ONLY.equals(getIntent().getStringExtra(WALLPAPER_FLAVOR_EXTRA))) {
            Log.d(TAG, "WALLPAPER_ONLY intent");
            return;
        }
        //Theme
        CustomizationInjector injector = (CustomizationInjector) InjectorProvider.getInjector();
        mWallpaperSetter = new WallpaperSetter(injector.getWallpaperPersister(this),
                injector.getPreferences(this), mUserEventLogger, false);
        ThemesUserEventLogger eventLogger = (ThemesUserEventLogger) injector.getUserEventLogger(
                this);
        ThemeManager themeManager = injector.getThemeManager(
                new DefaultThemeProvider(this, injector.getCustomizationPreferences(this)),
                this,
                mWallpaperSetter, new OverlayManagerCompat(this), eventLogger);
        if (themeManager.isAvailable()) {
            mSections.put(R.id.nav_theme, new ThemeSection(R.id.nav_theme, themeManager));
        } else {
            Log.d(TAG, "ThemeManager not available, removing Style section");
        }
        //Clock
        ClockManager clockManager = new ClockManager(getContentResolver(),
                new ContentProviderClockProvider(this), eventLogger);
        if (clockManager.isAvailable()) {
            mSections.put(R.id.nav_clock, new ClockSection(R.id.nav_clock, clockManager));
        } else {
            Log.d(TAG, "ClockManager not available, removing Clock section");
        }
        //Grid
        GridOptionsManager gridManager = new GridOptionsManager(
                new LauncherGridOptionsProvider(this,
                        getString(R.string.grid_control_metadata_name)),
                eventLogger);
        if (gridManager.isAvailable()) {
            mSections.put(R.id.nav_grid, new GridSection(R.id.nav_grid, gridManager));
        } else {
            Log.d(TAG, "GridOptionsManager not available, removing Grid section");
        }
        mSections.put(R.id.nav_wallpaper, new WallpaperSection(R.id.nav_wallpaper));
    }

    private void setUpBottomNavView() {
        mBottomNav = findViewById(R.id.main_bottom_nav);
        Menu menu = mBottomNav.getMenu();
        DefaultCustomizationPreferences prefs =
            new DefaultCustomizationPreferences(getApplicationContext());
        for (int i = menu.size() - 1; i >= 0; i--) {
            MenuItem item = menu.getItem(i);
            int id = item.getItemId();
            if (!mSections.containsKey(id)) {
                menu.removeItem(id);
            }  else if (!prefs.getTabVisited(getResources().getResourceName(id))) {
                showTipDot(item);
            }
        }

        mBottomNav.setOnNavigationItemSelectedListener(item -> {
            int id = item.getItemId();
            CustomizationSection section = mSections.get(id);
            switchFragment(section);
            section.onVisible();
            String name = getResources().getResourceName(id);
            if (!prefs.getTabVisited(name)) {
                prefs.setTabVisited(name);
                hideTipDot(item);

                if (id == R.id.nav_theme) {
                    getThemeManager().storeEmptyTheme();
                }
            }
            return true;
        });
    }

    private void showTipDot(MenuItem item) {
        Drawable icon = item.getIcon();
        Drawable dot = new NoTintDrawableWrapper(getResources().getDrawable(R.drawable.tip_dot));
        Drawable[] layers = {icon, dot};
        LayerDrawable iconWithDot = new LayerDrawable(layers);

        // Position dot in the upper-right corner
        int dotSize = (int) getResources().getDimension(R.dimen.tip_dot_size)
            + (int) getResources().getDimension(R.dimen.tip_dot_line_width) * 2;
        int linewidth = (int) getResources().getDimension(R.dimen.tip_dot_line_width);
        iconWithDot.setLayerGravity(1, Gravity.TOP | Gravity.RIGHT);
        iconWithDot.setLayerWidth(1, dotSize);
        iconWithDot.setLayerHeight(1, dotSize);
        iconWithDot.setLayerInsetTop(1, -linewidth);
        iconWithDot.setLayerInsetRight(1, -linewidth);

        item.setIcon(iconWithDot);
    }


    private void hideTipDot(MenuItem item) {
        Drawable iconWithDot = item.getIcon();
        if (iconWithDot instanceof LayerDrawable) {
            LayerDrawable layers = (LayerDrawable) iconWithDot;
            Drawable icon = layers.getDrawable(0);
            item.setIcon(icon);
        }
    }

    @Override
    public void onBackPressed() {
        if (getSupportFragmentManager().popBackStackImmediate()) {
            return;
        }
        if (moveTaskToBack(false)) {
            return;
        }
        super.onBackPressed();
    }

    private void navigateToSection(@IdRes int id) {
        mBottomNav.setSelectedItemId(id);
    }

    private void switchFragment(CustomizationSection section) {
        final FragmentManager fragmentManager = getSupportFragmentManager();

        Fragment fragment = section.getFragment();

        final FragmentTransaction fragmentTransaction = fragmentManager.beginTransaction();
        fragmentTransaction.replace(R.id.fragment_container, fragment);
        fragmentTransaction.commitNow();
    }


    @Override
    public void requestExternalStoragePermission(PermissionChangedListener listener) {
        mDelegate.requestExternalStoragePermission(listener);
    }

    @Override
    public boolean isReadExternalStoragePermissionGranted() {
        return mDelegate.isReadExternalStoragePermissionGranted();
    }

    @Override
    public void showViewOnlyPreview(WallpaperInfo wallpaperInfo) {
        mDelegate.showViewOnlyPreview(wallpaperInfo);
    }

    /**
     * Shows the picker activity for the given category.
     */
    @Override
    public void show(String collectionId) {
        mDelegate.show(collectionId);
    }

    @Override
    public void onWallpapersReady() {

    }

    @Nullable
    @Override
    public CategoryFragment getCategoryFragment() {
        return mWallpaperCategoryFragment;
    }

    @Override
    public void doneFetchingCategories() {

    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        mDelegate.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public MyPhotosStarter getMyPhotosStarter() {
        return mDelegate;
    }

    @Override
    public ClockManager getClockManager() {
        CustomizationSection section = mSections.get(R.id.nav_clock);
        return section == null ? null : (ClockManager) section.customizationManager;
    }

    @Override
    public GridOptionsManager getGridOptionsManager() {
        CustomizationSection section = mSections.get(R.id.nav_grid);
        return section == null ? null : (GridOptionsManager) section.customizationManager;
    }

    @Override
    public ThemeManager getThemeManager() {
        CustomizationSection section = mSections.get(R.id.nav_theme);
        return section == null ? null : (ThemeManager) section.customizationManager;
    }

    @Override
    protected void onStop() {
        mUserEventLogger.logStopped();
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mWallpaperSetter != null) {
            mWallpaperSetter.cleanUp();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (mDelegate.handleActivityResult(requestCode, resultCode, data)) {
            finishActivityWithResultOk();
        }
    }

    private void finishActivityWithResultOk() {
        overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        setResult(Activity.RESULT_OK);
        finish();
    }

    /**
     * Represents a section of the Picker (eg "ThemeBundle", "Clock", etc).
     * There should be a concrete subclass per available section, providing the corresponding
     * Fragment to be displayed when switching to each section.
     */
    static abstract class CustomizationSection<T extends CustomizationOption> {

        /**
         * IdRes used to identify this section in the BottomNavigationView menu.
         */
        @IdRes final int id;
        protected final CustomizationManager<T> customizationManager;

        private CustomizationSection(@IdRes int id, CustomizationManager<T> manager) {
            this.id = id;
            this.customizationManager = manager;
        }

        /**
         * @return the Fragment corresponding to this section.
         */
        abstract Fragment getFragment();

        void onVisible() {}
    }

    /**
     * {@link CustomizationSection} corresponding to the "Wallpaper" section of the Picker.
     */
    private class WallpaperSection extends CustomizationSection {
        private boolean mForceCategoryRefresh;

        private WallpaperSection(int id) {
            super(id, null);
        }

        @Override
        Fragment getFragment() {
            if (mWallpaperCategoryFragment == null) {
                mWallpaperCategoryFragment = CategoryFragment.newInstance(
                        getString(R.string.wallpaper_title));
                mForceCategoryRefresh = true;
            }
            return mWallpaperCategoryFragment;
        }

        @Override
        void onVisible() {
            if (!mWallpaperCategoryInitialized) {
                mDelegate.initialize(mForceCategoryRefresh);
            }
            mWallpaperCategoryInitialized = true;
        }
    }

    private class ThemeSection extends CustomizationSection<ThemeBundle> {

        private ThemeFragment mFragment;

        private ThemeSection(int id, ThemeManager manager) {
            super(id, manager);
        }

        @Override
        Fragment getFragment() {
            if (mFragment == null) {
                mFragment = ThemeFragment.newInstance(getString(R.string.theme_title));
            }
            return mFragment;
        }
    }

    private class GridSection extends CustomizationSection<GridOption> {

        private GridFragment mFragment;

        private GridSection(int id, GridOptionsManager manager) {
            super(id, manager);
        }

        @Override
        Fragment getFragment() {
            if (mFragment == null) {
                mFragment = GridFragment.newInstance(getString(R.string.grid_title));
            }
            return mFragment;
        }
    }

    private class ClockSection extends CustomizationSection<Clockface> {

        private ClockFragment mFragment;

        private ClockSection(int id, ClockManager manager) {
            super(id, manager);
        }

        @Override
        Fragment getFragment() {
            if (mFragment == null) {
                mFragment = ClockFragment.newInstance(getString(R.string.clock_title));
            }
            return mFragment;
        }
    }
}
