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

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.graphics.PorterDuff.Mode;
import android.net.Uri;
import android.os.Build.VERSION;
import android.os.Build.VERSION_CODES;
import android.os.Bundle;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.wallpaper.R;
import com.android.wallpaper.asset.Asset;
import com.android.wallpaper.config.Flags;
import com.android.wallpaper.model.Category;
import com.android.wallpaper.model.WallpaperInfo;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory;
import com.android.wallpaper.module.CurrentWallpaperInfoFactory.WallpaperInfoCallback;
import com.android.wallpaper.module.ExploreIntentChecker;
import com.android.wallpaper.module.InjectorProvider;
import com.android.wallpaper.module.LockWallpaperStatusChecker;
import com.android.wallpaper.module.UserEventLogger;
import com.android.wallpaper.module.WallpaperPreferences;
import com.android.wallpaper.module.WallpaperPreferences.PresentationMode;
import com.android.wallpaper.module.WallpaperRotationRefresher;
import com.android.wallpaper.module.WallpaperRotationRefresher.Listener;
import com.android.wallpaper.picker.MyPhotosStarter.MyPhotosStarterProvider;
import com.android.wallpaper.picker.MyPhotosStarter.PermissionChangedListener;
import com.android.wallpaper.util.DisplayMetricsRetriever;
import com.android.wallpaper.util.ScreenSizeCalculator;
import com.android.wallpaper.util.TileSizeCalculator;
import com.android.wallpaper.widget.GridMarginDecoration;

import com.bumptech.glide.Glide;
import com.bumptech.glide.MemoryCategory;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * Displays the Main UI for picking a category of wallpapers to choose from.
 */
public class CategoryFragment extends ToolbarFragment {

    /**
     * Interface to be implemented by an Activity hosting a {@link CategoryFragment}
     */
    public interface CategoryFragmentHost extends MyPhotosStarterProvider {

        void requestExternalStoragePermission(PermissionChangedListener listener);

        boolean isReadExternalStoragePermissionGranted();

        void showViewOnlyPreview(WallpaperInfo wallpaperInfo);

        void show(String collectionId);
    }

    public static CategoryFragment newInstance(CharSequence title) {
        CategoryFragment fragment = new CategoryFragment();
        fragment.setArguments(ToolbarFragment.createArguments(title));
        return fragment;
    }

    private static final String TAG = "CategoryFragment";

    // The number of ViewHolders that don't pertain to category tiles.
    // Currently 2: one for the metadata section and one for the "Select wallpaper" header.
    private static final int NUM_NON_CATEGORY_VIEW_HOLDERS = 2;

    /**
     * The fixed RecyclerView.Adapter position of the ViewHolder for the initial item in the grid --
     * usually the wallpaper metadata, or a "permission needed" warning UI.
     */
    private static final int INITIAL_HOLDER_ADAPTER_POSITION = 0;

    private static final int SETTINGS_APP_INFO_REQUEST_CODE = 1;

    private static final String PERMISSION_READ_WALLPAPER_INTERNAL =
            "android.permission.READ_WALLPAPER_INTERNAL";

    private RecyclerView mImageGrid;
    private CategoryAdapter mAdapter;
    private ArrayList<Category> mCategories = new ArrayList<>();
    private Point mTileSizePx;
    private boolean mAwaitingCategories;
    private ProgressDialog mRefreshWallpaperProgressDialog;
    private boolean mTestingMode;

    public CategoryFragment() {
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mAdapter = new CategoryAdapter(mCategories);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_category_picker, container, /* attachToRoot */ false);

        mImageGrid = view.findViewById(R.id.category_grid);
        GridMarginDecoration.applyTo(mImageGrid);

        mTileSizePx = TileSizeCalculator.getCategoryTileSize(getActivity());

        if (LockWallpaperStatusChecker.isLockWallpaperSet(getContext())) {
            mAdapter.setNumMetadataCards(CategoryAdapter.METADATA_VIEW_TWO_CARDS);
        } else {
            mAdapter.setNumMetadataCards(CategoryAdapter.METADATA_VIEW_SINGLE_CARD);
        }
        mImageGrid.setAdapter(mAdapter);

        GridLayoutManager gridLayoutManager = new GridLayoutManager(getActivity(), getNumColumns());
        gridLayoutManager.setSpanSizeLookup(new CategorySpanSizeLookup(mAdapter));
        mImageGrid.setLayoutManager(gridLayoutManager);
        setUpToolbar(view);
        return view;
    }

    @Override
    public CharSequence getDefaultTitle() {
        return getContext().getString(R.string.app_name);
    }

    @Override
    public void onResume() {
        super.onResume();

        WallpaperPreferences preferences = InjectorProvider.getInjector().getPreferences(getActivity());
        preferences.setLastAppActiveTimestamp(new Date().getTime());

        // Reset Glide memory settings to a "normal" level of usage since it may have been lowered in
        // PreviewFragment.
        Glide.get(getActivity()).setMemoryCategory(MemoryCategory.NORMAL);

        // Refresh metadata since it may have changed since the activity was paused.
        ViewHolder initialViewHolder =
                mImageGrid.findViewHolderForAdapterPosition(INITIAL_HOLDER_ADAPTER_POSITION);
        MetadataHolder metadataHolder = null;
        if (initialViewHolder instanceof MetadataHolder) {
            metadataHolder = (MetadataHolder) initialViewHolder;
        }

        // The wallpaper may have been set while this fragment was paused, so force refresh the current
        // wallpapers and presentation mode.
        refreshCurrentWallpapers(metadataHolder, true /* forceRefresh */);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mRefreshWallpaperProgressDialog != null) {
            mRefreshWallpaperProgressDialog.dismiss();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == SETTINGS_APP_INFO_REQUEST_CODE) {
            mAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Inserts the given category into the categories list in priority order.
     */
    public void addCategory(Category category, boolean loading) {
        // If not previously waiting for categories, enter the waiting state by showing the loading
        // indicator.
        if (loading && !mAwaitingCategories) {
            mAdapter.notifyItemChanged(getNumColumns());
            mAdapter.notifyItemInserted(getNumColumns());
            mAwaitingCategories = true;
        }
        // Not add existing category to category list
        if (mCategories.indexOf(category) >= 0) {
            return;
        }

        int priority = category.getPriority();

        int index = 0;
        while (index < mCategories.size() && priority >= mCategories.get(index).getPriority()) {
            index++;
        }

        mCategories.add(index, category);
        if (mAdapter != null) {
            // Offset the index because of the static metadata element at beginning of RecyclerView.
            mAdapter.notifyItemInserted(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
        }
    }

    public void removeCategory(Category category) {
        int index = mCategories.indexOf(category);
        if (index >= 0) {
            mCategories.remove(index);
            mAdapter.notifyItemRemoved(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
        }
    }

    public void updateCategory(Category category) {
        int index = mCategories.indexOf(category);
        if (index >= 0) {
            mCategories.remove(index);
            mCategories.add(index, category);
            mAdapter.notifyItemChanged(index + NUM_NON_CATEGORY_VIEW_HOLDERS);
        }
    }

    public void clearCategories() {
        mCategories.clear();
        mAdapter.notifyDataSetChanged();
    }

    /**
     * Notifies the CategoryFragment that no further categories are expected so it may hide
     * the loading indicator.
     */
    public void doneFetchingCategories() {
        if (mAwaitingCategories) {
            mAdapter.notifyItemRemoved(mAdapter.getItemCount() - 1);
            mAwaitingCategories = false;
        }
    }

    /**
     * Enable a test mode of operation -- in which certain UI features are disabled to allow for
     * UI tests to run correctly. Works around issue in ProgressDialog currently where the dialog
     * constantly keeps the UI thread alive and blocks a test forever.
     */
    void setTestingMode(boolean testingMode) {
        mTestingMode = testingMode;
    }

    private boolean canShowCurrentWallpaper() {
        Activity activity = getActivity();
        CategoryFragmentHost host = getFragmentHost();
        PackageManager packageManager = activity.getPackageManager();
        String packageName = activity.getPackageName();

        boolean hasReadWallpaperInternal = packageManager.checkPermission(
                PERMISSION_READ_WALLPAPER_INTERNAL, packageName) == PackageManager.PERMISSION_GRANTED;
        return hasReadWallpaperInternal || host.isReadExternalStoragePermissionGranted();
    }

    private CategoryFragmentHost getFragmentHost() {
        return (CategoryFragmentHost) getActivity();
    }

    /**
     * Obtains the {@link WallpaperInfo} object(s) representing the wallpaper(s) currently set to the
     * device from the {@link CurrentWallpaperInfoFactory} and binds them to the provided
     * {@link MetadataHolder}.
     */
    private void refreshCurrentWallpapers(@Nullable final MetadataHolder holder,
                                          boolean forceRefresh) {
        CurrentWallpaperInfoFactory factory = InjectorProvider.getInjector()
                .getCurrentWallpaperFactory(getActivity().getApplicationContext());

        factory.createCurrentWallpaperInfos(new WallpaperInfoCallback() {
            @Override
            public void onWallpaperInfoCreated(
                    final WallpaperInfo homeWallpaper,
                    @Nullable final WallpaperInfo lockWallpaper,
                    @PresentationMode final int presentationMode) {

                // Update the metadata displayed on screen. Do this in a Handler so it is scheduled at the
                // end of the message queue. This is necessary to ensure we do not remove or add data from
                // the adapter while the layout is being computed. RecyclerView documentation therefore
                // recommends performing such changes in a Handler.
                new android.os.Handler().post(new Runnable() {
                    @Override
                    public void run() {
                        // A config change may have destroyed the activity since the refresh started, so check
                        // for that.
                        if (getActivity() == null) {
                            return;
                        }

                        int numMetadataCards = (lockWallpaper == null)
                                ? CategoryAdapter.METADATA_VIEW_SINGLE_CARD
                                : CategoryAdapter.METADATA_VIEW_TWO_CARDS;
                        mAdapter.setNumMetadataCards(numMetadataCards);

                        // The MetadataHolder may be null if the RecyclerView has not yet created the view
                        // holder.
                        if (holder != null) {
                            holder.bindWallpapers(homeWallpaper, lockWallpaper, presentationMode);
                        }
                    }
                });
            }
        }, forceRefresh);
    }

    private int getNumColumns() {
        Activity activity = getActivity();
        return activity == null ? 0 : TileSizeCalculator.getNumCategoryColumns(activity);
    }

    /**
     * Returns the width to use for the home screen wallpaper in the "single metadata" configuration.
     */
    private int getSingleWallpaperImageWidth() {
        Point screenSize = ScreenSizeCalculator.getInstance()
                .getScreenSize(getActivity().getWindowManager().getDefaultDisplay());

        int height = getResources().getDimensionPixelSize(R.dimen.single_metadata_card_layout_height);
        return height * screenSize.x / screenSize.y;
    }

    /**
     * Refreshes the current wallpaper in a daily wallpaper rotation.
     */
    private void refreshDailyWallpaper() {
        // ProgressDialog endlessly updates the UI thread, keeping it from going idle which therefore
        // causes Espresso to hang once the dialog is shown.
        if (!mTestingMode) {
            int themeResId;
            if (VERSION.SDK_INT < VERSION_CODES.LOLLIPOP) {
                themeResId = R.style.ProgressDialogThemePreL;
            } else {
                themeResId = R.style.LightDialogTheme;
            }
            mRefreshWallpaperProgressDialog = new ProgressDialog(getActivity(), themeResId);
            mRefreshWallpaperProgressDialog.setTitle(null);
            mRefreshWallpaperProgressDialog.setMessage(
                    getResources().getString(R.string.refreshing_daily_wallpaper_dialog_message));
            mRefreshWallpaperProgressDialog.setIndeterminate(true);
            mRefreshWallpaperProgressDialog.setCancelable(false);
            mRefreshWallpaperProgressDialog.show();
        }

        WallpaperRotationRefresher wallpaperRotationRefresher =
                InjectorProvider.getInjector().getWallpaperRotationRefresher();
        wallpaperRotationRefresher.refreshWallpaper(getContext(), new Listener() {
            @Override
            public void onRefreshed() {
                // If the fragment is detached from the activity there's nothing to do here and the UI will
                // update when the fragment is resumed.
                if (getActivity() == null) {
                    return;
                }

                if (mRefreshWallpaperProgressDialog != null) {
                    mRefreshWallpaperProgressDialog.dismiss();
                }

                ViewHolder initialViewHolder =
                        mImageGrid.findViewHolderForAdapterPosition(INITIAL_HOLDER_ADAPTER_POSITION);
                if (initialViewHolder instanceof MetadataHolder) {
                    MetadataHolder metadataHolder = (MetadataHolder) initialViewHolder;
                    // Update the metadata pane since we know now the UI there is stale.
                    refreshCurrentWallpapers(metadataHolder, true /* forceRefresh */);
                }
            }

            @Override
            public void onError() {
                if (getActivity() == null) {
                    return;
                }

                if (mRefreshWallpaperProgressDialog != null) {
                    mRefreshWallpaperProgressDialog.dismiss();
                }

                AlertDialog errorDialog = new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                        .setMessage(R.string.refresh_daily_wallpaper_failed_message)
                        .setPositiveButton(android.R.string.ok, null /* onClickListener */)
                        .create();
                errorDialog.show();
            }
        });
    }

    /**
     * Returns the width to use for the home and lock screen wallpapers in the "both metadata"
     * configuration.
     */
    private int getBothWallpaperImageWidth() {
        DisplayMetrics metrics = DisplayMetricsRetriever.getInstance().getDisplayMetrics(getResources(),
                getActivity().getWindowManager().getDefaultDisplay());

        // In the "both metadata" configuration, wallpaper images minus the gutters account for the full
        // width of the device's screen.
        return metrics.widthPixels - (3 * getResources().getDimensionPixelSize(R.dimen.grid_padding));
    }

    private interface MetadataHolder {
        /**
         * Binds {@link WallpaperInfo} objects representing the currently-set wallpapers to the
         * ViewHolder layout.
         */
        void bindWallpapers(WallpaperInfo homeWallpaper, WallpaperInfo lockWallpaper,
                            @PresentationMode int presentationMode);
    }

    private static class SelectWallpaperHeaderHolder extends RecyclerView.ViewHolder {
        public SelectWallpaperHeaderHolder(View headerView) {
            super(headerView);
        }
    }

    /**
     * SpanSizeLookup subclass which provides that the item in the first position spans the number of
     * columns in the RecyclerView and all other items only take up a single span.
     */
    private class CategorySpanSizeLookup extends SpanSizeLookup {
        CategoryAdapter mAdapter;

        public CategorySpanSizeLookup(CategoryAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        public int getSpanSize(int position) {
            if (position < NUM_NON_CATEGORY_VIEW_HOLDERS
                    || mAdapter.getItemViewType(position)
                    == CategoryAdapter.ITEM_VIEW_TYPE_LOADING_INDICATOR) {
                return getNumColumns();
            }

            return 1;
        }
    }

    /**
     * ViewHolder subclass for a metadata "card" at the beginning of the RecyclerView.
     */
    private class SingleWallpaperMetadataHolder extends RecyclerView.ViewHolder
            implements MetadataHolder {
        private WallpaperInfo mWallpaperInfo;
        private ImageView mWallpaperImage;
        private TextView mWallpaperPresentationModeSubtitle;
        private TextView mWallpaperTitle;
        private TextView mWallpaperSubtitle;
        private TextView mWallpaperSubtitle2;
        private ImageButton mWallpaperExploreButtonNoText;
        private ImageButton mSkipWallpaperButton;

        public SingleWallpaperMetadataHolder(View metadataView) {
            super(metadataView);

            mWallpaperImage = metadataView.findViewById(R.id.wallpaper_image);
            mWallpaperImage.getLayoutParams().width = getSingleWallpaperImageWidth();

            mWallpaperPresentationModeSubtitle =
                    metadataView.findViewById(R.id.wallpaper_presentation_mode_subtitle);
            mWallpaperTitle = metadataView.findViewById(R.id.wallpaper_title);
            mWallpaperSubtitle = metadataView.findViewById(R.id.wallpaper_subtitle);
            mWallpaperSubtitle2 = metadataView.findViewById(R.id.wallpaper_subtitle2);

            mWallpaperExploreButtonNoText =
                    metadataView.findViewById(R.id.wallpaper_explore_button_notext);

            mSkipWallpaperButton = metadataView.findViewById(R.id.skip_wallpaper_button);
        }

        /**
         * Binds home screen wallpaper to the ViewHolder layout.
         */
        @Override
        public void bindWallpapers(WallpaperInfo homeWallpaper, WallpaperInfo lockWallpaper,
                @PresentationMode int presentationMode) {
            mWallpaperInfo = homeWallpaper;

            bindWallpaperAsset();
            bindWallpaperText(presentationMode);
            bindWallpaperActionButtons(presentationMode);
        }

        private void bindWallpaperAsset() {
            final UserEventLogger eventLogger =
                    InjectorProvider.getInjector().getUserEventLogger(getActivity());

            mWallpaperInfo.getThumbAsset(getActivity().getApplicationContext()).loadDrawable(
                    getActivity(), mWallpaperImage, getResources().getColor(R.color.secondary_color));

            mWallpaperImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    getFragmentHost().showViewOnlyPreview(mWallpaperInfo);
                    eventLogger.logCurrentWallpaperPreviewed();
                }
            });
        }

        private void bindWallpaperText(@PresentationMode int presentationMode) {
            Context appContext = getActivity().getApplicationContext();

            mWallpaperPresentationModeSubtitle.setText(
                    AttributionFormatter.getHumanReadableWallpaperPresentationMode(
                            appContext, presentationMode));

            List<String> attributions = mWallpaperInfo.getAttributions(appContext);
            if (!attributions.isEmpty()) {
                mWallpaperTitle.setText(attributions.get(0));
            }
            if (attributions.size() > 1) {
                mWallpaperSubtitle.setText(attributions.get(1));
            } else {
                mWallpaperSubtitle.setVisibility(View.INVISIBLE);
            }
            if (attributions.size() > 2) {
                mWallpaperSubtitle2.setText(attributions.get(2));
            } else {
                mWallpaperSubtitle2.setVisibility(View.INVISIBLE);
            }
        }

        private void bindWallpaperActionButtons(@PresentationMode int presentationMode) {
            final Context appContext = getActivity().getApplicationContext();

            final String actionUrl = mWallpaperInfo.getActionUrl(appContext);
            if (actionUrl != null && !actionUrl.isEmpty()) {

                Uri exploreUri = Uri.parse(actionUrl);

                ExploreIntentChecker intentChecker =
                        InjectorProvider.getInjector().getExploreIntentChecker(appContext);
                intentChecker.fetchValidActionViewIntent(exploreUri, (@Nullable Intent exploreIntent) -> {
                    if (getActivity() == null) {
                        return;
                    }

                    updateExploreSectionVisibility(presentationMode, exploreIntent);
                });
            } else {
                updateExploreSectionVisibility(presentationMode, null /* exploreIntent */);
            }
        }

        /**
         * Shows or hides appropriate elements in the "Explore section" (containing the Explore button
         * and the Next Wallpaper button) depending on the current wallpaper.
         *
         * @param presentationMode The presentation mode of the current wallpaper.
         * @param exploreIntent    An optional explore intent for the current wallpaper.
         */
        private void updateExploreSectionVisibility(
                @PresentationMode int presentationMode, @Nullable Intent exploreIntent) {

            final Context appContext = getActivity().getApplicationContext();
            final UserEventLogger eventLogger =
                    InjectorProvider.getInjector().getUserEventLogger(appContext);

            boolean showSkipWallpaperButton = Flags.skipDailyWallpaperButtonEnabled
                    && presentationMode == WallpaperPreferences.PRESENTATION_MODE_ROTATING;

            if (exploreIntent != null) {
                mWallpaperExploreButtonNoText.setImageDrawable(getContext().getDrawable(
                                mWallpaperInfo.getActionIconRes(appContext)));
                mWallpaperExploreButtonNoText.setContentDescription(
                                getString(mWallpaperInfo.getActionLabelRes(appContext)));
                mWallpaperExploreButtonNoText.setColorFilter(
                                getResources().getColor(R.color.currently_set_explore_button_color,
                                        getContext().getTheme()),
                                Mode.SRC_IN);
                mWallpaperExploreButtonNoText.setVisibility(View.VISIBLE);
                mWallpaperExploreButtonNoText.setOnClickListener((View view) -> {
                    eventLogger.logActionClicked(mWallpaperInfo.getCollectionId(appContext),
                           mWallpaperInfo.getActionLabelRes(appContext));
                    startActivity(exploreIntent);
                });
            }

            if (showSkipWallpaperButton) {
                mSkipWallpaperButton.setVisibility(View.VISIBLE);
                mSkipWallpaperButton.setOnClickListener((View view) -> refreshDailyWallpaper());
            }
        }
    }

    /**
     * ViewHolder subclass for a metadata "card" at the beginning of the RecyclerView that shows
     * both home screen and lock screen wallpapers.
     */
    private class TwoWallpapersMetadataHolder extends RecyclerView.ViewHolder
            implements MetadataHolder {
        private WallpaperInfo mHomeWallpaperInfo;
        private ImageView mHomeWallpaperImage;
        private TextView mHomeWallpaperPresentationMode;
        private TextView mHomeWallpaperTitle;
        private TextView mHomeWallpaperSubtitle1;
        private TextView mHomeWallpaperSubtitle2;

        private ImageButton mHomeWallpaperExploreButton;
        private ImageButton mSkipWallpaperButton;
        private ViewGroup mHomeWallpaperPresentationSection;

        private WallpaperInfo mLockWallpaperInfo;
        private ImageView mLockWallpaperImage;
        private TextView mLockWallpaperTitle;
        private TextView mLockWallpaperSubtitle1;
        private TextView mLockWallpaperSubtitle2;

        private ImageButton mLockWallpaperExploreButton;

        public TwoWallpapersMetadataHolder(View metadataView) {
            super(metadataView);

            // Set the min width of the metadata panel to be the screen width minus space for the
            // 2 gutters on the sides. This ensures the RecyclerView's GridLayoutManager gives it
            // a wide-enough initial width to fill up the width of the grid prior to the view being
            // fully populated.
            final Display display = getActivity().getWindowManager().getDefaultDisplay();
            Point screenSize = ScreenSizeCalculator.getInstance().getScreenSize(display);
            metadataView.setMinimumWidth(
                    screenSize.x - 2 * getResources().getDimensionPixelSize(R.dimen.grid_padding));

            int bothWallpaperImageWidth = getBothWallpaperImageWidth();

            FrameLayout homeWallpaperSection = metadataView.findViewById(
                    R.id.home_wallpaper_section);
            homeWallpaperSection.setMinimumWidth(bothWallpaperImageWidth);
            mHomeWallpaperImage = metadataView.findViewById(R.id.home_wallpaper_image);

            mHomeWallpaperPresentationMode =
                    metadataView.findViewById(R.id.home_wallpaper_presentation_mode);
            mHomeWallpaperTitle = metadataView.findViewById(R.id.home_wallpaper_title);
            mHomeWallpaperSubtitle1 = metadataView.findViewById(R.id.home_wallpaper_subtitle1);
            mHomeWallpaperSubtitle2 = metadataView.findViewById(R.id.home_wallpaper_subtitle2);
            mHomeWallpaperPresentationSection = metadataView.findViewById(
                    R.id.home_wallpaper_presentation_section);
            mHomeWallpaperExploreButton =
                    metadataView.findViewById(R.id.home_wallpaper_explore_button);
            mSkipWallpaperButton = metadataView.findViewById(R.id.skip_home_wallpaper);

            FrameLayout lockWallpaperSection = metadataView.findViewById(
                    R.id.lock_wallpaper_section);
            lockWallpaperSection.setMinimumWidth(bothWallpaperImageWidth);
            mLockWallpaperImage = metadataView.findViewById(R.id.lock_wallpaper_image);

            mLockWallpaperTitle = metadataView.findViewById(R.id.lock_wallpaper_title);
            mLockWallpaperSubtitle1 = metadataView.findViewById(R.id.lock_wallpaper_subtitle1);
            mLockWallpaperSubtitle2 = metadataView.findViewById(R.id.lock_wallpaper_subtitle2);
            mLockWallpaperExploreButton =
                    metadataView.findViewById(R.id.lock_wallpaper_explore_button);
        }

        @Override
        public void bindWallpapers(WallpaperInfo homeWallpaper, WallpaperInfo lockWallpaper,
                @PresentationMode int presentationMode) {
            bindHomeWallpaper(homeWallpaper, presentationMode);
            bindLockWallpaper(lockWallpaper);
        }

        private void bindHomeWallpaper(WallpaperInfo homeWallpaper,
                                       @PresentationMode int presentationMode) {
            final Context appContext = getActivity().getApplicationContext();
            final UserEventLogger eventLogger =
                    InjectorProvider.getInjector().getUserEventLogger(appContext);

            mHomeWallpaperInfo = homeWallpaper;

            homeWallpaper.getThumbAsset(appContext).loadDrawable(
                    getActivity(), mHomeWallpaperImage,
                    getResources().getColor(R.color.secondary_color, getContext().getTheme()));

            mHomeWallpaperPresentationMode.setText(
                    AttributionFormatter.getHumanReadableWallpaperPresentationMode(
                            appContext, presentationMode));

            List<String> attributions = homeWallpaper.getAttributions(appContext);
            if (!attributions.isEmpty()) {
                mHomeWallpaperTitle.setText(attributions.get(0));
            }
            if (attributions.size() > 1) {
                mHomeWallpaperSubtitle1.setText(attributions.get(1));
            }
            if (attributions.size() > 2) {
                mHomeWallpaperSubtitle2.setText(attributions.get(2));
            }

            final String homeActionUrl = homeWallpaper.getActionUrl(appContext);

            if (homeActionUrl != null && !homeActionUrl.isEmpty()) {
                Uri homeExploreUri = Uri.parse(homeActionUrl);

                ExploreIntentChecker intentChecker =
                        InjectorProvider.getInjector().getExploreIntentChecker(appContext);

                intentChecker.fetchValidActionViewIntent(
                    homeExploreUri, (@Nullable Intent exploreIntent) -> {
                        if (exploreIntent == null || getActivity() == null) {
                            return;
                        }

                        mHomeWallpaperExploreButton.setVisibility(View.VISIBLE);
                        mHomeWallpaperExploreButton.setImageDrawable(getContext().getDrawable(
                                homeWallpaper.getActionIconRes(appContext)));
                        mHomeWallpaperExploreButton.setContentDescription(getString(homeWallpaper
                                .getActionLabelRes(appContext)));
                        mHomeWallpaperExploreButton.setColorFilter(
                                getResources().getColor(R.color.currently_set_explore_button_color,
                                        getContext().getTheme()),
                                Mode.SRC_IN);
                        mHomeWallpaperExploreButton.setOnClickListener(v -> {
                            eventLogger.logActionClicked(
                                    mHomeWallpaperInfo.getCollectionId(appContext),
                                    mHomeWallpaperInfo.getActionLabelRes(appContext));
                            startActivity(exploreIntent);
                        });
                    });
            } else {
                mHomeWallpaperExploreButton.setVisibility(View.GONE);
            }

            if (presentationMode == WallpaperPreferences.PRESENTATION_MODE_ROTATING) {
                mHomeWallpaperPresentationSection.setVisibility(View.VISIBLE);
                if (Flags.skipDailyWallpaperButtonEnabled) {
                    mSkipWallpaperButton.setVisibility(View.VISIBLE);
                    mSkipWallpaperButton.setColorFilter(
                            getResources().getColor(R.color.currently_set_explore_button_color,
                                    getContext().getTheme()), Mode.SRC_IN);
                    mSkipWallpaperButton.setOnClickListener(view -> refreshDailyWallpaper());
                } else {
                    mSkipWallpaperButton.setVisibility(View.GONE);
                }
            } else {
                mHomeWallpaperPresentationSection.setVisibility(View.GONE);
            }

            mHomeWallpaperImage.setOnClickListener(v -> {
                eventLogger.logCurrentWallpaperPreviewed();
                getFragmentHost().showViewOnlyPreview(mHomeWallpaperInfo);
            });
        }

        private void bindLockWallpaper(WallpaperInfo lockWallpaper) {
            if (lockWallpaper == null) {
                Log.e(TAG, "TwoWallpapersMetadataHolder bound without a lock screen wallpaper.");
                return;
            }

            final Context appContext = getActivity().getApplicationContext();
            final UserEventLogger eventLogger =
                    InjectorProvider.getInjector().getUserEventLogger(getActivity());

            mLockWallpaperInfo = lockWallpaper;

            lockWallpaper.getThumbAsset(appContext).loadDrawable(
                    getActivity(), mLockWallpaperImage, getResources().getColor(R.color.secondary_color));

            List<String> lockAttributions = lockWallpaper.getAttributions(appContext);
            if (!lockAttributions.isEmpty()) {
                mLockWallpaperTitle.setText(lockAttributions.get(0));
            }
            if (lockAttributions.size() > 1) {
                mLockWallpaperSubtitle1.setText(lockAttributions.get(1));
            }
            if (lockAttributions.size() > 2) {
                mLockWallpaperSubtitle2.setText(lockAttributions.get(2));
            }

            final String lockActionUrl = lockWallpaper.getActionUrl(appContext);

            if (lockActionUrl != null && !lockActionUrl.isEmpty()) {
                Uri lockExploreUri = Uri.parse(lockActionUrl);

                ExploreIntentChecker intentChecker =
                        InjectorProvider.getInjector().getExploreIntentChecker(appContext);
                intentChecker.fetchValidActionViewIntent(
                        lockExploreUri, (@Nullable Intent exploreIntent) -> {
                            if (exploreIntent == null || getActivity() == null) {
                                return;
                            }
                            mLockWallpaperExploreButton.setImageDrawable(getContext().getDrawable(
                                    lockWallpaper.getActionIconRes(appContext)));
                            mLockWallpaperExploreButton.setContentDescription(getString(
                                    lockWallpaper.getActionLabelRes(appContext)));
                            mLockWallpaperExploreButton.setVisibility(View.VISIBLE);
                            mLockWallpaperExploreButton.setColorFilter(
                                    getResources().getColor(
                                            R.color.currently_set_explore_button_color),
                                    Mode.SRC_IN);
                            mLockWallpaperExploreButton.setOnClickListener(new OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    eventLogger.logActionClicked(
                                            mLockWallpaperInfo.getCollectionId(appContext),
                                            mLockWallpaperInfo.getActionLabelRes(appContext));
                                    startActivity(exploreIntent);
                                }
                            });
                        });
            } else {
                mLockWallpaperExploreButton.setVisibility(View.GONE);
            }

            mLockWallpaperImage.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    eventLogger.logCurrentWallpaperPreviewed();
                    getFragmentHost().showViewOnlyPreview(mLockWallpaperInfo);
                }
            });
        }
    }

    /**
     * ViewHolder subclass for a category tile in the RecyclerView.
     */
    private class CategoryHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        private Category mCategory;
        private RelativeLayout mTileLayout;
        private ImageView mImageView;
        private ImageView mOverlayIconView;
        private TextView mTitleView;

        public CategoryHolder(View itemView) {
            super(itemView);
            itemView.setOnClickListener(this);

            mTileLayout = itemView.findViewById(R.id.tile);
            mImageView = itemView.findViewById(R.id.image);
            mOverlayIconView = itemView.findViewById(R.id.overlay_icon);
            mTitleView = itemView.findViewById(R.id.category_title);

            mTileLayout.getLayoutParams().height = mTileSizePx.y;
        }

        @Override
        public void onClick(View view) {
            final UserEventLogger eventLogger =
                    InjectorProvider.getInjector().getUserEventLogger(getActivity());
            eventLogger.logCategorySelected(mCategory.getCollectionId());

            if (mCategory.supportsCustomPhotos()) {
                getFragmentHost().getMyPhotosStarter().requestCustomPhotoPicker(
                        new PermissionChangedListener() {
                            @Override
                            public void onPermissionsGranted() {
                                drawThumbnailAndOverlayIcon();
                            }

                            @Override
                            public void onPermissionsDenied(boolean dontAskAgain) {
                                // No-op
                            }
                        });
                return;
            }

            getFragmentHost().show(mCategory.getCollectionId());
        }

        /**
         * Binds the given category to this CategoryHolder.
         */
        public void bindCategory(Category category) {
            mCategory = category;
            mTitleView.setText(category.getTitle());
            drawThumbnailAndOverlayIcon();
        }

        /**
         * Draws the CategoryHolder's thumbnail and overlay icon.
         */
        public void drawThumbnailAndOverlayIcon() {
            mOverlayIconView.setImageDrawable(mCategory.getOverlayIcon(
                    getActivity().getApplicationContext()));

            // Size the overlay icon according to the category.
            int overlayIconDimenDp = mCategory.getOverlayIconSizeDp();
            DisplayMetrics metrics = DisplayMetricsRetriever.getInstance().getDisplayMetrics(
                    getResources(), getActivity().getWindowManager().getDefaultDisplay());
            int overlayIconDimenPx = (int) (overlayIconDimenDp * metrics.density);
            mOverlayIconView.getLayoutParams().width = overlayIconDimenPx;
            mOverlayIconView.getLayoutParams().height = overlayIconDimenPx;

            Asset thumbnail = mCategory.getThumbnail(getActivity().getApplicationContext());
            if (thumbnail != null) {
                thumbnail.loadDrawable(getActivity(), mImageView,
                        getResources().getColor(R.color.secondary_color));
            } else {
                // TODO(orenb): Replace this workaround for b/62584914 with a proper way of unloading the
                // ImageView such that no incorrect image is improperly loaded upon rapid scroll.
                Object nullObj = null;
                Glide.with(getActivity())
                        .asDrawable()
                        .load(nullObj)
                        .into(mImageView);

            }
        }
    }

    /**
     * ViewHolder subclass for the loading indicator ("spinner") shown when categories are being
     * fetched.
     */
    private class LoadingIndicatorHolder extends RecyclerView.ViewHolder {
        public LoadingIndicatorHolder(View view) {
            super(view);
            ProgressBar progressBar = view.findViewById(R.id.loading_indicator);
            progressBar.getIndeterminateDrawable().setColorFilter(
                    getResources().getColor(R.color.accent_color), Mode.SRC_IN);
        }
    }

    /**
     * ViewHolder subclass for a "card" at the beginning of the RecyclerView showing the app needs the
     * user to grant the storage permission to show the currently set wallpaper.
     */
    private class PermissionNeededHolder extends RecyclerView.ViewHolder {
        private Button mAllowAccessButton;

        public PermissionNeededHolder(View view) {
            super(view);

            mAllowAccessButton = view.findViewById(R.id.permission_needed_allow_access_button);
            mAllowAccessButton.setOnClickListener((View v) -> {
                getFragmentHost().requestExternalStoragePermission(mAdapter);
            });

            // Replace explanation text with text containing the Wallpapers app name which replaces the
            // placeholder.
            String appName = getString(R.string.app_name);
            String explanation = getString(R.string.permission_needed_explanation, appName);
            TextView explanationTextView = view.findViewById(R.id.permission_needed_explanation);
            explanationTextView.setText(explanation);
        }
    }

    /**
     * RecyclerView Adapter subclass for the category tiles in the RecyclerView.
     */
    private class CategoryAdapter extends RecyclerView.Adapter<ViewHolder>
            implements PermissionChangedListener {
        public static final int METADATA_VIEW_SINGLE_CARD = 1;
        public static final int METADATA_VIEW_TWO_CARDS = 2;
        private static final int ITEM_VIEW_TYPE_METADATA = 1;
        private static final int ITEM_VIEW_TYPE_SELECT_WALLPAPER_HEADER = 2;
        private static final int ITEM_VIEW_TYPE_CATEGORY = 3;
        private static final int ITEM_VIEW_TYPE_LOADING_INDICATOR = 4;
        private static final int ITEM_VIEW_TYPE_PERMISSION_NEEDED = 5;
        private List<Category> mCategories;
        private int mNumMetadataCards;

        public CategoryAdapter(List<Category> categories) {
            mCategories = categories;
            mNumMetadataCards = METADATA_VIEW_SINGLE_CARD;
        }

        /**
         * Sets the number of metadata cards to be shown in the metadata view holder. Updates the UI
         * to reflect any changes in that number (e.g., a lock screen wallpaper has been set so we now
         * need to show two cards).
         */
        public void setNumMetadataCards(int numMetadataCards) {
            if (numMetadataCards != mNumMetadataCards && getItemCount() > 0) {
                notifyItemChanged(0);
            }

            mNumMetadataCards = numMetadataCards;
        }

        @Override
        public int getItemViewType(int position) {
            if (position == 0) {
                if (canShowCurrentWallpaper()) {
                    return ITEM_VIEW_TYPE_METADATA;
                } else {
                    return ITEM_VIEW_TYPE_PERMISSION_NEEDED;
                }
            } else if (position == 1) {
                return ITEM_VIEW_TYPE_SELECT_WALLPAPER_HEADER;
            } else if (mAwaitingCategories && position == getItemCount() - 1) {
                return ITEM_VIEW_TYPE_LOADING_INDICATOR;
            }

            return ITEM_VIEW_TYPE_CATEGORY;
        }

        @Override
        public ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            LayoutInflater layoutInflater = LayoutInflater.from(getActivity());
            View view;

            switch (viewType) {
                case ITEM_VIEW_TYPE_METADATA:
                    if (mNumMetadataCards == METADATA_VIEW_SINGLE_CARD) {
                        view = layoutInflater.inflate(
                                R.layout.grid_item_single_metadata, parent, /* attachToRoot */ false);
                        return new SingleWallpaperMetadataHolder(view);
                    } else { // TWO_CARDS
                        view = layoutInflater.inflate(
                                R.layout.grid_item_both_metadata, parent, /* attachToRoot */ false);
                        return new TwoWallpapersMetadataHolder(view);
                    }
                case ITEM_VIEW_TYPE_SELECT_WALLPAPER_HEADER:
                    view = layoutInflater.inflate(
                            R.layout.grid_item_select_wallpaper_header, parent, /* attachToRoot */ false);
                    return new SelectWallpaperHeaderHolder(view);
                case ITEM_VIEW_TYPE_LOADING_INDICATOR:
                    view = layoutInflater.inflate(
                            R.layout.grid_item_loading_indicator, parent, /* attachToRoot */ false);
                    return new LoadingIndicatorHolder(view);
                case ITEM_VIEW_TYPE_CATEGORY:
                    view = layoutInflater.inflate(
                            R.layout.grid_item_category, parent, /* attachToRoot */ false);
                    return new CategoryHolder(view);
                case ITEM_VIEW_TYPE_PERMISSION_NEEDED:
                    view = layoutInflater.inflate(
                            R.layout.grid_item_permission_needed, parent, /* attachToRoot */ false);
                    return new PermissionNeededHolder(view);
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
                    return null;
            }
        }

        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            int viewType = getItemViewType(position);

            switch (viewType) {
                case ITEM_VIEW_TYPE_METADATA:
                    refreshCurrentWallpapers((MetadataHolder) holder, false /* forceRefresh */);
                    break;
                case ITEM_VIEW_TYPE_SELECT_WALLPAPER_HEADER:
                    // No op.
                    break;
                case ITEM_VIEW_TYPE_CATEGORY:
                    // Offset position to get category index to account for the non-category view holders.
                    Category category = mCategories.get(position - NUM_NON_CATEGORY_VIEW_HOLDERS);
                    ((CategoryHolder) holder).bindCategory(category);
                    break;
                case ITEM_VIEW_TYPE_LOADING_INDICATOR:
                case ITEM_VIEW_TYPE_PERMISSION_NEEDED:
                    // No op.
                    break;
                default:
                    Log.e(TAG, "Unsupported viewType " + viewType + " in CategoryAdapter");
            }
        }

        @Override
        public int getItemCount() {
            // Add to size of categories to account for the metadata related views.
            // Add 1 more for the loading indicator if not yet done loading.
            int size = mCategories.size() + NUM_NON_CATEGORY_VIEW_HOLDERS;
            if (mAwaitingCategories) {
                size += 1;
            }

            return size;
        }

        @Override
        public void onPermissionsGranted() {
            notifyDataSetChanged();
        }

        @Override
        public void onPermissionsDenied(boolean dontAskAgain) {
            if (!dontAskAgain) {
                return;
            }

            String permissionNeededMessage =
                    getString(R.string.permission_needed_explanation_go_to_settings);
            AlertDialog dialog = new AlertDialog.Builder(getActivity(), R.style.LightDialogTheme)
                    .setMessage(permissionNeededMessage)
                    .setPositiveButton(android.R.string.ok, null /* onClickListener */)
                    .setNegativeButton(
                            R.string.settings_button_label,
                            new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialogInterface, int i) {
                                    Intent appInfoIntent = new Intent();
                                    appInfoIntent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                                    Uri uri = Uri.fromParts(
                                            "package", getActivity().getPackageName(), null /* fragment */);
                                    appInfoIntent.setData(uri);
                                    startActivityForResult(appInfoIntent, SETTINGS_APP_INFO_REQUEST_CODE);
                                }
                            })
                    .create();
            dialog.show();
        }
    }
}
