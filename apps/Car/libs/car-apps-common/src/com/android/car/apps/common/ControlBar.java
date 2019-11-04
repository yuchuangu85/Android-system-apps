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

package com.android.car.apps.common;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.drawable.Drawable;
import android.transition.ChangeBounds;
import android.transition.Fade;
import android.transition.TransitionManager;
import android.transition.TransitionSet;
import android.util.AttributeSet;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.Space;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import androidx.interpolator.view.animation.FastOutSlowInInterpolator;

import com.android.internal.util.Preconditions;

import java.util.Locale;


/**
 * An actions panel with three distinctive zones:
 * <ul>
 * <li>Main control: located in the bottom center it shows a highlighted icon and a circular
 * progress bar.
 * <li>Secondary controls: these are displayed at the left and at the right of the main control.
 * <li>Overflow controls: these are displayed at the left and at the right of the secondary controls
 * (if the space allows) and on the additional space if the panel is expanded.
 * </ul>
 */
public class ControlBar extends RelativeLayout implements ExpandableControlBar {
    private static final String TAG = "ControlBar";

    // Rows container
    private ViewGroup mRowsContainer;
    // All slots in this action bar where 0 is the bottom-start corner of the matrix, and
    // mNumColumns * nNumRows - 1 is the top-end corner
    private FrameLayout[] mSlots;
    /**
     * Reference to the first slot we create. Used to properly inflate buttons without loosing
     * their layout params.
     */
    private FrameLayout mFirstCreatedSlot;
    /** Views to set in particular {@link SlotPosition}s */
    private final SparseArray<View> mFixedViews = new SparseArray<>();
    // View to be used for the expand/collapse action
    private @Nullable View mExpandCollapseView;
    // Default expand/collapse view to use one is not provided.
    private View mDefaultExpandCollapseView;
    // Number of rows in actual use. This is the number of extra rows that will be displayed when
    // the action bar is expanded
    private int mNumExtraRowsInUse;
    // Whether the action bar is expanded or not.
    private boolean mIsExpanded;
    // Views to accomodate in the slots.
    private @Nullable View[] mViews;
    // Number of columns of slots to use.
    private int mNumColumns;
    // Maximum number of rows to use (at least one!).
    private int mNumRows;
    // Whether the expand button should be visible or not
    private boolean mExpandEnabled;
    // Callback for the expand/collapse button
    private ExpandCollapseCallback mExpandCollapseCallback;

    // Default number of columns, if unspecified
    private static final int DEFAULT_COLUMNS = 3;
    // Weight for the spacers used between buttons
    private static final float SPACERS_WEIGHT = 1f;

    public ControlBar(Context context) {
        super(context);
        init(context, null, 0, 0);
    }

    public ControlBar(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context, attrs, 0, 0);
    }

    public ControlBar(Context context, AttributeSet attrs, int defStyleAttrs) {
        super(context, attrs, defStyleAttrs);
        init(context, attrs, defStyleAttrs, 0);
    }

    public ControlBar(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        super(context, attrs, defStyleAttrs, defStyleRes);
        init(context, attrs, defStyleAttrs, defStyleRes);
    }

    private void init(Context context, AttributeSet attrs, int defStyleAttrs, int defStyleRes) {
        inflate(context, R.layout.control_bar, this);

        TypedArray ta = context.obtainStyledAttributes(attrs, R.styleable.ControlBar,
                defStyleAttrs, defStyleRes);
        mNumColumns = ta.getInteger(R.styleable.ControlBar_columns, DEFAULT_COLUMNS);
        mExpandEnabled = ta.getBoolean(R.styleable.ControlBar_enableOverflow, true);
        ta.recycle();

        mRowsContainer = findViewById(R.id.rows_container);
        mNumRows = mRowsContainer.getChildCount();
        Preconditions.checkState(mNumRows > 0, "Must have at least 1 row");

        mSlots = new FrameLayout[mNumColumns * mNumRows];

        LayoutInflater inflater = LayoutInflater.from(context);
        final boolean attachToRoot = false;

        for (int i = 0; i < mNumRows; i++) {
            // Slots are reserved in reverse order (first slots are in the bottom row)
            ViewGroup row = (ViewGroup) mRowsContainer.getChildAt(mNumRows - i - 1);
            // Inflate necessary number of columns
            for (int j = 0; j < mNumColumns; j++) {
                int pos = i * mNumColumns + j;
                mSlots[pos] = (FrameLayout) inflater.inflate(R.layout.control_bar_slot, row,
                        attachToRoot);
                if (mFirstCreatedSlot == null) {
                    mFirstCreatedSlot = mSlots[pos];
                }
                if (j > 0) {
                    Space space = new Space(context);
                    row.addView(space);
                    space.setLayoutParams(new LinearLayout.LayoutParams(0,
                            ViewGroup.LayoutParams.MATCH_PARENT, SPACERS_WEIGHT));
                }
                row.addView(mSlots[pos]);
            }
        }

        mDefaultExpandCollapseView = createIconButton(
                context.getDrawable(R.drawable.ic_overflow_button));
        mDefaultExpandCollapseView.setContentDescription(context.getString(
                R.string.control_bar_expand_collapse_button));
        mDefaultExpandCollapseView.setOnClickListener(v -> onExpandCollapse());
    }

    private int getSlotIndex(@SlotPosition int slotPosition) {
        return CarControlBar.getSlotIndex(slotPosition, mNumColumns);
    }

    @Override
    public void setView(@Nullable View view, @SlotPosition int slotPosition) {
        if (view != null) {
            mFixedViews.put(slotPosition, view);
        } else {
            mFixedViews.remove(slotPosition);
        }
        updateViewsLayout();
    }

    /**
     * Sets the view to use for the expand/collapse action. If not provided, a default
     * {@link ImageButton} will be used. The provided {@link View} should be able be able to display
     * changes in the "activated" state appropriately.
     *
     * @param view {@link View} to use for the expand/collapse action.
     */
    public void setExpandCollapseView(@NonNull View view) {
        mExpandCollapseView = view;
        mExpandCollapseView.setOnClickListener(v -> onExpandCollapse());
        updateViewsLayout();
    }

    private View getExpandCollapseView() {
        return mExpandCollapseView != null ? mExpandCollapseView : mDefaultExpandCollapseView;
    }

    @Override
    public ImageButton createIconButton(Drawable icon) {
        return createIconButton(icon, R.layout.control_bar_button);
    }

    @Override
    public ImageButton createIconButton(Drawable icon, int viewId) {
        LayoutInflater inflater = LayoutInflater.from(mFirstCreatedSlot.getContext());
        final boolean attachToRoot = false;
        ImageButton button = (ImageButton) inflater.inflate(viewId, mFirstCreatedSlot,
                attachToRoot);
        button.setImageDrawable(icon);
        return button;
    }

    @Override
    public void registerExpandCollapseCallback(@Nullable ExpandCollapseCallback callback) {
        mExpandCollapseCallback = callback;
    }

    @Override
    public void close() {
        if (mIsExpanded) {
            onExpandCollapse();
        }
    }

    @Override
    public void setViews(@Nullable View[] views) {
        mViews = views;
        updateViewsLayout();
    }

    private void updateViewsLayout() {
        // Prepare an array of positions taken
        int totalSlots = mSlots.length;
        View[] slotViews = new View[totalSlots];

        // Take all known positions
        for (int i = 0; i < mFixedViews.size(); i++) {
            int index = getSlotIndex(mFixedViews.keyAt(i));
            if (index >= 0 && index < slotViews.length) {
                slotViews[index] = mFixedViews.valueAt(i);
            }
        }

        // Set all views using both the fixed and flexible positions
        int expandCollapseIndex = getSlotIndex(SLOT_EXPAND_COLLAPSE);
        int lastUsedIndex = 0;
        int viewsIndex = 0;
        for (int i = 0; i < totalSlots; i++) {
            View viewToUse = null;

            if (slotViews[i] != null) {
                // If there is a view assigned for this slot, use it.
                viewToUse = slotViews[i];
            } else if (mExpandEnabled && i == expandCollapseIndex && mViews != null
                    && viewsIndex < mViews.length - 1) {
                // If this is the expand/collapse slot, use the corresponding view
                viewToUse = getExpandCollapseView();
                Log.d(TAG, "" + this + "Setting expand control");
            } else if (mViews != null && viewsIndex < mViews.length) {
                // Otherwise, if the slot is not reserved, and if we still have views to assign,
                // take one and assign it to this slot.
                viewToUse = mViews[viewsIndex];
                viewsIndex++;
            }
            setView(viewToUse, mSlots[i]);
            if (viewToUse != null) {
                lastUsedIndex = i;
            }
        }

        mNumExtraRowsInUse = lastUsedIndex / mNumColumns;
        final int lastIndex = lastUsedIndex;

        if (mNumRows > 1) {
            // Align expanded control bar rows
            mRowsContainer.getViewTreeObserver().addOnGlobalLayoutListener(() -> {
                for (int i  = 1; i < mNumRows; i++) {
                    // mRowsContainer's children are in reverse order (last row is at index 0)
                    int rowIndex = mNumRows - 1 - i;
                    if (lastIndex < (i + 1) * mNumColumns) {
                        // Align the last row's center with the first row by translating the last
                        // row by half the difference between the two rows' length.
                        // We use the position of the last slot as a proxy for the length, since the
                        // slots have the same size, and both rows have the same start point.
                        float lastRowX = mSlots[lastIndex].getX();
                        float firstRowX = mSlots[mNumColumns - 1].getX();
                        mRowsContainer.getChildAt(rowIndex).setTranslationX(
                                (firstRowX - lastRowX) / 2);
                    } else {
                        mRowsContainer.getChildAt(rowIndex).setTranslationX(0);
                    }
                }
            });
        }
    }

    private void setView(@Nullable View view, FrameLayout container) {
        container.removeAllViews();
        if (view != null) {
            ViewGroup parent = (ViewGroup) view.getParent();
            // As we are removing views (on BT disconnect, for example), some items will be
            // shifting from expanded to collapsed (like Queue item) - remove those from the
            // group before adding to the new slot
            if (view.getParent() != null) {
                parent.removeView(view);
            }
            container.addView(view);
            container.setVisibility(VISIBLE);
        } else {
            container.setVisibility(INVISIBLE);
        }
    }

    private void onExpandCollapse() {
        mIsExpanded = !mIsExpanded;
        if (mExpandCollapseView != null) {
            mExpandCollapseView.setSelected(mIsExpanded);
        }
        if (mExpandCollapseCallback != null) {
            mExpandCollapseCallback.onExpandCollapse(mIsExpanded);
        }
        mSlots[getSlotIndex(SLOT_EXPAND_COLLAPSE)].setActivated(mIsExpanded);

        int animationDuration = getContext().getResources().getInteger(mIsExpanded
                ? R.integer.control_bar_expand_anim_duration
                : R.integer.control_bar_collapse_anim_duration);
        TransitionSet set = new TransitionSet()
                .addTransition(new ChangeBounds())
                .addTransition(new Fade())
                .setDuration(animationDuration)
                .setInterpolator(new FastOutSlowInInterpolator());
        TransitionManager.beginDelayedTransition(this, set);
        for (int i = 0; i < mNumExtraRowsInUse; i++) {
            mRowsContainer.getChildAt(i).setVisibility(mIsExpanded ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Returns the view assigned to the given row and column, after layout.
     *
     * @param rowIdx row index from 0 being the top row, and {@link #mNumRows{ -1 being the bottom
     *               row.
     * @param colIdx column index from 0 on start (left), to {@link #mNumColumns} on end (right)
     */
    @VisibleForTesting
    @Nullable
    View getViewAt(int rowIdx, int colIdx) {
        if (rowIdx < 0 || rowIdx > mRowsContainer.getChildCount()) {
            throw new IllegalArgumentException(String.format((Locale) null,
                    "Row index out of range (requested: %d, max: %d)",
                    rowIdx, mRowsContainer.getChildCount()));
        }
        if (colIdx < 0 || colIdx > mNumColumns) {
            throw new IllegalArgumentException(String.format((Locale) null,
                    "Column index out of range (requested: %d, max: %d)",
                    colIdx, mNumColumns));
        }
        FrameLayout slot = (FrameLayout) ((LinearLayout) mRowsContainer.getChildAt(rowIdx))
                .getChildAt(colIdx + 1);
        return slot.getChildCount() > 0 ? slot.getChildAt(0) : null;
    }
}
