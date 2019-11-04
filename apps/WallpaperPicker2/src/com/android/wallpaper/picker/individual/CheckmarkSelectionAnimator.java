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

import android.animation.Animator;
import android.animation.Animator.AnimatorListener;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.content.Context;
import android.os.Handler;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnHoverListener;
import android.widget.ImageView;

import com.android.wallpaper.R;

/**
 * Implementation of {@code SelectionAnimator} which uses a checkmark and inset around the tile to
 * indicate a selected state.
 */
public class CheckmarkSelectionAnimator implements SelectionAnimator {
    private static final int HOVER_TIMEOUT_MS = 200;
    private static final float HOVER_CHECK_CIRCLE_OPACITY = 0.67f;

    private Context mAppContext;

    private View mTile;
    private ImageView mCheckCircle;
    private View mLoadingIndicatorContainer;
    private boolean mIsSelected;
    private boolean mIsHovered;
    private Handler mHoverHandler;

    private Runnable mHoverEnterRunnable = new Runnable() {
        @Override
        public void run() {
            mIsHovered = true;

            mCheckCircle.setImageDrawable(mAppContext.getDrawable(
                    R.drawable.material_ic_check_circle_white_24));
            mCheckCircle.setVisibility(View.VISIBLE);
            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                    mCheckCircle, "alpha", 0f, HOVER_CHECK_CIRCLE_OPACITY);
            alphaAnimator.start();

            alphaAnimator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mIsHovered = true;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });
        }
    };

    private Runnable mHoverExitRunnable = new Runnable() {
        @Override
        public void run() {
            mIsHovered = false;

            ObjectAnimator alphaAnimator = ObjectAnimator.ofFloat(
                    mCheckCircle, "alpha", HOVER_CHECK_CIRCLE_OPACITY, 0f);
            alphaAnimator.addListener(new AnimatorListener() {
                @Override
                public void onAnimationStart(Animator animation) {
                }

                @Override
                public void onAnimationEnd(Animator animation) {
                    mCheckCircle.setVisibility(View.GONE);
                    mIsHovered = false;
                }

                @Override
                public void onAnimationCancel(Animator animation) {
                }

                @Override
                public void onAnimationRepeat(Animator animation) {
                }
            });

            alphaAnimator.start();
        }
    };

    public CheckmarkSelectionAnimator(Context appContext, View itemView) {
        mAppContext = appContext;

        mTile = itemView.findViewById(R.id.tile);
        mCheckCircle = (ImageView) itemView.findViewById(R.id.check_circle);
        mLoadingIndicatorContainer = itemView.findViewById(R.id.loading_indicator_container);
        mHoverHandler = new Handler();

        mTile.setOnHoverListener(new OnHoverListener() {
            @Override
            public boolean onHover(View v, MotionEvent event) {
                // If this ViewHolder is already selected, then don't change the state of the check circle.
                if (mIsSelected) {
                    return false;
                }

                int actionMasked = event.getActionMasked();

                switch (actionMasked) {
                    case MotionEvent.ACTION_HOVER_ENTER:
                        animateHoverEnter();
                        break;
                    case MotionEvent.ACTION_HOVER_EXIT:
                        animateHoverExit();
                        break;
                    default:
                        // fall out
                }

                return false;
            }
        });
    }

    @Override
    public void selectImmediately() {
        mIsSelected = true;
        int insetPx = mAppContext.getResources().getDimensionPixelSize(
                R.dimen.grid_item_individual_wallpaper_selected_inset);
        mTile.setPadding(insetPx, insetPx, insetPx, insetPx);
        mCheckCircle.setImageDrawable(mAppContext.getDrawable(R.drawable.check_circle_blue));
        mCheckCircle.setVisibility(View.VISIBLE);
        mCheckCircle.setAlpha(1f);
        mLoadingIndicatorContainer.setVisibility(View.GONE);
    }

    @Override
    public void deselectImmediately() {
        mIsSelected = false;
        mCheckCircle.setAlpha(0f);
        mCheckCircle.setVisibility(View.GONE);
        mTile.setPadding(0, 0, 0, 0);
        mLoadingIndicatorContainer.setVisibility(View.GONE);
    }

    @Override
    public void animateSelected() {
        // If already selected, do nothing.
        if (mIsSelected) {
            return;
        }

        mLoadingIndicatorContainer.setVisibility(View.GONE);

        int[][] values = new int[2][4];
        values[0] = new int[]{0, 0, 0, 0};
        int insetPx = mAppContext.getResources().getDimensionPixelSize(
                R.dimen.grid_item_individual_wallpaper_selected_inset);
        values[1] = new int[]{insetPx, insetPx, insetPx, insetPx};

        ObjectAnimator paddingAnimator = ObjectAnimator.ofMultiInt(mTile, "padding", values);
        ObjectAnimator checkCircleAlphaAnimator = ObjectAnimator.ofFloat(mCheckCircle, "alpha", 0f, 1f);

        mCheckCircle.setImageDrawable(mAppContext.getDrawable(R.drawable.check_circle_blue));

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(paddingAnimator, checkCircleAlphaAnimator);
        animatorSet.setDuration(200);
        animatorSet.start();

        mCheckCircle.setVisibility(View.VISIBLE);

        mIsSelected = true;
    }

    @Override
    public void animateDeselected() {
        mLoadingIndicatorContainer.setVisibility(View.GONE);

        // If already deselected, do nothing.
        if (!mIsSelected) {
            return;
        }

        int[][] values = new int[2][4];
        int insetPx = mAppContext.getResources().getDimensionPixelSize(
                R.dimen.grid_item_individual_wallpaper_selected_inset);
        values[0] = new int[]{insetPx, insetPx, insetPx, insetPx};
        values[1] = new int[]{0, 0, 0, 0};

        ObjectAnimator paddingAnimator = ObjectAnimator.ofMultiInt(mTile, "padding", values);
        ObjectAnimator checkCircleAlphaAnimator = ObjectAnimator.ofFloat(mCheckCircle, "alpha", 1f, 0f);

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(paddingAnimator, checkCircleAlphaAnimator);
        animatorSet.setDuration(200);
        animatorSet.start();

        checkCircleAlphaAnimator.addListener(new AnimatorListener() {
            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationEnd(Animator animation) {
                mCheckCircle.setVisibility(View.GONE);
            }

            @Override
            public void onAnimationCancel(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });

        mIsSelected = false;
    }

    @Override
    public void showLoading() {
        mLoadingIndicatorContainer.setVisibility(View.VISIBLE);
    }

    @Override
    public void showNotLoading() {
        mLoadingIndicatorContainer.setVisibility(View.GONE);
    }

    private void animateHoverEnter() {
        removeHoverHandlerCallbacks();

        if (mIsHovered) {
            return;
        }

        mHoverHandler.postDelayed(mHoverEnterRunnable, HOVER_TIMEOUT_MS);
    }

    private void animateHoverExit() {
        removeHoverHandlerCallbacks();

        if (!mIsHovered) {
            return;
        }

        mHoverHandler.postDelayed(mHoverExitRunnable, HOVER_TIMEOUT_MS);
    }

    @Override
    public boolean isSelected() {
        return mIsSelected;
    }

    private void removeHoverHandlerCallbacks() {
        mHoverHandler.removeCallbacks(mHoverEnterRunnable);
        mHoverHandler.removeCallbacks(mHoverExitRunnable);
    }
}
