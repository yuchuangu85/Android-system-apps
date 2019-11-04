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

package com.android.car.notification;

import static com.android.internal.util.Preconditions.checkArgument;

import android.annotation.Nullable;
import android.content.Context;
import android.content.res.Resources;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.service.notification.NotificationStats;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;
import android.view.ViewConfiguration;

import androidx.recyclerview.widget.RecyclerView;

import com.android.car.notification.template.CarNotificationBaseViewHolder;
import com.android.car.notification.template.CarNotificationFooterViewHolder;
import com.android.car.notification.template.CarNotificationHeaderViewHolder;
import com.android.car.notification.template.GroupNotificationViewHolder;
import com.android.internal.statusbar.IStatusBarService;
import com.android.internal.statusbar.NotificationVisibility;

import java.util.concurrent.TimeUnit;

/**
 * The item touch listener for notification cards that enables swiping for dismissible notifications
 * and resistant swiping for undismissible notifications.
 */
public class CarNotificationItemTouchListener extends RecyclerView.SimpleOnItemTouchListener {

    private static final String TAG = "CarNotificationItemTouchListener";

    private final CarNotificationViewAdapter mAdapter;


    /** StatusBarService for dismissing a notification. */
    private final IStatusBarService mBarService;

    /** A general animation tool kit to dismiss {@link CarNotificationBaseViewHolder} */
    private final DismissAnimationHelper mDismissAnimationHelper;
    /**
     * The multiplier of swipe in the delta in the y direction more than the delta x direction to be
     * consider not in the a swipe in the x direction.
     */
    private final float mErrorFactorMultiplier;
    /**
     * The smallest percentage of the view holder's width a swipe gesture's delta x to be determined
     * as fast enough. Either the gesture's x velocity or gesture's x distance can be used to
     * determine if the gesture should result in a dismiss.
     */
    private final float mFlingPercentageOfWidthToDismiss;
    /**
     * The smallest percentage of the view holder's width a swipe gesture's delta x to be determined
     * as having enough swipe distance. Either the gesture's x velocity or gesture's x distance
     * can be used to determine if the gesture should result in a dismiss.
     */
    private final float mPercentageOfWidthToDismiss;
    /**
     * The minimum velocity in pixel per second that is used to determine whether a swipe that has
     * crossed the {@link #mPercentageOfWidthToDismiss} threshold is moving in the same direction.
     */
    private final int mMinVelocityForSwipeDirection;
    /**
     * The amount of space a touch move sequence is allow to wander before it is determined to be a
     * gesture.
     */
    private final int mTouchSlop;
    /** The minimum velocity in pixel per second the swipe gesture to initiate a dismiss action. */
    private final int mMinimumFlingVelocity;
    /** The cap on velocity in pixel per second a swipe gesture is calculated to have. */
    private final int mMaximumFlingVelocity;
    private final float mGroupHeaderHeight;

    /* Valid throughout a single gesture. */
    private VelocityTracker mVelocityTracker;
    private float mInitialX;
    private float mInitialY;
    private boolean mIsSwiping;
    @Nullable
    private CarNotificationBaseViewHolder mViewHolder;

    public CarNotificationItemTouchListener(Context context, CarNotificationViewAdapter adapter) {
        mAdapter = adapter;
        mBarService = IStatusBarService.Stub.asInterface(
                ServiceManager.getService(Context.STATUS_BAR_SERVICE));

        mDismissAnimationHelper = new DismissAnimationHelper(context, (viewHolder) -> {
            if (viewHolder.isDismissible()) {
                StatusBarNotification notification = viewHolder.getStatusBarNotification();
                try {
                    // rank and count is used for logging and is not need at this time thus -1
                    NotificationVisibility notificationVisibility = NotificationVisibility.obtain(
                            notification.getKey(),
                            /* rank= */ -1,
                            /* count= */ -1,
                            /* visible= */ true);

                    // The grouped notification view holder returns a notification representing the
                    // group (SummaryNotification) when viewHolder.getStatusBarNotification() is
                    // called. The platform will clear all notifications sharing the group key
                    // attached to this notification. Since grouping is not strictly based on
                    // group key, it is preferred to dismiss notifications bound to the view holder
                    // individually.
                    if (viewHolder instanceof GroupNotificationViewHolder) {
                        NotificationGroup notificationGroup =
                                ((GroupNotificationViewHolder)viewHolder).getNotificationGroup();
                        for (StatusBarNotification sbn
                                : notificationGroup.getChildNotifications()) {
                            mBarService.onNotificationClear(
                                    sbn.getPackageName(),
                                    sbn.getTag(),
                                    sbn.getId(),
                                    sbn.getUser().getIdentifier(),
                                    sbn.getKey(),
                                    NotificationStats.DISMISSAL_SHADE,
                                    NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                                    notificationVisibility
                            );
                        }
                    } else {
                        mBarService.onNotificationClear(
                                notification.getPackageName(),
                                notification.getTag(),
                                notification.getId(),
                                notification.getUser().getIdentifier(),
                                notification.getKey(),
                                NotificationStats.DISMISSAL_SHADE,
                                NotificationStats.DISMISS_SENTIMENT_NEUTRAL,
                                notificationVisibility
                        );
                    }
                } catch (RemoteException e) {
                    throw e.rethrowFromSystemServer();
                }
            }
        });

        Resources res = context.getResources();

        mGroupHeaderHeight = res.getDimension(R.dimen.notification_card_header_height);

        mErrorFactorMultiplier = res.getFloat(R.dimen.error_factor_multiplier);

        mFlingPercentageOfWidthToDismiss =
                res.getFloat(R.dimen.fling_percentage_of_width_to_dismiss);

        mPercentageOfWidthToDismiss = res.getFloat(R.dimen.percentage_of_width_to_dismiss);

        mMinVelocityForSwipeDirection =
                res.getInteger(R.integer.min_velocity_for_swipe_direction_detection);

        mTouchSlop = res.getDimensionPixelSize(R.dimen.touch_slop);

        ViewConfiguration viewConfiguration = ViewConfiguration.get(context);
        mMaximumFlingVelocity = viewConfiguration.getScaledMaximumFlingVelocity();
        mMinimumFlingVelocity = viewConfiguration.getScaledMinimumFlingVelocity();
    }

    @Override
    public boolean onInterceptTouchEvent(RecyclerView recyclerView, MotionEvent event) {
        if (event.getPointerCount() > 1) {
            // Ignore subsequent pointers.
            return false;
        }

        // We are not yet tracking a swipe gesture. Begin detection by spying on
        // touch events bubbling down to our children.
        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                onGestureStart();

                mVelocityTracker.addMovement(event);
                mInitialX = event.getX();
                mInitialY = event.getY();

                View viewAtPoint = recyclerView.findChildViewUnder(mInitialX, mInitialY);
                if (viewAtPoint == null) {
                    // swiping from a point which has no element.
                    onGestureEnd();
                    return false;
                }

                RecyclerView.ViewHolder viewHolderAtPoint =
                        recyclerView.findContainingViewHolder(viewAtPoint);
                if (viewHolderAtPoint instanceof CarNotificationHeaderViewHolder
                        || viewHolderAtPoint instanceof CarNotificationFooterViewHolder) {
                    return false;
                }
                checkArgument(viewHolderAtPoint instanceof CarNotificationBaseViewHolder);
                mViewHolder = (CarNotificationBaseViewHolder) viewHolderAtPoint;

                // Ensure that we're not trying to swipe a view that is animating away or
                // restoring back to it's initial state from a previous animation.
                if (mViewHolder.isAnimating()) {
                    mViewHolder = null;
                }

                break;
            case MotionEvent.ACTION_MOVE:
                if (!hasValidGestureSwipeTarget()) {
                    break;
                }

                mVelocityTracker.addMovement(event);

                int historicalCount = event.getHistorySize();
                // First consume the historical events, then consume the current ones.
                for (int i = 0; i < historicalCount + 1; i++) {
                    float currX;
                    float currY;
                    if (i < historicalCount) {
                        currX = event.getHistoricalX(i);
                        currY = event.getHistoricalY(i);
                    } else {
                        currX = event.getX();
                        currY = event.getY();
                    }
                    float deltaX = currX - mInitialX;
                    float deltaY = currY - mInitialY;
                    float absDeltaX = Math.abs(deltaX);
                    float absDeltaY = Math.abs(deltaY);

                    // Ensuring that we're swiping more in the x axis than in the y axis.
                    // This is defined as having more delta y than the touch slop and more
                    // delta y than delta x by a defined factor.
                    if (!mIsSwiping
                            && absDeltaY > mTouchSlop
                            && absDeltaY > (mErrorFactorMultiplier * absDeltaX)) {
                        // Stop detecting swipe for the remainder of this gesture.
                        onGestureEnd();
                        return false;
                    }

                    // If a group notification is expanded, we desire a behavior that swiping on the
                    // header would swipe the entire group away; while swiping on the child
                    // notifications would swipe individual child notification away.
                    if (mAdapter.isExpanded(mViewHolder.getStatusBarNotification().getGroupKey())) {
                        float itemTop = mViewHolder.itemView.getY();
                        boolean isTouchingGroupHeader =
                                (currY > itemTop) && (currY < itemTop + mGroupHeaderHeight);
                        if (!isTouchingGroupHeader) {
                            return false;
                        }
                    }

                    if (absDeltaX > mTouchSlop) {
                        // Swipe detected. Return true so we can handle the gesture in
                        // onTouchEvent.
                        mIsSwiping = true;

                        // We don't want to suddenly jump the slop distance.
                        mInitialX = event.getX();
                        mInitialY = event.getY();

                        onSwipeGestureStart(recyclerView, mViewHolder);
                        return true;
                    }
                }

                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                if (hasValidGestureSwipeTarget()) {
                    onGestureEnd();
                }
                break;
            default:
                break;
        }

        // Start intercepting touch events from children if we detect a swipe.
        return mIsSwiping;
    }

    @Override
    public void onTouchEvent(RecyclerView recyclerView, MotionEvent event) {
        // We should only be here if we intercepted the touch due to swipe.
        checkArgument(mIsSwiping);

        // We are now tracking a swipe gesture.
        mVelocityTracker.addMovement(event);

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_OUTSIDE:
            case MotionEvent.ACTION_MOVE:
                if (!hasValidGestureSwipeTarget()) {
                    break;
                }

                float deltaX = event.getX() - mInitialX;
                float translateX = mDismissAnimationHelper.calculateTranslateDistance(mViewHolder,
                        deltaX);
                float alpha = mDismissAnimationHelper.calculateAlphaValue(mViewHolder, deltaX);
                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "ACTION_MOVE translateX=" + translateX + " alpha=" + alpha);
                }
                mViewHolder.setSwipeTranslationX(translateX);
                mViewHolder.setSwipeAlpha(alpha);

                break;
            case MotionEvent.ACTION_UP:
                if (!hasValidGestureSwipeTarget()) {
                    onGestureEnd();
                    break;
                }

                mVelocityTracker.computeCurrentVelocity(
                        (int) TimeUnit.SECONDS.toMillis(1) /* pixels/second */,
                        mMaximumFlingVelocity);
                float velocityX = getLastComputedXVelocity();

                float translationX = mViewHolder.getSwipeTranslationX();

                @DismissAnimationHelper.Direction
                int swipeDirection = DismissAnimationHelper.Direction.RIGHT;
                if (translationX != 0) {
                    swipeDirection =
                            (translationX > 0)
                                    ? DismissAnimationHelper.Direction.RIGHT
                                    : DismissAnimationHelper.Direction.LEFT;
                } else if (velocityX != 0) {
                    swipeDirection =
                            (velocityX > 0)
                                    ? DismissAnimationHelper.Direction.LEFT
                                    : DismissAnimationHelper.Direction.RIGHT;
                }

                boolean fastEnough = isTargetSwipedFastEnough();
                boolean farEnough = isTargetSwipedFarEnough();
                boolean shouldDismiss = (fastEnough || farEnough) && mViewHolder.isDismissible();
                if (shouldDismiss) {
                    if (fastEnough) {
                        mDismissAnimationHelper.animateDismiss(mViewHolder, swipeDirection);
                    } else {
                        mDismissAnimationHelper.animateDismiss(mViewHolder, swipeDirection);
                    }
                } else {
                    mDismissAnimationHelper.animateRestore(mViewHolder, velocityX);
                }

                onSwipeGestureEnd(recyclerView, mViewHolder);
                break;
            case MotionEvent.ACTION_CANCEL:
                if (hasValidGestureSwipeTarget()) {
                    mDismissAnimationHelper.animateRestore(mViewHolder, 0f);
                    onSwipeGestureEnd(recyclerView, mViewHolder);
                } else {
                    onGestureEnd();
                }
                break;
            default:
                break;
        }

    }

    /** We have started to intercept a series of touch events. */
    private void onGestureStart() {
        mIsSwiping = false;
        // Work around b/117872229 in RecyclerView that sends two identical ACTION_DOWN
        // events to #onInterceptTouchEvent.
        if (mVelocityTracker == null) {
            mVelocityTracker = VelocityTracker.obtain();
        }
        mVelocityTracker.clear();
    }

    /**
     * The series of touch events has been detected as a swipe.
     *
     * <p>Now that the gesture is a swipe, we will begin translating the view of the given
     * mViewHolder.
     */
    private void onSwipeGestureStart(
            RecyclerView recyclerView, CarNotificationBaseViewHolder viewHolder) {
        recyclerView.getParent().requestDisallowInterceptTouchEvent(true);
        viewHolder.setIsAnimating(true);
    }

    /** The current swipe gesture is complete. */
    private void onSwipeGestureEnd(RecyclerView recyclerView,
            CarNotificationBaseViewHolder viewHolder) {
        recyclerView.getParent().requestDisallowInterceptTouchEvent(false);
        viewHolder.setIsAnimating(false);
        onGestureEnd();
    }

    /**
     * The series of touch events has ended in an {@link MotionEvent#ACTION_UP} or {@link
     * MotionEvent#ACTION_CANCEL}.
     */
    private void onGestureEnd() {
        mVelocityTracker.recycle();
        mVelocityTracker = null;
        mIsSwiping = false;
        mViewHolder = null;
    }

    /** Determine if the swipe has enough velocity to be dismissed. */
    private boolean isTargetSwipedFastEnough() {
        float velocityX = getLastComputedXVelocity();
        float velocityY = mVelocityTracker.getYVelocity();
        float minVelocity = mMinimumFlingVelocity;
        float translationX = mViewHolder.getSwipeTranslationX();
        float width = mViewHolder.itemView.getWidth();
        float minWidthToTranslate = mFlingPercentageOfWidthToDismiss * width;

        boolean isFastEnough = (Math.abs(velocityX) > minVelocity);
        boolean isIntentional = (Math.abs(velocityX) > Math.abs(velocityY));
        boolean isSameDirection = (velocityX > 0) == (translationX > 0);
        boolean hasEnoughMovement = Math.abs(translationX) > minWidthToTranslate;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                    TAG,
                    "isTargetSwipedFastEnough"
                            + " isFastEnough=" + isFastEnough
                            + " isIntentional=" + isIntentional
                            + " isSameDirection=" + isSameDirection
                            + " hasEnoughMovement=" + hasEnoughMovement);
        }

        return isFastEnough && isIntentional && isSameDirection && hasEnoughMovement;
    }

    /**
     * Only used during a swipe gesture. Determine if the swipe has enough distance to be dismissed.
     */
    private boolean isTargetSwipedFarEnough() {
        float velocityX = getLastComputedXVelocity();
        float translationX = mViewHolder.getSwipeTranslationX();
        float width = mViewHolder.itemView.getWidth();
        float minWidthToTranslate = mPercentageOfWidthToDismiss * width;

        boolean isVelocityHighEnough = (Math.abs(velocityX) > mMinVelocityForSwipeDirection);
        // Do the same direction check only if the velocity is high enough, otherwise bypass the
        // direction check.
        boolean isSameDirection = !isVelocityHighEnough || ((velocityX > 0) == (translationX > 0));
        boolean hasEnoughMovement = Math.abs(translationX) > minWidthToTranslate;

        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(
                    TAG,
                    "isTargetSwipedFarEnough"
                            + " isSameDirection=" + isSameDirection
                            + " hasEnoughMovement=" + hasEnoughMovement
                            + " velocityX=%f" + velocityX);
        }

        return isSameDirection && hasEnoughMovement;
    }

    private boolean hasValidGestureSwipeTarget() {
        return mViewHolder != null;
    }

    /** @return Computed X velocity in px / second. */
    private float getLastComputedXVelocity() {
        return mVelocityTracker.getXVelocity();
    }
}
