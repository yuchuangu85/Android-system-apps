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

package com.android.car.dialer.ui.dialpad;

import android.os.Bundle;
import android.util.SparseArray;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.dialer.R;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * Fragment which displays a pad of keys.
 */
public class KeypadFragment extends Fragment {
    private static final SparseArray<Integer> sRIdMap = new SparseArray<>();

    static {
        sRIdMap.put(KeyEvent.KEYCODE_1, R.id.one);
        sRIdMap.put(KeyEvent.KEYCODE_2, R.id.two);
        sRIdMap.put(KeyEvent.KEYCODE_3, R.id.three);
        sRIdMap.put(KeyEvent.KEYCODE_4, R.id.four);
        sRIdMap.put(KeyEvent.KEYCODE_5, R.id.five);
        sRIdMap.put(KeyEvent.KEYCODE_6, R.id.six);
        sRIdMap.put(KeyEvent.KEYCODE_7, R.id.seven);
        sRIdMap.put(KeyEvent.KEYCODE_8, R.id.eight);
        sRIdMap.put(KeyEvent.KEYCODE_9, R.id.nine);
        sRIdMap.put(KeyEvent.KEYCODE_0, R.id.zero);
        sRIdMap.put(KeyEvent.KEYCODE_STAR, R.id.star);
        sRIdMap.put(KeyEvent.KEYCODE_POUND, R.id.pound);
    }

    /** Valid keycodes that can be sent to the callback. **/
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({KeyEvent.KEYCODE_0, KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_3,
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_6, KeyEvent.KEYCODE_7,
            KeyEvent.KEYCODE_8, KeyEvent.KEYCODE_9, KeyEvent.KEYCODE_STAR, KeyEvent.KEYCODE_POUND})
    @interface DialKeyCode {
    }

    /** Callback for keypad to interact with its host. */
    public interface KeypadCallback {

        /** Called when a key is long pressed. */
        void onKeypadKeyLongPressed(@DialKeyCode int keycode);

        /** Called when a key is pressed down. */
        void onKeypadKeyDown(@DialKeyCode int keycode);

        /** Called when a key is released. */
        void onKeypadKeyUp(@DialKeyCode int keycode);
    }


    private KeypadCallback mKeypadCallback;

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        if (getParentFragment() instanceof KeypadCallback) {
            mKeypadCallback = (KeypadCallback) getParentFragment();
        } else if (getHost() instanceof KeypadCallback) {
            mKeypadCallback = (KeypadCallback) getHost();
        }

        View keypadView = inflater.inflate(R.layout.keypad, container, false);
        setupKeypadClickListeners(keypadView);
        return keypadView;
    }

    /**
     * The click listener for all keypad buttons.  Reacts to touch-down and touch-up events, as
     * well as long-press for certain keys.  Mimics the behavior of the phone dialer app.
     */
    private class KeypadClickListener implements View.OnTouchListener,
            View.OnLongClickListener, View.OnKeyListener, View.OnFocusChangeListener {
        private final int mKeycode;
        private boolean mIsKeyDown = false;

        KeypadClickListener(@DialKeyCode int keyCode) {
            mKeycode = keyCode;
        }

        @Override
        public boolean onLongClick(View v) {
            mKeypadCallback.onKeypadKeyLongPressed(mKeycode);
            return true;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            if (mKeypadCallback != null) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    mKeypadCallback.onKeypadKeyDown(mKeycode);
                } else if (event.getAction() == MotionEvent.ACTION_UP) {
                    mKeypadCallback.onKeypadKeyUp(mKeycode);
                }
            }

            // Continue propagating the event
            return false;
        }

        @Override
        public boolean onKey(View v, int keyCode, KeyEvent event) {
            if (mKeypadCallback != null && KeyEvent.isConfirmKey(keyCode)) {
                if (event.getAction() == KeyEvent.ACTION_DOWN && !mIsKeyDown) {
                    mIsKeyDown = true;
                    mKeypadCallback.onKeypadKeyDown(mKeycode);
                } else if (event.getAction() == KeyEvent.ACTION_UP && mIsKeyDown) {
                    mIsKeyDown = false;
                    mKeypadCallback.onKeypadKeyUp(mKeycode);
                }
            }

            // Continue propagating the event
            return false;
        }

        @Override
        public void onFocusChange(View v, boolean hasFocus) {
            if (!hasFocus && mIsKeyDown) {
                mIsKeyDown = false;
                mKeypadCallback.onKeypadKeyUp(mKeycode);
            }
        }
    }

    private void setupKeypadClickListeners(View parent) {
        for (int i = 0; i < sRIdMap.size(); i++) {
            int key = sRIdMap.keyAt(i);
            KeypadClickListener clickListener = new KeypadClickListener(key);
            View v = parent.findViewById(sRIdMap.get(key));
            v.setOnTouchListener(clickListener);
            v.setOnLongClickListener(clickListener);
            v.setOnKeyListener(clickListener);
            v.setOnFocusChangeListener(clickListener);
        }
    }
}
