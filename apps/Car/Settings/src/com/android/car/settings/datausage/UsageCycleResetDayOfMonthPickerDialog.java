/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.car.settings.datausage;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.widget.NumberPicker;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;

/** Dialog that is used to pick the start day of month to track a data usage cycle. */
public class UsageCycleResetDayOfMonthPickerDialog extends DialogFragment {

    private static final String ARG_SELECTED_DAY_OF_MONTH = "arg_selected_day_of_month";

    /**
     * Defines the time frequency at which touch listener should be triggered when holding either
     * arrow button.
     */
    @VisibleForTesting
    static final int TIME_INTERVAL_MILLIS = 250;

    private static final int MIN_DAY = 1;
    private static final int MAX_DAY = 31;
    private ResetDayOfMonthPickedListener mResetDayOfMonthPickedListener;
    private NumberPicker mCycleDayOfMonthPicker;
    private View mUpArrow;
    private View mDownArrow;

    /**
     * Creates a new instance of the {@link UsageCycleResetDayOfMonthPickerDialog} with the {@link
     * NumberPicker} set to showing the value {@code startDayOfMonth}.
     */
    public static UsageCycleResetDayOfMonthPickerDialog newInstance(int startDayOfMonth) {
        UsageCycleResetDayOfMonthPickerDialog dialog = new UsageCycleResetDayOfMonthPickerDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_SELECTED_DAY_OF_MONTH, startDayOfMonth);
        dialog.setArguments(args);
        return dialog;
    }

    /** Sets a {@link ResetDayOfMonthPickedListener}. */
    public void setResetDayOfMonthPickedListener(
            ResetDayOfMonthPickedListener resetDayOfMonthPickedListener) {
        mResetDayOfMonthPickedListener = resetDayOfMonthPickedListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        // Use builder context to keep consistent theme.
        LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        View view = inflater.inflate(R.layout.usage_cycle_reset_day_of_month_picker,
                /* root= */ null, /* attachToRoot= */ false);

        int cycleDayOfMonth = getArguments().getInt(ARG_SELECTED_DAY_OF_MONTH);
        if (cycleDayOfMonth < MIN_DAY) {
            cycleDayOfMonth = MIN_DAY;
        }
        if (cycleDayOfMonth > MAX_DAY) {
            cycleDayOfMonth = MAX_DAY;
        }

        mCycleDayOfMonthPicker = view.findViewById(R.id.cycle_reset_day_of_month);
        mCycleDayOfMonthPicker.setMinValue(MIN_DAY);
        mCycleDayOfMonthPicker.setMaxValue(MAX_DAY);
        mCycleDayOfMonthPicker.setValue(cycleDayOfMonth);
        mCycleDayOfMonthPicker.setWrapSelectorWheel(true);

        mUpArrow = view.findViewById(R.id.up_arrow_container);
        mUpArrow.setOnTouchListener(new CycleArrowTouchListener(
                () -> mCycleDayOfMonthPicker.setValue(mCycleDayOfMonthPicker.getValue() - 1),
                TIME_INTERVAL_MILLIS));

        mDownArrow = view.findViewById(R.id.down_arrow_container);
        mDownArrow.setOnTouchListener(new CycleArrowTouchListener(
                () -> mCycleDayOfMonthPicker.setValue(mCycleDayOfMonthPicker.getValue() + 1),
                TIME_INTERVAL_MILLIS));

        return builder
                .setTitle(R.string.cycle_reset_day_of_month_picker_title)
                .setView(view)
                .setPositiveButton(R.string.cycle_reset_day_of_month_picker_positive_button,
                        (dialog, which) -> {
                            if (which == DialogInterface.BUTTON_POSITIVE) {
                                if (mResetDayOfMonthPickedListener != null) {
                                    mResetDayOfMonthPickedListener.onDayOfMonthPicked(
                                            mCycleDayOfMonthPicker.getValue());
                                }
                            }
                        })
                .create();
    }

    /** Gets the current day of month selected by the {@link NumberPicker}. */
    public int getSelectedDayOfMonth() {
        return mCycleDayOfMonthPicker.getValue();
    }

    /** A listener that is called when a date is selected. */
    public interface ResetDayOfMonthPickedListener {
        /** A method that determines how to process the selected day of month. */
        void onDayOfMonthPicked(int dayOfMonth);
    }

    private static class CycleArrowTouchListener implements View.OnTouchListener {

        private final IntervalActionListener mIntervalActionListener;
        private final long mTimeIntervalMillis;

        private Handler mHandler = new Handler();
        private Runnable mAction;

        CycleArrowTouchListener(IntervalActionListener listener, long timeIntervalMillis) {
            mIntervalActionListener = listener;
            mTimeIntervalMillis = timeIntervalMillis;

            mAction = () -> {
                mHandler.postDelayed(this.mAction, mTimeIntervalMillis);
                maybeTriggerAction();
            };
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    mHandler.removeCallbacks(mAction);
                    mHandler.postDelayed(mAction, mTimeIntervalMillis);
                    maybeTriggerAction();
                    v.setPressed(true);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    mHandler.removeCallbacks(mAction);
                    v.setPressed(false);
                    return true;
            }
            return false;
        }

        private void maybeTriggerAction() {
            if (mIntervalActionListener != null) {
                mIntervalActionListener.takeAction();
            }
        }

        /** Action that should be taken per time interval that the button is held. */
        interface IntervalActionListener {
            /** Defines the action to take at each time interval. */
            void takeAction();
        }
    }
}
