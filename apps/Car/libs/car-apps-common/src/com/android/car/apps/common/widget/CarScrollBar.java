/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.apps.common.widget;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.PointF;
import android.os.Handler;
import android.util.Log;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.MeasureSpec;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.Interpolator;
import android.widget.FrameLayout;
import android.widget.ImageView;

import androidx.annotation.IntRange;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.apps.common.R;
import com.android.car.apps.common.util.ScrollBarUI;
import com.android.car.apps.common.widget.PagedRecyclerView.ScrollBarPosition;

/**
 * Inspired by {@link androidx.car.widget.PagedListView}. Most pagination and scrolling logic has
 * been ported from the PLV with minor updates.
 *
 * The default scroll bar widget for the {@link PagedRecyclerView}.
 */
class CarScrollBar extends ScrollBarUI {
    private float mButtonDisabledAlpha;
    private static final String TAG = "CarScrollBar";
    private PagedSnapHelper mSnapHelper;

    private ImageView mUpButton;
    private PaginateButtonClickListener mUpButtonClickListener;
    private View mScrollView;
    private View mScrollThumb;
    private ImageView mDownButton;
    private PaginateButtonClickListener mDownButtonClickListener;

    private int mSeparatingMargin;
    private int mScrollBarThumbWidth;

    private int mPaddingStart;
    private int mPaddingEnd;

    /** The amount of space that the scroll thumb is allowed to roam over. */
    private int mScrollThumbTrackHeight;

    private final Interpolator mPaginationInterpolator = new AccelerateDecelerateInterpolator();

    private int mRowsPerPage = -1;
    private final Handler mHandler = new Handler();

    private OrientationHelper mOrientationHelper;

    /**
     * When doing a snap, offset the snap by this number of position and then do a smooth scroll to
     * the final position.
     */
    private static final int SNAP_SCROLL_OFFSET_POSITION = 2;

    /**
     * The amount of time after settling to wait before autoscrolling to the next page when the user
     * holds down a pagination button.
     */
    private static final int PAGINATION_HOLD_DELAY_MS = 400;

    @Override
    public void initialize(Context context, RecyclerView recyclerView,
            int scrollBarContainerWidth, @ScrollBarPosition int scrollBarPosition,
            boolean scrollBarAboveRecyclerView) {

        mRecyclerView = recyclerView;

        LayoutInflater inflater = (LayoutInflater) context.getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);

        FrameLayout parent = (FrameLayout) getRecyclerView().getParent();

        mScrollView = inflater.inflate(R.layout.car_paged_scrollbar_buttons, parent, false);
        mScrollView.setLayoutParams(
                new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT));

        mButtonDisabledAlpha = context.getResources().getFloat(R.dimen.button_disabled_alpha);

        if (scrollBarAboveRecyclerView) {
            parent.addView(mScrollView);
        } else {
            parent.addView(mScrollView, /* index= */0);
        }

        setScrollBarContainerWidth(scrollBarContainerWidth);
        setScrollBarPosition(scrollBarPosition);

        getRecyclerView().addOnScrollListener(mRecyclerViewOnScrollListener);
        getRecyclerView().getRecycledViewPool().setMaxRecycledViews(0, 12);

        Resources res = context.getResources();
        mSeparatingMargin = res.getDimensionPixelSize(R.dimen.car_scroll_bar_separator_margin);
        mScrollBarThumbWidth = res.getDimensionPixelSize(R.dimen.car_scroll_bar_thumb_width);

        mUpButton = mScrollView.findViewById(R.id.page_up);
        mUpButtonClickListener = new PaginateButtonClickListener(PaginationListener.PAGE_UP);
        mUpButton.setOnClickListener(mUpButtonClickListener);

        mDownButton = mScrollView.findViewById(R.id.page_down);
        mDownButtonClickListener = new PaginateButtonClickListener(PaginationListener.PAGE_DOWN);
        mDownButton.setOnClickListener(mDownButtonClickListener);

        mScrollThumb = mScrollView.findViewById(R.id.scrollbar_thumb);

        mSnapHelper = new PagedSnapHelper(context);
        getRecyclerView().setOnFlingListener(null);
        mSnapHelper.attachToRecyclerView(getRecyclerView());

        mScrollView.addOnLayoutChangeListener((View v, int left, int top, int right, int bottom,
                    int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
            int width = right - left;

            OrientationHelper orientationHelper =
                    getOrientationHelper(getRecyclerView().getLayoutManager());

            // This value will keep track of the top of the current view being laid out.
            int layoutTop = orientationHelper.getStartAfterPadding() + mPaddingStart;

            // Lay out the up button at the top of the view.
            layoutViewCenteredFromTop(mUpButton, layoutTop, width);
            layoutTop = mUpButton.getBottom();

            // Lay out the scroll thumb
            layoutTop += mSeparatingMargin;
            layoutViewCenteredFromTop(mScrollThumb, layoutTop, width);

            // Lay out the bottom button at the bottom of the view.
            int downBottom = orientationHelper.getEndAfterPadding() - mPaddingEnd;
            layoutViewCenteredFromBottom(mDownButton, downBottom, width);

            mHandler.post(this::calculateScrollThumbTrackHeight);
            mHandler.post(() -> updatePaginationButtons(/* animate= */false));
        });
    }

    @Override
    public void requestLayout() {
        mScrollView.requestLayout();
    }

    @Override
    public void setPadding(int paddingStart, int paddingEnd) {
        mPaddingStart = paddingStart;
        mPaddingEnd = paddingEnd;
        requestLayout();
    }

    /**
     * Sets the listener that will be notified when the up and down buttons have been pressed.
     *
     * @param listener The listener to set.
     */
    void setPaginationListener(PaginationListener listener) {
        mUpButtonClickListener.setPaginationListener(listener);
        mDownButtonClickListener.setPaginationListener(listener);
    }

    /** Returns {@code true} if the "up" button is pressed */
    private boolean isUpPressed() {
        return mUpButton.isPressed();
    }

    /** Returns {@code true} if the "down" button is pressed */
    private boolean isDownPressed() {
        return mDownButton.isPressed();
    }

    /**
     * Sets the width of the container that holds the scrollbar. The scrollbar will be centered
     * within this width.
     *
     * @param width The width of the scrollbar container.
     */
    void setScrollBarContainerWidth(int width) {
        ViewGroup.LayoutParams layoutParams = mScrollView.getLayoutParams();
        layoutParams.width = width;
        mScrollView.requestLayout();
    }

    /**
     * Sets the position of the scrollbar.
     *
     * @param position Enum value of the scrollbar position. 0 for Start and 1 for end.
     */
    void setScrollBarPosition(@ScrollBarPosition int position) {
        FrameLayout.LayoutParams layoutParams =
                (FrameLayout.LayoutParams) mScrollView.getLayoutParams();
        if (position == ScrollBarPosition.START) {
            layoutParams.gravity = Gravity.LEFT;
        } else {
            layoutParams.gravity = Gravity.RIGHT;
        }

        mScrollView.requestLayout();
    }

    /**
     * Sets whether or not the up button on the scroll bar is clickable.
     *
     * @param enabled {@code true} if the up button is enabled.
     */
    private void setUpEnabled(boolean enabled) {
        mUpButton.setEnabled(enabled);
        mUpButton.setAlpha(enabled ? 1f : mButtonDisabledAlpha);
    }

    /**
     * Sets whether or not the down button on the scroll bar is clickable.
     *
     * @param enabled {@code true} if the down button is enabled.
     */
    private void setDownEnabled(boolean enabled) {
        mDownButton.setEnabled(enabled);
        mDownButton.setAlpha(enabled ? 1f : mButtonDisabledAlpha);
    }

    /**
     * Returns whether or not the down button on the scroll bar is clickable.
     *
     * @return {@code true} if the down button is enabled. {@code false} otherwise.
     */
    private boolean isDownEnabled() {
        return mDownButton.isEnabled();
    }

    /** Listener for when the list should paginate. */
    interface PaginationListener {
        int PAGE_UP = 0;
        int PAGE_DOWN = 1;

        /** Called when the linked view should be paged in the given direction */
        void onPaginate(int direction);
    }

    /**
     * Calculate the amount of space that the scroll bar thumb is allowed to roam. The thumb
     * is allowed to take up the space between the down bottom and the up or alpha jump
     * button, depending on if the latter is visible.
     */
    private void calculateScrollThumbTrackHeight() {
        // Subtracting (2 * mSeparatingMargin) for the top/bottom margin above and below the
        // scroll bar thumb.
        mScrollThumbTrackHeight = mDownButton.getTop() - (2 * mSeparatingMargin);

        // If there's an alpha jump button, then the thumb is laid out starting from below that.
        mScrollThumbTrackHeight -= mUpButton.getBottom();
    }

    private void measureScrollThumb() {
        int scrollWidth = MeasureSpec.makeMeasureSpec(mScrollBarThumbWidth, MeasureSpec.EXACTLY);
        int scrollHeight = MeasureSpec.makeMeasureSpec(
                mScrollThumb.getLayoutParams().height,
                MeasureSpec.EXACTLY);
        mScrollThumb.measure(scrollWidth, scrollHeight);
    }

    /**
     * An optimization method to only remeasure and lay out the scroll thumb. This method should be
     * used when the height of the thumb has changed, but no other views need to be remeasured.
     */
    private void measureAndLayoutScrollThumb() {
        measureScrollThumb();

        // The top value should not change from what it was before; only the height is assumed to
        // be changing.
        int layoutTop = mScrollThumb.getTop();
        layoutViewCenteredFromTop(mScrollThumb, layoutTop, mScrollView.getMeasuredWidth());
    }

    /**
     * Lays out the given View starting from the given {@code top} value downwards and centered
     * within the given {@code availableWidth}.
     *
     * @param  view The view to lay out.
     * @param  top The top value to start laying out from. This value will be the resulting top
     *             value of the view.
     * @param  availableWidth The width in which to center the given view.
     */
    private void layoutViewCenteredFromTop(View view, int top, int availableWidth) {
        int viewWidth = view.getMeasuredWidth();
        int viewLeft = (availableWidth - viewWidth) / 2;
        view.layout(viewLeft, top, viewLeft + viewWidth,
                top + view.getMeasuredHeight());
    }

    /**
     * Lays out the given View starting from the given {@code bottom} value upwards and centered
     * within the given {@code availableSpace}.
     *
     * @param  view The view to lay out.
     * @param  bottom The bottom value to start laying out from. This value will be the resulting
     *                bottom value of the view.
     * @param  availableWidth The width in which to center the given view.
     */
    private void layoutViewCenteredFromBottom(View view, int bottom, int availableWidth) {
        int viewWidth = view.getMeasuredWidth();
        int viewLeft = (availableWidth - viewWidth) / 2;
        view.layout(viewLeft, bottom - view.getMeasuredHeight(),
                viewLeft + viewWidth, bottom);
    }

    /**
     * Sets the range, offset and extent of the scroll bar. The range represents the size of a
     * container for the scrollbar thumb; offset is the distance from the start of the container
     * to where the thumb should be; and finally, extent is the size of the thumb.
     *
     * <p>These values can be expressed in arbitrary units, so long as they share the same units.
     * The values should also be positive.
     *
     * @param range The range of the scrollbar's thumb
     * @param offset The offset of the scrollbar's thumb
     * @param extent The extent of the scrollbar's thumb
     * @param animate Whether or not the thumb should animate from its current position to the
     *                position specified by the given range, offset and extent.
     */
    void setParameters(
            @IntRange(from = 0) int range,
            @IntRange(from = 0) int offset,
            @IntRange(from = 0) int extent, boolean animate) {
        // Not laid out yet, so values cannot be calculated.
        if (!mScrollView.isLaidOut()) {
            return;
        }

        // If the scroll bars aren't visible, then no need to update.
        if (mScrollView.getVisibility() == View.GONE || range == 0) {
            return;
        }

        int thumbLength = calculateScrollThumbLength(range, extent);
        int thumbOffset = calculateScrollThumbOffset(range, offset, thumbLength);

        // Sets the size of the thumb and request a redraw if needed.
        ViewGroup.LayoutParams lp = mScrollThumb.getLayoutParams();

        if (lp.height != thumbLength) {
            lp.height = thumbLength;
            mScrollThumb.requestLayout();
        }

        moveY(mScrollThumb, thumbOffset, animate);
    }

    /**
     * An optimized version of {@link #setParameters(int, int, int, boolean)} that is meant to be
     * called if a view is laying itself out. This method will avoid a complete remeasure of
     * the views in the {@code PagedScrollBarView} if the scroll thumb's height needs to be changed.
     * Instead, only the thumb itself will be remeasured and laid out.
     *
     * <p>These values can be expressed in arbitrary units, so long as they share the same units.
     *
     * @param range The range of the scrollbar's thumb
     * @param offset The offset of the scrollbar's thumb
     * @param extent The extent of the scrollbar's thumb
     *
     * @see #setParameters(int, int, int, boolean)
     */
    void setParametersInLayout(int range, int offset, int extent) {
        // If the scroll bars aren't visible, then no need to update.
        if (mScrollView.getVisibility() == View.GONE || range == 0) {
            return;
        }

        int thumbLength = calculateScrollThumbLength(range, extent);
        int thumbOffset = calculateScrollThumbOffset(range, offset, thumbLength);

        // Sets the size of the thumb and request a redraw if needed.
        ViewGroup.LayoutParams lp = mScrollThumb.getLayoutParams();

        if (lp.height != thumbLength) {
            lp.height = thumbLength;
            measureAndLayoutScrollThumb();
        }

        mScrollThumb.setY(thumbOffset);
    }

    /**
     * Calculates and returns how big the scroll bar thumb should be based on the given range and
     * extent.
     *
     * @param range The total amount of space the scroll bar is allowed to roam over.
     * @param extent The amount of space that the scroll bar takes up relative to the range.
     * @return The height of the scroll bar thumb in pixels.
     */
    private int calculateScrollThumbLength(int range, int extent) {
        // Scale the length by the available space that the thumb can fill.
        return Math.round(((float) extent / range) * mScrollThumbTrackHeight);
    }

    /**
     * Calculates and returns how much the scroll thumb should be offset from the top of where it
     * has been laid out.
     *
     * @param  range The total amount of space the scroll bar is allowed to roam over.
     * @param  offset The amount the scroll bar should be offset, expressed in the same units as
     *                the given range.
     * @param  thumbLength The current length of the thumb in pixels.
     * @return The amount the thumb should be offset in pixels.
     */
    private int calculateScrollThumbOffset(int range, int offset, int thumbLength) {
        // Ensure that if the user has reached the bottom of the list, then the scroll bar is
        // aligned to the bottom as well. Otherwise, scale the offset appropriately.
        // This offset will be a value relative to the parent of this scrollbar, so start by where
        // the top of mScrollThumb is.
        return mScrollThumb.getTop() + (isDownEnabled()
                ? Math.round(((float) offset / range) * mScrollThumbTrackHeight)
                : mScrollThumbTrackHeight - thumbLength);
    }

    /** Moves the given view to the specified 'y' position. */
    private void moveY(final View view, float newPosition, boolean animate) {
        final int duration = animate ? 200 : 0;
        view.animate()
                .y(newPosition)
                .setDuration(duration)
                .setInterpolator(mPaginationInterpolator)
                .start();
    }

    /**
     * Updates the rows number per current page, which is used for calculating how many items we
     * want to show.
     */
    private void updateRowsPerPage() {
        RecyclerView.LayoutManager layoutManager = getRecyclerView().getLayoutManager();
        if (layoutManager == null) {
            mRowsPerPage = 1;
            return;
        }

        View firstChild = layoutManager.getChildAt(0);
        if (firstChild == null || firstChild.getHeight() == 0) {
            mRowsPerPage = 1;
        } else {
            mRowsPerPage = Math.max(1, getRecyclerView().getHeight() / firstChild.getHeight());
        }
    }

    private class PaginateButtonClickListener implements View.OnClickListener {
        private final int mPaginateDirection;
        private PaginationListener mPaginationListener;

        PaginateButtonClickListener(int paginateDirection) {
            mPaginateDirection = paginateDirection;
        }

        public void setPaginationListener(PaginationListener listener) {
            mPaginationListener = listener;
        }

        @Override
        public void onClick(View v) {
            if (mPaginationListener != null) {
                mPaginationListener.onPaginate(mPaginateDirection);
            }
            if (mPaginateDirection == PaginationListener.PAGE_DOWN) {
                pageDown();
            } else if (mPaginateDirection == PaginationListener.PAGE_UP) {
                pageUp();
            }
        }
    }

    private final RecyclerView.OnScrollListener mRecyclerViewOnScrollListener =
            new RecyclerView.OnScrollListener() {
                @Override
                public void onScrolled(RecyclerView recyclerView, int dx, int dy) {
                    updatePaginationButtons(false);
                }
            };

    private final Runnable mPaginationRunnable = () -> {
        boolean upPressed = isUpPressed();
        boolean downPressed = isDownPressed();
        if (upPressed && downPressed) {
            return;
        }
        if (upPressed) {
            pageUp();
        } else if (downPressed) {
            pageDown();
        }
    };

    /** Returns the page the given position is on, starting with page 0. */
    int getPage(int position) {
        if (mRowsPerPage == -1) {
            return -1;
        }
        if (mRowsPerPage == 0) {
            return 0;
        }
        return position / mRowsPerPage;
    }

    private OrientationHelper getOrientationHelper(RecyclerView.LayoutManager layoutManager) {
        if (mOrientationHelper == null || mOrientationHelper.getLayoutManager() != layoutManager) {
            // PagedRecyclerView is assumed to be a list that always vertically scrolls.
            mOrientationHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return mOrientationHelper;
    }

    /**
     * Scrolls the contents of the RecyclerView up a page. A page is defined as the height of the
     * {@code PagedRecyclerView}.
     *
     * <p>The resulting first item in the list will be snapped to so that it is completely visible.
     * If this is not possible due to the first item being taller than the containing
     * {@code PagedRecyclerView}, then the snapping will not occur.
     */
    void pageUp() {
        int currentOffset = getRecyclerView().computeVerticalScrollOffset();
        if (getRecyclerView().getLayoutManager() == null
                || getRecyclerView().getChildCount() == 0 || currentOffset == 0) {
            return;
        }

        // Use OrientationHelper to calculate scroll distance in order to match snapping behavior.
        OrientationHelper orientationHelper =
                getOrientationHelper(getRecyclerView().getLayoutManager());
        int screenSize = orientationHelper.getTotalSpace();

        int scrollDistance = screenSize;
        // The iteration order matters. In case where there are 2 items longer than screen size, we
        // want to focus on upcoming view.
        for (int i = 0; i < getRecyclerView().getChildCount(); i++) {
            /*
             * We treat child View longer than screen size differently:
             * 1) When it enters screen, next pageUp will align its bottom with parent bottom;
             * 2) When it leaves screen, next pageUp will align its top with parent top.
             */
            View child = getRecyclerView().getChildAt(i);
            if (child.getHeight() > screenSize) {
                if (orientationHelper.getDecoratedEnd(child) < screenSize) {
                    // Child view bottom is entering screen. Align its bottom with parent bottom.
                    scrollDistance = screenSize - orientationHelper.getDecoratedEnd(child);
                } else if (-screenSize < orientationHelper.getDecoratedStart(child)
                        && orientationHelper.getDecoratedStart(child) < 0) {
                    // Child view top is about to enter screen - its distance to parent top
                    // is less than a full scroll. Align child top with parent top.
                    scrollDistance = Math.abs(orientationHelper.getDecoratedStart(child));
                }
                // There can be two items that are longer than the screen. We stop at the first one.
                // This is affected by the iteration order.
                break;
            }
        }
        // Distance should always be positive. Negate its value to scroll up.
        getRecyclerView().smoothScrollBy(0, -scrollDistance);
    }

    /**
     * Scrolls the contents of the RecyclerView down a page. A page is defined as the height of the
     * {@code PagedRecyclerView}.
     *
     * <p>This method will attempt to bring the last item in the list as the first item. If the
     * current first item in the list is taller than the {@code PagedRecyclerView}, then it will be
     * scrolled the length of a page, but not snapped to.
     */
    void pageDown() {
        if (getRecyclerView().getLayoutManager() == null
                || getRecyclerView().getChildCount() == 0) {
            return;
        }

        OrientationHelper orientationHelper =
                getOrientationHelper(getRecyclerView().getLayoutManager());
        int screenSize = orientationHelper.getTotalSpace();
        int scrollDistance = screenSize;

        // If the last item is partially visible, page down should bring it to the top.
        View lastChild = getRecyclerView().getChildAt(getRecyclerView().getChildCount() - 1);
        if (getRecyclerView().getLayoutManager().isViewPartiallyVisible(lastChild,
                /* completelyVisible= */ false, /* acceptEndPointInclusion= */ false)) {
            scrollDistance = orientationHelper.getDecoratedStart(lastChild);
            if (scrollDistance < 0) {
                // Scroll value can be negative if the child is longer than the screen size and the
                // visible area of the screen does not show the start of the child.
                // Scroll to the next screen if the start value is negative
                scrollDistance = screenSize;
            }
        }

        // The iteration order matters. In case where there are 2 items longer than screen size, we
        // want to focus on upcoming view (the one at the bottom of screen).
        for (int i = getRecyclerView().getChildCount() - 1; i >= 0; i--) {
            /* We treat child View longer than screen size differently:
             * 1) When it enters screen, next pageDown will align its top with parent top;
             * 2) When it leaves screen, next pageDown will align its bottom with parent bottom.
             */
            View child = getRecyclerView().getChildAt(i);
            if (child.getHeight() > screenSize) {
                if (orientationHelper.getDecoratedStart(child) > 0) {
                    // Child view top is entering screen. Align its top with parent top.
                    scrollDistance = orientationHelper.getDecoratedStart(child);
                } else if (screenSize < orientationHelper.getDecoratedEnd(child)
                        && orientationHelper.getDecoratedEnd(child) < 2 * screenSize) {
                    // Child view bottom is about to enter screen - its distance to parent bottom
                    // is less than a full scroll. Align child bottom with parent bottom.
                    scrollDistance = orientationHelper.getDecoratedEnd(child) - screenSize;
                }
                // There can be two items that are longer than the screen. We stop at the first one.
                // This is affected by the iteration order.
                break;
            }
        }

        getRecyclerView().smoothScrollBy(0, scrollDistance);
    }

    /**
     * Determines if scrollbar should be visible or not and shows/hides it accordingly. If this is
     * being called as a result of adapter changes, it should be called after the new layout has
     * been calculated because the method of determining scrollbar visibility uses the current
     * layout. If this is called after an adapter change but before the new layout, the visibility
     * determination may not be correct.
     *
     * @param animate {@code true} if the scrollbar should animate to its new position.
     *                {@code false} if no animation is used
     */
    private void updatePaginationButtons(boolean animate) {

        boolean isAtStart = isAtStart();
        boolean isAtEnd = isAtEnd();
        RecyclerView.LayoutManager layoutManager = getRecyclerView().getLayoutManager();

        if ((isAtStart && isAtEnd) || layoutManager == null || layoutManager.getItemCount() == 0) {
            mScrollView.setVisibility(View.INVISIBLE);
        } else {
            mScrollView.setVisibility(View.VISIBLE);
        }
        setUpEnabled(!isAtStart);
        setDownEnabled(!isAtEnd);

        if (layoutManager == null) {
            return;
        }

        if (layoutManager.canScrollVertically()) {
            setParameters(
                    getRecyclerView().computeVerticalScrollRange(),
                    getRecyclerView().computeVerticalScrollOffset(),
                    getRecyclerView().computeVerticalScrollExtent(), animate);
        } else {
            setParameters(
                    getRecyclerView().computeHorizontalScrollRange(),
                    getRecyclerView().computeHorizontalScrollOffset(),
                    getRecyclerView().computeHorizontalScrollExtent(), animate);
        }

        mScrollView.invalidate();
    }

    /** Returns {@code true} if the RecyclerView is completely displaying the first item. */
    boolean isAtStart() {
        return mSnapHelper.isAtStart(getRecyclerView().getLayoutManager());
    }

    /** Returns {@code true} if the RecyclerView is completely displaying the last item. */
    boolean isAtEnd() {
        return mSnapHelper.isAtEnd(getRecyclerView().getLayoutManager());
    }

    /**
     * Scrolls to the given position in the PagedRecyclerView.
     *
     * @param position The position in the list to scroll to.
     */
    private void scrollToPosition(int position) {
        RecyclerView.LayoutManager layoutManager = getRecyclerView().getLayoutManager();
        if (layoutManager == null) {
            return;
        }

        RecyclerView.SmoothScroller smoothScroller = mSnapHelper.createScroller(layoutManager);
        smoothScroller.setTargetPosition(position);

        layoutManager.startSmoothScroll(smoothScroller);

        // Sometimes #scrollToPosition doesn't change the scroll state so we need to make sure
        // the pagination arrows actually get updated. See b/15801119
        mHandler.post(() -> updatePaginationButtons(true /*animate*/));
    }

    /**
     * Snap to the given position. This method will snap instantly to a position that's "close" to
     * the given position and then animate a short decelerate to indicate the direction that the
     * snap happened.
     *
     * @param position The position in the list to scroll to.
     */
    void snapToPosition(int position) {
        RecyclerView.LayoutManager layoutManager = getRecyclerView().getLayoutManager();

        if (layoutManager == null) {
            return;
        }

        int startPosition = position;
        if ((layoutManager instanceof RecyclerView.SmoothScroller.ScrollVectorProvider)) {
            PointF vector = ((RecyclerView.SmoothScroller.ScrollVectorProvider) layoutManager)
                    .computeScrollVectorForPosition(position);
            // A positive value in the vector means scrolling down, so should offset by scrolling to
            // an item previous in the list.
            int offsetDirection = (vector == null || vector.y > 0) ? -1 : 1;
            startPosition += offsetDirection * SNAP_SCROLL_OFFSET_POSITION;

            // Clamp the start position.
            startPosition = Math.max(0, Math.min(startPosition, layoutManager.getItemCount() - 1));
        } else {
            // If the LayoutManager doesn't implement ScrollVectorProvider (the default for
            // PagedRecyclerView, LinearLayoutManager does, but if the user has overridden it) then
            // we cannot compute the direction we need to scroll. So just snap instantly instead.
            Log.w(TAG, "LayoutManager is not a ScrollVectorProvider, can't do snap animation.");
        }

        if (layoutManager instanceof LinearLayoutManager) {
            ((LinearLayoutManager) layoutManager).scrollToPositionWithOffset(startPosition, 0);
        } else {
            layoutManager.scrollToPosition(startPosition);
        }

        if (startPosition != position) {
            // The actual scroll above happens on the next update, so we wait for that to finish
            // before doing the smooth scroll.
            mScrollView.post(() -> scrollToPosition(position));
        }
    }
}
