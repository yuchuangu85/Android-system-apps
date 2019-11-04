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
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.LinearSnapHelper;
import androidx.recyclerview.widget.OrientationHelper;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.LayoutManager;

import com.android.car.apps.common.util.PagedSmoothScroller;

/**
 * Inspired by {@link androidx.car.widget.PagedSnapHelper}
 *
 * Extension of a {@link LinearSnapHelper} that will snap to the start of the target child view to
 * the start of the attached {@link RecyclerView}. The start of the view is defined as the top
 * if the RecyclerView is scrolling vertically; it is defined as the left (or right if RTL) if the
 * RecyclerView is scrolling horizontally.
 */
public class PagedSnapHelper extends LinearSnapHelper {

    private final Context mContext;
    private RecyclerView mRecyclerView;

    public PagedSnapHelper(Context context) {
        mContext = context;
    }

    // Orientation helpers are lazily created per LayoutManager.
    @Nullable private OrientationHelper mVerticalHelper;
    @Nullable private OrientationHelper mHorizontalHelper;

    @Override
    public int[] calculateDistanceToFinalSnap(
            @NonNull LayoutManager layoutManager, @NonNull View targetView) {
        int[] out = new int[2];
        if (layoutManager.canScrollHorizontally()) {
            out[0] = distanceToTopMargin(layoutManager, targetView,
                    getHorizontalHelper(layoutManager));
        } else {
            out[0] = 0;
        }

        if (layoutManager.canScrollVertically()) {
            out[1] = distanceToTopMargin(layoutManager, targetView,
                    getVerticalHelper(layoutManager));
        } else {
            out[1] = 0;
        }
        return out;
    }

    @Override
    public View findSnapView(LayoutManager layoutManager) {
        OrientationHelper orientationHelper = getOrientationHelper(layoutManager);

        if (mRecyclerView.computeVerticalScrollRange() - mRecyclerView.computeVerticalScrollOffset()
                 <= orientationHelper.getTotalSpace()
                + mRecyclerView.getPaddingTop() + mRecyclerView.getPaddingBottom()) {
            return null;
        }

        if (layoutManager.canScrollVertically()) {
            return findTopView(layoutManager, getVerticalHelper(layoutManager));
        } else if (layoutManager.canScrollHorizontally()) {
            return findTopView(layoutManager, getHorizontalHelper(layoutManager));
        }
        return null;
    }

    private int distanceToTopMargin(@NonNull LayoutManager layoutManager,
            @NonNull View targetView, OrientationHelper helper) {
        final int childTop = helper.getDecoratedStart(targetView);
        final int containerTop = helper.getStartAfterPadding();
        return childTop - containerTop;
    }

    /**
     * Finds the view to snap to. The view to snap to is the child of the LayoutManager that is
     * closest to the start of the RecyclerView. The "start" depends on if the LayoutManager
     * is scrolling horizontally or vertically. If it is horizontally scrolling, then the
     * start is the view on the left (right if RTL). Otherwise, it is the top-most view.
     *
     * @param layoutManager The current {@link RecyclerView.LayoutManager} for the attached
     *                      RecyclerView.
     * @return The View closest to the start of the RecyclerView.
     */
    private View findTopView(LayoutManager layoutManager,
            OrientationHelper helper) {
        int childCount = layoutManager.getChildCount();
        if (childCount == 0) {
            return null;
        }

        View closestChild = null;
        int absClosest = Integer.MAX_VALUE;

        for (int i = 0; i < childCount; i++) {
            View child = layoutManager.getChildAt(i);
            if (child == null) continue;
            int absDistance = Math.abs(distanceToTopMargin(layoutManager, child, helper));

            /** if child top is closer than previous closest, set it as closest  **/
            if (absDistance < absClosest) {
                absClosest = absDistance;
                closestChild = child;
            }
        }
        return closestChild;
    }

    /**
     * Returns the percentage of the given view that is visible, relative to its containing
     * RecyclerView.
     *
     * @param view The View to get the percentage visible of.
     * @param helper An {@link OrientationHelper} to aid with calculation.
     * @return A float indicating the percentage of the given view that is visible.
     */
    private float getPercentageVisible(View view,
            OrientationHelper helper) {

        int start = helper.getStartAfterPadding();
        int end = helper.getEndAfterPadding();

        int viewHeight = helper.getDecoratedMeasurement(view);

        int viewStart = helper.getDecoratedStart(view);
        int viewEnd = helper.getDecoratedEnd(view);

        if (viewEnd < start) {
            // The is outside of the bounds of the recyclerView.
            return 0f;
        } else if (viewStart >= start && viewEnd <= end) {
            // The view is within the bounds of the RecyclerView, so it's fully visible.
            return 1.f;
        } else if (viewStart <= start && viewEnd >= end) {
            // The view is larger than the height of the RecyclerView.
            return 1.f - ((float) (Math.abs(viewStart) + Math.abs(viewEnd)) / viewHeight);
        } else if (viewStart < start) {
            // The view is above the start of the RecyclerView, so subtract the start offset
            // from the total height.
            return 1.f - ((float) Math.abs(viewStart) / helper.getDecoratedMeasurement(view));
        } else {
            // The view is below the end of the RecyclerView, so subtract the end offset from the
            // total height.
            return 1.f - ((float) Math.abs(viewEnd) / helper.getDecoratedMeasurement(view));
        }
    }

    @Override
    public void attachToRecyclerView(@Nullable RecyclerView recyclerView) {
        mRecyclerView = recyclerView;
        super.attachToRecyclerView(recyclerView);
    }

    /**
     * Returns a scroller specific to this {@code PagedSnapHelper}. This scroller is used for all
     * smooth scrolling operations, including flings.
     *
     * @param layoutManager The {@link RecyclerView.LayoutManager} associated with the attached
     *                      {@link RecyclerView}.
     *
     * @return a {@link RecyclerView.SmoothScroller} which will handle the scrolling.
     */
    @Override
    protected RecyclerView.SmoothScroller createScroller(RecyclerView.LayoutManager layoutManager) {
        return new PagedSmoothScroller(mContext);
    }

    /**
     * Calculate the estimated scroll distance in each direction given velocities on both axes.
     * This method will clamp the maximum scroll distance so that a single fling will never scroll
     * more than one page.
     *
     * @param velocityX Fling velocity on the horizontal axis.
     * @param velocityY Fling velocity on the vertical axis.
     * @return An array holding the calculated distances in x and y directions respectively.
     */
    @Override
    public int[] calculateScrollDistance(int velocityX, int velocityY) {
        int[] outDist = super.calculateScrollDistance(velocityX, velocityY);

        if (mRecyclerView == null) {
            return outDist;
        }

        RecyclerView.LayoutManager layoutManager = mRecyclerView.getLayoutManager();
        if (layoutManager == null || layoutManager.getChildCount() == 0) {
            return outDist;
        }

        int lastChildPosition = isAtEnd(layoutManager) ? 0 : layoutManager.getChildCount() - 1;

        OrientationHelper orientationHelper = getOrientationHelper(layoutManager);
        View lastChild = layoutManager.getChildAt(lastChildPosition);
        float percentageVisible = getPercentageVisible(lastChild, orientationHelper);

        int maxDistance = layoutManager.getHeight();
        if (percentageVisible > 0.f) {
            // The max and min distance is the total height of the RecyclerView minus the height of
            // the last child. This ensures that each scroll will never scroll more than a single
            // page on the RecyclerView. That is, the max scroll will make the last child the
            // first child and vice versa when scrolling the opposite way.
            maxDistance -= layoutManager.getDecoratedMeasuredHeight(lastChild);
        }

        int minDistance = -maxDistance;

        outDist[0] = clamp(outDist[0], minDistance, maxDistance);
        outDist[1] = clamp(outDist[1], minDistance, maxDistance);

        return outDist;
    }

    /** Returns {@code true} if the RecyclerView is completely displaying the first item. */
    boolean isAtStart(RecyclerView.LayoutManager layoutManager) {
        if (layoutManager == null || layoutManager.getChildCount() == 0) {
            return true;
        }

        View firstChild = layoutManager.getChildAt(0);
        OrientationHelper orientationHelper = layoutManager.canScrollVertically()
                ? getVerticalHelper(layoutManager)
                : getHorizontalHelper(layoutManager);

        // Check that the first child is completely visible and is the first item in the list.
        return orientationHelper.getDecoratedStart(firstChild)
                >= orientationHelper.getStartAfterPadding()
                && layoutManager.getPosition(firstChild) == 0;
    }

    /** Returns {@code true} if the RecyclerView is completely displaying the last item. */
    public boolean isAtEnd(RecyclerView.LayoutManager layoutManager) {
        if (layoutManager == null || layoutManager.getChildCount() == 0) {
            return true;
        }

        int childCount = layoutManager.getChildCount();
        OrientationHelper orientationHelper = layoutManager.canScrollVertically()
                ? getVerticalHelper(layoutManager)
                : getHorizontalHelper(layoutManager);

        View lastVisibleChild = layoutManager.getChildAt(childCount - 1);

        // The list has reached the bottom if the last child that is visible is the last item
        // in the list and it's fully shown.
        return layoutManager.getPosition(lastVisibleChild) == (layoutManager.getItemCount() - 1)
                && layoutManager.getDecoratedBottom(lastVisibleChild)
                <= orientationHelper.getEndAfterPadding();
    }

    /**
     * Returns an {@link OrientationHelper} that corresponds to the current scroll direction of
     * the given {@link RecyclerView.LayoutManager}.
     */
    @NonNull
    private OrientationHelper getOrientationHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        return layoutManager.canScrollVertically()
                ? getVerticalHelper(layoutManager)
                : getHorizontalHelper(layoutManager);
    }

    @NonNull
    private OrientationHelper getVerticalHelper(@NonNull RecyclerView.LayoutManager layoutManager) {
        if (mVerticalHelper == null || mVerticalHelper.getLayoutManager() != layoutManager) {
            mVerticalHelper = OrientationHelper.createVerticalHelper(layoutManager);
        }
        return mVerticalHelper;
    }

    @NonNull
    private OrientationHelper getHorizontalHelper(
            @NonNull RecyclerView.LayoutManager layoutManager) {
        if (mHorizontalHelper == null || mHorizontalHelper.getLayoutManager() != layoutManager) {
            mHorizontalHelper = OrientationHelper.createHorizontalHelper(layoutManager);
        }
        return mHorizontalHelper;
    }

    /**
     * Ensures that the given value falls between the range given by the min and max values. This
     * method does not check that the min value is greater than or equal to the max value. If the
     * parameters are not well-formed, this method's behavior is undefined.
     *
     * @param value The value to clamp.
     * @param min The minimum value the given value can be.
     * @param max The maximum value the given value can be.
     * @return A number that falls between {@code min} or {@code max} or one of those values if the
     * given value is less than or greater than {@code min} and {@code max} respectively.
     */
    private static int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}
