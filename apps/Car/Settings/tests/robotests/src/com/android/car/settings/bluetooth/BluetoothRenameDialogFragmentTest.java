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

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.content.Context;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;

import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.Robolectric;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowDialog;

/** Unit test for {@link BluetoothRenameDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class BluetoothRenameDialogFragmentTest {

    private TestBluetoothRenameDialogFragment mFragment;
    private AlertDialog mDialog;

    @Before
    public void setUp() {
        BaseTestActivity activity = Robolectric.setupActivity(BaseTestActivity.class);
        mFragment = new TestBluetoothRenameDialogFragment();
        activity.showDialog(mFragment, /* tag= */ null);
        mDialog = (AlertDialog) ShadowDialog.getLatestDialog();
    }

    @Test
    public void initialTextIsCurrentDeviceName() {
        EditText editText = mDialog.findViewById(android.R.id.edit);

        assertThat(editText.getText().toString()).isEqualTo(mFragment.getDeviceName());
    }

    @Test
    public void softInputShown() {
        InputMethodManager imm =
                (InputMethodManager) RuntimeEnvironment.application.getSystemService(
                        Context.INPUT_METHOD_SERVICE);
        assertThat(Shadows.shadowOf(imm).isSoftInputVisible()).isTrue();
    }

    @Test
    public void noUserInput_positiveButtonDisabled() {
        assertThat(mDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()).isFalse();
    }

    @Test
    public void userInput_positiveButtonEnabled() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        editText.append("1234");

        assertThat(mDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()).isTrue();
    }

    @Test
    public void userInput_emptyName_positiveButtonDisabled() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        editText.setText("");

        assertThat(mDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()).isFalse();
    }

    @Test
    public void nameUpdatedByCode_positiveButtonDisabled() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        editText.append("1234");

        mFragment.updateDeviceName();

        assertThat(mDialog.getButton(AlertDialog.BUTTON_POSITIVE).isEnabled()).isFalse();
    }

    @Test
    public void editorDoneAction_dismissesDialog() {
        EditText editText = mDialog.findViewById(android.R.id.edit);

        editText.onEditorAction(EditorInfo.IME_ACTION_DONE);

        assertThat(mDialog.isShowing()).isFalse();
    }

    @Test
    public void editorDoneAction_setsDeviceName() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        String editStr = "1234";
        String expectedName = mFragment.getDeviceName() + editStr;

        editText.append(editStr);
        editText.onEditorAction(EditorInfo.IME_ACTION_DONE);

        assertThat(mFragment.getDeviceName()).isEqualTo(expectedName);
    }

    @Test
    public void editorDoneAction_emptyName_doesNotSetDeviceName() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        String expectedName = mFragment.getDeviceName();
        String editStr = "";

        editText.setText(editStr);
        editText.onEditorAction(EditorInfo.IME_ACTION_DONE);

        assertThat(mFragment.getDeviceName()).isEqualTo(expectedName);
    }

    @Test
    public void positiveButtonClicked_setsDeviceName() {
        EditText editText = mDialog.findViewById(android.R.id.edit);
        String editStr = "1234";
        String expectedName = mFragment.getDeviceName() + editStr;

        editText.append(editStr);
        mDialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();

        assertThat(mFragment.getDeviceName()).isEqualTo(expectedName);
    }

    /** Concrete impl of {@link BluetoothRenameDialogFragment} for testing. */
    public static class TestBluetoothRenameDialogFragment extends BluetoothRenameDialogFragment {

        private String mSetDeviceNameArg = "Device Name";

        @Override
        @StringRes
        protected int getDialogTitle() {
            return R.string.bt_rename_dialog_title;
        }

        @Nullable
        @Override
        protected String getDeviceName() {
            return mSetDeviceNameArg;
        }

        @Override
        protected void setDeviceName(String deviceName) {
            mSetDeviceNameArg = deviceName;
        }
    }
}
