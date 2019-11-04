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
package com.android.managedprovisioning.transition;

import android.graphics.drawable.Animatable2.AnimationCallback;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.widget.ImageView;
import com.android.managedprovisioning.R;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.RepeatingVectorAnimation;
import com.android.managedprovisioning.common.SetupGlifLayoutActivity;
import com.android.managedprovisioning.common.Utils;
import com.android.managedprovisioning.model.CustomizationParams;
import com.android.managedprovisioning.model.ProvisioningParams;

import com.google.android.setupdesign.GlifLayout;

/**
 * Activity which informs the user that they are about to set up their personal profile.
 */
public class TransitionActivity extends SetupGlifLayoutActivity {

    private ProvisioningParams mParams;
    private AnimatedVectorDrawable mIntroAnimation;
    private RepeatingVectorAnimation mRepeatingVectorAnimation;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mParams = getIntent().getParcelableExtra(ProvisioningParams.EXTRA_PROVISIONING_PARAMS);
        if (mParams == null) {
            ProvisionLogger.loge("Missing params in TransitionActivity activity");
            finish();
            return;
        }
        initializeUi();
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.start();
        } else {
            mIntroAnimation.start();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (mRepeatingVectorAnimation != null) {
            mRepeatingVectorAnimation.stop();
        }
        if (mIntroAnimation != null) {
            mIntroAnimation.stop();
        }
    }

    private void initializeUi() {
        CustomizationParams customizationParams =
            CustomizationParams.createInstance(mParams, this, mUtils);
        initializeLayoutParams(
                R.layout.transition_screen, R.string.now_lets_set_up_everything_else,
                customizationParams);
        setTitle(R.string.set_up_everything_else);

        final GlifLayout layout = findViewById(R.id.setup_wizard_layout);
        setupAnimation(layout);
        Utils.addNextButton(layout, v -> finish());
    }

    private void setupAnimation(GlifLayout layout) {
        final ImageView animationHolder = layout.findViewById(R.id.animation);
        final Drawable drawable = animationHolder.getDrawable();
        mIntroAnimation = (AnimatedVectorDrawable) drawable;
        mIntroAnimation.registerAnimationCallback(new AnimationCallback() {
            @Override
            public void onAnimationEnd(Drawable drawable) {
                animationHolder.setImageResource(R.drawable.everything_else_animation_repeating);
                final AnimatedVectorDrawable repeatingAnimation =
                        (AnimatedVectorDrawable) animationHolder.getDrawable();
                mRepeatingVectorAnimation = new RepeatingVectorAnimation(repeatingAnimation);
                mRepeatingVectorAnimation.start();
                mIntroAnimation = null;
            }
        });
        mIntroAnimation.start();
    }

    @Override
    public void onBackPressed() {}
}
