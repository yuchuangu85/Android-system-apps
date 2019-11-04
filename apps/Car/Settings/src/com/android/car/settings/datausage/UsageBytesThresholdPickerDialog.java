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
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.NumberPicker;

import androidx.annotation.IntegerRes;
import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;

/** Dialog that is used to pick the data usage warning/limit threshold bytes. */
public class UsageBytesThresholdPickerDialog extends DialogFragment implements
        DialogInterface.OnClickListener {

    /** Tag used to identify dialog in {@link androidx.fragment.app.FragmentManager}. */
    public static final String TAG = "UsageBytesThresholdPickerDialog";
    private static final String ARG_DIALOG_TITLE_RES = "arg_dialog_title_res";
    private static final String ARG_CURRENT_THRESHOLD = "arg_current_threshold";
    private static final float MB_GB_SUFFIX_THRESHOLD = 1.5f;

    @VisibleForTesting
    static final long MIB_IN_BYTES = 1024 * 1024;
    @VisibleForTesting
    static final long GIB_IN_BYTES = MIB_IN_BYTES * 1024;
    @VisibleForTesting
    static final long MAX_DATA_LIMIT_BYTES = 50000 * GIB_IN_BYTES;

    // Number pickers can be used to pick strings as well.
    private NumberPicker mBytesUnits;
    private View mUpArrow;
    private View mDownArrow;
    private EditText mThresholdEditor;
    private BytesThresholdPickedListener mBytesThresholdPickedListener;
    private long mCurrentThreshold;

    /**
     * Creates a new instance of the {@link UsageBytesThresholdPickerDialog} with the
     * {@code currentThreshold} represented with the best units available.
     */
    public static UsageBytesThresholdPickerDialog newInstance(@IntegerRes int dialogTitle,
            long currentThreshold) {
        UsageBytesThresholdPickerDialog dialog = new UsageBytesThresholdPickerDialog();
        Bundle args = new Bundle();
        args.putInt(ARG_DIALOG_TITLE_RES, dialogTitle);
        args.putLong(ARG_CURRENT_THRESHOLD, currentThreshold);
        dialog.setArguments(args);
        return dialog;
    }

    /** Sets a {@link BytesThresholdPickedListener}. */
    public void setBytesThresholdPickedListener(
            BytesThresholdPickedListener bytesThresholdPickedListener) {
        mBytesThresholdPickedListener = bytesThresholdPickedListener;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        AlertDialog.Builder builder = new AlertDialog.Builder(getContext());

        // Use builder context to keep consistent theme.
        LayoutInflater inflater = LayoutInflater.from(builder.getContext());
        View view = inflater.inflate(R.layout.usage_bytes_threshold_picker,
                /* root= */ null, /* attachToRoot= */ false);

        mCurrentThreshold = getArguments().getLong(ARG_CURRENT_THRESHOLD);
        if (mCurrentThreshold < 0) {
            mCurrentThreshold = 0;
        }

        String[] units = getContext().getResources().getStringArray(R.array.bytes_picker_sizes);
        mBytesUnits = view.findViewById(R.id.bytes_units);
        mBytesUnits.setMinValue(0);
        mBytesUnits.setMaxValue(units.length - 1);
        mBytesUnits.setDisplayedValues(units);

        mThresholdEditor = view.findViewById(R.id.bytes_threshold);

        mUpArrow = view.findViewById(R.id.up_arrow_container);
        mUpArrow.setOnClickListener(v -> mBytesUnits.setValue(mBytesUnits.getValue() - 1));

        mDownArrow = view.findViewById(R.id.down_arrow_container);
        mDownArrow.setOnClickListener(v -> mBytesUnits.setValue(mBytesUnits.getValue() + 1));

        updateCurrentView(mCurrentThreshold);

        return builder
                .setTitle(getArguments().getInt(ARG_DIALOG_TITLE_RES))
                .setView(view)
                .setPositiveButton(R.string.usage_bytes_threshold_picker_positive_button,
                        /* onClickListener= */ this)
                .create();
    }

    @Override
    public void onClick(DialogInterface dialog, int which) {
        if (which == DialogInterface.BUTTON_POSITIVE) {
            long newThreshold = getCurrentThreshold();
            if (mBytesThresholdPickedListener != null
                    && mCurrentThreshold != newThreshold) {
                mBytesThresholdPickedListener.onThresholdPicked(newThreshold);
            }
        }
    }

    /** Gets the threshold currently represented by this {@link UsageBytesThresholdPickerDialog}. */
    public long getCurrentThreshold() {
        String bytesString = mThresholdEditor.getText().toString();
        if (bytesString.isEmpty() || bytesString.equals(".")) {
            bytesString = "0";
        }

        long bytes = (long) (Float.valueOf(bytesString) * (mBytesUnits.getValue() == 0
                ? MIB_IN_BYTES : GIB_IN_BYTES));

        // To fix the overflow problem.
        long correctedBytes = Math.min(MAX_DATA_LIMIT_BYTES, bytes);

        return correctedBytes;
    }

    @VisibleForTesting
    void setThresholdEditor(long threshold) {
        updateCurrentView(threshold);
    }

    private void updateCurrentView(long threshold) {
        String bytesText;
        if (threshold > MB_GB_SUFFIX_THRESHOLD * GIB_IN_BYTES) {
            bytesText = formatText(threshold / (float) GIB_IN_BYTES);
            mBytesUnits.setValue(1);
        } else {
            bytesText = formatText(threshold / (float) MIB_IN_BYTES);
            mBytesUnits.setValue(0);
        }
        mThresholdEditor.setText(bytesText);
        mThresholdEditor.setSelection(0, bytesText.length());
    }

    private String formatText(float v) {
        v = Math.round(v * 100) / 100f;
        return String.valueOf(v);
    }

    /** A listener that is called when a date is selected. */
    public interface BytesThresholdPickedListener {
        /** A method that determines how to process the selected day of month. */
        void onThresholdPicked(long numBytes);
    }
}
