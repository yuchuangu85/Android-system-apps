/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.apps.common.util;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.NonNull;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

/**
 * Utility methods to operate over views.
 */
public class ViewUtils {
    /**
     * Hides a view using a fade-out animation
     *
     * @param view     {@link View} to be hidden
     * @param duration animation duration in milliseconds.
     */
    public static void hideViewAnimated(@NonNull View view, int duration) {
        // Cancel existing animation to avoid race condition
        // if show and hide are called at the same time
        view.animate().cancel();

        if (!view.isLaidOut()) {
            // If the view hasn't been displayed yet, just adjust visibility without animation
            view.setVisibility(View.GONE);
            return;
        }

        view.animate()
                .setDuration(duration)
                .setListener(hideViewAfterAnimation(view))
                .alpha(0f);
    }

    /** Returns an AnimatorListener that hides the view at the end. */
    public static Animator.AnimatorListener hideViewAfterAnimation(View view) {
        return new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                view.setVisibility(View.GONE);
            }
        };
    }

    /**
     * Shows a view using a fade-in animation
     *
     * @param view     {@link View} to be shown
     * @param duration animation duration in milliseconds.
     */
    public static void showViewAnimated(@NonNull View view, int duration) {
        // Cancel existing animation to avoid race condition
        // if show and hide are called at the same time
        view.animate().cancel();

        // Do the animation even if the view isn't laid out which is often the case for a view
        // that isn't shown (otherwise the view just pops onto the screen...

        view.animate()
                .setDuration(duration)
                .setListener(new AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationStart(Animator animation) {
                        view.setVisibility(View.VISIBLE);
                    }
                })
                .alpha(1f);
    }

    /** Sets the visibility of the (optional) view to {@link View#VISIBLE} or {@link View#GONE}. */
    public static void setVisible(@Nullable View view, boolean visible) {
        if (view != null) {
            view.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    /**
     * Sets the visibility of the (optional) view to {@link View#INVISIBLE} or {@link View#VISIBLE}.
     */
    public static void setInvisible(@Nullable View view, boolean invisible) {
        if (view != null) {
            view.setVisibility(invisible ? View.INVISIBLE : View.VISIBLE);
        }
    }

    /** Sets the text to the (optional) {@link TextView}. */
    public static void setText(@Nullable TextView view, @StringRes int resId) {
        if (view != null) {
            view.setText(resId);
        }
    }

    /** Sets the text to the (optional) {@link TextView}. */
    public static void setText(@Nullable TextView view, CharSequence text) {
        if (view != null) {
            view.setText(text);
        }
    }
}
