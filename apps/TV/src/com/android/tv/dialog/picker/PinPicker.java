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
package com.android.tv.dialog.picker;

import android.content.Context;
import android.support.annotation.Nullable;
import android.support.annotation.VisibleForTesting;
import android.support.v17.leanback.widget.picker.Picker;
import android.support.v17.leanback.widget.picker.PickerColumn;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import java.util.ArrayList;
import java.util.List;

/** 4 digit picker */
public final class PinPicker extends Picker {
    // TODO(b/116144491): use leanback pin picker.

    private final List<PickerColumn> mPickers = new ArrayList<>();
    private OnClickListener mOnClickListener;

    // the version of picker I link to does not have this constructor
    public PinPicker(Context context, AttributeSet attributeSet) {
        this(context, attributeSet, 0);
    }

    public PinPicker(Context context, AttributeSet attributeSet, int defStyleAttr) {
        super(context, attributeSet, defStyleAttr);

        for (int i = 0; i < 4; i++) {
            PickerColumn pickerColumn = new PickerColumn();
            pickerColumn.setMinValue(0);
            pickerColumn.setMaxValue(9);
            pickerColumn.setLabelFormat("%d");
            mPickers.add(pickerColumn);
        }
        setSeparator(" ");
        setColumns(mPickers);
        setActivated(true);
        setFocusable(true);
        super.setOnClickListener(this::onClick);
    }

    public String getPinInput() {
        String result = "";
        try {
            for (PickerColumn column : mPickers) {

                result += column.getCurrentValue();
            }
        } catch (IllegalStateException e) {
            result = "";
        }
        return result;
    }

    @Override
    public void setOnClickListener(@Nullable OnClickListener l) {
        mOnClickListener = l;
    }

    private void onClick(View v) {
        int selectedColumn = getSelectedColumn();
        int nextColumn = selectedColumn + 1;
        // Only call the click listener if we are on the last column
        // Otherwise move to the next column
        if (nextColumn == getColumnsCount()) {
            if (mOnClickListener != null) {
                mOnClickListener.onClick(v);
            }
        } else {
            setSelectedColumn(nextColumn);
            onRequestFocusInDescendants(ViewGroup.FOCUS_FORWARD, null);
        }
    }

    public void resetPinInput() {
        setActivated(false);
        for (int i = 0; i < 4; i++) {
            setColumnValue(i, 0, true);
        }
        setSelectedColumn(0);
        setActivated(true); // This resets the focus
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_UP) {
            int keyCode = event.getKeyCode();
            int digit = digitFromKeyCode(keyCode);
            if (digit != -1) {
                int selectedColumn = getSelectedColumn();
                setColumnValue(selectedColumn, digit, false);
                int nextColumn = selectedColumn + 1;
                if (nextColumn < getColumnsCount()) {
                    setSelectedColumn(nextColumn);
                    onRequestFocusInDescendants(ViewGroup.FOCUS_FORWARD, null);
                } else {
                    callOnClick();
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    @VisibleForTesting
    static int digitFromKeyCode(int keyCode) {
        if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
            return keyCode - KeyEvent.KEYCODE_0;
        } else if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
            return keyCode - KeyEvent.KEYCODE_NUMPAD_0;
        }
        return -1;
    }
}
