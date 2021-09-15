/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock

import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TypeEvaluator
import android.animation.ValueAnimator
import android.graphics.Rect
import android.graphics.drawable.Animatable
import android.graphics.drawable.Drawable
import android.graphics.drawable.LayerDrawable
import android.util.Property
import android.view.View
import android.view.animation.Interpolator
import android.widget.ImageView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

import kotlin.math.roundToLong

object AnimatorUtils {
    @JvmField
    val DECELERATE_ACCELERATE_INTERPOLATOR =
            Interpolator { x -> 0.5f + 4.0f * (x - 0.5f) * (x - 0.5f) * (x - 0.5f) }

    @JvmField
    val INTERPOLATOR_FAST_OUT_SLOW_IN: Interpolator = FastOutSlowInInterpolator()

    @JvmField
    val BACKGROUND_ALPHA: Property<View, Int> =
            object : Property<View, Int>(Int::class.java, "background.alpha") {
        override fun get(view: View): Int {
            var background = view.background
            if (background is LayerDrawable &&
                    background.numberOfLayers > 0) {
                background = background.getDrawable(0)
            }
            return background.alpha
        }

        override fun set(view: View, value: Int) {
            setBackgroundAlpha(view, value)
        }
    }

    /**
     * Sets the alpha of the top layer's drawable (of the background) only, if the background is a
     * layer drawable, to ensure that the other layers (i.e., the selectable item background, and
     * therefore the touch feedback RippleDrawable) are not affected.
     *
     * @param view the affected view
     * @param value the alpha value (0-255)
     */
    @JvmStatic
    fun setBackgroundAlpha(view: View, value: Int?) {
        var background = view.background
        if (background is LayerDrawable &&
                background.numberOfLayers > 0) {
            background = background.getDrawable(0)
        }
        background.alpha = value!!
    }

    @JvmField
    val DRAWABLE_ALPHA: Property<ImageView, Int> =
            object : Property<ImageView, Int>(Int::class.java, "drawable.alpha") {
        override fun get(view: ImageView): Int {
            return view.drawable.alpha
        }

        override fun set(view: ImageView, value: Int) {
            view.drawable.alpha = value
        }
    }

    @JvmField
    val DRAWABLE_TINT: Property<ImageView, Int> =
            object : Property<ImageView, Int>(Int::class.java, "drawable.tint") {
        override fun get(view: ImageView): Int? {
            return null
        }

        override fun set(view: ImageView, value: Int) {
            // Ensure the drawable is wrapped using DrawableCompat.
            val drawable = view.drawable
            val wrappedDrawable: Drawable = DrawableCompat.wrap(drawable)
            if (wrappedDrawable !== drawable) {
                view.setImageDrawable(wrappedDrawable)
            }
            // Set the new tint value via DrawableCompat.
            DrawableCompat.setTint(wrappedDrawable, value)
        }
    }

    @JvmField
    val ARGB_EVALUATOR: TypeEvaluator<Int> = ArgbEvaluator() as TypeEvaluator<Int>

    private var sAnimateValue: Method? = null

    private var sTryAnimateValue = true

    @JvmStatic
    fun setAnimatedFraction(animator: ValueAnimator, fraction: Float) {
        if (Utils.isLMR1OrLater) {
            animator.setCurrentFraction(fraction)
            return
        }

        if (sTryAnimateValue) {
            // try to set the animated fraction directly so that it isn't affected by the
            // internal animator scale or time (b/17938711)
            try {
                if (sAnimateValue == null) {
                    sAnimateValue = ValueAnimator::class.java
                            .getDeclaredMethod("animateValue", Float::class.javaPrimitiveType)
                    sAnimateValue!!.isAccessible = true
                }

                sAnimateValue!!.invoke(animator, fraction)
                return
            } catch (e: NoSuchMethodException) {
                // something went wrong, don't try that again
                LogUtils.e("Unable to use animateValue directly", e)
                sTryAnimateValue = false
            } catch (e: InvocationTargetException) {
                LogUtils.e("Unable to use animateValue directly", e)
                sTryAnimateValue = false
            } catch (e: IllegalAccessException) {
                LogUtils.e("Unable to use animateValue directly", e)
                sTryAnimateValue = false
            }
        }

        // if that doesn't work then just fall back to setting the current play time
        animator.currentPlayTime = (fraction * animator.duration).roundToLong()
    }

    @JvmStatic
    fun reverse(vararg animators: ValueAnimator) {
        for (animator in animators) {
            val fraction = animator.animatedFraction
            if (fraction > 0.0f) {
                animator.reverse()
                setAnimatedFraction(animator, 1.0f - fraction)
            }
        }
    }

    fun cancel(vararg animators: ValueAnimator) {
        for (animator in animators) {
            animator.cancel()
        }
    }

    @JvmStatic
    fun getScaleAnimator(view: View?, vararg values: Float): ValueAnimator {
        return ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofFloat(View.SCALE_X, *values),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, *values))
    }

    @JvmStatic
    fun getAlphaAnimator(view: View, vararg values: Float): ValueAnimator {
        return ObjectAnimator.ofFloat(view, View.ALPHA, *values)
    }

    val VIEW_LEFT: Property<View, Int> = object : Property<View, Int>(Int::class.java, "left") {
        override fun get(view: View): Int {
            return view.left
        }

        override fun set(view: View, left: Int) {
            view.left = left
        }
    }

    val VIEW_TOP: Property<View, Int> = object : Property<View, Int>(Int::class.java, "top") {
        override fun get(view: View): Int {
            return view.top
        }

        override fun set(view: View, top: Int) {
            view.top = top
        }
    }

    val VIEW_BOTTOM: Property<View, Int> = object : Property<View, Int>(Int::class.java, "bottom") {
        override fun get(view: View): Int {
            return view.bottom
        }

        override fun set(view: View, bottom: Int) {
            view.bottom = bottom
        }
    }

    val VIEW_RIGHT: Property<View, Int> = object : Property<View, Int>(Int::class.java, "right") {
        override fun get(view: View): Int {
            return view.right
        }

        override fun set(view: View, right: Int) {
            view.right = right
        }
    }

    /**
     * @param target the view to be morphed
     * @param from the bounds of the `target` before animating
     * @param to the bounds of the `target` after animating
     * @return an animator that morphs the `target` between the `from` bounds and the
     * `to` bounds. Note that it is the *content* bounds that matter here, so padding
     * insets contributed by the background are subtracted from the views when computing the
     * `target` bounds.
     */
    fun getBoundsAnimator(target: View, from: View, to: View): Animator {
        // Fetch the content insets for the views. Content bounds are what matter, not total bounds.
        val targetInsets = Rect()
        target.background.getPadding(targetInsets)
        val fromInsets = Rect()
        from.background.getPadding(fromInsets)
        val toInsets = Rect()
        to.background.getPadding(toInsets)

        // Before animating, the content bounds of target must match the content bounds of from.
        val startLeft = from.left - fromInsets.left + targetInsets.left
        val startTop = from.top - fromInsets.top + targetInsets.top
        val startRight = from.right - fromInsets.right + targetInsets.right
        val startBottom = from.bottom - fromInsets.bottom + targetInsets.bottom

        // After animating, the content bounds of target must match the content bounds of to.
        val endLeft = to.left - toInsets.left + targetInsets.left
        val endTop = to.top - toInsets.top + targetInsets.top
        val endRight = to.right - toInsets.right + targetInsets.right
        val endBottom = to.bottom - toInsets.bottom + targetInsets.bottom

        return getBoundsAnimator(target, startLeft, startTop, startRight, startBottom, endLeft,
                endTop, endRight, endBottom)
    }

    /**
     * Returns an animator that animates the bounds of a single view.
     */
    @JvmStatic
    fun getBoundsAnimator(
        view: View,
        fromLeft: Int,
        fromTop: Int,
        fromRight: Int,
        fromBottom: Int,
        toLeft: Int,
        toTop: Int,
        toRight: Int,
        toBottom: Int
    ): Animator {
        view.left = fromLeft
        view.top = fromTop
        view.right = fromRight
        view.bottom = fromBottom

        return ObjectAnimator.ofPropertyValuesHolder(view,
                PropertyValuesHolder.ofInt(VIEW_LEFT, toLeft),
                PropertyValuesHolder.ofInt(VIEW_TOP, toTop),
                PropertyValuesHolder.ofInt(VIEW_RIGHT, toRight),
                PropertyValuesHolder.ofInt(VIEW_BOTTOM, toBottom))
    }

    @JvmStatic
    fun startDrawableAnimation(view: ImageView) {
        val d = view.drawable
        if (d is Animatable) {
            d.start()
        }
    }
}