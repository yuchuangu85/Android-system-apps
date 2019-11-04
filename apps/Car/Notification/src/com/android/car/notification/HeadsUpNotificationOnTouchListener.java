/*
 * Copyright 2018 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.car.notification;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.view.MotionEvent;
import android.view.VelocityTracker;
import android.view.View;

/**
 * OnTouchListener that enables swipe-to-dismiss gesture on heads-up notifications.
 */
class HeadsUpNotificationOnTouchListener implements View.OnTouchListener {
    /**
     * Minimum velocity to initiate a fling, as measured in pixels per second.
     */
    private static final int MINIMUM_FLING_VELOCITY = 2000;

    /**
     * Distance a touch can wander before we think the user is scrolling in pixels.
     */
    private static final int TOUCH_SLOP = 20;

    /**
     * The proportion which view has to be swiped before it dismisses.
     */
    private static final float THRESHOLD = 0.3f;

    /**
     * The unit of velocity in milliseconds. A value of 1 means "pixels per millisecond",
     * 1000 means "pixels per 1000 milliseconds (1 second)".
     */
    private static final int VELOCITY_UNITS = 1000;

    private final View mView;
    private final DismissCallbacks mCallbacks;

    private VelocityTracker mVelocityTracker;
    private float mDownX;
    private boolean mSwiping;
    private int mSwipingSlop;
    private float mTranslationX;
    private boolean mDismissOnSwipe = true;

    /**
     * The callback indicating the supplied view has been dismissed.
     */
    interface DismissCallbacks {
        void onDismiss();
    }

    HeadsUpNotificationOnTouchListener(View view, boolean dismissOnSwipe,
            DismissCallbacks callbacks) {
        mView = view;
        mCallbacks = callbacks;
        mDismissOnSwipe = dismissOnSwipe;
    }

    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        motionEvent.offsetLocation(mTranslationX, /* deltaY= */ 0);
        int viewWidth = mView.getWidth();

        switch (motionEvent.getActionMasked()) {
            case MotionEvent.ACTION_DOWN: {
                mDownX = motionEvent.getRawX();
                mVelocityTracker = VelocityTracker.obtain();
                mVelocityTracker.addMovement(motionEvent);
                return false;
            }

            case MotionEvent.ACTION_UP: {
                if (mVelocityTracker == null) {
                    return false;
                }

                float deltaX = motionEvent.getRawX() - mDownX;
                mVelocityTracker.addMovement(motionEvent);
                mVelocityTracker.computeCurrentVelocity(VELOCITY_UNITS);
                float velocityX = mVelocityTracker.getXVelocity();
                float absVelocityX = Math.abs(velocityX);
                float absVelocityY = Math.abs(mVelocityTracker.getYVelocity());
                boolean dismiss = false;
                boolean dismissRight = false;
                if (Math.abs(deltaX) > viewWidth * THRESHOLD) {
                    // dismiss when the movement is more than the defined threshold.
                    dismiss = true;
                    dismissRight = deltaX > 0;
                } else if (MINIMUM_FLING_VELOCITY <= absVelocityX
                        && absVelocityY < absVelocityX
                        && mSwiping) {
                    // dismiss when the velocity is more than the defined threshold.
                    // dismiss only if flinging in the same direction as dragging.
                    dismiss = (velocityX < 0) == (deltaX < 0);
                    dismissRight = mVelocityTracker.getXVelocity() > 0;
                }
                if (dismiss && mDismissOnSwipe) {
                    mCallbacks.onDismiss();
                    mView.animate()
                            .translationX(dismissRight ? viewWidth : -viewWidth)
                            .alpha(0)
                            .setListener(new AnimatorListenerAdapter() {
                                @Override
                                public void onAnimationEnd(Animator animation) {
                                    mView.setAlpha(1f);
                                    mView.setTranslationX(0);
                                }
                            });
                } else if (mSwiping) {
                    animateToCenter();
                }
                reset();
                break;
            }

            case MotionEvent.ACTION_CANCEL: {
                if (mVelocityTracker == null) {
                    return false;
                }
                animateToCenter();
                reset();
                return false;
            }

            case MotionEvent.ACTION_MOVE: {
                if (mVelocityTracker == null) {
                    return false;
                }

                mVelocityTracker.addMovement(motionEvent);
                float deltaX = motionEvent.getRawX() - mDownX;
                if (Math.abs(deltaX) > TOUCH_SLOP) {
                    mSwiping = true;
                    mSwipingSlop = (deltaX > 0 ? TOUCH_SLOP : -TOUCH_SLOP);
                    mView.getParent().requestDisallowInterceptTouchEvent(true);

                    // prevent onClickListener being triggered when moving.
                    MotionEvent cancelEvent = MotionEvent.obtain(motionEvent);
                    cancelEvent.setAction(MotionEvent.ACTION_CANCEL |
                            (motionEvent.getActionIndex() <<
                                    MotionEvent.ACTION_POINTER_INDEX_SHIFT));
                    mView.onTouchEvent(cancelEvent);
                    cancelEvent.recycle();
                }

                if (mSwiping) {
                    mTranslationX = deltaX;
                    mView.setTranslationX(deltaX - mSwipingSlop);
                    if (!mDismissOnSwipe) {
                        return true;
                    }
                    mView.setAlpha(Math.max(0f, Math.min(1f,
                            1f - 2f * Math.abs(deltaX) / viewWidth)));
                    return true;
                }
            }

            default: {
                return false;
            }
        }
        return false;
    }

    private void animateToCenter() {
        mView.animate()
                .translationX(0)
                .alpha(1)
                .setListener(null);
    }

    private void reset() {
        if (mVelocityTracker != null) {
            mVelocityTracker.recycle();
        }
        mVelocityTracker = null;
        mTranslationX = 0;
        mDownX = 0;
        mSwiping = false;
    }
}
