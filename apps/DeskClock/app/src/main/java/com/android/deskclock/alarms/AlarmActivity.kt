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

package com.android.deskclock.alarms

import android.accessibilityservice.AccessibilityServiceInfo
import android.accessibilityservice.AccessibilityServiceInfo.FEEDBACK_GENERIC
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.TimeInterpolator
import android.animation.ValueAnimator
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.content.pm.ActivityInfo
import android.graphics.Color
import android.graphics.Rect
import android.graphics.drawable.ColorDrawable
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.accessibility.AccessibilityManager
import android.widget.ImageView
import android.widget.TextClock
import android.widget.TextView
import androidx.core.graphics.ColorUtils
import androidx.core.view.animation.PathInterpolatorCompat

import com.android.deskclock.AnimatorUtils
import com.android.deskclock.BaseActivity
import com.android.deskclock.LogUtils
import com.android.deskclock.data.DataModel
import com.android.deskclock.data.DataModel.AlarmVolumeButtonBehavior
import com.android.deskclock.events.Events
import com.android.deskclock.provider.AlarmInstance
import com.android.deskclock.provider.ClockContract.InstancesColumns
import com.android.deskclock.R
import com.android.deskclock.ThemeUtils
import com.android.deskclock.Utils
import com.android.deskclock.widget.CircleView

import kotlin.math.max
import kotlin.math.sqrt

class AlarmActivity : BaseActivity(), View.OnClickListener, View.OnTouchListener {
    private val mHandler: Handler = Handler(Looper.myLooper()!!)

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent) {
            val action: String? = intent.getAction()
            LOGGER.v("Received broadcast: %s", action)

            if (!mAlarmHandled) {
                when (action) {
                    AlarmService.ALARM_SNOOZE_ACTION -> snooze()
                    AlarmService.ALARM_DISMISS_ACTION -> dismiss()
                    AlarmService.ALARM_DONE_ACTION -> finish()
                    else -> LOGGER.i("Unknown broadcast: %s", action)
                }
            } else {
                LOGGER.v("Ignored broadcast: %s", action)
            }
        }
    }

    private val mConnection: ServiceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            LOGGER.i("Finished binding to AlarmService")
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            LOGGER.i("Disconnected from AlarmService")
        }
    }

    private var mAlarmInstance: AlarmInstance? = null
    private var mAlarmHandled = false
    private var mVolumeBehavior: AlarmVolumeButtonBehavior? = null
    private var mCurrentHourColor = 0
    private var mReceiverRegistered = false
    /** Whether the AlarmService is currently bound  */
    private var mServiceBound = false

    private var mAccessibilityManager: AccessibilityManager? = null

    private lateinit var mAlertView: ViewGroup
    private lateinit var mAlertTitleView: TextView
    private lateinit var mAlertInfoView: TextView

    private lateinit var mContentView: ViewGroup
    private lateinit var mAlarmButton: ImageView
    private lateinit var mSnoozeButton: ImageView
    private lateinit var mDismissButton: ImageView
    private lateinit var mHintView: TextView

    private lateinit var mAlarmAnimator: ValueAnimator
    private lateinit var mSnoozeAnimator: ValueAnimator
    private lateinit var mDismissAnimator: ValueAnimator
    private lateinit var mPulseAnimator: ValueAnimator

    private var mInitialPointerIndex: Int = MotionEvent.INVALID_POINTER_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setVolumeControlStream(AudioManager.STREAM_ALARM)
        val instanceId = AlarmInstance.getId(getIntent().getData()!!)
        mAlarmInstance = AlarmInstance.getInstance(getContentResolver(), instanceId)
        if (mAlarmInstance == null) {
            // The alarm was deleted before the activity got created, so just finish()
            LOGGER.e("Error displaying alarm for intent: %s", getIntent())
            finish()
            return
        } else if (mAlarmInstance!!.mAlarmState != InstancesColumns.FIRED_STATE) {
            LOGGER.i("Skip displaying alarm for instance: %s", mAlarmInstance)
            finish()
            return
        }

        LOGGER.i("Displaying alarm for instance: %s", mAlarmInstance)

        // Get the volume/camera button behavior setting
        mVolumeBehavior = DataModel.dataModel.alarmVolumeButtonBehavior

        if (Utils.isOOrLater) {
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        } else {
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
                    or WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD
                    or WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                    or WindowManager.LayoutParams.FLAG_ALLOW_LOCK_WHILE_SCREEN_ON)
        }

        // Hide navigation bar to minimize accidental tap on Home key
        hideNavigationBar()

        // Close dialogs and window shade, so this is fully visible
        sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))

        // Honor rotation on tablets; fix the orientation on phones.
        if (!getResources().getBoolean(R.bool.rotateAlarmAlert)) {
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_NOSENSOR)
        }

        mAccessibilityManager = getSystemService(ACCESSIBILITY_SERVICE) as AccessibilityManager?

        setContentView(R.layout.alarm_activity)

        mAlertView = findViewById(R.id.alert) as ViewGroup
        mAlertTitleView = mAlertView.findViewById(R.id.alert_title) as TextView
        mAlertInfoView = mAlertView.findViewById(R.id.alert_info) as TextView

        mContentView = findViewById(R.id.content) as ViewGroup
        mAlarmButton = mContentView.findViewById(R.id.alarm) as ImageView
        mSnoozeButton = mContentView.findViewById(R.id.snooze) as ImageView
        mDismissButton = mContentView.findViewById(R.id.dismiss) as ImageView
        mHintView = mContentView.findViewById(R.id.hint) as TextView

        val titleView: TextView = mContentView.findViewById(R.id.title) as TextView
        val digitalClock: TextClock = mContentView.findViewById(R.id.digital_clock) as TextClock
        val pulseView = mContentView.findViewById(R.id.pulse) as CircleView

        titleView.setText(mAlarmInstance!!.getLabelOrDefault(this))
        Utils.setTimeFormat(digitalClock, false)

        mCurrentHourColor = ThemeUtils.resolveColor(this, android.R.attr.windowBackground)
        getWindow().setBackgroundDrawable(ColorDrawable(mCurrentHourColor))

        mAlarmButton.setOnTouchListener(this)
        mSnoozeButton.setOnClickListener(this)
        mDismissButton.setOnClickListener(this)

        mAlarmAnimator = AnimatorUtils.getScaleAnimator(mAlarmButton, 1.0f, 0.0f)
        mSnoozeAnimator = getButtonAnimator(mSnoozeButton, Color.WHITE)
        mDismissAnimator = getButtonAnimator(mDismissButton, mCurrentHourColor)
        mPulseAnimator = ObjectAnimator.ofPropertyValuesHolder(pulseView,
                PropertyValuesHolder.ofFloat(CircleView.RADIUS, 0.0f, pulseView.radius),
                PropertyValuesHolder.ofObject(CircleView.FILL_COLOR, AnimatorUtils.ARGB_EVALUATOR,
                        ColorUtils.setAlphaComponent(pulseView.fillColor, 0)))
        mPulseAnimator.setDuration(PULSE_DURATION_MILLIS.toLong())
        mPulseAnimator.setInterpolator(PULSE_INTERPOLATOR)
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE)
        mPulseAnimator.start()
    }

    override fun onResume() {
        super.onResume()

        // Re-query for AlarmInstance in case the state has changed externally
        val instanceId = AlarmInstance.getId(getIntent().getData()!!)
        mAlarmInstance = AlarmInstance.getInstance(getContentResolver(), instanceId)

        if (mAlarmInstance == null) {
            LOGGER.i("No alarm instance for instanceId: %d", instanceId)
            finish()
            return
        }

        // Verify that the alarm is still firing before showing the activity
        if (mAlarmInstance!!.mAlarmState != InstancesColumns.FIRED_STATE) {
            LOGGER.i("Skip displaying alarm for instance: %s", mAlarmInstance)
            finish()
            return
        }

        if (!mReceiverRegistered) {
            // Register to get the alarm done/snooze/dismiss intent.
            val filter = IntentFilter(AlarmService.ALARM_DONE_ACTION)
            filter.addAction(AlarmService.ALARM_SNOOZE_ACTION)
            filter.addAction(AlarmService.ALARM_DISMISS_ACTION)
            registerReceiver(mReceiver, filter)
            mReceiverRegistered = true
        }
        bindAlarmService()
        resetAnimations()
    }

    override fun onPause() {
        super.onPause()
        unbindAlarmService()

        // Skip if register didn't happen to avoid IllegalArgumentException
        if (mReceiverRegistered) {
            unregisterReceiver(mReceiver)
            mReceiverRegistered = false
        }
    }

    override fun dispatchKeyEvent(keyEvent: KeyEvent): Boolean {
        // Do this in dispatch to intercept a few of the system keys.
        LOGGER.v("dispatchKeyEvent: %s", keyEvent)

        val keyCode: Int = keyEvent.getKeyCode()
        when (keyCode) {
            KeyEvent.KEYCODE_VOLUME_UP,
            KeyEvent.KEYCODE_VOLUME_DOWN,
            KeyEvent.KEYCODE_VOLUME_MUTE,
            KeyEvent.KEYCODE_HEADSETHOOK,
            KeyEvent.KEYCODE_CAMERA,
            KeyEvent.KEYCODE_FOCUS -> if (!mAlarmHandled) {
                when (mVolumeBehavior) {
                    AlarmVolumeButtonBehavior.SNOOZE -> {
                        if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                            snooze()
                        }
                        return true
                    }
                    AlarmVolumeButtonBehavior.DISMISS -> {
                        if (keyEvent.getAction() == KeyEvent.ACTION_UP) {
                            dismiss()
                        }
                        return true
                    }
                    AlarmVolumeButtonBehavior.NOTHING -> {
                    }
                }
            }
        }
        return super.dispatchKeyEvent(keyEvent)
    }

    override fun onBackPressed() {
        // Don't allow back to dismiss.
    }

    override fun onClick(view: View) {
        if (mAlarmHandled) {
            LOGGER.v("onClick ignored: %s", view)
            return
        }
        LOGGER.v("onClick: %s", view)

        // If in accessibility mode, allow snooze/dismiss by double tapping on respective icons.
        if (isAccessibilityEnabled) {
            if (view == mSnoozeButton) {
                snooze()
            } else if (view == mDismissButton) {
                dismiss()
            }
            return
        }

        if (view == mSnoozeButton) {
            hintSnooze()
        } else if (view == mDismissButton) {
            hintDismiss()
        }
    }

    override fun onTouch(view: View?, event: MotionEvent): Boolean {
        if (mAlarmHandled) {
            LOGGER.v("onTouch ignored: %s", event)
            return false
        }

        val action: Int = event.getActionMasked()
        if (action == MotionEvent.ACTION_DOWN) {
            LOGGER.v("onTouch started: %s", event)

            // Track the pointer that initiated the touch sequence.
            mInitialPointerIndex = event.getPointerId(event.getActionIndex())

            // Stop the pulse, allowing the last pulse to finish.
            mPulseAnimator.setRepeatCount(0)
        } else if (action == MotionEvent.ACTION_CANCEL) {
            LOGGER.v("onTouch canceled: %s", event)

            // Clear the pointer index.
            mInitialPointerIndex = MotionEvent.INVALID_POINTER_ID

            // Reset everything.
            resetAnimations()
        }

        val actionIndex: Int = event.getActionIndex()
        if (mInitialPointerIndex == MotionEvent.INVALID_POINTER_ID ||
                mInitialPointerIndex != event.getPointerId(actionIndex)) {
            // Ignore any pointers other than the initial one, bail early.
            return true
        }

        val contentLocation = intArrayOf(0, 0)
        mContentView.getLocationOnScreen(contentLocation)

        val x: Float = event.getRawX() - contentLocation[0]
        val y: Float = event.getRawY() - contentLocation[1]

        val alarmLeft: Int = mAlarmButton.getLeft() + mAlarmButton.getPaddingLeft()
        val alarmRight: Int = mAlarmButton.getRight() - mAlarmButton.getPaddingRight()

        val snoozeFraction: Float
        val dismissFraction: Float
        if (mContentView.getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
            snoozeFraction =
                    getFraction(alarmRight.toFloat(), mSnoozeButton.getLeft().toFloat(), x)
            dismissFraction =
                    getFraction(alarmLeft.toFloat(), mDismissButton.getRight().toFloat(), x)
        } else {
            snoozeFraction = getFraction(alarmLeft.toFloat(), mSnoozeButton.getRight().toFloat(), x)
            dismissFraction =
                    getFraction(alarmRight.toFloat(), mDismissButton.getLeft().toFloat(), x)
        }
        setAnimatedFractions(snoozeFraction, dismissFraction)

        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_POINTER_UP) {
            LOGGER.v("onTouch ended: %s", event)

            mInitialPointerIndex = MotionEvent.INVALID_POINTER_ID
            if (snoozeFraction == 1.0f) {
                snooze()
            } else if (dismissFraction == 1.0f) {
                dismiss()
            } else {
                if (snoozeFraction > 0.0f || dismissFraction > 0.0f) {
                    // Animate back to the initial state.
                    AnimatorUtils.reverse(mAlarmAnimator, mSnoozeAnimator, mDismissAnimator)
                } else if (mAlarmButton.getTop() <= y && y <= mAlarmButton.getBottom()) {
                    // User touched the alarm button, hint the dismiss action.
                    hintDismiss()
                }

                // Restart the pulse.
                mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE)
                if (!mPulseAnimator.isStarted()) {
                    mPulseAnimator.start()
                }
            }
        }

        return true
    }

    private fun hideNavigationBar() {
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY)
    }

    /**
     * Returns `true` if accessibility is enabled, to enable alternate behavior for click
     * handling, etc.
     */
    private val isAccessibilityEnabled: Boolean
        get() {
            if (mAccessibilityManager == null || !mAccessibilityManager!!.isEnabled()) {
                // Accessibility is unavailable or disabled.
                return false
            } else if (mAccessibilityManager!!.isTouchExplorationEnabled()) {
                // TalkBack's touch exploration mode is enabled.
                return true
            }

            // Check if "Switch Access" is enabled.
            val enabledAccessibilityServices: List<AccessibilityServiceInfo> =
                    mAccessibilityManager!!.getEnabledAccessibilityServiceList(FEEDBACK_GENERIC)
            return !enabledAccessibilityServices.isEmpty()
        }

    private fun hintSnooze() {
        val alarmLeft: Int = mAlarmButton.getLeft() + mAlarmButton.getPaddingLeft()
        val alarmRight: Int = mAlarmButton.getRight() - mAlarmButton.getPaddingRight()
        val translationX = (Math.max(mSnoozeButton.getLeft() - alarmRight, 0) +
                Math.min(mSnoozeButton.getRight() - alarmLeft, 0)).toFloat()
        getAlarmBounceAnimator(translationX, if (translationX < 0.0f) {
            R.string.description_direction_left
        } else {
            R.string.description_direction_right
        }).start()
    }

    private fun hintDismiss() {
        val alarmLeft: Int = mAlarmButton.getLeft() + mAlarmButton.getPaddingLeft()
        val alarmRight: Int = mAlarmButton.getRight() - mAlarmButton.getPaddingRight()
        val translationX = (Math.max(mDismissButton.getLeft() - alarmRight, 0) +
                Math.min(mDismissButton.getRight() - alarmLeft, 0)).toFloat()
        getAlarmBounceAnimator(translationX, if (translationX < 0.0f) {
            R.string.description_direction_left
        } else {
            R.string.description_direction_right
        }).start()
    }

    /**
     * Set animators to initial values and restart pulse on alarm button.
     */
    private fun resetAnimations() {
        // Set the animators to their initial values.
        setAnimatedFractions(0.0f /* snoozeFraction */, 0.0f /* dismissFraction */)
        // Restart the pulse.
        mPulseAnimator.setRepeatCount(ValueAnimator.INFINITE)
        if (!mPulseAnimator.isStarted()) {
            mPulseAnimator.start()
        }
    }

    /**
     * Perform snooze animation and send snooze intent.
     */
    private fun snooze() {
        mAlarmHandled = true
        LOGGER.v("Snoozed: %s", mAlarmInstance)

        val colorAccent = ThemeUtils.resolveColor(this, R.attr.colorAccent)
        setAnimatedFractions(1.0f /* snoozeFraction */, 0.0f /* dismissFraction */)

        val snoozeMinutes = DataModel.dataModel.snoozeLength
        val infoText: String = getResources().getQuantityString(
                R.plurals.alarm_alert_snooze_duration, snoozeMinutes, snoozeMinutes)
        val accessibilityText: String = getResources().getQuantityString(
                R.plurals.alarm_alert_snooze_set, snoozeMinutes, snoozeMinutes)

        getAlertAnimator(mSnoozeButton, R.string.alarm_alert_snoozed_text, infoText,
                accessibilityText, colorAccent, colorAccent).start()

        AlarmStateManager.setSnoozeState(this, mAlarmInstance!!, false /* showToast */)

        Events.sendAlarmEvent(R.string.action_snooze, R.string.label_deskclock)

        // Unbind here, otherwise alarm will keep ringing until activity finishes.
        unbindAlarmService()
    }

    /**
     * Perform dismiss animation and send dismiss intent.
     */
    private fun dismiss() {
        mAlarmHandled = true
        LOGGER.v("Dismissed: %s", mAlarmInstance)

        setAnimatedFractions(0.0f /* snoozeFraction */, 1.0f /* dismissFraction */)

        getAlertAnimator(mDismissButton, R.string.alarm_alert_off_text, null /* infoText */,
                getString(R.string.alarm_alert_off_text) /* accessibilityText */,
                Color.WHITE, mCurrentHourColor).start()

        AlarmStateManager.deleteInstanceAndUpdateParent(this, mAlarmInstance!!)

        Events.sendAlarmEvent(R.string.action_dismiss, R.string.label_deskclock)

        // Unbind here, otherwise alarm will keep ringing until activity finishes.
        unbindAlarmService()
    }

    /**
     * Bind AlarmService if not yet bound.
     */
    private fun bindAlarmService() {
        if (!mServiceBound) {
            val intent = Intent(this, AlarmService::class.java)
            bindService(intent, mConnection, Context.BIND_AUTO_CREATE)
            mServiceBound = true
        }
    }

    /**
     * Unbind AlarmService if bound.
     */
    private fun unbindAlarmService() {
        if (mServiceBound) {
            unbindService(mConnection)
            mServiceBound = false
        }
    }

    private fun setAnimatedFractions(snoozeFraction: Float, dismissFraction: Float) {
        val alarmFraction = Math.max(snoozeFraction, dismissFraction)
        AnimatorUtils.setAnimatedFraction(mAlarmAnimator, alarmFraction)
        AnimatorUtils.setAnimatedFraction(mSnoozeAnimator, snoozeFraction)
        AnimatorUtils.setAnimatedFraction(mDismissAnimator, dismissFraction)
    }

    private fun getFraction(x0: Float, x1: Float, x: Float): Float {
        return Math.max(Math.min((x - x0) / (x1 - x0), 1.0f), 0.0f)
    }

    private fun getButtonAnimator(button: ImageView?, tintColor: Int): ValueAnimator {
        return ObjectAnimator.ofPropertyValuesHolder(button,
                PropertyValuesHolder.ofFloat(View.SCALE_X, BUTTON_SCALE_DEFAULT, 1.0f),
                PropertyValuesHolder.ofFloat(View.SCALE_Y, BUTTON_SCALE_DEFAULT, 1.0f),
                PropertyValuesHolder.ofInt(AnimatorUtils.BACKGROUND_ALPHA, 0, 255),
                PropertyValuesHolder.ofInt(AnimatorUtils.DRAWABLE_ALPHA,
                        BUTTON_DRAWABLE_ALPHA_DEFAULT, 255),
                PropertyValuesHolder.ofObject(AnimatorUtils.DRAWABLE_TINT,
                        AnimatorUtils.ARGB_EVALUATOR, Color.WHITE, tintColor))
    }

    private fun getAlarmBounceAnimator(translationX: Float, hintResId: Int): ValueAnimator {
        val bounceAnimator: ValueAnimator = ObjectAnimator.ofFloat(mAlarmButton,
                View.TRANSLATION_X, mAlarmButton.getTranslationX(), translationX, 0.0f)
        bounceAnimator.setInterpolator(AnimatorUtils.DECELERATE_ACCELERATE_INTERPOLATOR)
        bounceAnimator.setDuration(ALARM_BOUNCE_DURATION_MILLIS.toLong())
        bounceAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animator: Animator?) {
                mHintView.setText(hintResId)
                if (mHintView.getVisibility() != View.VISIBLE) {
                    mHintView.setVisibility(View.VISIBLE)
                    ObjectAnimator.ofFloat(mHintView, View.ALPHA, 0.0f, 1.0f).start()
                }
            }
        })
        return bounceAnimator
    }

    private fun getAlertAnimator(
        source: View,
        titleResId: Int,
        infoText: String?,
        accessibilityText: String,
        revealColor: Int,
        backgroundColor: Int
    ): Animator {
        val containerView: ViewGroup = findViewById(android.R.id.content) as ViewGroup

        val sourceBounds = Rect(0, 0, source.getHeight(), source.getWidth())
        containerView.offsetDescendantRectToMyCoords(source, sourceBounds)

        val centerX: Int = sourceBounds.centerX()
        val centerY: Int = sourceBounds.centerY()

        val xMax = max(centerX, containerView.getWidth() - centerX)
        val yMax = max(centerY, containerView.getHeight() - centerY)

        val startRadius: Float = max(sourceBounds.width(), sourceBounds.height()) / 2.0f
        val endRadius = sqrt(xMax * xMax + yMax * yMax.toDouble()).toFloat()

        val revealView = CircleView(this)
                .setCenterX(centerX.toFloat())
                .setCenterY(centerY.toFloat())
                .setFillColor(revealColor)
        containerView.addView(revealView)

        // TODO: Fade out source icon over the reveal (like LOLLIPOP version).

        val revealAnimator: Animator = ObjectAnimator.ofFloat(
                revealView, CircleView.RADIUS, startRadius, endRadius)
        revealAnimator.setDuration(ALERT_REVEAL_DURATION_MILLIS.toLong())
        revealAnimator.setInterpolator(REVEAL_INTERPOLATOR)
        revealAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator?) {
                mAlertView.setVisibility(View.VISIBLE)
                mAlertTitleView.setText(titleResId)
                if (infoText != null) {
                    mAlertInfoView.setText(infoText)
                    mAlertInfoView.setVisibility(View.VISIBLE)
                }
                mContentView.setVisibility(View.GONE)
                getWindow().setBackgroundDrawable(ColorDrawable(backgroundColor))
            }
        })

        val fadeAnimator: ValueAnimator = ObjectAnimator.ofFloat(revealView, View.ALPHA, 0.0f)
        fadeAnimator.setDuration(ALERT_FADE_DURATION_MILLIS.toLong())
        fadeAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: Animator?) {
                containerView.removeView(revealView)
            }
        })

        val alertAnimator = AnimatorSet()
        alertAnimator.play(revealAnimator).before(fadeAnimator)
        alertAnimator.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationEnd(animator: Animator?) {
                mAlertView.announceForAccessibility(accessibilityText)
                mHandler.postDelayed(Runnable { finish() }, ALERT_DISMISS_DELAY_MILLIS.toLong())
            }
        })

        return alertAnimator
    }

    companion object {
        private val LOGGER = LogUtils.Logger("AlarmActivity")

        private val PULSE_INTERPOLATOR: TimeInterpolator =
                PathInterpolatorCompat.create(0.4f, 0.0f, 0.2f, 1.0f)
        private val REVEAL_INTERPOLATOR: TimeInterpolator =
                PathInterpolatorCompat.create(0.0f, 0.0f, 0.2f, 1.0f)

        private const val PULSE_DURATION_MILLIS = 1000
        private const val ALARM_BOUNCE_DURATION_MILLIS = 500
        private const val ALERT_REVEAL_DURATION_MILLIS = 500
        private const val ALERT_FADE_DURATION_MILLIS = 500
        private const val ALERT_DISMISS_DELAY_MILLIS = 2000

        private const val BUTTON_SCALE_DEFAULT = 0.7f
        private const val BUTTON_DRAWABLE_ALPHA_DEFAULT = 165
    }
}