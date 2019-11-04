/*
 * Copyright (C) 2009 The Android Open Source Project
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

package com.android.wallpaper.livepicker;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.WallpaperColors;
import android.app.WallpaperInfo;
import android.app.WallpaperManager;
import android.content.ActivityNotFoundException;
import android.content.ComponentName;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources.NotFoundException;
import android.graphics.Outline;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.service.wallpaper.IWallpaperConnection;
import android.service.wallpaper.IWallpaperEngine;
import android.service.wallpaper.IWallpaperService;
import android.service.wallpaper.WallpaperService;
import android.service.wallpaper.WallpaperSettingsActivity;
import android.text.TextUtils;
import android.util.Log;
import android.util.Pair;
import android.view.ContextThemeWrapper;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.WindowManager.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toolbar;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.LiveData;
import androidx.slice.Slice;
import androidx.slice.widget.SliceLiveData;
import androidx.slice.widget.SliceView;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetBehavior.BottomSheetCallback;
import com.google.android.material.tabs.TabLayout;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LiveWallpaperPreview extends Activity {
    static final String EXTRA_LIVE_WALLPAPER_INFO = "android.live_wallpaper.info";

    private static final String LOG_TAG = "LiveWallpaperPreview";

    private static final boolean SHOW_DUMMY_DATA = false;

    private WallpaperManager mWallpaperManager;
    private WallpaperConnection mWallpaperConnection;

    private Intent mWallpaperIntent;
    private Intent mSettingsIntent;
    private Intent mDeleteIntent;

    private View mLoading;
    private View mViewBottomPane;
    private BottomSheetBehavior mBottomSheetBehavior;
    private ViewPager mViewPager;
    private TabLayout mTabLayout;
    private CheckBox mPreview;

    protected final List<Pair<String, View>> mPages = new ArrayList<>();
    private SliceView mSliceViewSettings;
    private LiveData<Slice> mLiveDataSettings;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        init();
    }

    protected void init() {
        WallpaperInfo info = getIntent().getParcelableExtra(EXTRA_LIVE_WALLPAPER_INFO);
        if (info == null) {
            finish();
            return;
        }
        initUI(info, null /* deleteAction */);
    }

    protected void initUI(WallpaperInfo info, @Nullable String deleteAction) {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        setContentView(R.layout.live_wallpaper_preview);
        mLoading = findViewById(R.id.loading);

        final String packageName = info.getPackageName();
        mWallpaperIntent = new Intent(WallpaperService.SERVICE_INTERFACE)
                .setClassName(info.getPackageName(), info.getServiceName());

        final String settingsActivity = info.getSettingsActivity();
        if (settingsActivity != null) {
            mSettingsIntent = new Intent();
            mSettingsIntent.setComponent(new ComponentName(packageName, settingsActivity));
            mSettingsIntent.putExtra(WallpaperSettingsActivity.EXTRA_PREVIEW_MODE, true);
            final PackageManager pm = getPackageManager();
            final ActivityInfo activityInfo = mSettingsIntent.resolveActivityInfo(pm, 0);
            if (activityInfo == null) {
                Log.e(LOG_TAG, "Couldn't find settings activity: " + settingsActivity);
                mSettingsIntent = null;
            }
        }

        final Toolbar toolbar = findViewById(R.id.toolbar);
        setActionBar(toolbar);
        getActionBar().setDisplayHomeAsUpEnabled(true);
        getActionBar().setDisplayShowTitleEnabled(false);

        final Drawable backArrow = getDrawable(R.drawable.ic_arrow_back_white_24dp);
        backArrow.setAutoMirrored(true);
        toolbar.setNavigationIcon(backArrow);

        mWallpaperManager = WallpaperManager.getInstance(this);
        mWallpaperConnection = new WallpaperConnection(mWallpaperIntent);
        getWindow().getDecorView().post(new Runnable() {
            public void run() {
                if (!mWallpaperConnection.connect()) {
                    mWallpaperConnection = null;
                }
            }
        });

        if (!TextUtils.isEmpty(deleteAction)) {
            mDeleteIntent = new Intent(deleteAction);
            mDeleteIntent.setPackage(info.getPackageName());
            mDeleteIntent.putExtra(EXTRA_LIVE_WALLPAPER_INFO, info);
        }

        initInfoPage(info);
        initSettingsPage(info);
        populateBottomPane();
    }

    private void initInfoPage(WallpaperInfo info) {
        final View pageInfo = getLayoutInflater().inflate(R.layout.page_info, null /* root */);
        final TextView attributionTitle = pageInfo.findViewById(
                R.id.preview_attribution_pane_title);
        final TextView attributionAuthor = pageInfo.findViewById(
                R.id.preview_attribution_pane_author);
        final TextView attributionDescription = pageInfo.findViewById(
                R.id.preview_attribution_pane_description);
        final Button attributionExploreButton = pageInfo.findViewById(
                R.id.preview_attribution_pane_explore_button);
        final View spacer = pageInfo.findViewById(R.id.spacer);
        final Button setWallpaperButton = pageInfo.findViewById(
                R.id.preview_attribution_pane_set_wallpaper_button);

        setWallpaperButton.setOnClickListener(this::setLiveWallpaper);
        mPages.add(Pair.create(getString(R.string.tab_info), pageInfo));

        if (SHOW_DUMMY_DATA) {
            attributionTitle.setText("Diorama, Yosemite");
            attributionTitle.setVisibility(View.VISIBLE);
            attributionAuthor.setText("Live Earth Collection - Android Earth");
            attributionAuthor.setVisibility(View.VISIBLE);
            attributionDescription.setText("Lorem ipsum dolor sit amet, consectetur adipiscing"
                    + " elit. Sed imperdiet et mauris molestie laoreet. Proin volutpat elit nec"
                    + " magna tempus, ac aliquet lectus volutpat.");
            attributionDescription.setVisibility(View.VISIBLE);
            attributionExploreButton.setText("Explore");
            attributionExploreButton.setVisibility(View.VISIBLE);
            spacer.setVisibility(View.VISIBLE);
            return;
        }

        final PackageManager pm = getPackageManager();

        // Set attribution title
        final CharSequence title = info.loadLabel(pm);
        if (!TextUtils.isEmpty(title)) {
            attributionTitle.setText(title);
            attributionTitle.setVisibility(View.VISIBLE);
        }

        // Don't show other meta data if attribute showMetadataInPreview is set to False
        if (!info.getShowMetadataInPreview()) {
            return;
        }

        // Set attribution author
        try {
            final CharSequence author = info.loadAuthor(pm);
            if (!TextUtils.isEmpty(author)) {
                attributionAuthor.setText(author);
                attributionAuthor.setVisibility(View.VISIBLE);
            }
        } catch (NotFoundException e) {
            // It's expected if the live wallpaper doesn't provide this information
        }

        // Set attribution description
        try {
            final CharSequence description = info.loadDescription(pm);
            if (!TextUtils.isEmpty(description)) {
                attributionDescription.setText(description);
                attributionDescription.setVisibility(View.VISIBLE);
            }
        } catch (NotFoundException e) {
            // It's expected if the live wallpaper doesn't provide this information
        }

        // Set context information
        try {
            final Uri contextUri = info.loadContextUri(pm);
            if (contextUri != null) {
                final Intent intent = new Intent(Intent.ACTION_VIEW, contextUri);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                attributionExploreButton.setOnClickListener(v -> {
                    try {
                        startActivity(intent);
                    } catch (ActivityNotFoundException e) {
                        Log.e(LOG_TAG, "Couldn't find activity for context link.", e);
                    }
                });
                attributionExploreButton.setVisibility(View.VISIBLE);
                spacer.setVisibility(View.VISIBLE);

                // Update context description string if it's provided
                final CharSequence contextDescription = info.loadContextDescription(pm);
                if (!TextUtils.isEmpty(contextDescription)) {
                    attributionExploreButton.setText(contextDescription);
                }
            }
        } catch (NotFoundException e) {
            // It's expected if the wallpaper doesn't provide this information
        }
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    protected Uri getSettingsSliceUri(@NonNull WallpaperInfo info) {
        return info.getSettingsSliceUri();
    }

    private void initSettingsPage(WallpaperInfo info) {
        final Uri uriSettingsSlice = getSettingsSliceUri(info);
        if (uriSettingsSlice == null) {
            return;
        }

        final View pageSettings = getLayoutInflater().inflate(R.layout.page_settings,
                null /* root */);
        final Button setWallpaperButton = pageSettings.findViewById(
                R.id.preview_attribution_pane_set_wallpaper_button);

        mSliceViewSettings = pageSettings.findViewById(R.id.settings_slice);
        mSliceViewSettings.setMode(SliceView.MODE_LARGE);
        mSliceViewSettings.setScrollable(false);

        // Set LiveData for SliceView
        mLiveDataSettings = SliceLiveData.fromUri(this /* context */, uriSettingsSlice);
        mLiveDataSettings.observeForever(mSliceViewSettings);

        setWallpaperButton.setOnClickListener(this::setLiveWallpaper);

        mPages.add(Pair.create(getResources().getString(R.string.tab_customize), pageSettings));
    }

    private void populateBottomPane() {
        mViewBottomPane = findViewById(R.id.bottom_pane);
        mViewPager = findViewById(R.id.viewpager);
        mTabLayout = findViewById(R.id.tablayout);

        mBottomSheetBehavior = BottomSheetBehavior.from(mViewBottomPane);
        mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);

        // Create PagerAdapter
        final PagerAdapter pagerAdapter = new PagerAdapter() {
            @NonNull
            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                final View page = mPages.get(position).second;
                container.addView(page);
                return page;
            }

            @Override
            public void destroyItem(@NonNull ViewGroup container, int position,
                    @NonNull Object object) {
                if (object instanceof View) {
                    container.removeView((View) object);
                }
            }

            @Override
            public int getCount() {
                return mPages.size();
            }

            @Override
            public CharSequence getPageTitle(int position) {
                try {
                    return mPages.get(position).first;
                } catch (IndexOutOfBoundsException e) {
                    return null;
                }
            }

            @Override
            public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
                return (view == object);
            }
        };

        // Add OnPageChangeListener to re-measure ViewPager's height
        mViewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mViewPager.requestLayout();
            }
        });

        // Set PagerAdapter
        mViewPager.setAdapter(pagerAdapter);

        // Make TabLayout visible if there are more than one page
        if (mPages.size() > 1) {
            mTabLayout.setVisibility(View.VISIBLE);
            mTabLayout.setupWithViewPager(mViewPager);
        }

        // Initializes a rounded rectangle outline and clips the upper corners to be rounded.
        mViewBottomPane.setOutlineProvider(new ViewOutlineProvider() {
            private final int radius = getResources().getDimensionPixelSize(
                    R.dimen.preview_viewpager_round_radius);

            @Override
            public void getOutline(View view, Outline outline) {
                outline.setRoundRect(0 /* left */, 0 /* top */, view.getWidth(),
                        view.getHeight() + radius, radius);
            }
        });
        mViewBottomPane.setClipToOutline(true);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_preview, menu);
        setupPreviewMenu(menu);
        menu.findItem(R.id.configure).setVisible(mSettingsIntent != null);
        menu.findItem(R.id.delete_wallpaper).setVisible(mDeleteIntent != null);
        return super.onCreateOptionsMenu(menu);
    }

    private void setupPreviewMenu(Menu menu) {
        mPreview = (CheckBox) menu.findItem(R.id.preview).getActionView();
        mPreview.setOnClickListener(this::setPreviewBehavior);

        BottomSheetCallback callback = new BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                switch (newState) {
                    case BottomSheetBehavior.STATE_COLLAPSED:
                        setPreviewChecked(true /* checked */);
                        break;
                    case BottomSheetBehavior.STATE_EXPANDED:
                        setPreviewChecked(false /* checked */);
                        break;
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
                mTabLayout.setAlpha(slideOffset);
                mViewPager.setAlpha(slideOffset);
            }
        };
        mBottomSheetBehavior.setBottomSheetCallback(callback);

        int state = mBottomSheetBehavior.getState();
        callback.onStateChanged(mViewBottomPane, state);
        switch (state) {
            case BottomSheetBehavior.STATE_COLLAPSED:
                callback.onSlide(mViewBottomPane, 0f);
                break;
            case BottomSheetBehavior.STATE_EXPANDED:
                callback.onSlide(mViewBottomPane, 1f);
                break;
        }
    }

    private void setPreviewChecked(boolean checked) {
        if (mPreview != null) {
            mPreview.setChecked(checked);
            int resId = checked ? R.string.expand_attribution_panel
                    : R.string.collapse_attribution_panel;
            mPreview.setContentDescription(getResources().getString(resId));
        }
    }

    private void setPreviewBehavior(final View v) {
        CheckBox checkbox = (CheckBox) v;
        if (checkbox.isChecked()) {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_COLLAPSED);
        } else {
            mBottomSheetBehavior.setState(BottomSheetBehavior.STATE_EXPANDED);
        }
    }

    public void setLiveWallpaper(final View v) {
        if (mWallpaperManager.getWallpaperInfo() != null
                && mWallpaperManager.getWallpaperId(WallpaperManager.FLAG_LOCK) < 0) {
            // The lock screen does not have a distinct wallpaper and the current wallpaper is a
            // live wallpaper, so since we cannot preserve any static imagery on the lock screen,
            // set the live wallpaper directly without giving the user a destination option.
            try {
                setLiveWallpaper(v.getRootView().getWindowToken());
                setResult(RESULT_OK);
            } catch (RuntimeException e) {
                Log.w(LOG_TAG, "Failure setting wallpaper", e);
            }
            finish();
        } else {
            // Otherwise, prompt to either set on home or both home and lock screen.
            final Context themedContext = new ContextThemeWrapper(this /* base */,
                    android.R.style.Theme_DeviceDefault_Settings);
            new AlertDialog.Builder(themedContext)
                    .setTitle(R.string.set_live_wallpaper)
                    .setAdapter(new WallpaperTargetAdapter(themedContext),
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    try {
                                        setLiveWallpaper(v.getRootView().getWindowToken());
                                        if (which == 1) {
                                            // "Home screen and lock screen"; clear the lock
                                            // screen so it
                                            // shows through to the live wallpaper on home.
                                            mWallpaperManager.clear(WallpaperManager.FLAG_LOCK);
                                        }
                                        setResult(RESULT_OK);
                                    } catch (RuntimeException | IOException e) {
                                        Log.w(LOG_TAG, "Failure setting wallpaper", e);
                                    }
                                    finish();
                                }
                            })
                    .show();
        }
    }

    private void setLiveWallpaper(IBinder windowToken) {
        mWallpaperManager.setWallpaperComponent(mWallpaperIntent.getComponent());
        mWallpaperManager.setWallpaperOffsetSteps(0.5f /* xStep */, 0.0f /* yStep */);
        mWallpaperManager.setWallpaperOffsets(windowToken, 0.5f /* xOffset */, 0.0f /* yOffset */);
    }

    @VisibleForTesting
    void deleteLiveWallpaper() {
        if (mDeleteIntent != null) {
            startService(mDeleteIntent);
            finish();
        }
    }

    private void showDeleteConfirmDialog() {
        final AlertDialog alertDialog = new AlertDialog.Builder(this /* context */,
                R.style.AlertDialogStyle)
                .setMessage(R.string.delete_wallpaper_confirmation)
                .setPositiveButton(R.string.delete_live_wallpaper,
                        (dialog, which) -> deleteLiveWallpaper())
                .setNegativeButton(android.R.string.cancel, null /* listener */)
                .create();
        alertDialog.show();
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.configure) {
            startActivity(mSettingsIntent);
            return true;
        } else if (id == R.id.delete_wallpaper) {
            showDeleteConfirmDialog();
            return true;
        } else if (id == android.R.id.home) {
            onBackPressed();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(true);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        if (mWallpaperConnection != null) {
            mWallpaperConnection.setVisibility(false);
        }
    }

    @Override
    protected void onDestroy () {
        if (mLiveDataSettings != null && mLiveDataSettings.hasObservers()) {
            mLiveDataSettings.removeObserver(mSliceViewSettings);
            mLiveDataSettings = null;
        }
        if (mWallpaperConnection != null) {
            mWallpaperConnection.disconnect();
        }
        mWallpaperConnection = null;
        super.onDestroy();
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            onUserInteraction();
        }
        boolean handled = getWindow().superDispatchTouchEvent(ev);
        if (!handled) {
            handled = onTouchEvent(ev);
        }

        if (!handled && mWallpaperConnection != null && mWallpaperConnection.mEngine != null) {
            MotionEvent dup = MotionEvent.obtainNoHistory(ev);
            try {
                mWallpaperConnection.mEngine.dispatchPointer(dup);
            } catch (RemoteException e) {
            }

            int action = ev.getActionMasked();
            try {
                if (action == MotionEvent.ACTION_UP) {
                    mWallpaperConnection.mEngine.dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_TAP,
                            (int) ev.getX(), (int) ev.getY(), 0, null);
                } else if (action == MotionEvent.ACTION_POINTER_UP) {
                    int pointerIndex = ev.getActionIndex();
                    mWallpaperConnection.mEngine.dispatchWallpaperCommand(
                            WallpaperManager.COMMAND_SECONDARY_TAP,
                            (int) ev.getX(pointerIndex), (int) ev.getY(pointerIndex), 0, null);
                }
            } catch (RemoteException e) {
            }
        }
        return handled;
    }

    class WallpaperConnection extends IWallpaperConnection.Stub implements ServiceConnection {
        final Intent mIntent;
        IWallpaperService mService;
        IWallpaperEngine mEngine;
        boolean mConnected;
        boolean mIsVisible;
        boolean mIsEngineVisible;

        WallpaperConnection(Intent intent) {
            mIntent = intent;
        }

        public boolean connect() {
            synchronized (this) {
                if (!bindService(mIntent, this, Context.BIND_AUTO_CREATE)) {
                    return false;
                }

                mConnected = true;
                return true;
            }
        }

        public void disconnect() {
            synchronized (this) {
                mConnected = false;
                if (mEngine != null) {
                    try {
                        mEngine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                    mEngine = null;
                }
                try {
                    unbindService(this);
                } catch (IllegalArgumentException e) {
                    Log.w(LOG_TAG, "Can't unbind wallpaper service. "
                            + "It might have crashed, just ignoring.", e);
                }
                mService = null;
            }
        }

        public void onServiceConnected(ComponentName name, IBinder service) {
            if (mWallpaperConnection == this) {
                mService = IWallpaperService.Stub.asInterface(service);
                try {
                    final int displayId = getWindow().getDecorView().getDisplay().getDisplayId();
                    final View root = getWindow().getDecorView();
                    mService.attach(this, root.getWindowToken(),
                            LayoutParams.TYPE_APPLICATION_MEDIA,
                            true, root.getWidth(), root.getHeight(),
                            new Rect(0, 0, 0, 0), displayId);
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "Failed attaching wallpaper; clearing", e);
                }
            }
        }

        public void onServiceDisconnected(ComponentName name) {
            mService = null;
            mEngine = null;
            if (mWallpaperConnection == this) {
                Log.w(LOG_TAG, "Wallpaper service gone: " + name);
            }
        }

        public void attachEngine(IWallpaperEngine engine, int displayId) {
            synchronized (this) {
                if (mConnected) {
                    mEngine = engine;
                    if (mIsVisible) {
                        setEngineVisibility(true);
                    }
                } else {
                    try {
                        engine.destroy();
                    } catch (RemoteException e) {
                        // Ignore
                    }
                }
            }
        }

        public ParcelFileDescriptor setWallpaper(String name) {
            return null;
        }

        @Override
        public void onWallpaperColorsChanged(WallpaperColors colors, int displayId)
                throws RemoteException {

        }

        @Override
        public void engineShown(IWallpaperEngine engine) throws RemoteException {
            mLoading.post(() -> {
                mLoading.animate()
                        .alpha(0f)
                        .setDuration(220)
                        .setStartDelay(300)
                        .setInterpolator(AnimationUtils.loadInterpolator(LiveWallpaperPreview.this,
                                android.R.interpolator.fast_out_linear_in))
                        .withEndAction(() -> mLoading.setVisibility(View.INVISIBLE));
            });
        }

        public void setVisibility(boolean visible) {
            mIsVisible = visible;
            setEngineVisibility(visible);
        }

        private void setEngineVisibility(boolean visible) {
            if (mEngine != null && visible != mIsEngineVisible) {
                try {
                    mEngine.setVisibility(visible);
                    mIsEngineVisible = visible;
                } catch (RemoteException e) {
                    Log.w(LOG_TAG, "Failure setting wallpaper visibility ", e);
                }
            }
        }
    }

    private static class WallpaperTargetAdapter extends ArrayAdapter<CharSequence> {

        public WallpaperTargetAdapter(Context context) {
            super(context, R.layout.wallpaper_target_dialog_item,
                    context.getResources().getTextArray(R.array.which_wallpaper_options));
        }

        @Override
        public boolean hasStableIds() {
            return true;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            TextView tv = (TextView) super.getView(position, convertView, parent);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    position == 0 ? R.drawable.ic_home : R.drawable.ic_device, 0, 0, 0);
            return tv;
        }
    }
}
