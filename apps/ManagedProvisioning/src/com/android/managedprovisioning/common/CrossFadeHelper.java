/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.AnimatorSet;
import android.animation.ValueAnimator;
import android.animation.ValueAnimator.AnimatorUpdateListener;
import android.annotation.TargetApi;
import android.view.View;
import java.util.ArrayList;
import java.util.List;

/**
 * Performs fade out-fade in animations on a {@link View} list.
 */
public class CrossFadeHelper {
    public interface Callback {
        void fadeOutCompleted();
        void fadeInCompleted();
    }

    private final List<View> mViews;
    private final int mDuration;
    private final Callback mCallback;
    private final ValueAnimator mFadeOutAnimator;
    private final ValueAnimator mFadeInAnimator;
    private final AnimatorSet mAnimatorSet;

    private final AnimatorUpdateListener mAnimatorUpdateListener = new AnimatorUpdateListener() {
        @Override
        public void onAnimationUpdate(ValueAnimator animation) {
            final float alpha = (float) animation.getAnimatedValue();
            for (View view : mViews) {
                view.setAlpha(alpha);
            }
        }
    };

    public CrossFadeHelper(List<View> viewsToAnimate, int duration, Callback callback) {
        mViews = new ArrayList<>(viewsToAnimate);
        mDuration = duration;
        mCallback = callback;
        mFadeOutAnimator = getFadeOutAnimator();
        mFadeInAnimator = getFadeInAnimator();
        mAnimatorSet = new AnimatorSet();
        mAnimatorSet.playSequentially(mFadeOutAnimator, mFadeInAnimator);
    }

    public void start() {
        mAnimatorSet.start();
    }

    private ValueAnimator getFadeInAnimator() {
        final ValueAnimator fadeInAnimator = ValueAnimator.ofFloat(0f, 1f);
        fadeInAnimator.addUpdateListener(mAnimatorUpdateListener);
        fadeInAnimator.setDuration(mDuration);
        fadeInAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCallback != null) {
                    mCallback.fadeInCompleted();
                }
            }
        });
        return fadeInAnimator;
    }

    private ValueAnimator getFadeOutAnimator() {
        final ValueAnimator fadeOutAnimator = ValueAnimator.ofFloat(1f, 0f);
        fadeOutAnimator.addUpdateListener(mAnimatorUpdateListener);
        fadeOutAnimator.setDuration(mDuration);
        fadeOutAnimator.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                if (mCallback != null) {
                    mCallback.fadeOutCompleted();
                }
            }
        });
        return fadeOutAnimator;
    }

    public void cleanup() {
        mFadeInAnimator.removeAllUpdateListeners();
        mFadeInAnimator.removeAllListeners();
        mFadeOutAnimator.removeAllUpdateListeners();
        mFadeOutAnimator.removeAllListeners();
        mAnimatorSet.cancel();
        mViews.clear();
    }
}
