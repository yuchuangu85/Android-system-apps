/*
 * Copyright 2016, The Android Open Source Project
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
package com.android.managedprovisioning.preprovisioning.anim;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.animation.Animator;
import android.animation.AnimatorInflater;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.annotation.AnimRes;
import android.annotation.InterpolatorRes;
import android.app.Activity;
import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import androidx.annotation.NonNull;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup.LayoutParams;
import android.view.animation.AnimationUtils;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.model.CustomizationParams;

import java.util.ArrayList;
import java.util.List;

/**
 * <p>Drives the animation showing benefits of having a Managed Profile.
 * <p>Tightly coupled with the {@link R.layout#intro_animation} layout.
 * The animation consists of 4 horizontally scrolling text labels, placed horizontally.
 * Each label's horizontal translation is * <code>i*screenWidth/2</code>, where i is the
 * number of the label (0, 1, 2 or 3) and screenWidth is the actual screen width. We need to
 * calculate the views' widths dynamically because we want them to fit the display width.
 */
public class BenefitsAnimation {
    /** Array of Id pairs: {{@link ObjectAnimator}, {@link TextView}} */
    private static final int[][] ID_ANIMATION_TARGET = {
            {R.anim.text_scene_0_animation, R.id.text_0},
            {R.anim.text_scene_1_animation, R.id.text_1},
            {R.anim.text_scene_2_animation, R.id.text_2},
            {R.anim.text_scene_3_animation, R.id.text_3}};

    private static final int[] SLIDE_CAPTION_TEXT_VIEWS = {
            R.id.text_0, R.id.text_1, R.id.text_2, R.id.text_3};

    /** Id of an {@link ImageView} containing the animated graphic */
    private static final int ID_ANIMATED_GRAPHIC = R.id.animated_info;

    private static final int SLIDE_COUNT = 3;
    private static final int ANIMATION_ORIGINAL_WIDTH_PX = 1080;
    private static final int TRANSLATION_DURATION_MS = 1001;
    private static final float MASTER_CONTAINER_DISPLAY_WIDTH_MULTIPLIER = 2.5f;

    private final AnimatedVectorDrawable mTopAnimation;
    private Animator mTextAnimation;
    private final Activity mActivity;

    private boolean mStopped;

    /**
     * @param captions slide captions for the animation
     * @param contentDescription for accessibility
     */
    public BenefitsAnimation(@NonNull Activity activity, @NonNull List<Integer> captions,
            int contentDescription, CustomizationParams customizationParams) {
        if (captions.size() != SLIDE_COUNT) {
            throw new IllegalArgumentException(
                    "Wrong number of slide captions. Expected: " + SLIDE_COUNT);
        }
        mActivity = checkNotNull(activity);
        applySlideCaptions(captions);
        applyContentDescription(contentDescription);

        setTopInfoDrawable(customizationParams);

        mTopAnimation = checkNotNull(extractAnimationFromImageView(ID_ANIMATED_GRAPHIC));

        // once the screen is ready, adjust size
        mActivity.findViewById(android.R.id.content).post(this::adjustToScreenSize);
        mActivity.findViewById(android.R.id.content).post(this::prepareAnimations);
    }

    private void prepareAnimations() {
        mTextAnimation = checkNotNull(assembleTextAnimation());
        // chain all animations together
        chainAnimations();
    }

    private void setTopInfoDrawable(CustomizationParams customizationParams) {
        int swiperTheme = new SwiperThemeMatcher(mActivity, new ColorMatcher())
                .findTheme(customizationParams.mainColor);

        ContextThemeWrapper wrapper = new ContextThemeWrapper(mActivity, swiperTheme);
        Drawable drawable =
                mActivity.getResources().getDrawable(
                        R.drawable.topinfo_animation,
                        wrapper.getTheme());
        ImageView imageView = mActivity.findViewById(ID_ANIMATED_GRAPHIC);
        imageView.setImageDrawable(drawable);
    }

    /** Starts playing the animation in a loop. */
    public void start() {
        mStopped = false;
        mTopAnimation.start();
    }

    /** Stops the animation. */
    public void stop() {
        mStopped = true;
        mTopAnimation.stop();
    }

    /**
     * Adjust animation and text to match actual screen size
     */
    private void adjustToScreenSize() {
        if (mActivity.isDestroyed()) {
            return;
        }

        setupLabelDimensions();

        ImageView animatedInfo = mActivity.findViewById(R.id.animated_info);
        int widthPx = animatedInfo.getWidth();
        float scaleRatio = (float) widthPx / ANIMATION_ORIGINAL_WIDTH_PX;

        // adjust animation height; width happens automatically
        LayoutParams layoutParams = animatedInfo.getLayoutParams();
        int originalHeight = animatedInfo.getHeight();
        int adjustedHeight = (int) (originalHeight * scaleRatio);
        layoutParams.height = adjustedHeight;
        animatedInfo.setLayoutParams(layoutParams);

        // if the content is bigger than the screen, try to shrink just the animation
        int offset = adjustedHeight - originalHeight;
        int contentHeight = mActivity.findViewById(R.id.intro_po_content).getHeight() + offset;
        int viewportHeight = mActivity.findViewById(R.id.suc_layout_content).getHeight();
        if (contentHeight > viewportHeight) {
            int targetHeight = layoutParams.height - (contentHeight - viewportHeight);
            int minHeight = mActivity.getResources().getDimensionPixelSize(
                    R.dimen.intro_animation_min_height);

            // if the animation becomes too small, leave it as is and the scrollbar will show
            if (targetHeight >= minHeight) {
                layoutParams.height = targetHeight;
                animatedInfo.setLayoutParams(layoutParams);
            }
        }
    }

    private void setupLabelDimensions() {
        final ImageView animatedInfo = mActivity.findViewById(R.id.animated_info);
        final int width = animatedInfo.getWidth();
        final FrameLayout textAnimationViewport =
                mActivity.findViewById(R.id.text_animation_viewport);
        final LayoutParams viewportParams = textAnimationViewport.getLayoutParams();
        viewportParams.width = width;
        textAnimationViewport.setLayoutParams(viewportParams);

        final FrameLayout textMasterContainer = mActivity.findViewById(R.id.text_master);
        final LayoutParams masterContainerParams = textMasterContainer.getLayoutParams();
        masterContainerParams.width = (int) (width * MASTER_CONTAINER_DISPLAY_WIDTH_MULTIPLIER);
        textMasterContainer.setLayoutParams(masterContainerParams);

        int translation = 0;
        final int step = width / 2;
        for (int textViewId : SLIDE_CAPTION_TEXT_VIEWS) {
            final View textView = mActivity.findViewById(textViewId);
            final LayoutParams layoutParams = textView.getLayoutParams();
            layoutParams.width = width;
            textView.setLayoutParams(layoutParams);
            textView.setTranslationX(translation);
            translation += step;
        }
    }

    /**
     * <p>Chains all three sub-animations, and configures them to play in sync in a loop.
     * <p>Looping {@link AnimatedVectorDrawable} and {@link AnimatorSet} currently not possible in
     * XML.
     */
    private void chainAnimations() {
        mTopAnimation.registerAnimationCallback(new Animatable2.AnimationCallback() {
            @Override
            public void onAnimationStart(Drawable drawable) {
                super.onAnimationStart(drawable);

                // starting the other animations at the same time
                mTextAnimation.start();
            }

            @Override
            public void onAnimationEnd(Drawable drawable) {
                super.onAnimationEnd(drawable);

                // without explicitly stopping them, sometimes they won't restart
                mTextAnimation.cancel();

                // repeating the animation in loop
                if (!mStopped) {
                    mTopAnimation.start();
                }
            }
        });
    }

    /**
     * <p>Inflates animators required to animate text headers' part of the whole animation.
     * <p>This has to be done through code, as setting a target on {@link
     * android.animation.ObjectAnimator} is not currently possible in XML.
     *
     * @return {@link AnimatorSet} responsible for the animated text
     */
    private AnimatorSet assembleTextAnimation() {
        Animator[] animators = new Animator[ID_ANIMATION_TARGET.length + 1];
        for (int i = 0; i < ID_ANIMATION_TARGET.length; i++) {
            int[] instance = ID_ANIMATION_TARGET[i];
            animators[i] = AnimatorInflater.loadAnimator(mActivity, instance[0]);
            animators[i].setTarget(mActivity.findViewById(instance[1]));
        }
        animators[ID_ANIMATION_TARGET.length] = getTranslationAnimatorSet();

        AnimatorSet animatorSet = new AnimatorSet();
        animatorSet.playTogether(animators);
        return animatorSet;
    }

    /**
     * Creates and returns a sequential horizontal translation animation of the benefits labels.
     */
    private AnimatorSet getTranslationAnimatorSet() {
        ImageView animatedInfo = mActivity.findViewById(R.id.animated_info);
        final int width = animatedInfo.getWidth();
        AnimatorSet translationSet = new AnimatorSet();
        View textContainer = mActivity.findViewById(R.id.text_master);
        List<Animator> animators = new ArrayList<>();
        animators.add(getTranslationAnimator(textContainer, 0, 0,
                android.R.interpolator.linear));
        animators.add(getTranslationAnimator(textContainer, 0, -width/2,
                android.R.interpolator.fast_out_slow_in));
        animators.add(getTranslationAnimator(textContainer, -width/2, -width/2,
                android.R.interpolator.linear));
        animators.add(getTranslationAnimator(textContainer, -width/2, -width,
                android.R.interpolator.fast_out_slow_in));
        animators.add(getTranslationAnimator(textContainer, -width, -width,
                android.R.interpolator.linear));
        animators.add(getTranslationAnimator(textContainer, -width, (int) (-width * 1.5),
                android.R.interpolator.fast_out_slow_in));
        translationSet.playSequentially(animators);
        return translationSet;
    }

    private Animator getTranslationAnimator(View container, float from, float to,
            @AnimRes @InterpolatorRes int interpolator) {
        ObjectAnimator animator = ObjectAnimator.ofFloat(container, "translationX", from, to);
        animator.setDuration(TRANSLATION_DURATION_MS);
        animator.setInterpolator(AnimationUtils.loadInterpolator(mActivity, interpolator));
        return animator;
    }

    /**
     * @param captions slide titles
     */
    private void applySlideCaptions(List<Integer> captions) {
        int slideIx = 0;
        for (int viewId : SLIDE_CAPTION_TEXT_VIEWS) {
            ((TextView) mActivity.findViewById(viewId)).setText(
                    captions.get(slideIx++ % captions.size()));
        }
    }

    private void applyContentDescription(int contentDescription) {
        mActivity.findViewById(R.id.animation_top_level_frame).setContentDescription(
                mActivity.getString(contentDescription));
    }

    /** Extracts an {@link AnimatedVectorDrawable} from a containing {@link ImageView}. */
    private AnimatedVectorDrawable extractAnimationFromImageView(int id) {
        ImageView imageView = mActivity.findViewById(id);
        return (AnimatedVectorDrawable) imageView.getDrawable();
    }
}