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
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.view.View
import androidx.collection.ArrayMap
import androidx.recyclerview.widget.RecyclerView.State
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.RecyclerView.ItemAnimator
import androidx.recyclerview.widget.SimpleItemAnimator

class ItemAnimator : SimpleItemAnimator() {
    private val mAddAnimatorsList: MutableList<Animator> = ArrayList()
    private val mRemoveAnimatorsList: MutableList<Animator> = ArrayList()
    private val mChangeAnimatorsList: MutableList<Animator> = ArrayList()
    private val mMoveAnimatorsList: MutableList<Animator> = ArrayList()

    private val mAnimators: MutableMap<ViewHolder, Animator> = ArrayMap()

    override fun animateRemove(holder: ViewHolder): Boolean {
        endAnimation(holder)

        val prevAlpha: Float = holder.itemView.getAlpha()

        val removeAnimator: Animator? = ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, 0f)
        removeAnimator!!.duration = getRemoveDuration()
        removeAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator) {
                dispatchRemoveStarting(holder)
            }

            override fun onAnimationEnd(animator: Animator) {
                animator.removeAllListeners()
                mAnimators.remove(holder)
                holder.itemView.setAlpha(prevAlpha)
                dispatchRemoveFinished(holder)
            }
        })
        mRemoveAnimatorsList.add(removeAnimator)
        mAnimators[holder] = removeAnimator
        return true
    }

    override fun animateAdd(holder: ViewHolder): Boolean {
        endAnimation(holder)

        val prevAlpha: Float = holder.itemView.getAlpha()
        holder.itemView.setAlpha(0f)

        val addAnimator: Animator = ObjectAnimator.ofFloat(holder.itemView, View.ALPHA, 1f)
                .setDuration(getAddDuration())
        addAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator) {
                dispatchAddStarting(holder)
            }

            override fun onAnimationEnd(animator: Animator) {
                animator.removeAllListeners()
                mAnimators.remove(holder)
                holder.itemView.setAlpha(prevAlpha)
                dispatchAddFinished(holder)
            }
        })
        mAddAnimatorsList.add(addAnimator)
        mAnimators[holder] = addAnimator
        return true
    }

    override fun animateMove(
        holder: ViewHolder,
        fromX: Int,
        fromY: Int,
        toX: Int,
        toY: Int
    ): Boolean {
        endAnimation(holder)

        val deltaX = toX - fromX
        val deltaY = toY - fromY
        val moveDuration: Long = getMoveDuration()

        if (deltaX == 0 && deltaY == 0) {
            dispatchMoveFinished(holder)
            return false
        }

        val view: View = holder.itemView
        val prevTranslationX = view.translationX
        val prevTranslationY = view.translationY
        view.translationX = -deltaX.toFloat()
        view.translationY = -deltaY.toFloat()

        val moveAnimator: ObjectAnimator?
        if (deltaX != 0 && deltaY != 0) {
            val moveX = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f)
            val moveY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f)
            moveAnimator = ObjectAnimator.ofPropertyValuesHolder(holder.itemView, moveX, moveY)
        } else if (deltaX != 0) {
            val moveX = PropertyValuesHolder.ofFloat(View.TRANSLATION_X, 0f)
            moveAnimator = ObjectAnimator.ofPropertyValuesHolder(holder.itemView, moveX)
        } else {
            val moveY = PropertyValuesHolder.ofFloat(View.TRANSLATION_Y, 0f)
            moveAnimator = ObjectAnimator.ofPropertyValuesHolder(holder.itemView, moveY)
        }

        moveAnimator?.duration = moveDuration
        moveAnimator.interpolator = AnimatorUtils.INTERPOLATOR_FAST_OUT_SLOW_IN
        moveAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator?) {
                dispatchMoveStarting(holder)
            }

            override fun onAnimationEnd(animator: Animator?) {
                animator?.removeAllListeners()
                mAnimators.remove(holder)
                view.translationX = prevTranslationX
                view.translationY = prevTranslationY
                dispatchMoveFinished(holder)
            }
        })
        mMoveAnimatorsList.add(moveAnimator)
        mAnimators[holder] = moveAnimator

        return true
    }

    override fun animateChange(
        oldHolder: ViewHolder,
        newHolder: ViewHolder,
        preInfo: ItemHolderInfo,
        postInfo: ItemHolderInfo
    ): Boolean {
        endAnimation(oldHolder)
        endAnimation(newHolder)

        val changeDuration: Long = getChangeDuration()
        val payloads = if (preInfo is PayloadItemHolderInfo) preInfo.payloads else null

        if (oldHolder === newHolder) {
            val animator = (newHolder as OnAnimateChangeListener)
                    .onAnimateChange(payloads, preInfo.left, preInfo.top, preInfo.right,
                            preInfo.bottom, changeDuration)
            if (animator == null) {
                dispatchChangeFinished(newHolder, false)
                return false
            }
            animator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator) {
                    dispatchChangeStarting(newHolder, false)
                }

                override fun onAnimationEnd(animator: Animator) {
                    animator.removeAllListeners()
                    mAnimators.remove(newHolder)
                    dispatchChangeFinished(newHolder, false)
                }
            })
            mChangeAnimatorsList.add(animator)
            mAnimators[newHolder] = animator
            return true
        } else if (oldHolder !is OnAnimateChangeListener ||
                newHolder !is OnAnimateChangeListener) {
            // Both holders must implement OnAnimateChangeListener in order to animate.
            dispatchChangeFinished(oldHolder, true)
            dispatchChangeFinished(newHolder, true)
            return false
        }

        val oldChangeAnimator = (oldHolder as OnAnimateChangeListener)
                .onAnimateChange(oldHolder, newHolder, changeDuration)
        if (oldChangeAnimator != null) {
            oldChangeAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator) {
                    dispatchChangeStarting(oldHolder, true)
                }

                override fun onAnimationEnd(animator: Animator) {
                    animator.removeAllListeners()
                    mAnimators.remove(oldHolder)
                    dispatchChangeFinished(oldHolder, true)
                }
            })
            mAnimators[oldHolder] = oldChangeAnimator
            mChangeAnimatorsList.add(oldChangeAnimator)
        } else {
            dispatchChangeFinished(oldHolder, true)
        }

        val newChangeAnimator = (newHolder as OnAnimateChangeListener)
                .onAnimateChange(oldHolder, newHolder, changeDuration)
        if (newChangeAnimator != null) {
            newChangeAnimator.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animator: Animator) {
                    dispatchChangeStarting(newHolder, false)
                }

                override fun onAnimationEnd(animator: Animator) {
                    animator.removeAllListeners()
                    mAnimators.remove(newHolder)
                    dispatchChangeFinished(newHolder, false)
                }
            })
            mAnimators[newHolder] = newChangeAnimator
            mChangeAnimatorsList.add(newChangeAnimator)
        } else {
            dispatchChangeFinished(newHolder, false)
        }

        return true
    }

    override fun animateChange(
        oldHolder: ViewHolder,
        newHolder: ViewHolder,
        fromLeft: Int,
        fromTop: Int,
        toLeft: Int,
        toTop: Int
    ): Boolean {
        /* Unused */
        throw IllegalStateException("This method should not be used")
    }

    override fun runPendingAnimations() {
        val removeAnimatorSet = AnimatorSet()
        removeAnimatorSet.playTogether(mRemoveAnimatorsList)
        mRemoveAnimatorsList.clear()

        val addAnimatorSet = AnimatorSet()
        addAnimatorSet.playTogether(mAddAnimatorsList)
        mAddAnimatorsList.clear()

        val changeAnimatorSet = AnimatorSet()
        changeAnimatorSet.playTogether(mChangeAnimatorsList)
        mChangeAnimatorsList.clear()

        val moveAnimatorSet = AnimatorSet()
        moveAnimatorSet.playTogether(mMoveAnimatorsList)
        mMoveAnimatorsList.clear()

        val pendingAnimatorSet = AnimatorSet()
        pendingAnimatorSet.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator) {
                animator.removeAllListeners()
                dispatchFinishedWhenDone()
            }
        })
        // Required order: removes, then changes & moves simultaneously, then additions. There are
        // redundant edges because changes or moves may be empty, causing the removes to incorrectly
        // play immediately.
        pendingAnimatorSet.play(removeAnimatorSet).before(changeAnimatorSet)
        pendingAnimatorSet.play(removeAnimatorSet).before(moveAnimatorSet)
        pendingAnimatorSet.play(changeAnimatorSet).with(moveAnimatorSet)
        pendingAnimatorSet.play(addAnimatorSet).after(changeAnimatorSet)
        pendingAnimatorSet.play(addAnimatorSet).after(moveAnimatorSet)
        pendingAnimatorSet.start()
    }

    override fun endAnimation(holder: ViewHolder) {
        val animator = mAnimators[holder]

        mAnimators.remove(holder)
        mAddAnimatorsList.remove(animator)
        mRemoveAnimatorsList.remove(animator)
        mChangeAnimatorsList.remove(animator)
        mMoveAnimatorsList.remove(animator)

        animator?.end()
        dispatchFinishedWhenDone()
    }

    override fun endAnimations() {
        val animatorList: MutableList<Animator?> = ArrayList(mAnimators.values)
        for (animator in animatorList) {
            animator?.end()
        }
        dispatchFinishedWhenDone()
    }

    override fun isRunning(): Boolean = mAnimators.isNotEmpty()

    private fun dispatchFinishedWhenDone() {
        if (!isRunning()) {
            dispatchAnimationsFinished()
        }
    }

    override fun recordPreLayoutInformation(
        state: State,
        viewHolder: ViewHolder,
        @AdapterChanges changeFlags: Int,
        payloads: MutableList<Any>
    ): ItemAnimator.ItemHolderInfo {
        val itemHolderInfo: ItemHolderInfo =
                super.recordPreLayoutInformation(state, viewHolder, changeFlags, payloads)
        if (itemHolderInfo is PayloadItemHolderInfo) {
            itemHolderInfo.payloads = payloads
        }
        return itemHolderInfo
    }

    override fun obtainHolderInfo(): ItemAnimator.ItemHolderInfo {
        return PayloadItemHolderInfo()
    }

    override fun canReuseUpdatedViewHolder(
        viewHolder: ViewHolder,
        payloads: MutableList<Any?>
    ): Boolean {
        val defaultReusePolicy: Boolean = super.canReuseUpdatedViewHolder(viewHolder, payloads)
        // Whenever we have a payload, this is an in-place animation.
        return payloads.isNotEmpty() || defaultReusePolicy
    }

    private class PayloadItemHolderInfo : ItemHolderInfo() {
        private val mPayloads: MutableList<Any> = ArrayList()

        var payloads: MutableList<Any>
            get() = mPayloads
            set(payloads) {
                mPayloads.clear()
                mPayloads.addAll(payloads)
            }
    }

    interface OnAnimateChangeListener {
        fun onAnimateChange(oldHolder: ViewHolder, newHolder: ViewHolder, duration: Long): Animator?

        fun onAnimateChange(
            payloads: List<Any>?,
            fromLeft: Int,
            fromTop: Int,
            fromRight: Int,
            fromBottom: Int,
            duration: Long
        ): Animator?
    }
}