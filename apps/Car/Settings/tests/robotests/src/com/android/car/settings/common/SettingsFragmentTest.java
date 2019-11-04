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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.testng.Assert.assertThrows;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.IntentSender;
import android.widget.FrameLayout;

import androidx.fragment.app.DialogFragment;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.DummyFragment;
import com.android.car.settings.testutils.FragmentController;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

/** Unit test for {@link SettingsFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class SettingsFragmentTest {

    private static final String TEST_TAG = "test_tag";

    private Context mContext;
    private FragmentController<TestSettingsFragment> mFragmentController;
    private SettingsFragment mFragment;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        mFragmentController = FragmentController.of(new TestSettingsFragment());
        mFragment = mFragmentController.get();
    }

    @Test
    public void use_returnsController() {
        mFragmentController.setup();
        assertThat(mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller)).isNotNull();
    }

    @Test
    public void onAttach_registersLifecycleObservers() {
        mFragmentController.create();
        FakePreferenceController controller = mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller);
        assertThat(controller.getOnCreateInternalCallCount()).isEqualTo(1);
        mFragmentController.destroy();
        assertThat(controller.getOnDestroyInternalCallCount()).isEqualTo(1);
    }

    @Test
    public void onUxRestrictionsChanged_propagatesToControllers() {
        mFragmentController.setup();
        FakePreferenceController controller = mFragment.use(FakePreferenceController.class,
                R.string.tpk_fake_controller);
        CarUxRestrictions uxRestrictions = new CarUxRestrictions.Builder(/* reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_NO_KEYBOARD, /* timestamp= */ 0).build();
        mFragment.onUxRestrictionsChanged(uxRestrictions);
        assertThat(controller.getUxRestrictions()).isEqualTo(uxRestrictions);
    }

    @Test
    public void onDisplayPreferenceDialog_editTextPreference_showsDialog() {
        mFragmentController.setup();

        mFragment.getPreferenceScreen().findPreference(
                mContext.getString(R.string.tpk_edit_text_preference)).performClick();

        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                SettingsFragment.DIALOG_FRAGMENT_TAG)).isInstanceOf(
                EditTextPreferenceDialogFragment.class);
    }

    @Test
    public void onDisplayPreferenceDialog_listPreference_showsDialog() {
        mFragmentController.setup();

        mFragment.getPreferenceScreen().findPreference(
                mContext.getString(R.string.tpk_list_preference)).performClick();

        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                SettingsFragment.DIALOG_FRAGMENT_TAG)).isInstanceOf(
                SettingsListPreferenceDialogFragment.class);
    }

    @Test
    public void onDisplayPreferenceDialog_unknownPreferenceType_throwsIllegalArgumentException() {
        mFragmentController.setup();

        assertThrows(IllegalArgumentException.class,
                () -> mFragment.onDisplayPreferenceDialog(new Preference(mContext)));
    }

    @Test
    public void onDisplayPreferenceDialog_alreadyShowing_doesNothing() {
        mFragmentController.setup();

        // Show a dialog.
        mFragment.getPreferenceScreen().findPreference(
                mContext.getString(R.string.tpk_edit_text_preference)).performClick();
        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                SettingsFragment.DIALOG_FRAGMENT_TAG)).isInstanceOf(
                EditTextPreferenceDialogFragment.class);

        // Attempt to show another.
        mFragment.getPreferenceScreen().findPreference(
                mContext.getString(R.string.tpk_list_preference)).performClick();

        // Assert only one shown at a time.
        assertThat(mFragment.getFragmentManager().findFragmentByTag(
                SettingsFragment.DIALOG_FRAGMENT_TAG)).isInstanceOf(
                EditTextPreferenceDialogFragment.class);
    }

    @Test
    public void launchFragment_otherFragment_opensFragment() {
        mFragmentController.setup();
        TestSettingsFragment otherFragment = new TestSettingsFragment();
        mFragment.launchFragment(otherFragment);
        assertThat(
                mFragment.getFragmentManager().findFragmentById(R.id.fragment_container)).isEqualTo(
                otherFragment);
    }

    @Test
    public void launchFragment_dialogFragment_throwsError() {
        mFragmentController.setup();
        DialogFragment dialogFragment = new DialogFragment();
        assertThrows(IllegalArgumentException.class,
                () -> mFragment.launchFragment(dialogFragment));
    }

    @Test
    public void showDialog_noTag_launchesDialogFragment() {
        mFragmentController.setup();
        DialogFragment dialogFragment = mock(DialogFragment.class);
        mFragment.showDialog(dialogFragment, /* tag */ null);
        verify(dialogFragment).show(mFragment.getFragmentManager(), null);
    }

    @Test
    public void showDialog_withTag_launchesDialogFragment() {
        mFragmentController.setup();
        DialogFragment dialogFragment = mock(DialogFragment.class);
        mFragment.showDialog(dialogFragment, TEST_TAG);
        verify(dialogFragment).show(mFragment.getFragmentManager(), TEST_TAG);
    }

    @Test
    public void findDialogByTag_retrieveOriginalDialog_returnsDialog() {
        mFragmentController.setup();
        DialogFragment dialogFragment = new DialogFragment();
        mFragment.showDialog(dialogFragment, TEST_TAG);
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isEqualTo(dialogFragment);
    }

    @Test
    public void findDialogByTag_notDialogFragment_returnsNull() {
        mFragmentController.setup();
        TestSettingsFragment fragment = new TestSettingsFragment();
        mFragment.getFragmentManager().beginTransaction().add(fragment, TEST_TAG).commit();
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isNull();
    }

    @Test
    public void findDialogByTag_noSuchFragment_returnsNull() {
        mFragmentController.setup();
        assertThat(mFragment.findDialogByTag(TEST_TAG)).isNull();
    }

    @Test
    public void startActivityForResult_largeRequestCode_throwsError() {
        mFragmentController.setup();
        assertThrows(() -> mFragment.startActivityForResult(new Intent(), 0xffff,
                mock(ActivityResultCallback.class)));
    }

    @Test
    public void startActivityForResult_tooManyRequests_throwsError() {
        mFragmentController.setup();
        assertThrows(() -> {
            for (int i = 0; i < 0xff; i++) {
                mFragment.startActivityForResult(new Intent(), i,
                        mock(ActivityResultCallback.class));
            }
        });
    }

    @Test
    public void startIntentSenderForResult_largeRequestCode_throwsError() {
        mFragmentController.setup();
        assertThrows(
                () -> mFragment.startIntentSenderForResult(
                        mock(IntentSender.class), /* requestCode= */ 0xffff,
                        /* fillInIntent= */null, /* flagsMask= */ 0,
                        /* flagsValues= */0, /* options= */ null,
                        mock(ActivityResultCallback.class)));
    }

    @Test
    public void startIntentSenderForResult_tooManyRequests_throwsError() {
        mFragmentController.setup();
        assertThrows(() -> {
            for (int i = 0; i < 0xff; i++) {
                mFragment.startIntentSenderForResult(
                        mock(IntentSender.class), /* requestCode= */ 0xffff,
                        /* fillInIntent= */null, /* flagsMask= */ 0,
                        /* flagsValues= */0, /* options= */ null,
                        mock(ActivityResultCallback.class));
            }
        });
    }

    @Test
    public void onActivityResult_hasValidRequestCode_triggersOnActivityResult() {
        mFragmentController.setup();
        ActivityResultCallback callback = mock(ActivityResultCallback.class);

        int reqCode = 100;
        int resCode = -1;
        mFragment.startActivityForResult(new Intent(), reqCode, callback);
        int fragmentReqCode = (1 << 8) + reqCode;
        mFragment.onActivityResult(fragmentReqCode, resCode, new Intent());
        verify(callback).processActivityResult(eq(reqCode), eq(resCode), any(Intent.class));
    }

    @Test
    public void onActivityResult_wrongRequestCode_doesntTriggerOnActivityResult() {
        mFragmentController.setup();
        ActivityResultCallback callback = mock(ActivityResultCallback.class);

        int reqCode = 100;
        int resCode = -1;
        mFragment.startActivityForResult(new Intent(), reqCode,
                callback);
        int fragmentReqCode = (2 << 8) + reqCode;
        mFragment.onActivityResult(fragmentReqCode, resCode, new Intent());
        verify(callback, never()).processActivityResult(anyInt(), anyInt(),
                any(Intent.class));
    }

    @Test
    public void onActivityCreated_hasAppIconIfRoot() {
        mFragmentController.setup();
        DummyFragment otherFragment = new DummyFragment();
        mFragment.launchFragment(otherFragment);

        FrameLayout actionBarContainer =
                otherFragment.requireActivity().findViewById(R.id.action_bar);

        assertThat(actionBarContainer.requireViewById(R.id.back_button).getTag(R.id.back_button))
                .isEqualTo(R.drawable.ic_launcher_settings);
    }

    @Test
    public void onActivityCreated_hasBackArrowIconIfNotRoot() {
        mFragmentController.setup();

        TestSettingsFragment otherFragment1 = new TestSettingsFragment();
        mFragment.launchFragment(otherFragment1);

        TestSettingsFragment otherFragment2 = new TestSettingsFragment();
        mFragment.launchFragment(otherFragment2);

        FrameLayout actionBarContainer =
                otherFragment2.requireActivity().findViewById(R.id.action_bar);

        assertThat(actionBarContainer.requireViewById(R.id.back_button).getTag(R.id.back_button))
                .isEqualTo(R.drawable.ic_arrow_back);
    }

    /** Concrete {@link SettingsFragment} for testing. */
    public static class TestSettingsFragment extends SettingsFragment {
        @Override
        protected int getPreferenceScreenResId() {
            return R.xml.settings_fragment;
        }
    }
}
