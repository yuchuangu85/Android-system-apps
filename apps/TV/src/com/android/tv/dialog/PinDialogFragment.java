/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.dialog;

import android.app.ActivityManager;
import android.app.Dialog;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.media.tv.TvContentRating;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.widget.TextView;
import android.widget.Toast;
import com.android.tv.R;
import com.android.tv.TvSingletons;
import com.android.tv.common.SoftPreconditions;
import com.android.tv.dialog.picker.PinPicker;
import com.android.tv.util.TvSettings;

public class PinDialogFragment extends SafeDismissDialogFragment {
    private static final String TAG = "PinDialogFragment";
    private static final boolean DEBUG = false;

    /** PIN code dialog for unlock channel */
    public static final int PIN_DIALOG_TYPE_UNLOCK_CHANNEL = 0;

    /**
     * PIN code dialog for unlock content. Only difference between {@code
     * PIN_DIALOG_TYPE_UNLOCK_CHANNEL} is it's title.
     */
    public static final int PIN_DIALOG_TYPE_UNLOCK_PROGRAM = 1;

    /** PIN code dialog for change parental control settings */
    public static final int PIN_DIALOG_TYPE_ENTER_PIN = 2;

    /** PIN code dialog for set new PIN */
    public static final int PIN_DIALOG_TYPE_NEW_PIN = 3;

    // PIN code dialog for checking old PIN. Only used in this class.
    private static final int PIN_DIALOG_TYPE_OLD_PIN = 4;

    /** PIN code dialog for unlocking DVR playback */
    public static final int PIN_DIALOG_TYPE_UNLOCK_DVR = 5;

    private static final int MAX_WRONG_PIN_COUNT = 5;
    private static final int DISABLE_PIN_DURATION_MILLIS = 60 * 1000; // 1 minute

    private static final String TRACKER_LABEL = "Pin dialog";
    private static final String ARGS_TYPE = "args_type";
    private static final String ARGS_RATING = "args_rating";

    public static final String DIALOG_TAG = PinDialogFragment.class.getName();

    private int mType;
    private int mRequestType;
    private boolean mPinChecked;
    private boolean mDismissSilently;

    private TextView mWrongPinView;
    private View mEnterPinView;
    private TextView mTitleView;
    private PinPicker mPicker;
    private SharedPreferences mSharedPreferences;
    private String mPrevPin;
    private String mPin;
    private String mRatingString;
    private int mWrongPinCount;
    private long mDisablePinUntil;
    private final Handler mHandler = new Handler();

    public static PinDialogFragment create(int type) {
        return create(type, null);
    }

    public static PinDialogFragment create(int type, String rating) {
        PinDialogFragment fragment = new PinDialogFragment();
        Bundle args = new Bundle();
        args.putInt(ARGS_TYPE, type);
        args.putString(ARGS_RATING, rating);
        fragment.setArguments(args);
        return fragment;
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mRequestType = getArguments().getInt(ARGS_TYPE, PIN_DIALOG_TYPE_ENTER_PIN);
        mType = mRequestType;
        mRatingString = getArguments().getString(ARGS_RATING);
        setStyle(STYLE_NO_TITLE, 0);
        mSharedPreferences = PreferenceManager.getDefaultSharedPreferences(getActivity());
        mDisablePinUntil = TvSettings.getDisablePinUntil(getActivity());
        if (ActivityManager.isUserAMonkey()) {
            // Skip PIN dialog half the time for monkeys
            if (Math.random() < 0.5) {
                exit(true);
            }
        }
        mPinChecked = false;
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        Dialog dlg = super.onCreateDialog(savedInstanceState);
        dlg.getWindow().getAttributes().windowAnimations = R.style.pin_dialog_animation;
        return dlg;
    }

    @Override
    public String getTrackerLabel() {
        return TRACKER_LABEL;
    }

    @Override
    public void onStart() {
        super.onStart();
        // Dialog size is determined by its windows size, not inflated view size.
        // So apply view size to window after the DialogFragment.onStart() where dialog is shown.
        Dialog dlg = getDialog();
        if (dlg != null) {
            dlg.getWindow()
                    .setLayout(
                            getResources().getDimensionPixelSize(R.dimen.pin_dialog_width),
                            LayoutParams.WRAP_CONTENT);
        }
    }

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        final View v = inflater.inflate(R.layout.pin_dialog, container, false);

        mWrongPinView = (TextView) v.findViewById(R.id.wrong_pin);
        mEnterPinView = v.findViewById(R.id.enter_pin);
        mTitleView = (TextView) mEnterPinView.findViewById(R.id.title);
        mPicker = v.findViewById(R.id.pin_picker);
        mPicker.setOnClickListener(
                view -> {
                    String pin = getPinInput();
                    if (!TextUtils.isEmpty(pin)) {
                        done(pin);
                    }
                });
        if (TextUtils.isEmpty(getPin())) {
            // If PIN isn't set, user should set a PIN.
            // Successfully setting a new set is considered as entering correct PIN.
            mType = PIN_DIALOG_TYPE_NEW_PIN;
        }
        switch (mType) {
            case PIN_DIALOG_TYPE_UNLOCK_CHANNEL:
                mTitleView.setText(R.string.pin_enter_unlock_channel);
                break;
            case PIN_DIALOG_TYPE_UNLOCK_PROGRAM:
                mTitleView.setText(R.string.pin_enter_unlock_program);
                break;
            case PIN_DIALOG_TYPE_UNLOCK_DVR:
                TvContentRating tvContentRating =
                        TvContentRating.unflattenFromString(mRatingString);
                if (TvContentRating.UNRATED.equals(tvContentRating)) {
                    mTitleView.setText(getString(R.string.pin_enter_unlock_dvr_unrated));
                } else {
                    mTitleView.setText(
                            getString(
                                    R.string.pin_enter_unlock_dvr,
                                    TvSingletons.getSingletons(getContext())
                                            .getTvInputManagerHelper()
                                            .getContentRatingsManager()
                                            .getDisplayNameForRating(tvContentRating)));
                }
                break;
            case PIN_DIALOG_TYPE_ENTER_PIN:
                mTitleView.setText(R.string.pin_enter_pin);
                break;
            case PIN_DIALOG_TYPE_NEW_PIN:
                if (TextUtils.isEmpty(getPin())) {
                    mTitleView.setText(R.string.pin_enter_create_pin);
                } else {
                    mTitleView.setText(R.string.pin_enter_old_pin);
                    mType = PIN_DIALOG_TYPE_OLD_PIN;
                }
        }

        if (mType != PIN_DIALOG_TYPE_NEW_PIN) {
            updateWrongPin();
        }
        mPicker.requestFocus();
        return v;
    }

    private void updateWrongPin() {
        if (getActivity() == null) {
            // The activity is already detached. No need to update.
            mHandler.removeCallbacks(null);
            return;
        }

        int remainingSeconds = (int) ((mDisablePinUntil - System.currentTimeMillis()) / 1000);
        boolean enabled = remainingSeconds < 1;
        if (enabled) {
            mWrongPinView.setVisibility(View.INVISIBLE);
            mEnterPinView.setVisibility(View.VISIBLE);
            mWrongPinCount = 0;
        } else {
            mEnterPinView.setVisibility(View.INVISIBLE);
            mWrongPinView.setVisibility(View.VISIBLE);
            mWrongPinView.setText(
                    getResources()
                            .getQuantityString(
                                    R.plurals.pin_enter_countdown,
                                    remainingSeconds,
                                    remainingSeconds));

            mHandler.postDelayed(this::updateWrongPin, 1000);
        }
    }

    private void exit(boolean pinChecked) {
        mPinChecked = pinChecked;
        dismiss();
    }

    /** Dismisses the pin dialog without calling activity listener. */
    public void dismissSilently() {
        mDismissSilently = true;
        dismiss();
    }

    @Override
    public void onDismiss(DialogInterface dialog) {
        super.onDismiss(dialog);
        if (DEBUG) Log.d(TAG, "onDismiss: mPinChecked=" + mPinChecked);
        SoftPreconditions.checkState(getActivity() instanceof OnPinCheckedListener);
        if (!mDismissSilently && getActivity() instanceof OnPinCheckedListener) {
            ((OnPinCheckedListener) getActivity())
                    .onPinChecked(mPinChecked, mRequestType, mRatingString);
        }
        mDismissSilently = false;
    }

    private void handleWrongPin() {
        if (++mWrongPinCount >= MAX_WRONG_PIN_COUNT) {
            mDisablePinUntil = System.currentTimeMillis() + DISABLE_PIN_DURATION_MILLIS;
            TvSettings.setDisablePinUntil(getActivity(), mDisablePinUntil);
            updateWrongPin();
        } else {
            showToast(R.string.pin_toast_wrong);
        }
    }

    private void showToast(int resId) {
        Toast.makeText(getActivity(), resId, Toast.LENGTH_SHORT).show();
    }

    private void done(String pin) {
        if (DEBUG) Log.d(TAG, "done: mType=" + mType + " pin=" + pin + " stored=" + getPin());
        switch (mType) {
            case PIN_DIALOG_TYPE_UNLOCK_CHANNEL:
            case PIN_DIALOG_TYPE_UNLOCK_PROGRAM:
            case PIN_DIALOG_TYPE_UNLOCK_DVR:
            case PIN_DIALOG_TYPE_ENTER_PIN:
                if (TextUtils.isEmpty(getPin()) || pin.equals(getPin())) {
                    exit(true);
                } else {
                    resetPinInput();
                    handleWrongPin();
                }
                break;
            case PIN_DIALOG_TYPE_NEW_PIN:
                resetPinInput();
                if (mPrevPin == null) {
                    mPrevPin = pin;
                    mTitleView.setText(R.string.pin_enter_again);
                } else {
                    if (pin.equals(mPrevPin)) {
                        setPin(pin);
                        exit(true);
                    } else {
                        if (TextUtils.isEmpty(getPin())) {
                            mTitleView.setText(R.string.pin_enter_create_pin);
                        } else {
                            mTitleView.setText(R.string.pin_enter_new_pin);
                        }
                        mPrevPin = null;
                        showToast(R.string.pin_toast_not_match);
                    }
                }
                break;
            case PIN_DIALOG_TYPE_OLD_PIN:
                // Call resetPinInput() here because we'll get additional PIN input
                // regardless of the result.
                resetPinInput();
                if (pin.equals(getPin())) {
                    mType = PIN_DIALOG_TYPE_NEW_PIN;
                    mTitleView.setText(R.string.pin_enter_new_pin);
                } else {
                    handleWrongPin();
                }
                break;
        }
    }

    public int getType() {
        return mType;
    }

    private void setPin(String pin) {
        if (DEBUG) Log.d(TAG, "setPin: " + pin);
        mPin = pin;
        mSharedPreferences.edit().putString(TvSettings.PREF_PIN, pin).apply();
    }

    private String getPin() {
        if (mPin == null) {
            mPin = mSharedPreferences.getString(TvSettings.PREF_PIN, "");
        }
        return mPin;
    }

    private String getPinInput() {
        return mPicker.getPinInput();
    }

    private void resetPinInput() {
        mPicker.resetPinInput();
    }

    /**
     * A listener to the result of {@link PinDialogFragment}. Any activity requiring pin code
     * checking should implement this listener to receive the result.
     */
    public interface OnPinCheckedListener {
        /**
         * Called when {@link PinDialogFragment} is dismissed.
         *
         * @param checked {@code true} if the pin code entered is checked to be correct, otherwise
         *     {@code false}.
         * @param type The dialog type regarding to what pin entering is for.
         * @param rating The target rating to unblock for.
         */
        void onPinChecked(boolean checked, int type, String rating);
    }
}
