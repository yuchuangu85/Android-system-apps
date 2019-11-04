/*
 * Copyright (c) 2016, The Android Open Source Project
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
package com.android.car.radio;

import android.content.Context;
import android.hardware.radio.ProgramSelector;
import android.view.View;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.bands.RegionConfig;
import com.android.car.radio.widget.BandSelector;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ManualTunerController {
    private final Object mLock = new Object();

    private final List<View> mDigitButtons = new ArrayList<>();
    private final View mEnterButton;
    private final TextView mChannelDisplay;
    private BandSelector mBandSelector;

    private ProgramType mProgramType;
    private final RegionConfig mRegionConfig;
    private final TuningDoneListener mDoneListener;
    private int mEnteredDigits;

    /**
     * Listener for manual tuning done event.
     */
    public interface TuningDoneListener {
        /**
         * Called when the user accepted channel selection.
         */
        void onDone(@NonNull ProgramSelector sel);
    }

    public ManualTunerController(Context context, View container,
            @NonNull RegionConfig regionConfig, @NonNull TuningDoneListener doneListener) {
        mRegionConfig = Objects.requireNonNull(regionConfig);
        mDoneListener = Objects.requireNonNull(doneListener);

        mChannelDisplay = Objects.requireNonNull(container.findViewById(R.id.manual_tuner_channel));
        mEnterButton = container.findViewById(R.id.manual_tuner_done_button);
        mBandSelector = container.findViewById(R.id.manual_tuner_band_selector);

        mEnterButton.setOnClickListener(this::onDoneClick);
        mBandSelector.setCallback(this::switchProgramType);
        mBandSelector.setSupportedProgramTypes(regionConfig.getSupportedProgramTypes());

        View dialpad = container.findViewById(R.id.dialpad_layout);
        View.OnClickListener digitClickListener = this::onDigitClick;
        for (int i = 0; i <= 9; i++) {
            View btn = dialpad.findViewWithTag(Integer.toString(i));
            btn.setOnClickListener(digitClickListener);
            mDigitButtons.add(i, btn);
        }
        View backspace = dialpad.findViewById(R.id.manual_tuner_backspace);
        backspace.setOnClickListener(this::onBackspaceClick);

        switchProgramType(ProgramType.FM);
    }

    private void updateDisplay() {
        synchronized (mLock) {
            mChannelDisplay.setText(mProgramType.format(mEnteredDigits));
            mEnterButton.setEnabled(mProgramType.isComplete(mRegionConfig, mEnteredDigits));
            boolean[] valid = mProgramType.getValidAppendices(mRegionConfig, mEnteredDigits);
            for (int i = 0; i < 10; i++) {
                mDigitButtons.get(i).setEnabled(valid[i]);
            }
        }
    }

    private void onDigitClick(View btn) {
        int digit = Integer.parseInt((String) btn.getTag());
        synchronized (mLock) {
            mEnteredDigits *= 10;
            mEnteredDigits += digit;
        }
        updateDisplay();
    }

    private void onBackspaceClick(View btn) {
        synchronized (mLock) {
            mEnteredDigits /= 10;
        }
        updateDisplay();
    }

    private void onDoneClick(View btn) {
        synchronized (mLock) {
            mDoneListener.onDone(mProgramType.parseDigits(mEnteredDigits));
            mEnteredDigits = 0;
        }
        updateDisplay();
    }

    /**
     * Switches program type (band).
     */
    public void switchProgramType(@NonNull ProgramType pt) {
        Objects.requireNonNull(pt);
        synchronized (mLock) {
            if (mProgramType == pt) return;
            mProgramType = pt;
            mEnteredDigits = 0;
            mBandSelector.setType(pt);
        }
        updateDisplay();
    }
}
