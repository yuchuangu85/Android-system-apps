/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.settings.bluetooth;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.text.Editable;
import android.text.InputFilter;
import android.text.InputType;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;

import com.android.car.settings.R;

import java.util.Objects;

/** Dialog fragment for renaming a Bluetooth device. */
public abstract class BluetoothRenameDialogFragment extends DialogFragment implements TextWatcher,
        TextView.OnEditorActionListener {

    // Keys to save the edited name and edit status for restoring after configuration change.
    private static final String KEY_NAME = "device_name";

    private static final int BLUETOOTH_NAME_MAX_LENGTH_BYTES = 248;

    private AlertDialog mAlertDialog;
    private EditText mDeviceNameView;
    private Button mRenameButton;

    /** Returns the title to use for the dialog. */
    @StringRes
    protected abstract int getDialogTitle();

    /** Returns the current name used for this device or {@code null} if a name is not available. */
    @Nullable
    protected abstract String getDeviceName();

    /**
     * Set the device to the given name.
     *
     * @param deviceName the name to use.
     */
    protected abstract void setDeviceName(String deviceName);

    @Override
    @NonNull
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        String deviceName = getDeviceName();
        if (savedInstanceState != null) {
            deviceName = savedInstanceState.getString(KEY_NAME, deviceName);
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity())
                .setTitle(getDialogTitle())
                .setView(createDialogView(deviceName))
                .setPositiveButton(R.string.bluetooth_rename_button,
                        (dialog, which) -> setDeviceName(
                                mDeviceNameView.getText().toString().trim()))
                .setNegativeButton(android.R.string.cancel, /* listener= */ null);
        mAlertDialog = builder.create();
        mAlertDialog.setOnShowListener(d -> {
            if (mDeviceNameView.requestFocus()) {
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(
                        Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(mDeviceNameView, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        });

        return mAlertDialog;
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        outState.putString(KEY_NAME, mDeviceNameView.getText().toString());
    }

    private View createDialogView(String deviceName) {
        final LayoutInflater layoutInflater = (LayoutInflater) requireActivity().getSystemService(
                Context.LAYOUT_INFLATER_SERVICE);
        // TODO: use dialog layout defined in preference theme.
        View view = layoutInflater.inflate(R.layout.preference_dialog_edittext, /* root= */ null);
        mDeviceNameView = view.findViewById(android.R.id.edit);
        mDeviceNameView.setFilters(new InputFilter[]{
                new Utf8ByteLengthFilter(BLUETOOTH_NAME_MAX_LENGTH_BYTES)
        });
        mDeviceNameView.setText(deviceName); // Set initial value before adding listener.
        if (!TextUtils.isEmpty(deviceName)) {
            mDeviceNameView.setSelection(deviceName.length());
        }
        mDeviceNameView.addTextChangedListener(this);
        mDeviceNameView.setOnEditorActionListener(this);
        mDeviceNameView.setRawInputType(InputType.TYPE_CLASS_TEXT);
        mDeviceNameView.setImeOptions(EditorInfo.IME_ACTION_DONE);

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        mRenameButton = mAlertDialog.getButton(DialogInterface.BUTTON_POSITIVE);
        refreshRenameButton();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mAlertDialog = null;
        mDeviceNameView = null;
        mRenameButton = null;
    }

    /** Refreshes the displayed device name with the latest value from {@link #getDeviceName()}. */
    protected void updateDeviceName() {
        String name = getDeviceName();
        if (name != null) {
            mDeviceNameView.setText(name);
        }
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
    }

    @Override
    public void afterTextChanged(Editable s) {
        refreshRenameButton();
    }

    @Override
    public boolean onEditorAction(TextView v, int actionId, KeyEvent event) {
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            String editedName = getEditedName();
            if (TextUtils.isEmpty(editedName)) {
                return false;
            }
            setDeviceName(editedName);
            if (mAlertDialog != null && mAlertDialog.isShowing()) {
                mAlertDialog.dismiss();
            }
            return true;
        }
        return false;
    }

    private void refreshRenameButton() {
        String editedName = getEditedName();
        mRenameButton.setEnabled(
                !TextUtils.isEmpty(editedName) && !Objects.equals(editedName, getDeviceName()));
    }

    private String getEditedName() {
        return mDeviceNameView.getText().toString().trim();
    }
}
