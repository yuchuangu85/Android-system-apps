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

import static java.lang.annotation.RetentionPolicy.SOURCE;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.IntDef;
import android.content.Context;
import android.util.Log;

import com.android.car.notification.template.CarNotificationBaseViewHolder;

import java.lang.annotation.Retention;

/** A general animation tool kit to dismiss {@link CarNotificationBaseViewHolder} */
class DismissAnimationHelper {
    private static final String TAG = "CarDismissHelper";
    /**
     * The weight of how much swipe distance plays on the alpha value of the view.
     * A weight of 1F will make the view completely transparent if the swipe distance is larger
     * than the view width.
     */
    private static final float SWIPE_DISTANCE_WEIGHT_ON_ALPHA = 0.9F;
    private final DismissCallback mCallBacks;

    /**
     * The direction of motion.
     * <ol>
     *     <li> LEFT means swiping to the left.
     *     <li> RIGHT means swiping to the right.
     * </ol>
     */
    @Retention(SOURCE)
    @IntDef({Direction.LEFT, Direction.RIGHT})
    public @interface Direction {
        int LEFT = 1;
        int RIGHT = 2;
    }

    /**
     * The percentage of the view holder's width a non-dismissible view holder is allow to translate
     * during a swipe gesture. As gesture's delta x distance grows the view holder should translate
     * asymptotically to this amount.
     */
    private final float mMaxPercentageOfWidthWithResistance;

    /**
     * The callback indicating the supplied view has been dismissed.
     */
    interface DismissCallback {

        /**
         * Called after animation ends and the view is considered dismissed.
         */
        void onDismiss(CarNotificationBaseViewHolder viewHolder);
    }

    DismissAnimationHelper(Context context, DismissCallback callbacks) {
        mCallBacks = callbacks;

        mMaxPercentageOfWidthWithResistance =
                context.getResources().getFloat(R.dimen.max_percentage_of_width_with_resistance);
    }

    /** Animate the dismissal of the given item. The velocityX is assumed to be 0. */
    void animateDismiss(CarNotificationBaseViewHolder viewHolder,
            @Direction int swipeDirection) {
        animateDismiss(viewHolder, swipeDirection, 0f);
    }

    /** Animate the dismissal of the given item. */
    void animateDismiss(
            CarNotificationBaseViewHolder viewHolder,
            @Direction int swipeDirection,
            float velocityX) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "animateDismiss direction=" + swipeDirection + " velocityX=" + velocityX);
        }

        viewHolder.setIsAnimating(true);

        int viewWidth = viewHolder.itemView.getWidth();
        viewHolder.itemView.animate()
                .translationX(swipeDirection == Direction.RIGHT ? viewWidth : -viewWidth)
                .alpha(0)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        mCallBacks.onDismiss(viewHolder);
                    }
                });
    }

    /** Animate the restore back of the given item back to it's initial state. */
    void animateRestore(CarNotificationBaseViewHolder viewHolder, float velocityX) {
        if (Log.isLoggable(TAG, Log.DEBUG)) {
            Log.d(TAG, "animateRestore velocityX=" + velocityX);
        }

        viewHolder.setIsAnimating(true);

        viewHolder.itemView.animate()
                .translationX(0)
                .alpha(1)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(Animator animation) {
                        viewHolder.setIsAnimating(false);
                    }
                });
    }

    float calculateAlphaValue(CarNotificationBaseViewHolder viewHolder, float translateX) {
        if (!viewHolder.isDismissible() || translateX == 0) {
            return 1F;
        }

        int width = viewHolder.itemView.getWidth();
        return SWIPE_DISTANCE_WEIGHT_ON_ALPHA * (1 - Math.min(Math.abs(translateX / width), 1))
                + (1  - SWIPE_DISTANCE_WEIGHT_ON_ALPHA);
    }

    float calculateTranslateDistance(CarNotificationBaseViewHolder viewHolder, float moveDeltaX) {
        // If we can dismiss then translate the same distance the touch event moved and if delta
        // x is 0 just return 0.
        if (viewHolder.isDismissible() || moveDeltaX == 0) {
            return moveDeltaX;
        }

        // Calculate possible drag resistance.
        int swipeDirection = moveDeltaX > 0 ? Direction.RIGHT : Direction.LEFT;

        int width = viewHolder.itemView.getWidth();
        float maxSwipeDistanceWithResistance = mMaxPercentageOfWidthWithResistance * width;
        if (Math.abs(moveDeltaX) >= width) {
            // If deltaX is too large, constrain to
            // maxScrollDistanceWithResistance.
            return (swipeDirection == Direction.RIGHT)
                    ? maxSwipeDistanceWithResistance
                    : -maxSwipeDistanceWithResistance;
        } else {
            // Otherwise, just attenuate deltaX.
            return maxSwipeDistanceWithResistance
                    * (float) Math.sin((moveDeltaX / width) * (Math.PI / 2));
        }
    }
}