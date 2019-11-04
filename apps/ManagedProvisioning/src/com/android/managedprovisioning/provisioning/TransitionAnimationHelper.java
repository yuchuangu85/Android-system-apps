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
package com.android.managedprovisioning.provisioning;

import static com.android.internal.util.Preconditions.checkNotNull;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_FULLY_MANAGED_DEVICE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE;
import static com.android.managedprovisioning.provisioning.ProvisioningActivity.PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE;

import android.annotation.DrawableRes;
import android.annotation.StringRes;
import android.content.Context;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.CrossFadeHelper;
import com.android.managedprovisioning.common.CrossFadeHelper.Callback;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.provisioning.ProvisioningActivity.ProvisioningMode;
import java.util.Arrays;
import java.util.List;

/**
 * Handles the animated transitions in the education screens. Transitions consist of cross fade
 * animations between different headers and banner images.
 */
class TransitionAnimationHelper {

    interface TransitionAnimationCallback {
        void onAllTransitionsShown();
    }

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_WRAPPER
            = new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_1_header,
                R.drawable.separate_work_and_personal_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_2_header,
                R.drawable.pause_work_apps_animation),
        new TransitionScreenWrapper(R.string.work_profile_provisioning_step_3_header,
                R.drawable.not_private_animation)
    }, R.string.work_profile_provisioning_summary);

    @VisibleForTesting
    static final ProvisioningModeWrapper FULLY_MANAGED_DEVICE_WRAPPER
            = new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_1_header,
                R.drawable.connect_on_the_go_animation),
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_2_header,
                R.drawable.not_private_animation,
                R.string.fully_managed_device_provisioning_step_2_subheader,
                /* showContactAdmin */ true)
    }, R.string.fully_managed_device_provisioning_summary);

    @VisibleForTesting
    static final ProvisioningModeWrapper WORK_PROFILE_ON_FULLY_MANAGED_DEVICE_WRAPPER
            = new ProvisioningModeWrapper(new TransitionScreenWrapper[] {
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_1_header,
                R.drawable.connect_on_the_go_animation),
        new TransitionScreenWrapper(R.string.fully_managed_device_provisioning_step_2_header,
                R.drawable.not_private_animation,
                R.string.fully_managed_device_provisioning_step_2_subheader,
                /* showContactAdmin */ true)
    }, R.string.fully_managed_device_provisioning_summary);

    private static final int TRANSITION_TIME_MILLIS = 5000;
    private static final int CROSSFADE_ANIMATION_DURATION_MILLIS = 500;

    private final CrossFadeHelper mCrossFadeHelper;
    private final AnimationComponents mAnimationComponents;
    private final Runnable mStartNextTransitionRunnable = this::startNextAnimation;
    private final boolean mShowAnimations;
    private TransitionAnimationCallback mCallback;
    private final ProvisioningModeWrapper mProvisioningModeWrapper;

    private Handler mUiThreadHandler = new Handler(Looper.getMainLooper());
    private int mCurrentTransitionIndex;
    private RepeatingVectorAnimation mRepeatingVectorAnimation;

    TransitionAnimationHelper(@ProvisioningMode int provisioningMode,
            AnimationComponents animationComponents, TransitionAnimationCallback callback) {
        mAnimationComponents = checkNotNull(animationComponents);
        mCallback = checkNotNull(callback);
        mProvisioningModeWrapper = getProvisioningModeWrapper(provisioningMode);
        mCrossFadeHelper = getCrossFadeHelper();
        mShowAnimations = shouldShowAnimations();

        applyContentDescription();
        updateUiValues(mCurrentTransitionIndex);
    }

    boolean areAllTransitionsShown() {
        return mCurrentTransitionIndex == mProvisioningModeWrapper.transitions.length - 1;
    }

    void start() {
        mUiThreadHandler.postDelayed(mStartNextTransitionRunnable, TRANSITION_TIME_MILLIS);
        updateUiValues(mCurrentTransitionIndex);
        startCurrentAnimatedDrawable();
    }

    void clean() {
        stopCurrentAnimatedDrawable();
        mCrossFadeHelper.cleanup();
        mUiThreadHandler.removeCallbacksAndMessages(null);
        mUiThreadHandler = null;
        mCallback = null;
    }

    @VisibleForTesting
    CrossFadeHelper getCrossFadeHelper() {
        return new CrossFadeHelper(
            mAnimationComponents.asList(),
            CROSSFADE_ANIMATION_DURATION_MILLIS,
            new Callback() {
                @Override
                public void fadeOutCompleted() {
                    stopCurrentAnimatedDrawable();
                    mCurrentTransitionIndex++;
                    updateUiValues(mCurrentTransitionIndex);
                    startCurrentAnimatedDrawable();
                }

                @Override
                public void fadeInCompleted() {
                    mUiThreadHandler.postDelayed(
                        mStartNextTransitionRunnable, TRANSITION_TIME_MILLIS);
                }
            });
    }

    @VisibleForTesting
    void startNextAnimation() {
        if (mCurrentTransitionIndex >= mProvisioningModeWrapper.transitions.length-1) {
            if (mCallback != null) {
                mCallback.onAllTransitionsShown();
            }
            return;
        }
        mCrossFadeHelper.start();
    }

    @VisibleForTesting
    void startCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        if (!(mAnimationComponents.image.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        final AnimatedVectorDrawable vectorDrawable =
            (AnimatedVectorDrawable) mAnimationComponents.image.getDrawable();
        mRepeatingVectorAnimation = new RepeatingVectorAnimation(vectorDrawable);
        mRepeatingVectorAnimation.start();
    }

    @VisibleForTesting
    void stopCurrentAnimatedDrawable() {
        if (!mShowAnimations) {
            return;
        }
        if (!(mAnimationComponents.image.getDrawable() instanceof AnimatedVectorDrawable)) {
            return;
        }
        mRepeatingVectorAnimation.stop();
    }

    @VisibleForTesting
    void updateUiValues(int currentTransitionIndex) {
        final TransitionScreenWrapper[] transitions = mProvisioningModeWrapper.transitions;
        final TransitionScreenWrapper transition =
                transitions[currentTransitionIndex % transitions.length];

        mAnimationComponents.header.setText(transition.header);

        final ImageView image = mAnimationComponents.image;
        if (mShowAnimations) {
            image.setImageResource(transition.drawable);
        } else {
            image.setVisibility(View.GONE);
        }

        final TextView subHeader = mAnimationComponents.subHeader;
        if (transition.subHeader != 0) {
            subHeader.setVisibility(View.VISIBLE);
            subHeader.setText(transition.subHeader);
        } else {
            subHeader.setVisibility(View.INVISIBLE);
        }

        final TextView providerInfo = mAnimationComponents.providerInfo;
        if (transition.showContactAdmin) {
            providerInfo.setVisibility(View.VISIBLE);
        } else {
            providerInfo.setVisibility(View.INVISIBLE);
        }
    }

    @VisibleForTesting
    ProvisioningModeWrapper getProvisioningModeWrapper(
            @ProvisioningMode int provisioningMode) {
        switch (provisioningMode) {
            case PROVISIONING_MODE_WORK_PROFILE:
                return WORK_PROFILE_WRAPPER;
            case PROVISIONING_MODE_FULLY_MANAGED_DEVICE:
                return FULLY_MANAGED_DEVICE_WRAPPER;
            case PROVISIONING_MODE_WORK_PROFILE_ON_FULLY_MANAGED_DEVICE:
                return WORK_PROFILE_ON_FULLY_MANAGED_DEVICE_WRAPPER;
        }
        throw new IllegalStateException("Unexpected provisioning mode " + provisioningMode);
    }

    private boolean shouldShowAnimations() {
        final Context context = mAnimationComponents.header.getContext();
        return context.getResources().getBoolean(R.bool.show_edu_animations);
    }

    private void applyContentDescription() {
        final TextView header = mAnimationComponents.header;
        final Context context = header.getContext();
        header.setContentDescription(context.getString(mProvisioningModeWrapper.summary));
    }

    private static final class TransitionScreenWrapper {
        final @StringRes int header;
        final @DrawableRes int drawable;
        final @StringRes int subHeader;
        final boolean showContactAdmin;

        TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable) {
            this(header, drawable, /* subHeader */ 0, /* showContactAdmin */ false);
        }

        TransitionScreenWrapper(@StringRes int header, @DrawableRes int drawable,
                @StringRes int subHeader, boolean showContactAdmin) {
            this.header = checkNotNull(header,
                    "Header resource id must be a positive number.");
            this.drawable = checkNotNull(drawable,
                    "Drawable resource id must be a positive number.");
            this.subHeader = subHeader;
            this.showContactAdmin = showContactAdmin;
        }
    }

    private static final class ProvisioningModeWrapper {
        final TransitionScreenWrapper[] transitions;
        final @StringRes int summary;

        ProvisioningModeWrapper(TransitionScreenWrapper[] transitions, @StringRes int summary) {
            this.transitions = checkNotNull(transitions);
            this.summary = summary;
        }
    }

    static final class AnimationComponents {
        private final TextView header;
        private final TextView subHeader;
        private final ImageView image;
        private final TextView providerInfo;

        AnimationComponents(
                TextView header, TextView subHeader, ImageView image, TextView providerInfo) {
            this.header = checkNotNull(header);
            this.subHeader = checkNotNull(subHeader);
            this.image = checkNotNull(image);
            this.providerInfo = checkNotNull(providerInfo);
        }

        List<View> asList() {
            return Arrays.asList(header, subHeader, image, providerInfo);
        }
    }
}
