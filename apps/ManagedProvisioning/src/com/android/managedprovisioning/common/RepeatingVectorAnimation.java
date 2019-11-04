/*
 * Copyright 2019, The Android Open Source Project
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
package com.android.managedprovisioning.common;

import static com.android.internal.util.Preconditions.checkNotNull;

import android.graphics.drawable.Animatable2;
import android.graphics.drawable.AnimatedVectorDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;

/**
 * A repeating {@link AnimatedVectorDrawable} animation.
 */
public class RepeatingVectorAnimation {
    /** Repeats the animation once it is done **/
    private final Animatable2.AnimationCallback mAnimationCallback =
        new Animatable2.AnimationCallback() {
            @Override
            public void onAnimationEnd(Drawable drawable) {
                mUiThreadHandler.post(mAnimatedVectorDrawable::start);
            }
        };

    private final Handler mUiThreadHandler = new Handler();
    private final AnimatedVectorDrawable mAnimatedVectorDrawable;

    public RepeatingVectorAnimation(AnimatedVectorDrawable animatedVectorDrawable) {
        mAnimatedVectorDrawable = checkNotNull(animatedVectorDrawable);
    }

    public void start() {
        // Unregister callback in case it was already registered. Otherwise we get multiple
        // calls of the same callback.
        mAnimatedVectorDrawable.unregisterAnimationCallback(mAnimationCallback);
        mAnimatedVectorDrawable.registerAnimationCallback(mAnimationCallback);
        mAnimatedVectorDrawable.reset();
        mAnimatedVectorDrawable.start();
    }

    public void stop() {
        mAnimatedVectorDrawable.stop();
        mAnimatedVectorDrawable.unregisterAnimationCallback(mAnimationCallback);
    }
}
