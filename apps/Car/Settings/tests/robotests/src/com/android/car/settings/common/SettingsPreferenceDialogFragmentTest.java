/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;

import androidx.preference.DialogPreference;
import androidx.preference.PreferenceFragmentCompat;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.BaseTestActivity;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.android.controller.ActivityController;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowAlertDialog;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowWindow;

/** Unit test for {@link SettingsPreferenceDialogFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class SettingsPreferenceDialogFragmentTest {

    private ActivityController<BaseTestActivity> mTestActivityController;
    private BaseTestActivity mTestActivity;
    private DialogPreference mPreference;
    private TestSettingsPreferenceDialogFragment mFragment;

    @Before
    public void setUp() {
        Context context = RuntimeEnvironment.application;

        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup();

        TestTargetFragment targetFragment = new TestTargetFragment();
        mTestActivity.launchFragment(targetFragment);
        mPreference = new TestDialogPreference(context);
        mPreference.setKey("key");
        targetFragment.getPreferenceScreen().addPreference(mPreference);

        mFragment = TestSettingsPreferenceDialogFragment.newInstance(mPreference.getKey());
        mFragment.setTargetFragment(targetFragment, /* requestCode= */ 0);
    }

    @Test
    public void dialogFieldsPopulatedWithPreferenceFields() {
        mPreference.setDialogTitle("title");
        mPreference.setPositiveButtonText("positive button text");
        mPreference.setNegativeButtonText("negative button text");
        mPreference.setDialogMessage("dialog message");

        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(getShadowAlertDialog().getTitle()).isEqualTo(mPreference.getDialogTitle());
        assertThat(ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_POSITIVE).getText()).isEqualTo(
                mPreference.getPositiveButtonText());
        assertThat(ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_NEGATIVE).getText()).isEqualTo(
                mPreference.getNegativeButtonText());
        assertThat(getShadowAlertDialog().getMessage()).isEqualTo(mPreference.getDialogMessage());
    }

    @Test
    public void dialogMessage_messageViewShown() {
        mPreference.setDialogTitle("title");
        mPreference.setPositiveButtonText("positive button text");
        mPreference.setNegativeButtonText("negative button text");
        mPreference.setDialogMessage("dialog message");

        mTestActivity.showDialog(mFragment, /* tag= */ null);
        View messageView = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.message);

        assertThat(messageView.getVisibility()).isEqualTo(View.VISIBLE);
    }

    @Test
    public void noDialogMessage_messageViewHidden() {
        mPreference.setDialogTitle("title");
        mPreference.setPositiveButtonText("positive button text");
        mPreference.setNegativeButtonText("negative button text");

        mTestActivity.showDialog(mFragment, /* tag= */ null);
        View messageView = ShadowAlertDialog.getLatestAlertDialog().findViewById(
                android.R.id.message);

        assertThat(messageView.getVisibility()).isEqualTo(View.GONE);
    }

    @Test
    public void getPreference_returnsDialogRequestingPreference() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(mFragment.getPreference()).isEqualTo(mPreference);
    }

    @Test
    public void dialogClosed_positiveButton_callsOnDialogClosed() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

        dialog.getButton(DialogInterface.BUTTON_POSITIVE).performClick();

        assertThat(mFragment.getDialogClosedResult()).isEqualTo(Boolean.TRUE);
    }

    @Test
    public void dialogClosed_negativeButton_callsOnDialogClosed() {
        mTestActivity.showDialog(mFragment, /* tag= */ null);
        AlertDialog dialog = ShadowAlertDialog.getLatestAlertDialog();

        dialog.getButton(DialogInterface.BUTTON_NEGATIVE).performClick();

        assertThat(mFragment.getDialogClosedResult()).isEqualTo(Boolean.FALSE);
    }

    @Test
    public void subclassNeedsInputMethod_softInputModeSetOnWindow() {
        mFragment.setNeedsInputMethod(true);
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(getShadowWindowFromDialog(
                ShadowAlertDialog.getLatestAlertDialog()).getSoftInputMode()).isEqualTo(
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
    }

    @Test
    public void subclassDoesNotNeedInputMethod_noWindowSoftInputMode() {
        mFragment.setNeedsInputMethod(false);
        mTestActivity.showDialog(mFragment, /* tag= */ null);

        assertThat(getShadowWindowFromDialog(
                ShadowAlertDialog.getLatestAlertDialog()).getSoftInputMode()).isEqualTo(0);
    }

    @Test
    public void instanceStateRetained() {
        String dialogTitle = "dialog title";
        String positiveButtonText = "positive button text";
        String negativeButtonText = "negative button text";
        String dialogMessage = "dialog message";
        mPreference.setDialogTitle(dialogTitle);
        mPreference.setPositiveButtonText(positiveButtonText);
        mPreference.setNegativeButtonText(negativeButtonText);
        mPreference.setDialogMessage(dialogMessage);

        mTestActivity.showDialog(mFragment, /* tag= */ null);

        // Save instance state.
        Bundle outState = new Bundle();
        mTestActivityController.pause().saveInstanceState(outState).stop();

        // Recreate everything with saved state.
        mTestActivityController = ActivityController.of(new BaseTestActivity());
        mTestActivity = mTestActivityController.get();
        mTestActivityController.setup(outState);

        // Ensure saved fields were applied.
        assertThat(getShadowAlertDialog().getTitle()).isEqualTo(dialogTitle);
        assertThat(ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_POSITIVE).getText()).isEqualTo(positiveButtonText);
        assertThat(ShadowAlertDialog.getLatestAlertDialog().getButton(
                DialogInterface.BUTTON_NEGATIVE).getText()).isEqualTo(negativeButtonText);
        assertThat(getShadowAlertDialog().getMessage()).isEqualTo(dialogMessage);
    }

    private ShadowAlertDialog getShadowAlertDialog() {
        return ShadowApplication.getInstance().getLatestAlertDialog();
    }

    private ShadowWindow getShadowWindowFromDialog(AlertDialog dialog) {
        return (ShadowWindow) Shadow.extract(dialog.getWindow());
    }

    /** Concrete implementation of the fragment under test. */
    public static class TestSettingsPreferenceDialogFragment extends
            SettingsPreferenceDialogFragment {

        private Boolean mDialogClosedResult;
        private boolean mNeedsInputMethod;

        static TestSettingsPreferenceDialogFragment newInstance(String key) {
            TestSettingsPreferenceDialogFragment fragment =
                    new TestSettingsPreferenceDialogFragment();
            Bundle b = new Bundle(/* capacity= */ 1);
            b.putString(ARG_KEY, key);
            fragment.setArguments(b);
            return fragment;
        }

        @Override
        protected boolean needInputMethod() {
            return mNeedsInputMethod;
        }

        void setNeedsInputMethod(boolean needsInputMethod) {
            mNeedsInputMethod = needsInputMethod;
        }

        @Override
        protected void onDialogClosed(boolean positiveResult) {
            mDialogClosedResult = positiveResult;
        }

        Boolean getDialogClosedResult() {
            return mDialogClosedResult;
        }
    }

    /** Concrete implementation of {@link DialogPreference} for testing use. */
    private static class TestDialogPreference extends DialogPreference {
        TestDialogPreference(Context context) {
            super(context);
        }
    }

    /** Simple {@link PreferenceFragmentCompat} implementation to serve as the target fragment. */
    public static class TestTargetFragment extends PreferenceFragmentCompat {
        @Override
        public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
            setPreferenceScreen(getPreferenceManager().createPreferenceScreen(getContext()));
        }
    }
}
