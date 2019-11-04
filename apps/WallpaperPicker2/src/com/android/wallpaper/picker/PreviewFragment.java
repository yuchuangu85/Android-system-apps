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

import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_COLLAPSED;
import static com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Bitmap.Config;
import android.graphics.Color;
import android.graphics.Point;
import android.graphics.PointF;
import android.graphics.PorterDuff.Mode;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.ContextThemeWrapper;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.Surface;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.BitmapReceiver;
import com.android.wallpaper.asset.Asset.DimensionsReceiver;
import com.android.wallpaper.compat.BuildCompat;
import com.android.wallpaper.model.LiveWallpaperInfo;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.ExploreIntentChecker;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPersister.SetWallpaperCallback;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.module.WallpaperSetter;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.WallpaperCropUtils;
import com.android.wallpaper.widget.MaterialProgressDrawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;
import com.davemorrissey.labs.subscaleview.ImageSource;
import com.davemorrissey.labs.subscaleview.SubsamplingScaleImageView;
import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetBehavior.State;

import java.util.Date;
import java.util.List;

/**
 * Fragment which displays the UI for previewing an individual wallpaper and its attribution
 * information.
 */
public class PreviewFragment extends Fragment implements
        SetWallpaperDialogFragment.Listener, SetWallpaperErrorDialogFragment.Listener,
        LoadWallpaperErrorDialogFragment.Listener {

    /**
     * User can view wallpaper and attributions in full screen, but "Set wallpaper" button is hidden.
     */
    public static final int MODE_VIEW_ONLY = 0;

    /**
     * User can view wallpaper and attributions in full screen and click "Set wallpaper" to set the
     * wallpaper with pan and crop position to the device.
     */
    public static final int MODE_CROP_AND_SET_WALLPAPER = 1;

    /**
     * Possible preview modes for the fragment.
     */
    @IntDef({
            MODE_VIEW_ONLY,
            MODE_CROP_AND_SET_WALLPAPER})
    public @interface PreviewMode {
    }

    public static final String ARG_WALLPAPER = "wallpaper";
    public static final String ARG_PREVIEW_MODE = "preview_mode";
    public static final String ARG_TESTING_MODE_ENABLED = "testing_mode_enabled";
    private static final String TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "load_wallpaper_error_dialog";
    private static final String TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "set_wallpaper_error_dialog";
    private static final int UNUSED_REQUEST_CODE = 1;
    private static final float DEFAULT_WALLPAPER_MAX_ZOOM = 8f;
    private static final String TAG = "PreviewFragment";
    private static final float PAGE_BITMAP_MAX_HEAP_RATIO = 0.25f;
    private static final String KEY_BOTTOM_SHEET_STATE = "key_bottom_sheet_state";

    @PreviewMode
    private int mPreviewMode;

    /**
     * When true, enables a test mode of operation -- in which certain UI features are disabled to
     * allow for UI tests to run correctly. Works around issue in ProgressDialog currently where the
     * dialog constantly keeps the UI thread alive and blocks a test forever.
     */
    private boolean mTestingModeEnabled;

    protected SubsamplingScaleImageView mFullResImageView;
    protected WallpaperInfo mWallpaper;
    private Asset mWallpaperAsset;
    private WallpaperSetter mWallpaperSetter;
    private UserEventLogger mUserEventLogger;
    private LinearLayout mBottomSheet;
    private TextView mAttributionTitle;
    private TextView mAttributionSubtitle1;
    private TextView mAttributionSubtitle2;
    private Button mAttributionExploreButton;
    private int mCurrentScreenOrientation;
    private Point mDefaultCropSurfaceSize;
    private Point mScreenSize;
    private Point mRawWallpaperSize; // Native size of wallpaper image.
    private ImageView mLoadingIndicator;
    private MaterialProgressDrawable mProgressDrawable;
    private ImageView mLowResImageView;
    private Button mSetWallpaperButton;
    private View mSpacer;
    private CheckBox mPreview;

    @SuppressWarnings("RestrictTo")
    @State
    private int mBottomSheetInitialState;

    private Intent mExploreIntent;

    /**
     * Staged error dialog fragments that were unable to be shown when the hosting activity didn't
     * allow committing fragment transactions.
     */
    private SetWallpaperErrorDialogFragment mStagedSetWallpaperErrorDialogFragment;
    private LoadWallpaperErrorDialogFragment mStagedLoadWallpaperErrorDialogFragment;

    /**
     * Creates and returns new instance of {@link PreviewFragment} with the provided wallpaper set as
     * an argument.
     */
    public static PreviewFragment newInstance(
            WallpaperInfo wallpaperInfo, @PreviewMode int mode, boolean testingModeEnabled) {
        Bundle args = new Bundle();
        args.putParcelable(ARG_WALLPAPER, wallpaperInfo);
        args.putInt(ARG_PREVIEW_MODE, mode);
        args.putBoolean(ARG_TESTING_MODE_ENABLED, testingModeEnabled);

        PreviewFragment fragment = new PreviewFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Activity activity = getActivity();
        Context appContext = activity.getApplicationContext();
        Injector injector = InjectorProvider.getInjector();

        mUserEventLogger = injector.getUserEventLogger(appContext);
        mWallpaper = getArguments().getParcelable(ARG_WALLPAPER);
        mWallpaperAsset = mWallpaper.getAsset(appContext);
        //noinspection ResourceType
        mPreviewMode = getArguments().getInt(ARG_PREVIEW_MODE);
        mTestingModeEnabled = getArguments().getBoolean(ARG_TESTING_MODE_ENABLED);
        mWallpaperSetter = new WallpaperSetter(injector.getWallpaperPersister(appContext),
                injector.getPreferences(appContext), mUserEventLogger, mTestingModeEnabled);

        setHasOptionsMenu(true);

        // Allow the layout to draw fullscreen even behind the status bar, so we can set as the status
        // bar color a color that has a custom translucency in the theme.
        Window window = activity.getWindow();
        window.getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);

        List<String> attributions = mWallpaper.getAttributions(activity);
        if (attributions.size() > 0 && attributions.get(0) != null) {
            activity.setTitle(attributions.get(0));
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_preview, container, false);

        // Set toolbar as the action bar.
        Toolbar toolbar = view.findViewById(R.id.toolbar);
        AppCompatActivity activity = (AppCompatActivity) getActivity();
        activity.setSupportActionBar(toolbar);
        activity.getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        activity.getSupportActionBar().setDisplayShowTitleEnabled(false);

        // Use updated fancy arrow icon for O+.
        if (BuildCompat.isAtLeastO()) {
            Drawable navigationIcon = getResources().getDrawable(
                    R.drawable.material_ic_arrow_back_black_24);

            // This Drawable's state is shared across the app, so make a copy of it before applying a
            // color tint as not to affect other clients elsewhere in the app.
            navigationIcon = navigationIcon.getConstantState().newDrawable().mutate();
            navigationIcon.setColorFilter(
                    getResources().getColor(R.color.material_white_100), Mode.SRC_IN);
            navigationIcon.setAutoMirrored(true);
            toolbar.setNavigationIcon(navigationIcon);
        }

        ViewCompat.setPaddingRelative(toolbar,
        /* start */ getResources().getDimensionPixelSize(
                        R.dimen.preview_toolbar_up_button_start_padding),
        /* top */ 0,
        /* end */ getResources().getDimensionPixelSize(
                        R.dimen.preview_toolbar_set_wallpaper_button_end_padding),
        /* bottom */ 0);

        mFullResImageView = view.findViewById(R.id.full_res_image);
        mLoadingIndicator = view.findViewById(R.id.loading_indicator);

        mBottomSheet = view.findViewById(R.id.bottom_sheet);
        mAttributionTitle = view.findViewById(R.id.preview_attribution_pane_title);
        mAttributionSubtitle1 = view.findViewById(R.id.preview_attribution_pane_subtitle1);
        mAttributionSubtitle2 = view.findViewById(R.id.preview_attribution_pane_subtitle2);
        mAttributionExploreButton = view.findViewById(
                R.id.preview_attribution_pane_explore_button);
        mLowResImageView = view.findViewById(R.id.low_res_image);
        mSetWallpaperButton = view.findViewById(R.id.preview_attribution_pane_set_wallpaper_button);
        mSpacer = view.findViewById(R.id.spacer);

        // Workaround as we don't have access to bottomDialogCornerRadius, mBottomSheet radii are
        // set to dialogCornerRadius by default.
        GradientDrawable bottomSheetBackground = (GradientDrawable) mBottomSheet.getBackground();
        float[] radii = bottomSheetBackground.getCornerRadii();
        for (int i = 0; i < radii.length; i++) {
            radii[i]*=2f;
        }
        bottomSheetBackground = ((GradientDrawable)bottomSheetBackground.mutate());
        bottomSheetBackground.setCornerRadii(radii);
        mBottomSheet.setBackground(bottomSheetBackground);

        // Trim some memory from Glide to make room for the full-size image in this fragment.
        Glide.get(getActivity()).setMemoryCategory(MemoryCategory.LOW);

        mDefaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                getResources(), getActivity().getWindowManager().getDefaultDisplay());
        mScreenSize = ScreenSizeCalculator.getInstance().getScreenSize(
                getActivity().getWindowManager().getDefaultDisplay());

        // Load a low-res placeholder image if there's a thumbnail available from the asset that can be
        // shown to the user more quickly than the full-sized image.
        if (mWallpaperAsset.hasLowResDataSource()) {
            mWallpaperAsset.loadLowResDrawable(getActivity(), mLowResImageView, Color.BLACK,
                    new WallpaperPreviewBitmapTransformation(getActivity().getApplicationContext(),
                            isRtl()));
        }

        mWallpaperAsset.decodeRawDimensions(getActivity(), new DimensionsReceiver() {
            @Override
            public void onDimensionsDecoded(Point dimensions) {
                // Don't continue loading the wallpaper if the Fragment is detached.
                Activity activity = getActivity();
                if (activity == null) {
                    return;
                }

                // Return early and show a dialog if dimensions are null (signaling a decoding error).
                if (dimensions == null) {
                    showLoadWallpaperErrorDialog();
                    return;
                }

                mRawWallpaperSize = dimensions;
                ExploreIntentChecker intentChecker =
                        InjectorProvider.getInjector().getExploreIntentChecker(activity);
                String actionUrl = mWallpaper.getActionUrl(activity);
                if (actionUrl != null && !actionUrl.isEmpty()) {
                    Uri exploreUri = Uri.parse(mWallpaper.getActionUrl(activity));

                    intentChecker.fetchValidActionViewIntent(exploreUri, exploreIntent -> {
                        if (getActivity() == null) {
                            return;
                        }

                        mExploreIntent = exploreIntent;
                        initFullResView();
                    });
                } else {
                    initFullResView();
                }
            }
        });

        // Configure loading indicator with a MaterialProgressDrawable.
        mProgressDrawable = new MaterialProgressDrawable(getActivity().getApplicationContext(),
                mLoadingIndicator);
        mProgressDrawable.setAlpha(255);
        mProgressDrawable.setBackgroundColor(getResources().getColor(R.color.material_white_100,
                getContext().getTheme()));
        mProgressDrawable.setColorSchemeColors(getAttrColor(
                new ContextThemeWrapper(getContext(), getDeviceDefaultTheme()),
                android.R.attr.colorAccent));
        mProgressDrawable.updateSizes(MaterialProgressDrawable.LARGE);
        mLoadingIndicator.setImageDrawable(mProgressDrawable);

        // We don't want to show the spinner every time we load an image if it loads quickly; instead,
        // only start showing the spinner if loading the image has taken longer than half of a second.
        mLoadingIndicator.postDelayed(() -> {
            if (mFullResImageView != null && !mFullResImageView.hasImage()
                    && !mTestingModeEnabled) {
                mLoadingIndicator.setVisibility(View.VISIBLE);
                mLoadingIndicator.setAlpha(1f);
                if (mProgressDrawable != null) {
                    mProgressDrawable.start();
                }
            }
        }, 500);


        mBottomSheetInitialState = (savedInstanceState == null)
                ? STATE_EXPANDED
                : savedInstanceState.getInt(KEY_BOTTOM_SHEET_STATE,
                        STATE_EXPANDED);
        setUpBottomSheetListeners();

        return view;
    }

    protected int getDeviceDefaultTheme() {
        return android.R.style.Theme_DeviceDefault;
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences =
                InjectorProvider.getInjector().getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());

        // Show the staged 'load wallpaper' or 'set wallpaper' error dialog fragments if there is
        // one that was unable to be shown earlier when this fragment's hosting activity didn't
        // allow committing fragment transactions.
        if (mStagedLoadWallpaperErrorDialogFragment != null) {
            mStagedLoadWallpaperErrorDialogFragment.show(
                    getFragmentManager(), TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedLoadWallpaperErrorDialogFragment = null;
        }
        if (mStagedSetWallpaperErrorDialogFragment != null) {
            mStagedSetWallpaperErrorDialogFragment.show(
                    getFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedSetWallpaperErrorDialogFragment = null;
        }
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.preview_menu, menu);
    }

    @Override
    public void onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        setupPreviewMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == android.R.id.home) {
            // The Preview screen has multiple entry points. It could be opened from either
            // the IndividualPreviewActivity, the "My photos" selection (by way of
            // TopLevelPickerActivity), or from a system "crop and set wallpaper" intent.
            // Therefore, handle the Up button as a global Back.
            getActivity().onBackPressed();
            return true;
        }

        return false;
    }

    protected void setupPreviewMenu(Menu menu) {
        mPreview = (CheckBox) menu.findItem(R.id.preview).getActionView();
        mPreview.setChecked(mBottomSheetInitialState == STATE_COLLAPSED);
        mPreview.setOnClickListener(this::setPreviewBehavior);
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
        BottomSheetBehavior<?> behavior = BottomSheetBehavior.from(mBottomSheet);

        if (checkbox.isChecked()) {
            behavior.setState(STATE_COLLAPSED);
        } else {
            behavior.setState(STATE_EXPANDED);
        }
    }

    @Override
    public void onSet(int destination) {
        setCurrentWallpaper(destination);
    }

    @Override
    public void onClickTryAgain(@Destination int wallpaperDestination) {
        setCurrentWallpaper(wallpaperDestination);
    }

    @Override
    public void onClickOk() {
        FragmentActivity activity = getActivity();
        if (activity != null) {
            activity.finish();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mWallpaperSetter.cleanUp();
        if (mProgressDrawable != null) {
            mProgressDrawable.stop();
        }
        mFullResImageView.recycle();
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);
        outState.putInt(KEY_BOTTOM_SHEET_STATE, bottomSheetBehavior.getState());
    }

    private void onSetWallpaperClicked(View button) {
        if (BuildCompat.isAtLeastN()) {
            mWallpaperSetter.requestDestination(getContext(), getFragmentManager(), this,
                    mWallpaper instanceof LiveWallpaperInfo);
        } else {
            setCurrentWallpaper(WallpaperPersister.DEST_HOME_SCREEN);
        }
    }

    /**
     * Returns a zoom level that is similar to the actual zoom, but that is exactly 0.5 ** n for some
     * integer n. This is useful for downsampling a bitmap--we want to see the bitmap at full detail,
     * or downsampled to 1 in every 2 pixels, or 1 in 4, and so on, depending on the zoom.
     */
    private static float getDownsampleZoom(float actualZoom) {
        if (actualZoom > 1) {
            // Very zoomed in, but we can't sample more than 1 pixel per pixel.
            return 1.0f;
        }
        float lower = 1.0f / roundUpToPower2((int) Math.ceil(1 / actualZoom));
        float upper = lower * 2;
        return nearestValue(actualZoom, lower, upper);
    }

    /**
     * Returns the integer rounded up to the next power of 2.
     */
    private static int roundUpToPower2(int value) {
        return 1 << (32 - Integer.numberOfLeadingZeros(value - 1));
    }

    /**
     * Returns the closer of two values a and b to the given value.
     */
    private static float nearestValue(float value, float a, float b) {
        return Math.abs(a - value) < Math.abs(b - value) ? a : b;
    }

    private void setUpBottomSheetListeners() {
        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);

        bottomSheetBehavior.setBottomSheetCallback(new BottomSheetBehavior.BottomSheetCallback() {
            @Override
            public void onStateChanged(View bottomSheet, int newState) {
                // Don't respond to lingering state change events occurring after the fragment has
                // already been detached from the activity. Else, IllegalStateException may occur
                // when trying to fetch resources.
                if (getActivity() == null) {
                    return;
                }
                switch (newState) {
                    case STATE_COLLAPSED:
                        setPreviewChecked(true /* checked */);
                        break;
                    case STATE_EXPANDED:
                        setPreviewChecked(false /* checked */);
                        break;
                }
            }

            @Override
            public void onSlide(View bottomSheet, float slideOffset) {
                float alpha;
                if (slideOffset >= 0) {
                    alpha = slideOffset;
                } else {
                    alpha = 1f - slideOffset;
                }
                mAttributionTitle.setAlpha(alpha);
                mAttributionSubtitle1.setAlpha(alpha);
                mAttributionSubtitle2.setAlpha(alpha);
                mAttributionExploreButton.setAlpha(alpha);
            }
        });
    }

    private boolean isWallpaperLoaded() {
        return mFullResImageView.hasImage();
    }

    private void populateAttributionPane() {
        final Context context = getContext();

        final BottomSheetBehavior bottomSheetBehavior = BottomSheetBehavior.from(mBottomSheet);

        List<String> attributions = mWallpaper.getAttributions(context);
        if (attributions.size() > 0 && attributions.get(0) != null) {
            mAttributionTitle.setText(attributions.get(0));
        }

        if (attributions.size() > 1 && attributions.get(1) != null) {
            mAttributionSubtitle1.setVisibility(View.VISIBLE);
            mAttributionSubtitle1.setText(attributions.get(1));
        }

        if (attributions.size() > 2 && attributions.get(2) != null) {
            mAttributionSubtitle2.setVisibility(View.VISIBLE);
            mAttributionSubtitle2.setText(attributions.get(2));
        }

        if (mPreviewMode == MODE_CROP_AND_SET_WALLPAPER) {
            mSetWallpaperButton.setVisibility(View.VISIBLE);
            mSetWallpaperButton.setOnClickListener(this::onSetWallpaperClicked);
        } else {
            mSetWallpaperButton.setVisibility(View.GONE);
        }

        String actionUrl = mWallpaper.getActionUrl(context);

        mAttributionExploreButton.setVisibility(View.GONE);
        if (actionUrl != null && !actionUrl.isEmpty()) {
            if (mExploreIntent != null) {
                mAttributionExploreButton.setVisibility(View.VISIBLE);
                mAttributionExploreButton.setText(context.getString(
                        mWallpaper.getActionLabelRes(context)));

                mAttributionExploreButton.setOnClickListener(view -> {
                    mUserEventLogger.logActionClicked(mWallpaper.getCollectionId(context),
                            mWallpaper.getActionLabelRes(context));

                    startActivity(mExploreIntent);
                });
            }
        }

        if (mAttributionExploreButton.getVisibility() == View.VISIBLE
                && mSetWallpaperButton.getVisibility() == View.VISIBLE) {
            mSpacer.setVisibility(View.VISIBLE);
        } else {
            mSpacer.setVisibility(View.GONE);
        }

        mBottomSheet.setVisibility(View.VISIBLE);

        // Initialize the state of the BottomSheet based on the current state because if the initial
        // and current state are the same, the state change listener won't fire and set the correct
        // arrow asset and text alpha.
        if (bottomSheetBehavior.getState() == STATE_EXPANDED) {
            setPreviewChecked(false);
            mAttributionTitle.setAlpha(1f);
            mAttributionSubtitle1.setAlpha(1f);
            mAttributionSubtitle2.setAlpha(1f);
        } else {
            setPreviewChecked(true);
            mAttributionTitle.setAlpha(0f);
            mAttributionSubtitle1.setAlpha(0f);
            mAttributionSubtitle2.setAlpha(0f);
        }

        // Let the state change listener take care of animating a state change to the initial state
        // if there's a state change.
        bottomSheetBehavior.setState(mBottomSheetInitialState);
    }

    /**
     * Initializes MosaicView by initializing tiling, setting a fallback page bitmap, and
     * initializing a zoom-scroll observer and click listener.
     */
    private void initFullResView() {
        mFullResImageView.setMinimumScaleType(SubsamplingScaleImageView.SCALE_TYPE_CENTER_CROP);

        // Set a solid black "page bitmap" so MosaicView draws a black background while waiting
        // for the image to load or a transparent one if a thumbnail already loaded.
        Bitmap blackBitmap = Bitmap.createBitmap(1, 1, Config.ARGB_8888);
        int color = (mLowResImageView.getDrawable() == null) ? Color.BLACK : Color.TRANSPARENT;
        blackBitmap.setPixel(0, 0, color);
        mFullResImageView.setImage(ImageSource.bitmap(blackBitmap));

        // Then set a fallback "page bitmap" to cover the whole MosaicView, which is an actual
        // (lower res) version of the image to be displayed.
        Point targetPageBitmapSize = new Point(mRawWallpaperSize);
        mWallpaperAsset.decodeBitmap(targetPageBitmapSize.x, targetPageBitmapSize.y,
                new BitmapReceiver() {
                    @Override
                    public void onBitmapDecoded(Bitmap pageBitmap) {
                        // Check that the activity is still around since the decoding task started.
                        if (getActivity() == null) {
                            return;
                        }

                        // Some of these may be null depending on if the Fragment is paused, stopped,
                        // or destroyed.
                        if (mLoadingIndicator != null) {
                            mLoadingIndicator.setVisibility(View.GONE);
                        }
                        // The page bitmap may be null if there was a decoding error, so show an
                        // error dialog.
                        if (pageBitmap == null) {
                            showLoadWallpaperErrorDialog();
                            return;
                        }
                        if (mFullResImageView != null) {
                            // Set page bitmap.
                            mFullResImageView.setImage(ImageSource.bitmap(pageBitmap));

                            setDefaultWallpaperZoomAndScroll();
                            crossFadeInMosaicView();
                        }
                        if (mProgressDrawable != null) {
                            mProgressDrawable.stop();
                        }
                        getActivity().invalidateOptionsMenu();

                        populateAttributionPane();
                    }
                });
    }

    /**
     * Makes the MosaicView visible with an alpha fade-in animation while fading out the loading
     * indicator.
     */
    private void crossFadeInMosaicView() {
        long shortAnimationDuration = getResources().getInteger(
                android.R.integer.config_shortAnimTime);

        mFullResImageView.setAlpha(0f);
        mFullResImageView.animate()
                .alpha(1f)
                .setDuration(shortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        // Clear the thumbnail bitmap reference to save memory since it's no longer
                        // visible.
                        if (mLowResImageView != null) {
                            mLowResImageView.setImageBitmap(null);
                        }
                    }
                });

        mLoadingIndicator.animate()
                .alpha(0f)
                .setDuration(shortAnimationDuration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        if (mLoadingIndicator != null) {
                            mLoadingIndicator.setVisibility(View.GONE);
                        }
                    }
                });
    }

    /**
     * Sets the default wallpaper zoom and scroll position based on a "crop surface"
     * (with extra width to account for parallax) superimposed on the screen. Shows as much of the
     * wallpaper as possible on the crop surface and align screen to crop surface such that the
     * default preview matches what would be seen by the user in the left-most home screen.
     *
     * <p>This method is called once in the Fragment lifecycle after the wallpaper asset has loaded
     * and rendered to the layout.
     */
    private void setDefaultWallpaperZoomAndScroll() {
        // Determine minimum zoom to fit maximum visible area of wallpaper on crop surface.
        float defaultWallpaperZoom =
                WallpaperCropUtils.calculateMinZoom(mRawWallpaperSize, mDefaultCropSurfaceSize);
        float minWallpaperZoom =
                WallpaperCropUtils.calculateMinZoom(mRawWallpaperSize, mScreenSize);

        Point screenToCropSurfacePosition = WallpaperCropUtils.calculateCenterPosition(
                mDefaultCropSurfaceSize, mScreenSize, true /* alignStart */, isRtl());
        Point zoomedWallpaperSize = new Point(
                Math.round(mRawWallpaperSize.x * defaultWallpaperZoom),
                Math.round(mRawWallpaperSize.y * defaultWallpaperZoom));
        Point cropSurfaceToWallpaperPosition = WallpaperCropUtils.calculateCenterPosition(
                zoomedWallpaperSize, mDefaultCropSurfaceSize, false /* alignStart */, isRtl());

        // Set min wallpaper zoom and max zoom on MosaicView widget.
        mFullResImageView.setMaxScale(Math.max(DEFAULT_WALLPAPER_MAX_ZOOM, defaultWallpaperZoom));
        mFullResImageView.setMinScale(minWallpaperZoom);

        // Set center to composite positioning between scaled wallpaper and screen.
        PointF centerPosition = new PointF(
                mRawWallpaperSize.x / 2f,
                mRawWallpaperSize.y / 2f);
        centerPosition.offset( - (screenToCropSurfacePosition.x + cropSurfaceToWallpaperPosition.x),
                - (screenToCropSurfacePosition.y + cropSurfaceToWallpaperPosition.y));

        mFullResImageView.setScaleAndCenter(minWallpaperZoom, centerPosition);
    }

    protected Rect calculateCropRect() {
        // Calculate Rect of wallpaper in physical pixel terms (i.e., scaled to current zoom).
        float wallpaperZoom = mFullResImageView.getScale();
        int scaledWallpaperWidth = (int) (mRawWallpaperSize.x * wallpaperZoom);
        int scaledWallpaperHeight = (int) (mRawWallpaperSize.y * wallpaperZoom);
        Rect rect = new Rect();
        mFullResImageView.visibleFileRect(rect);
        int scrollX = (int) (rect.left * wallpaperZoom);
        int scrollY = (int) (rect.top * wallpaperZoom);

        rect.set(0, 0, scaledWallpaperWidth, scaledWallpaperHeight);
        Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(
                getActivity().getWindowManager().getDefaultDisplay());
        // Crop rect should start off as the visible screen and then include extra width and height
        // if available within wallpaper at the current zoom.
        Rect cropRect = new Rect(scrollX, scrollY, scrollX + screenSize.x, scrollY + screenSize.y);

        Point defaultCropSurfaceSize = WallpaperCropUtils.getDefaultCropSurfaceSize(
                getResources(), getActivity().getWindowManager().getDefaultDisplay());
        int extraWidth = defaultCropSurfaceSize.x - screenSize.x;
        int extraHeightTopAndBottom = (int) ((defaultCropSurfaceSize.y - screenSize.y) / 2f);

        // Try to increase size of screenRect to include extra width depending on the layout
        // direction.
        if (isRtl()) {
            cropRect.left = Math.max(cropRect.left - extraWidth, rect.left);
        } else {
            cropRect.right = Math.min(cropRect.right + extraWidth, rect.right);
        }

        // Try to increase the size of the cropRect to to include extra height.
        int availableExtraHeightTop = cropRect.top - Math.max(
                rect.top,
                cropRect.top - extraHeightTopAndBottom);
        int availableExtraHeightBottom = Math.min(
                rect.bottom,
                cropRect.bottom + extraHeightTopAndBottom) - cropRect.bottom;

        int availableExtraHeightTopAndBottom =
                Math.min(availableExtraHeightTop, availableExtraHeightBottom);
        cropRect.top -= availableExtraHeightTopAndBottom;
        cropRect.bottom += availableExtraHeightTopAndBottom;

        return cropRect;
    }

    /**
     * Sets current wallpaper to the device based on current zoom and scroll state.
     *
     * @param destination The wallpaper destination i.e. home vs. lockscreen vs. both.
     */
    private void setCurrentWallpaper(@Destination final int destination) {
        mWallpaperSetter.setCurrentWallpaper(getActivity(), mWallpaper, mWallpaperAsset,
                destination, mFullResImageView.getScale(), calculateCropRect(),
                new SetWallpaperCallback() {
                    @Override
                    public void onSuccess() {
                        finishActivityWithResultOk();
                    }

                    @Override
                    public void onError(@Nullable Throwable throwable) {
                        showSetWallpaperErrorDialog(destination);
                    }
                });
    }

    private void finishActivityWithResultOk() {
        try {
            Toast.makeText(
                    getActivity(), R.string.wallpaper_set_successfully_message, Toast.LENGTH_SHORT).show();
        } catch (NotFoundException e) {
            Log.e(TAG, "Could not show toast " + e);
        }
        getActivity().overridePendingTransition(R.anim.fade_in, R.anim.fade_out);
        getActivity().setResult(Activity.RESULT_OK);
        getActivity().finish();
    }

    private void showSetWallpaperErrorDialog(@Destination int wallpaperDestination) {
        SetWallpaperErrorDialogFragment newFragment = SetWallpaperErrorDialogFragment.newInstance(
                R.string.set_wallpaper_error_message, wallpaperDestination);
        newFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        // Show 'set wallpaper' error dialog now if it's safe to commit fragment transactions,
        // otherwise stage it for later when the hosting activity is in a state to commit fragment
        // transactions.
        BasePreviewActivity activity = (BasePreviewActivity) getActivity();
        if (activity.isSafeToCommitFragmentTransaction()) {
            newFragment.show(getFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedSetWallpaperErrorDialogFragment = newFragment;
        }
    }

    /**
     * Shows 'load wallpaper' error dialog now or stage it to be shown when the hosting activity is
     * in a state that allows committing fragment transactions.
     */
    private void showLoadWallpaperErrorDialog() {
        LoadWallpaperErrorDialogFragment dialogFragment =
                LoadWallpaperErrorDialogFragment.newInstance();
        dialogFragment.setTargetFragment(PreviewFragment.this, UNUSED_REQUEST_CODE);

        // Show 'load wallpaper' error dialog now or stage it to be shown when the hosting
        // activity is in a state that allows committing fragment transactions.
        BasePreviewActivity activity = (BasePreviewActivity) getActivity();
        if (activity != null && activity.isSafeToCommitFragmentTransaction()) {
            dialogFragment.show(PreviewFragment.this.getFragmentManager(),
                    TAG_LOAD_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedLoadWallpaperErrorDialogFragment = dialogFragment;
        }
    }

    @IntDef({
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT,
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE})
    private @interface ActivityInfoScreenOrientation {
    }

    /**
     * Gets the appropriate ActivityInfo orientation for the current configuration orientation to
     * enable locking screen rotation at API levels lower than 18.
     */
    @ActivityInfoScreenOrientation
    private int getCompatActivityInfoOrientation() {
        int configOrientation = getResources().getConfiguration().orientation;
        final Display display = getActivity().getWindowManager().getDefaultDisplay();
        int naturalOrientation = Configuration.ORIENTATION_LANDSCAPE;
        switch (display.getRotation()) {
            case Surface.ROTATION_0:
            case Surface.ROTATION_180:
                // We are currently in the same basic orientation as the natural orientation.
                naturalOrientation = configOrientation;
                break;
            case Surface.ROTATION_90:
            case Surface.ROTATION_270:
                // We are currently in the other basic orientation to the natural orientation.
                naturalOrientation = (configOrientation == Configuration.ORIENTATION_LANDSCAPE)
                        ? Configuration.ORIENTATION_PORTRAIT : Configuration.ORIENTATION_LANDSCAPE;
                break;
            default:
                // continue below
        }

        // Since the map starts at portrait, we need to offset if this device's natural orientation
        // is landscape.
        int indexOffset = 0;
        if (naturalOrientation == Configuration.ORIENTATION_LANDSCAPE) {
            indexOffset = 1;
        }

        switch ((display.getRotation() + indexOffset) % 4) {
            case 0:
                return ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
            case 1:
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
            case 2:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT;
            case 3:
                return ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
            default:
                Log.e(TAG, "Display rotation did not correspond to a valid ActivityInfo orientation"
                        +  "with display rotation: " + display.getRotation() + " and index offset: "
                        + indexOffset + ".");
                return ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
        }
    }

    /**
     * Returns whether layout direction is RTL (or false for LTR). Since native RTL layout support
     * was added in API 17, returns false for versions lower than 17.
     */
    private boolean isRtl() {
        return getResources().getConfiguration().getLayoutDirection()
                    == View.LAYOUT_DIRECTION_RTL;
    }
}