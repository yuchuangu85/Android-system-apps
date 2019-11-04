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

package com.google.android.car.multidisplaytest.ime;

import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputConnection;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.multidisplaytest.R;

/**
 * Modified from GarageModeTestApp;
 * Including coping Watchdog.java and Logger.java
 */
public class InputTestFragment extends Fragment {
    private static final String TAG = InputTestFragment.class.getSimpleName();

    private Button mClearButton;
    private EditText mTestEditText;
    private InputMethodManager mInputManager;
    private InputConnection mInputConnection;
    private TextView mWatchdogTextView;
    private ViewGroup mInputViewGroup;
    private Watchdog mWatchdog;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle bundle) {
        View view = inflater.inflate(R.layout.input_type_test, container, false);
        setViewsFromFragment(view);
        setListners();

        return view;
    }

    @Override
    public void onResume() {
        super.onResume();

        Log.d(TAG, "Resuming watchdog");

        mWatchdog = new Watchdog(mWatchdogTextView);
        mWatchdog.start();
    }

    @Override
    public void onPause() {
        super.onPause();

        Log.d(TAG, "Pausing watchdog");

        if (mWatchdog != null) {
            mWatchdog.stop();
            mWatchdog = null;
        }
    }

    private void setViewsFromFragment(View view) {
        mWatchdogTextView = view.findViewById(R.id.ime_watchdog);
        mInputManager = (InputMethodManager) getActivity()
            .getSystemService(Context.INPUT_METHOD_SERVICE);
        mInputViewGroup = view.findViewById(R.id.inputViewGroup);
        mClearButton = view.findViewById(R.id.clearButton);
        // Log this EditText view's input focus to test for input connection with IME
        mTestEditText = view.findViewById(R.id.testEditText);
    }

    private void setListners() {
        mClearButton.setOnClickListener(view -> onClearButtonClick());
        mInputViewGroup.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_UP) {
                if (mWatchdog != null) {
                    boolean activeState = mInputManager.isActive();
                    boolean acceptingState = mInputManager.isAcceptingText();
                    String logMessage = String.format("IME states: Active - %b, AcceptingText - %b",
                            activeState, acceptingState);
                    mWatchdog.logEvent(logMessage);
                }
            }
            return true;
        });

        mTestEditText.setOnFocusChangeListener((view, hasFocus) -> {
            if (mWatchdog != null) {
                if (hasFocus) {
                    mWatchdog.logEvent("EditText view has input connection with IME");
                } else {
                    mWatchdog.logEvent("EditText view doesn't have input connection with IME");
                }
            }
        });
    }

    private void onClearButtonClick() {
        if (mWatchdog != null) {
            mWatchdog.logEvent("Clear botton test...");
            mWatchdog.start();
        }
    }
}
