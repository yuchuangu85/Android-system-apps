/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.phone;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.content.Context;
import android.metrics.LogMaker;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewAnimationUtils;
import android.view.accessibility.AccessibilityManager;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

/**
 * Emergency shortcut button displays a local emergency phone number information(including phone
 * number, and phone type). To decrease false clicking, it need to click twice to confirm to place
 * an emergency phone call.
 *
 * <p> The button need to be set an {@link OnConfirmClickListener} from activity to handle dial
 * function.
 *
 * <p> First clicking on the button, it would change the view of call number information to
 * the view of confirmation. And then clicking on the view of confirmation, it will place an
 * emergency call.
 *
 * <p> For screen reader, it changed to click twice on the view of call number information to
 * place an emergency call. The view of confirmation will not display.
 */
public class EmergencyShortcutButton extends FrameLayout implements View.OnClickListener {
    // Time to hide view of confirmation.
    private static final long HIDE_DELAY = 3000;

    private static final int[] ICON_VIEWS = {R.id.phone_type_icon, R.id.confirmed_phone_type_icon};
    private View mCallNumberInfoView;
    private View mConfirmView;

    private TextView mPhoneNumber;
    private TextView mPhoneTypeDescription;
    private TextView mPhoneCallHint;
    private MotionEvent mPendingTouchEvent;
    private OnConfirmClickListener mOnConfirmClickListener;

    private boolean mConfirmViewHiding;

    /**
     * The time, in millis, since boot when user taps on shortcut button to reveal confirm view.
     * This is used for metrics when calculating the interval between reveal tap and confirm tap.
     */
    private long mTimeOfRevealTapInMillis = 0;

    public EmergencyShortcutButton(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    /**
     * Interface definition for a callback to be invoked when the view of confirmation on shortcut
     * button is clicked.
     */
    public interface OnConfirmClickListener {
        /**
         * Called when the view of confirmation on shortcut button has been clicked.
         *
         * @param button The shortcut button that was clicked.
         */
        void onConfirmClick(EmergencyShortcutButton button);
    }

    /**
     * Register a callback {@link OnConfirmClickListener} to be invoked when view of confirmation
     * is clicked.
     *
     * @param onConfirmClickListener The callback that will run.
     */
    public void setOnConfirmClickListener(OnConfirmClickListener onConfirmClickListener) {
        mOnConfirmClickListener = onConfirmClickListener;
    }

    /**
     * Set icon for different phone number type.
     *
     * @param resId The resource identifier of the drawable.
     */
    public void setPhoneTypeIcon(int resId) {
        for (int iconView : ICON_VIEWS) {
            ImageView phoneTypeIcon = findViewById(iconView);
            phoneTypeIcon.setImageResource(resId);
        }
    }

    /**
     * Set emergency phone number description.
     */
    public void setPhoneDescription(@NonNull CharSequence description) {
        mPhoneTypeDescription.setText(description);
    }

    /**
     * Set emergency phone number.
     */
    public void setPhoneNumber(@NonNull CharSequence number) {
        mPhoneNumber.setText(number);
        mPhoneCallHint.setText(
                getContext().getString(R.string.emergency_call_shortcut_hint, number));

        // Set content description for phone number.
        if (number.length() > 1) {
            StringBuilder stringBuilder = new StringBuilder();
            for (char c : number.toString().toCharArray()) {
                stringBuilder.append(c).append(" ");
            }
            mPhoneNumber.setContentDescription(stringBuilder.toString().trim());
        }
    }

    /**
     * Get emergency phone number.
     *
     * @return phone number, or {@code null} if {@code mPhoneNumber} does not be set.
     */
    public String getPhoneNumber() {
        return mPhoneNumber != null ? mPhoneNumber.getText().toString() : null;
    }

    /**
     * Called by the activity before a touch event is dispatched to the view hierarchy.
     */
    public void onPreTouchEvent(MotionEvent event) {
        mPendingTouchEvent = event;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        boolean handled = super.dispatchTouchEvent(event);
        if (mPendingTouchEvent == event && handled) {
            mPendingTouchEvent = null;
        }
        return handled;
    }

    /**
     * Called by the activity after a touch event is dispatched to the view hierarchy.
     */
    public void onPostTouchEvent(MotionEvent event) {
        // Hide the confirmation button if a touch event was delivered to the activity but not to
        // this view.
        if (mPendingTouchEvent != null) {
            hideSelectedButton();
        }
        mPendingTouchEvent = null;
    }

    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        mCallNumberInfoView = findViewById(R.id.emergency_call_number_info_view);
        mConfirmView = findViewById(R.id.emergency_call_confirm_view);

        mCallNumberInfoView.setOnClickListener(this);
        mConfirmView.setOnClickListener(this);

        mPhoneNumber = (TextView) mCallNumberInfoView.findViewById(R.id.phone_number);
        mPhoneTypeDescription = (TextView) mCallNumberInfoView.findViewById(
                R.id.phone_number_description);

        mPhoneCallHint = (TextView) mConfirmView.findViewById(R.id.phone_call_hint);

        mConfirmViewHiding = true;
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.emergency_call_number_info_view:
                if (AccessibilityManager.getInstance(mContext).isTouchExplorationEnabled()) {
                    // TalkBack itself includes a prompt to confirm click action implicitly,
                    // so we don't need an additional confirmation with second tap on button.
                    if (mOnConfirmClickListener != null) {
                        mOnConfirmClickListener.onConfirmClick(this);
                    }
                } else {
                    revealSelectedButton();
                }
                break;
            case R.id.emergency_call_confirm_view:
                if (mTimeOfRevealTapInMillis != 0) {
                    long timeBetweenTwoTaps =
                            SystemClock.elapsedRealtime() - mTimeOfRevealTapInMillis;
                    // Reset reveal time to zero for next reveal-confirm taps pair.
                    mTimeOfRevealTapInMillis = 0;

                    writeMetricsForConfirmTap(timeBetweenTwoTaps);
                }

                if (mOnConfirmClickListener != null) {
                    mOnConfirmClickListener.onConfirmClick(this);
                }
                break;
        }
    }

    private void revealSelectedButton() {
        mConfirmViewHiding = false;

        mConfirmView.setVisibility(View.VISIBLE);
        mTimeOfRevealTapInMillis = SystemClock.elapsedRealtime();
        int centerX = mCallNumberInfoView.getLeft() + mCallNumberInfoView.getWidth() / 2;
        int centerY = mCallNumberInfoView.getTop() + mCallNumberInfoView.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mConfirmView,
                centerX,
                centerY,
                0,
                Math.max(centerX, mConfirmView.getWidth() - centerX)
                        + Math.max(centerY, mConfirmView.getHeight() - centerY));
        reveal.start();

        postDelayed(mCancelSelectedButtonRunnable, HIDE_DELAY);
        mConfirmView.requestFocus();
    }

    private void hideSelectedButton() {
        if (mConfirmViewHiding || mConfirmView.getVisibility() != VISIBLE) {
            return;
        }

        mConfirmViewHiding = true;

        removeCallbacks(mCancelSelectedButtonRunnable);
        int centerX = mConfirmView.getLeft() + mConfirmView.getWidth() / 2;
        int centerY = mConfirmView.getTop() + mConfirmView.getHeight() / 2;
        Animator reveal = ViewAnimationUtils.createCircularReveal(
                mConfirmView,
                centerX,
                centerY,
                Math.max(centerX, mCallNumberInfoView.getWidth() - centerX)
                        + Math.max(centerY, mCallNumberInfoView.getHeight() - centerY),
                0);
        reveal.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                mConfirmView.setVisibility(INVISIBLE);
                // Reset reveal time to zero for next reveal-confirm taps pair.
                mTimeOfRevealTapInMillis = 0;
            }
        });
        reveal.start();

        mCallNumberInfoView.requestFocus();
    }

    private final Runnable mCancelSelectedButtonRunnable = new Runnable() {
        @Override
        public void run() {
            if (!isAttachedToWindow()) return;
            hideSelectedButton();
        }
    };

    private void writeMetricsForConfirmTap(long timeBetweenTwoTaps) {
        LogMaker logContent = new LogMaker(MetricsEvent.EMERGENCY_DIALER_SHORTCUT_CONFIRM_TAP)
                .setType(MetricsEvent.TYPE_ACTION)
                .addTaggedData(MetricsEvent.FIELD_EMERGENCY_DIALER_SHORTCUT_TAPS_INTERVAL,
                        timeBetweenTwoTaps);
        MetricsLogger.action(logContent);
    }
}
