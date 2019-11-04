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
import android.app.ProgressDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources.NotFoundException;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.os.Handler;
import android.service.wallpaper.WallpaperService;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.asset.Asset.DrawableLoadedListener;
import com.android.wallpaper.config.Flags;
import com.android.wallpaper.model.WallpaperCategory;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.model.WallpaperReceiver;
import com.android.wallpaper.model.WallpaperRotationInitializer;
import com.android.wallpaper.model.WallpaperRotationInitializer.Listener;
import com.android.wallpaper.model.WallpaperRotationInitializer.NetworkPreference;
import com.android.wallpaper.model.WallpaperRotationInitializer.RotationInitializationState;
import com.android.wallpaper.model.WallpaperRotationInitializer.RotationStateListener;
import com.android.wallpaper.module.FormFactorChecker;
import com.android.wallpaper.module.FormFactorChecker.FormFactor;
import com.android.wallpaper.module.Injector;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.PackageStatusNotifier;
import com.android.wallpaper.module.RotatingWallpaperComponentChecker;
import com.android.wallpaper.module.WallpaperChangedNotifier;
import com.android.wallpaper.module.WallpaperPersister;
import com.android.wallpaper.module.WallpaperPersister.Destination;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.picker.BaseActivity;
import com.android.wallpaper.picker.CurrentWallpaperBottomSheetPresenter;
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosStarterProvider;
import com.android.wallpaper.picker.RotationStarter;
import com.android.wallpaper.picker.SetWallpaperErrorDialogFragment;
import com.android.wallpaper.picker.StartRotationDialogFragment;
import com.android.wallpaper.picker.StartRotationErrorDialogFragment;
import com.android.wallpaper.picker.WallpapersUiContainer;
import com.android.wallpaper.picker.individual.SetIndividualHolder.OnSetListener;
import com.android.wallpaper.util.DiskBasedLogger;
import com.android.wallpaper.util.TileSizeCalculator;
import com.android.wallpaper.widget.GridMarginDecoration;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Random;

/**
 * Displays the Main UI for picking an individual wallpaper image.
 */
public class IndividualPickerFragment extends Fragment
        implements RotationStarter, StartRotationErrorDialogFragment.Listener,
        CurrentWallpaperBottomSheetPresenter.RefreshListener,
        SetWallpaperErrorDialogFragment.Listener {
    /**
     * Position of a special tile that doesn't belong to an individual wallpaper of the category,
     * such as "my photos" or "daily rotation".
     */
    static final int SPECIAL_FIXED_TILE_ADAPTER_POSITION = 0;
    static final String ARG_CATEGORY_COLLECTION_ID = "category_collection_id";

    private static final String TAG = "IndividualPickerFrgmnt";
    private static final int UNUSED_REQUEST_CODE = 1;
    private static final String TAG_START_ROTATION_DIALOG = "start_rotation_dialog";
    private static final String TAG_START_ROTATION_ERROR_DIALOG = "start_rotation_error_dialog";
    private static final String PROGRESS_DIALOG_NO_TITLE = null;
    private static final boolean PROGRESS_DIALOG_INDETERMINATE = true;
    private static final String TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT =
            "individual_set_wallpaper_error_dialog";
    private static final String KEY_NIGHT_MODE = "IndividualPickerFragment.NIGHT_MODE";

    WallpaperPreferences mWallpaperPreferences;
    WallpaperChangedNotifier mWallpaperChangedNotifier;
    RotatingWallpaperComponentChecker mRotatingWallpaperComponentChecker;
    RecyclerView mImageGrid;
    IndividualAdapter mAdapter;
    WallpaperCategory mCategory;
    WallpaperRotationInitializer mWallpaperRotationInitializer;
    List<WallpaperInfo> mWallpapers;
    Point mTileSizePx;
    WallpapersUiContainer mWallpapersUiContainer;
    @FormFactor
    int mFormFactor;
    PackageStatusNotifier mPackageStatusNotifier;

    Handler mHandler;
    Random mRandom;

    WallpaperChangedNotifier.Listener mWallpaperChangedListener =
            new WallpaperChangedNotifier.Listener() {
        @Override
        public void onWallpaperChanged() {
            if (mFormFactor != FormFactorChecker.FORM_FACTOR_DESKTOP) {
                return;
            }

            ViewHolder selectedViewHolder = mImageGrid.findViewHolderForAdapterPosition(
                    mAdapter.mSelectedAdapterPosition);

            // Null remote ID => My Photos wallpaper, so deselect whatever was previously selected.
            if (mWallpaperPreferences.getHomeWallpaperRemoteId() == null) {
                if (selectedViewHolder instanceof SelectableHolder) {
                    ((SelectableHolder) selectedViewHolder).setSelectionState(
                            SelectableHolder.SELECTION_STATE_DESELECTED);
                }
            } else {
                mAdapter.updateSelectedTile(mAdapter.mPendingSelectedAdapterPosition);
            }
        }
    };
    PackageStatusNotifier.Listener mAppStatusListener;

    private ProgressDialog mProgressDialog;
    private boolean mTestingMode;
    private CurrentWallpaperBottomSheetPresenter mCurrentWallpaperBottomSheetPresenter;
    private SetIndividualHolder mPendingSetIndividualHolder;

    /**
     * Staged error dialog fragments that were unable to be shown when the activity didn't allow
     * committing fragment transactions.
     */
    private SetWallpaperErrorDialogFragment mStagedSetWallpaperErrorDialogFragment;
    private StartRotationErrorDialogFragment mStagedStartRotationErrorDialogFragment;

    private Runnable mCurrentWallpaperBottomSheetExpandedRunnable;

    /**
     * Whether {@code mUpdateDailyWallpaperThumbRunnable} has been run at least once in this
     * invocation of the fragment.
     */
    private boolean mWasUpdateRunnableRun;

    /**
     * A Runnable which regularly updates the thumbnail for the "Daily wallpapers" tile in desktop
     * mode.
     */
    private Runnable mUpdateDailyWallpaperThumbRunnable = new Runnable() {
        @Override
        public void run() {
            ViewHolder viewHolder = mImageGrid.findViewHolderForAdapterPosition(
                    SPECIAL_FIXED_TILE_ADAPTER_POSITION);
            if (viewHolder instanceof DesktopRotationHolder) {
                updateDesktopDailyRotationThumbnail((DesktopRotationHolder) viewHolder);
            } else { // viewHolder is null
                // If the rotation tile is unavailable (because user has scrolled down, causing the
                // ViewHolder to be recycled), schedule the update for some time later. Once user scrolls up
                // again, the ViewHolder will be re-bound and its thumbnail will be updated.
                mHandler.postDelayed(mUpdateDailyWallpaperThumbRunnable,
                        DesktopRotationHolder.CROSSFADE_DURATION_MILLIS
                                + DesktopRotationHolder.CROSSFADE_DURATION_PAUSE_MILLIS);
            }
        }
    };

    public static IndividualPickerFragment newInstance(String collectionId) {
        Bundle args = new Bundle();
        args.putString(ARG_CATEGORY_COLLECTION_ID, collectionId);

        IndividualPickerFragment fragment = new IndividualPickerFragment();
        fragment.setArguments(args);
        return fragment;
    }

    private static int getResIdForRotationState(@RotationInitializationState int rotationState) {
        switch (rotationState) {
            case WallpaperRotationInitializer.ROTATION_NOT_INITIALIZED:
                return R.string.daily_refresh_tile_subtitle;
            case WallpaperRotationInitializer.ROTATION_HOME_ONLY:
                return R.string.home_screen_message;
            case WallpaperRotationInitializer.ROTATION_HOME_AND_LOCK:
                return R.string.home_and_lock_short_label;
            default:
                Log.e(TAG, "Unknown rotation intialization state: " + rotationState);
                return R.string.home_screen_message;
        }
    }

    private void updateDesktopDailyRotationThumbnail(DesktopRotationHolder holder) {
        int wallpapersIndex = mRandom.nextInt(mWallpapers.size());
        Asset newThumbnailAsset = mWallpapers.get(wallpapersIndex).getThumbAsset(
                getActivity());
        holder.updateThumbnail(newThumbnailAsset, new DrawableLoadedListener() {
            @Override
            public void onDrawableLoaded() {
                if (getActivity() == null) {
                    return;
                }

                // Schedule the next update of the thumbnail.
                int delayMillis = DesktopRotationHolder.CROSSFADE_DURATION_MILLIS
                        + DesktopRotationHolder.CROSSFADE_DURATION_PAUSE_MILLIS;
                mHandler.postDelayed(mUpdateDailyWallpaperThumbRunnable, delayMillis);
            }
        });
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Injector injector = InjectorProvider.getInjector();
        Context appContext = getContext().getApplicationContext();
        mWallpaperPreferences = injector.getPreferences(appContext);

        mWallpaperChangedNotifier = WallpaperChangedNotifier.getInstance();
        mWallpaperChangedNotifier.registerListener(mWallpaperChangedListener);

        mRotatingWallpaperComponentChecker = injector.getRotatingWallpaperComponentChecker();

        mFormFactor = injector.getFormFactorChecker(appContext).getFormFactor();

        mPackageStatusNotifier = injector.getPackageStatusNotifier(appContext);

        mWallpapers = new ArrayList<>();
        mRandom = new Random();
        mHandler = new Handler();

        String collectionId = getArguments().getString(ARG_CATEGORY_COLLECTION_ID);
        mCategory = (WallpaperCategory) injector.getCategoryProvider(appContext).getCategory(
                collectionId);
        if (mCategory == null) {
            DiskBasedLogger.e(TAG, "Failed to find the category.", appContext);

            // The absence of this category in the CategoryProvider indicates a broken state, probably due
            // to a relaunch into this activity/fragment following a crash immediately prior; see
            // b//38030129. Hence, finish the activity and return.
            getActivity().finish();
            return;
        }

        mWallpaperRotationInitializer = mCategory.getWallpaperRotationInitializer();

        // Clear Glide's cache if night-mode changed to ensure thumbnails are reloaded
        if (savedInstanceState != null && (savedInstanceState.getInt(KEY_NIGHT_MODE)
                != (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK))) {
            Glide.get(getContext()).clearMemory();
        }

        fetchWallpapers(false);

        if (mCategory.supportsThirdParty()) {
            mAppStatusListener = (packageName, status) -> {
                if (status != PackageStatusNotifier.PackageStatus.REMOVED ||
                        mCategory.containsThirdParty(packageName)) {
                    fetchWallpapers(true);
                }
            };
            mPackageStatusNotifier.addListener(mAppStatusListener,
                    WallpaperService.SERVICE_INTERFACE);
        }
    }

    void fetchWallpapers(boolean forceReload) {
        mWallpapers.clear();
        mCategory.fetchWallpapers(getActivity().getApplicationContext(), new WallpaperReceiver() {
            @Override
            public void onWallpapersReceived(List<WallpaperInfo> wallpapers) {
                for (WallpaperInfo wallpaper : wallpapers) {
                    mWallpapers.add(wallpaper);
                }

                // Wallpapers may load after the adapter is initialized, in which case we have
                // to explicitly notify that the data set has changed.
                if (mAdapter != null) {
                    mAdapter.notifyDataSetChanged();
                }

                if (mWallpapersUiContainer != null) {
                    mWallpapersUiContainer.onWallpapersReady();
                } else {
                    if (wallpapers.isEmpty()) {
                        // If there are no more wallpapers and we're on phone, just finish the
                        // Activity.
                        Activity activity = getActivity();
                        if (activity != null
                                && mFormFactor == FormFactorChecker.FORM_FACTOR_MOBILE) {
                            activity.finish();
                        }
                    }
                }
            }
        }, forceReload);
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(KEY_NIGHT_MODE,
                getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_individual_picker, container, false);

        mTileSizePx = TileSizeCalculator.getIndividualTileSize(getActivity());

        mImageGrid = (RecyclerView) view.findViewById(R.id.wallpaper_grid);
        if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
            int gridPaddingPx = getResources().getDimensionPixelSize(R.dimen.grid_padding_desktop);
            updateImageGridPadding(false /* addExtraBottomSpace */);
            mImageGrid.setScrollBarSize(gridPaddingPx);
        }
        GridMarginDecoration.applyTo(mImageGrid);

        setUpImageGrid();
        setUpBottomSheet();

        return view;
    }

    @Override
    public void onClickTryAgain(@Destination int unused) {
        if (mPendingSetIndividualHolder != null) {
            mPendingSetIndividualHolder.setWallpaper();
        }
    }

    void updateImageGridPadding(boolean addExtraBottomSpace) {
        int gridPaddingPx = getResources().getDimensionPixelSize(R.dimen.grid_padding_desktop);
        int bottomSheetHeightPx = getResources().getDimensionPixelSize(
                R.dimen.current_wallpaper_bottom_sheet_layout_height);
        int paddingBottomPx = addExtraBottomSpace ? bottomSheetHeightPx : 0;
        // Only left and top may be set in order for the GridMarginDecoration to work properly.
        mImageGrid.setPadding(
                gridPaddingPx, gridPaddingPx, 0, paddingBottomPx);
    }

    void setUpImageGrid() {
        mAdapter = new IndividualAdapter(mWallpapers);
        mImageGrid.setAdapter(mAdapter);
        mImageGrid.setLayoutManager(new GridLayoutManager(getActivity(), getNumColumns()));
    }

    /**
     * Enables and populates the "Currently set" wallpaper BottomSheet.
     */
    void setUpBottomSheet() {
        mImageGrid.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrolled(RecyclerView recyclerView, int dx, final int dy) {
                if (mCurrentWallpaperBottomSheetPresenter == null) {
                    return;
                }

                if (mCurrentWallpaperBottomSheetExpandedRunnable != null) {
                    mHandler.removeCallbacks(mCurrentWallpaperBottomSheetExpandedRunnable);
                }
                mCurrentWallpaperBottomSheetExpandedRunnable = new Runnable() {
                    @Override
                    public void run() {
                        if (dy > 0) {
                            mCurrentWallpaperBottomSheetPresenter.setCurrentWallpapersExpanded(false);
                        } else {
                            mCurrentWallpaperBottomSheetPresenter.setCurrentWallpapersExpanded(true);
                        }
                    }
                };
                mHandler.postDelayed(mCurrentWallpaperBottomSheetExpandedRunnable, 100);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences = InjectorProvider.getInjector().getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());

        // Reset Glide memory settings to a "normal" level of usage since it may have been lowered in
        // PreviewFragment.
        Glide.get(getActivity()).setMemoryCategory(MemoryCategory.NORMAL);

        // Show the staged 'start rotation' error dialog fragment if there is one that was unable to be
        // shown earlier when this fragment's hosting activity didn't allow committing fragment
        // transactions.
        if (mStagedStartRotationErrorDialogFragment != null) {
            mStagedStartRotationErrorDialogFragment.show(
                    getFragmentManager(), TAG_START_ROTATION_ERROR_DIALOG);
            mStagedStartRotationErrorDialogFragment = null;
        }

        // Show the staged 'load wallpaper' or 'set wallpaper' error dialog fragments if there is one
        // that was unable to be shown earlier when this fragment's hosting activity didn't allow
        // committing fragment transactions.
        if (mStagedSetWallpaperErrorDialogFragment != null) {
            mStagedSetWallpaperErrorDialogFragment.show(
                    getFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
            mStagedSetWallpaperErrorDialogFragment = null;
        }

        if (isRotationEnabled()) {
            if (mFormFactor == FormFactorChecker.FORM_FACTOR_MOBILE) {
                // Refresh the state of the "start rotation" in case something changed the current daily
                // rotation while this fragment was paused.
                RotationHolder rotationHolder = (RotationHolder) mImageGrid
                        .findViewHolderForAdapterPosition(
                                SPECIAL_FIXED_TILE_ADAPTER_POSITION);
                // The RotationHolder may be null if the RecyclerView has not created the view
                // holder yet.
                if (rotationHolder != null && Flags.dynamicStartRotationTileEnabled) {
                    refreshRotationHolder(rotationHolder);
                }
            } else if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                if (mWasUpdateRunnableRun && !mWallpapers.isEmpty()) {
                    // Must be resuming from a previously stopped state, so re-schedule the update of the
                    // daily wallpapers tile thumbnail.
                    mUpdateDailyWallpaperThumbRunnable.run();
                }
            }
        }

    }

    @Override
    public void onStop() {
        super.onStop();
        mHandler.removeCallbacks(mUpdateDailyWallpaperThumbRunnable);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mProgressDialog != null) {
            mProgressDialog.dismiss();
        }
        mWallpaperChangedNotifier.unregisterListener(mWallpaperChangedListener);
        if (mAppStatusListener != null) {
            mPackageStatusNotifier.removeListener(mAppStatusListener);
        }
    }

    @Override
    public void retryStartRotation(@NetworkPreference int networkPreference) {
        startRotation(networkPreference);
    }

    public void setCurrentWallpaperBottomSheetPresenter(
            CurrentWallpaperBottomSheetPresenter presenter) {
        mCurrentWallpaperBottomSheetPresenter = presenter;
    }

    public void setWallpapersUiContainer(WallpapersUiContainer uiContainer) {
        mWallpapersUiContainer = uiContainer;
    }

    /**
     * Enable a test mode of operation -- in which certain UI features are disabled to allow for
     * UI tests to run correctly. Works around issue in ProgressDialog currently where the dialog
     * constantly keeps the UI thread alive and blocks a test forever.
     *
     * @param testingMode
     */
    void setTestingMode(boolean testingMode) {
        mTestingMode = testingMode;
    }

    /**
     * Asynchronously fetches the refreshed rotation initialization state that is up to date with the
     * state of the user's device and binds the state of the current category's rotation to the "start
     * rotation" tile.
     */
    private void refreshRotationHolder(final RotationHolder rotationHolder) {
        mWallpaperRotationInitializer.fetchRotationInitializationState(getContext(),
                new RotationStateListener() {
                    @Override
                    public void onRotationStateReceived(
                            @RotationInitializationState final int rotationInitializationState) {

                        // Update the UI state of the "start rotation" tile displayed on screen. Do this in a
                        // Handler so it is scheduled at the end of the message queue. This is necessary to
                        // ensure we do not remove or add data from the adapter while the layout is still being
                        // computed. RecyclerView documentation therefore recommends performing such changes in
                        // a Handler.
                        new android.os.Handler().post(new Runnable() {
                            @Override
                            public void run() {
                                // A config change may have destroyed the activity since the refresh started, so
                                // check for that to avoid an NPE.
                                if (getActivity() == null) {
                                    return;
                                }

                                rotationHolder.bindRotationInitializationState(rotationInitializationState);
                            }
                        });
                    }
                });
    }

    @Override
    public void startRotation(@NetworkPreference final int networkPreference) {
        if (!isRotationEnabled()) {
            Log.e(TAG, "Rotation is not enabled for this category " + mCategory.getTitle());
            return;
        }

        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which therefore
        // causes Espresso to hang once the dialog is shown.
        if (mFormFactor == FormFactorChecker.FORM_FACTOR_MOBILE && !mTestingMode) {
            int themeResId;
            if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                themeResId = R.style.ProgressDialogThemePreL;
            } else {
                themeResId = R.style.LightDialogTheme;
            }
            mProgressDialog = new ProgressDialog(getActivity(), themeResId);

            mProgressDialog.setTitle(PROGRESS_DIALOG_NO_TITLE);
            mProgressDialog.setMessage(
                    getResources().getString(R.string.start_rotation_progress_message));
            mProgressDialog.setIndeterminate(PROGRESS_DIALOG_INDETERMINATE);
            mProgressDialog.show();
        }

        if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
            mAdapter.mPendingSelectedAdapterPosition = SPECIAL_FIXED_TILE_ADAPTER_POSITION;
        }

        final Context appContext = getActivity().getApplicationContext();

        mWallpaperRotationInitializer.setFirstWallpaperInRotation(
                appContext,
                networkPreference,
                new Listener() {
                    @Override
                    public void onFirstWallpaperInRotationSet() {
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }

                        // The fragment may be detached from its containing activity if the user exits the
                        // app before the first wallpaper image in rotation finishes downloading.
                        Activity activity = getActivity();

                        if (activity != null
                                && mWallpaperRotationInitializer
                                .isNoBackupImageWallpaperPreviewNeeded(appContext)) {
                            ((IndividualPickerActivity) activity).showNoBackupImageWallpaperPreview();
                        } else {
                            if (mWallpaperRotationInitializer.startRotation(appContext)) {
                                if (activity != null && mFormFactor == FormFactorChecker.FORM_FACTOR_MOBILE) {
                                    try {
                                        Toast.makeText(getActivity(), R.string.wallpaper_set_successfully_message,
                                                Toast.LENGTH_SHORT).show();
                                    } catch (NotFoundException e) {
                                        Log.e(TAG, "Could not show toast " + e);
                                    }

                                    activity.setResult(Activity.RESULT_OK);
                                    activity.finish();
                                } else if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                                    mAdapter.updateSelectedTile(SPECIAL_FIXED_TILE_ADAPTER_POSITION);
                                }
                            } else { // Failed to start rotation.
                                showStartRotationErrorDialog(networkPreference);

                                if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                                    DesktopRotationHolder rotationViewHolder =
                                            (DesktopRotationHolder) mImageGrid.findViewHolderForAdapterPosition(
                                                    SPECIAL_FIXED_TILE_ADAPTER_POSITION);
                                    rotationViewHolder.setSelectionState(SelectableHolder.SELECTION_STATE_DESELECTED);
                                }
                            }
                        }
                    }

                    @Override
                    public void onError() {
                        if (mProgressDialog != null) {
                            mProgressDialog.dismiss();
                        }

                        showStartRotationErrorDialog(networkPreference);

                        if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                            DesktopRotationHolder rotationViewHolder =
                                    (DesktopRotationHolder) mImageGrid.findViewHolderForAdapterPosition(
                                            SPECIAL_FIXED_TILE_ADAPTER_POSITION);
                            rotationViewHolder.setSelectionState(SelectableHolder.SELECTION_STATE_DESELECTED);
                        }
                    }
                });
    }

    private void showStartRotationErrorDialog(@NetworkPreference int networkPreference) {
        BaseActivity activity = (BaseActivity) getActivity();
        if (activity != null) {
            StartRotationErrorDialogFragment startRotationErrorDialogFragment =
                    StartRotationErrorDialogFragment.newInstance(networkPreference);
            startRotationErrorDialogFragment.setTargetFragment(
                    IndividualPickerFragment.this, UNUSED_REQUEST_CODE);

            if (activity.isSafeToCommitFragmentTransaction()) {
                startRotationErrorDialogFragment.show(
                        getFragmentManager(), TAG_START_ROTATION_ERROR_DIALOG);
            } else {
                mStagedStartRotationErrorDialogFragment = startRotationErrorDialogFragment;
            }
        }
    }

    int getNumColumns() {
        Activity activity = getActivity();
        return activity == null ? 0 : TileSizeCalculator.getNumIndividualColumns(activity);
    }

    /**
     * Returns whether rotation is enabled for this category.
     */
    boolean isRotationEnabled() {
        boolean isRotationSupported =
                mRotatingWallpaperComponentChecker.getRotatingWallpaperSupport(getContext())
                        == RotatingWallpaperComponentChecker.ROTATING_WALLPAPER_SUPPORT_SUPPORTED;

        return isRotationSupported && mWallpaperRotationInitializer != null;
    }

    @Override
    public void onCurrentWallpaperRefreshed() {
        mCurrentWallpaperBottomSheetPresenter.setCurrentWallpapersExpanded(true);
    }

    /**
     * Shows a "set wallpaper" error dialog with a failure message and button to try again.
     */
    private void showSetWallpaperErrorDialog() {
        SetWallpaperErrorDialogFragment dialogFragment = SetWallpaperErrorDialogFragment.newInstance(
                R.string.set_wallpaper_error_message, WallpaperPersister.DEST_BOTH);
        dialogFragment.setTargetFragment(this, UNUSED_REQUEST_CODE);

        if (((BaseActivity) getActivity()).isSafeToCommitFragmentTransaction()) {
            dialogFragment.show(getFragmentManager(), TAG_SET_WALLPAPER_ERROR_DIALOG_FRAGMENT);
        } else {
            mStagedSetWallpaperErrorDialogFragment = dialogFragment;
        }
    }

    /**
     * ViewHolder subclass for "daily refresh" tile in the RecyclerView, only shown if rotation is
     * enabled for this category.
     */
    private class RotationHolder extends RecyclerView.ViewHolder implements View.OnClickListener {

        private FrameLayout mTileLayout;
        private TextView mRotationMessage;
        private TextView mRotationTitle;
        private ImageView mRefreshIcon;

        RotationHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);

            mTileLayout = (FrameLayout) itemView.findViewById(R.id.daily_refresh);
            mRotationMessage = (TextView) itemView.findViewById(R.id.rotation_tile_message);
            mRotationTitle = (TextView) itemView.findViewById(R.id.rotation_tile_title);
            mRefreshIcon = (ImageView) itemView.findViewById(R.id.rotation_tile_refresh_icon);
            mTileLayout.getLayoutParams().height = mTileSizePx.y;

            // If the feature flag for "dynamic start rotation tile" is not enabled, fall back to the
            // static UI with a blue accent color background and "Tap to turn on" text.
            if (!Flags.dynamicStartRotationTileEnabled) {
                mTileLayout.setBackgroundColor(
                        getResources().getColor(R.color.rotation_tile_enabled_background_color));
                mRotationMessage.setText(R.string.daily_refresh_tile_subtitle);
                mRotationTitle.setTextColor(
                        getResources().getColor(R.color.rotation_tile_enabled_title_text_color));
                mRotationMessage.setTextColor(
                        getResources().getColor(R.color.rotation_tile_enabled_subtitle_text_color));
                mRefreshIcon.setColorFilter(
                        getResources().getColor(R.color.rotation_tile_enabled_refresh_icon_color), Mode.SRC_IN);
                return;
            }

            // Initialize the state of the "start rotation" tile (i.e., whether it is gray or blue to
            // indicate if rotation is turned on for the current category) with last-known rotation state
            // that could be stale. The last-known rotation state is correct in most cases and is a good
            // starting point but may not be accurate if the user set a wallpaper through a 3rd party app
            // while this app was paused.
            int rotationState = mWallpaperRotationInitializer.getRotationInitializationStateDirty(
                    getContext());
            bindRotationInitializationState(rotationState);
        }

        @Override
        public void onClick(View v) {
            boolean isLiveWallpaperNeeded = mWallpaperRotationInitializer
                    .isNoBackupImageWallpaperPreviewNeeded(getActivity().getApplicationContext());
            DialogFragment startRotationDialogFragment = StartRotationDialogFragment
                    .newInstance(isLiveWallpaperNeeded);
            startRotationDialogFragment.setTargetFragment(
                    IndividualPickerFragment.this, UNUSED_REQUEST_CODE);
            startRotationDialogFragment.show(getFragmentManager(), TAG_START_ROTATION_DIALOG);
        }

        /**
         * Binds the provided rotation initialization state to the RotationHolder and updates the tile's
         * UI to be in sync with the state (i.e., message and color appropriately reflect the state to
         * the user).
         */
        void bindRotationInitializationState(@RotationInitializationState int rotationState) {
            int newBackgroundColor =
                    (rotationState == WallpaperRotationInitializer.ROTATION_NOT_INITIALIZED)
                            ? getResources().getColor(R.color.rotation_tile_not_enabled_background_color)
                            : getResources().getColor(R.color.rotation_tile_enabled_background_color);
            int newTitleTextColor =
                    (rotationState == WallpaperRotationInitializer.ROTATION_NOT_INITIALIZED)
                            ? getResources().getColor(R.color.rotation_tile_not_enabled_title_text_color)
                            : getResources().getColor(R.color.rotation_tile_enabled_title_text_color);
            int newSubtitleTextColor =
                    (rotationState == WallpaperRotationInitializer.ROTATION_NOT_INITIALIZED)
                            ? getResources().getColor(R.color.rotation_tile_not_enabled_subtitle_text_color)
                            : getResources().getColor(R.color.rotation_tile_enabled_subtitle_text_color);
            int newRefreshIconColor =
                    (rotationState == WallpaperRotationInitializer.ROTATION_NOT_INITIALIZED)
                            ? getResources().getColor(R.color.rotation_tile_not_enabled_refresh_icon_color)
                            : getResources().getColor(R.color.rotation_tile_enabled_refresh_icon_color);

            mTileLayout.setBackgroundColor(newBackgroundColor);
            mRotationTitle.setTextColor(newTitleTextColor);
            mRotationMessage.setText(getResIdForRotationState(rotationState));
            mRotationMessage.setTextColor(newSubtitleTextColor);
            mRefreshIcon.setColorFilter(newRefreshIconColor, Mode.SRC_IN);
        }
    }

    /**
     * RecyclerView Adapter subclass for the wallpaper tiles in the RecyclerView.
     */
    class IndividualAdapter extends RecyclerView.Adapter<ViewHolder> {
        static final int ITEM_VIEW_TYPE_ROTATION = 1;
        static final int ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER = 2;
        static final int ITEM_VIEW_TYPE_MY_PHOTOS = 3;

        private final List<WallpaperInfo> mWallpapers;

        private int mPendingSelectedAdapterPosition;
        private int mSelectedAdapterPosition;

        IndividualAdapter(List<WallpaperInfo> wallpapers) {
            mWallpapers = wallpapers;
            mPendingSelectedAdapterPosition = -1;
            mSelectedAdapterPosition = -1;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            switch (viewType) {
                case ITEM_VIEW_TYPE_ROTATION:
                    return createRotationHolder(parent);
                case ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER:
                    return createIndividualHolder(parent);
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    return createMyPhotosHolder(parent);
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in IndividualAdapter");
                    return null;
            }
        }

        @Override
        public int getItemViewType(int position) {
            if (isRotationEnabled() && position == SPECIAL_FIXED_TILE_ADAPTER_POSITION) {
                return ITEM_VIEW_TYPE_ROTATION;
            }

            // A category cannot have both a "start rotation" tile and a "my photos" tile.
            if (mCategory.supportsCustomPhotos()
                    && !isRotationEnabled()
                    && position == SPECIAL_FIXED_TILE_ADAPTER_POSITION) {
                return ITEM_VIEW_TYPE_MY_PHOTOS;
            }

            return ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER;
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            int viewType = getItemViewType(position);

            switch (viewType) {
                case ITEM_VIEW_TYPE_ROTATION:
                    onBindRotationHolder(holder, position);
                    break;
                case ITEM_VIEW_TYPE_INDIVIDUAL_WALLPAPER:
                    onBindIndividualHolder(holder, position);
                    break;
                case ITEM_VIEW_TYPE_MY_PHOTOS:
                    ((MyPhotosViewHolder) holder).bind();
                    break;
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in IndividualAdapter");
            }
        }

        @Override
        public int getItemCount() {
            return (isRotationEnabled() || mCategory.supportsCustomPhotos())
                    ? mWallpapers.size() + 1
                    : mWallpapers.size();
        }

        private ViewHolder createRotationHolder(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view;

            if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                view = layoutInflater.inflate(R.layout.grid_item_rotation_desktop, parent, false);
                SelectionAnimator selectionAnimator =
                        new CheckmarkSelectionAnimator(getActivity(), view);
                return new DesktopRotationHolder(
                        getActivity(), mTileSizePx.y, view, selectionAnimator,
                        IndividualPickerFragment.this);
            } else { // MOBILE
                view = layoutInflater.inflate(R.layout.grid_item_rotation, parent, false);
                return new RotationHolder(view);
            }
        }

        private ViewHolder createIndividualHolder(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.grid_item_image, parent, false);

            if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                SelectionAnimator selectionAnimator =
                        new CheckmarkSelectionAnimator(getActivity(), view);
                return new SetIndividualHolder(
                        getActivity(), mTileSizePx.y, view,
                        selectionAnimator,
                        new OnSetListener() {
                            @Override
                            public void onPendingWallpaperSet(int adapterPosition) {
                                // Deselect and hide loading indicator for any previously pending tile.
                                if (mPendingSelectedAdapterPosition != -1) {
                                    ViewHolder oldViewHolder = mImageGrid.findViewHolderForAdapterPosition(
                                            mPendingSelectedAdapterPosition);
                                    if (oldViewHolder instanceof SelectableHolder) {
                                        ((SelectableHolder) oldViewHolder).setSelectionState(
                                                SelectableHolder.SELECTION_STATE_DESELECTED);
                                    }
                                }

                                if (mSelectedAdapterPosition != -1) {
                                    ViewHolder oldViewHolder = mImageGrid.findViewHolderForAdapterPosition(
                                            mSelectedAdapterPosition);
                                    if (oldViewHolder instanceof SelectableHolder) {
                                        ((SelectableHolder) oldViewHolder).setSelectionState(
                                                SelectableHolder.SELECTION_STATE_DESELECTED);
                                    }
                                }

                                mPendingSelectedAdapterPosition = adapterPosition;
                            }

                            @Override
                            public void onWallpaperSet(int adapterPosition) {
                                // No-op -- UI handles a new wallpaper being set by reacting to the
                                // WallpaperChangedNotifier.
                            }

                            @Override
                            public void onWallpaperSetFailed(SetIndividualHolder holder) {
                                showSetWallpaperErrorDialog();
                                mPendingSetIndividualHolder = holder;
                            }
                        });
            } else { // MOBILE
                return new PreviewIndividualHolder(
                        (IndividualPickerActivity) getActivity(), mTileSizePx.y, view);
            }
        }

        private ViewHolder createMyPhotosHolder(ViewGroup parent) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view = layoutInflater.inflate(R.layout.grid_item_my_photos, parent, false);

            return new MyPhotosViewHolder(getActivity(),
                    ((MyPhotosStarterProvider) getActivity()).getMyPhotosStarter(),
                    mTileSizePx.y, view);
        }

        /**
         * Marks the tile at the given position as selected with a visual indication. Also updates the
         * "currently selected" BottomSheet to reflect the newly selected tile.
         */
        private void updateSelectedTile(int newlySelectedPosition) {
            // Prevent multiple spinners from appearing with a user tapping several tiles in rapid
            // succession.
            if (mPendingSelectedAdapterPosition == mSelectedAdapterPosition) {
                return;
            }

            if (mCurrentWallpaperBottomSheetPresenter != null) {
                mCurrentWallpaperBottomSheetPresenter.refreshCurrentWallpapers(
                        IndividualPickerFragment.this);

                if (mCurrentWallpaperBottomSheetExpandedRunnable != null) {
                    mHandler.removeCallbacks(mCurrentWallpaperBottomSheetExpandedRunnable);
                }
                mCurrentWallpaperBottomSheetExpandedRunnable = new Runnable() {
                    @Override
                    public void run() {
                        mCurrentWallpaperBottomSheetPresenter.setCurrentWallpapersExpanded(true);
                    }
                };
                mHandler.postDelayed(mCurrentWallpaperBottomSheetExpandedRunnable, 100);
            }

            // User may have switched to another category, thus detaching this fragment, so check here.
            // NOTE: We do this check after updating the current wallpaper BottomSheet so that the update
            // still occurs in the UI after the user selects that other category.
            if (getActivity() == null) {
                return;
            }

            // Update the newly selected wallpaper ViewHolder and the old one so that if
            // selection UI state applies (desktop UI), it is updated.
            if (mSelectedAdapterPosition >= 0) {
                ViewHolder oldViewHolder = mImageGrid.findViewHolderForAdapterPosition(
                        mSelectedAdapterPosition);
                if (oldViewHolder instanceof SelectableHolder) {
                    ((SelectableHolder) oldViewHolder).setSelectionState(
                            SelectableHolder.SELECTION_STATE_DESELECTED);
                }
            }

            // Animate selection of newly selected tile.
            ViewHolder newViewHolder = mImageGrid
                    .findViewHolderForAdapterPosition(newlySelectedPosition);
            if (newViewHolder instanceof SelectableHolder) {
                ((SelectableHolder) newViewHolder).setSelectionState(
                        SelectableHolder.SELECTION_STATE_SELECTED);
            }

            mSelectedAdapterPosition = newlySelectedPosition;

            // If the tile was in the last row of the grid, add space below it so the user can scroll down
            // and up to see the BottomSheet without it fully overlapping the newly selected tile.
            int spanCount = ((GridLayoutManager) mImageGrid.getLayoutManager()).getSpanCount();
            int numRows = (int) Math.ceil((float) getItemCount() / spanCount);
            int rowOfNewlySelectedTile = newlySelectedPosition / spanCount;
            boolean isInLastRow = rowOfNewlySelectedTile == numRows - 1;

            updateImageGridPadding(isInLastRow /* addExtraBottomSpace */);
        }

        void onBindRotationHolder(ViewHolder holder, int position) {
            if (mFormFactor == FormFactorChecker.FORM_FACTOR_DESKTOP) {
                String collectionId = mCategory.getCollectionId();
                ((DesktopRotationHolder) holder).bind(collectionId);

                if (mWallpaperPreferences.getWallpaperPresentationMode()
                        == WallpaperPreferences.PRESENTATION_MODE_ROTATING
                        && collectionId.equals(mWallpaperPreferences.getHomeWallpaperCollectionId())) {
                    mSelectedAdapterPosition = position;
                }

                if (!mWasUpdateRunnableRun && !mWallpapers.isEmpty()) {
                    updateDesktopDailyRotationThumbnail((DesktopRotationHolder) holder);
                    mWasUpdateRunnableRun = true;
                }
            }
        }

        void onBindIndividualHolder(ViewHolder holder, int position) {
            int wallpaperIndex = (isRotationEnabled() || mCategory.supportsCustomPhotos())
                    ? position - 1 : position;
            WallpaperInfo wallpaper = mWallpapers.get(wallpaperIndex);
            ((IndividualHolder) holder).bindWallpaper(wallpaper);
            WallpaperPreferences prefs = InjectorProvider.getInjector().getPreferences(getContext());

            String wallpaperId = wallpaper.getWallpaperId();
            if (wallpaperId != null && wallpaperId.equals(prefs.getHomeWallpaperRemoteId())) {
                mSelectedAdapterPosition = position;
            }
        }
    }
}
