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
package com.android.customization.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.text.TextUtils;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.customization.model.CustomizationManager;
import com.android.customization.model.CustomizationOption;
import com.android.wallpaper.R;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Simple controller for a RecyclerView-based widget to hold the options for each customization
 * section (eg, thumbnails for themes, clocks, grid sizes).
 * To use, just pass the RV that will contain the tiles and the list of {@link CustomizationOption}
 * representing each option, and call {@link #initOptions(CustomizationManager)} to populate the
 * widget.
 */
public class OptionSelectorController<T extends CustomizationOption<T>> {

    /**
     * Interface to be notified when an option is selected by the user.
     */
    public interface OptionSelectedListener {

        /**
         * Called when an option has been selected (and marked as such in the UI)
         */
        void onOptionSelected(CustomizationOption selected);
    }

    private final RecyclerView mContainer;
    private final List<T> mOptions;
    private final boolean mUseGrid;
    private final boolean mShowCheckmark;

    private final Set<OptionSelectedListener> mListeners = new HashSet<>();
    private RecyclerView.Adapter<TileViewHolder> mAdapter;
    private CustomizationOption mSelectedOption;
    private CustomizationOption mAppliedOption;

    public OptionSelectorController(RecyclerView container, List<T> options) {
        this(container, options, false, true);
    }

    public OptionSelectorController(RecyclerView container, List<T> options,
            boolean useGrid, boolean showCheckmark) {
        mContainer = container;
        mOptions = options;
        mUseGrid = container.getResources().getBoolean(R.bool.use_grid_for_options) || useGrid;
        mShowCheckmark = showCheckmark;
    }

    public void addListener(OptionSelectedListener listener) {
        mListeners.add(listener);
    }

    public void removeListener(OptionSelectedListener listener) {
        mListeners.remove(listener);
    }

    public void setSelectedOption(CustomizationOption option) {
        if (!mOptions.contains(option)) {
            throw new IllegalArgumentException("Invalid option");
        }
        updateActivatedStatus(mSelectedOption, false);
        updateActivatedStatus(option, true);
        mSelectedOption = option;
        notifyListeners();
    }

    /**
     * Mark an option as the one which is currently applied on the device. This will result in a
     * check being displayed in the lower-right corner of the corresponding ViewHolder.
     * @param option
     */
    public void setAppliedOption(CustomizationOption option) {
        if (!mOptions.contains(option)) {
            throw new IllegalArgumentException("Invalid option");
        }
        CustomizationOption lastAppliedOption = mAppliedOption;
        mAppliedOption = option;
        mAdapter.notifyItemChanged(mOptions.indexOf(option));
        if (lastAppliedOption != null) {
            mAdapter.notifyItemChanged(mOptions.indexOf(lastAppliedOption));
        }
    }

    private void updateActivatedStatus(CustomizationOption option, boolean isActivated) {
        int index = mOptions.indexOf(option);
        if (index < 0) {
            return;
        }
        RecyclerView.ViewHolder holder = mContainer.findViewHolderForAdapterPosition(index);
        if (holder != null && holder.itemView != null) {
            holder.itemView.setActivated(isActivated);

            if (holder instanceof TileViewHolder) {
                TileViewHolder tileHolder = (TileViewHolder) holder;
                if (isActivated) {
                    if (option == mAppliedOption && mShowCheckmark) {
                        tileHolder.setContentDescription(mContainer.getContext(), option,
                            R.string.option_applied_previewed_description);
                    } else {
                        tileHolder.setContentDescription(mContainer.getContext(), option,
                            R.string.option_previewed_description);
                    }
                } else if (option == mAppliedOption && mShowCheckmark) {
                    tileHolder.setContentDescription(mContainer.getContext(), option,
                        R.string.option_applied_description);
                } else {
                    tileHolder.resetContentDescription();
                }
            }
        } else {
            // Item is not visible, make sure the item is re-bound when it becomes visible
            mAdapter.notifyItemChanged(index);
        }
    }

    /**
     * Initializes the UI for the options passed in the constructor of this class.
     */
    public void initOptions(final CustomizationManager<T> manager) {
        mAdapter = new RecyclerView.Adapter<TileViewHolder>() {
            @Override
            public int getItemViewType(int position) {
                return mOptions.get(position).getLayoutResId();
            }

            @NonNull
            @Override
            public TileViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
                View v = LayoutInflater.from(parent.getContext()).inflate(viewType, parent, false);
                return new TileViewHolder(v);
            }

            @Override
            public void onBindViewHolder(@NonNull TileViewHolder holder, int position) {
                CustomizationOption option = mOptions.get(position);
                if (option.isActive(manager)) {
                    mAppliedOption = option;
                    if (mSelectedOption == null) {
                        mSelectedOption = option;
                    }
                }
                if (holder.labelView != null) {
                    holder.labelView.setText(option.getTitle());
                }
                option.bindThumbnailTile(holder.tileView);
                holder.itemView.setActivated(option.equals(mSelectedOption));
                holder.itemView.setOnClickListener(view -> setSelectedOption(option));

                if (mShowCheckmark && option.equals(mAppliedOption)) {
                    Resources res = mContainer.getContext().getResources();
                    Drawable checkmark = res.getDrawable(R.drawable.ic_check_circle_filled_24px);
                    Drawable frame = holder.tileView.getForeground();
                    Drawable[] layers = {frame, checkmark};
                    if (frame == null) {
                        layers = new Drawable[]{checkmark};
                    }
                    LayerDrawable checkedFrame = new LayerDrawable(layers);

                    // Position at lower right
                    int idx = layers.length - 1;
                    int checkSize = (int) res.getDimension(R.dimen.check_size);
                    int checkOffset = (int) res.getDimensionPixelOffset(R.dimen.check_offset);
                    checkedFrame.setLayerGravity(idx, Gravity.BOTTOM | Gravity.RIGHT);
                    checkedFrame.setLayerWidth(idx, checkSize);
                    checkedFrame.setLayerHeight(idx, checkSize);
                    checkedFrame.setLayerInsetBottom(idx, checkOffset);
                    checkedFrame.setLayerInsetRight(idx, checkOffset);
                    holder.tileView.setForeground(checkedFrame);

                    // Initialize the currently applied option
                    holder.setContentDescription(mContainer.getContext(), option,
                        R.string.option_applied_previewed_description);
                } else if (option.equals(mAppliedOption)) {
                    // Initialize with "previewed" description if we don't show checkmark
                    holder.setContentDescription(mContainer.getContext(), option,
                        R.string.option_previewed_description);
                } else if (mShowCheckmark) {
                    holder.tileView.setForeground(null);
                }
            }

            @Override
            public int getItemCount() {
                return mOptions.size();
            }
        };

        mContainer.setLayoutManager(new LinearLayoutManager(mContainer.getContext(),
                LinearLayoutManager.HORIZONTAL, false));
        Resources res = mContainer.getContext().getResources();
        mContainer.setAdapter(mAdapter);

        // Measure RecyclerView to get to the total amount of space used by all options.
        mContainer.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int fixWidth = res.getDimensionPixelSize(R.dimen.options_container_width);
        int availableWidth;
        if (fixWidth == 0) {
            DisplayMetrics metrics = new DisplayMetrics();
            mContainer.getContext().getSystemService(WindowManager.class)
                    .getDefaultDisplay().getMetrics(metrics);
            availableWidth = metrics.widthPixels;
        } else {
            availableWidth = fixWidth;
        }
        int totalWidth = mContainer.getMeasuredWidth();

        if (mUseGrid) {
            int numColumns = res.getInteger(R.integer.options_grid_num_columns);
            int widthPerItem = totalWidth / mAdapter.getItemCount();
            int extraSpace = availableWidth - widthPerItem * numColumns;
            int containerSidePadding = extraSpace / (numColumns + 1);
            mContainer.setLayoutManager(new GridLayoutManager(mContainer.getContext(), numColumns));
            mContainer.setPaddingRelative(containerSidePadding, 0, containerSidePadding, 0);
            mContainer.setOverScrollMode(View.OVER_SCROLL_NEVER);
            return;
        }

        int extraSpace = availableWidth - totalWidth;
        if (extraSpace >= 0) {
            mContainer.setOverScrollMode(View.OVER_SCROLL_NEVER);
        }
        int itemSideMargin =  res.getDimensionPixelOffset(R.dimen.option_tile_margin_horizontal);
        int defaultTotalPadding = itemSideMargin * (mAdapter.getItemCount() * 2 + 2);
        if (extraSpace > defaultTotalPadding) {
            int spaceBetweenItems = extraSpace / (mAdapter.getItemCount() + 1);
            itemSideMargin = spaceBetweenItems / 2;
        }
        mContainer.addItemDecoration(new HorizontalSpacerItemDecoration(itemSideMargin));
    }

    public void resetOptions(List<T> options) {
        mOptions.clear();
        mOptions.addAll(options);
        mAdapter.notifyDataSetChanged();
    }

    private void notifyListeners() {
        if (mListeners.isEmpty()) {
            return;
        }
        CustomizationOption option = mSelectedOption;
        Set<OptionSelectedListener> iterableListeners = new HashSet<>(mListeners);
        for (OptionSelectedListener listener : iterableListeners) {
            listener.onOptionSelected(option);
        }
    }

    private static class TileViewHolder extends RecyclerView.ViewHolder {
        TextView labelView;
        View tileView;
        CharSequence title;

        TileViewHolder(@NonNull View itemView) {
            super(itemView);
            labelView = itemView.findViewById(R.id.option_label);
            tileView = itemView.findViewById(R.id.option_tile);
            title = null;
        }

        /**
         * Set the content description for this holder using the given string id.
         * If the option does not have a label, the description will be set on the tile view.
         * @param context The view's context
         * @param option The customization option
         * @param id Resource ID of the string to use for the content description
         */
        public void setContentDescription(Context context, CustomizationOption option, int id) {
            title = option.getTitle();
            if (TextUtils.isEmpty(title) && tileView != null) {
                title = tileView.getContentDescription();
            }

            CharSequence cd = context.getString(id, title);
            if (labelView != null && !TextUtils.isEmpty(labelView.getText())) {
                labelView.setContentDescription(cd);
            } else if (tileView != null) {
                tileView.setAccessibilityPaneTitle(cd);
                tileView.setContentDescription(cd);
            }
        }

        public void resetContentDescription() {
            if (labelView != null && !TextUtils.isEmpty(labelView.getText())) {
                labelView.setContentDescription(title);
            } else if (tileView != null) {
                tileView.setAccessibilityPaneTitle(title);
                tileView.setContentDescription(title);
            }
        }
    }
}
