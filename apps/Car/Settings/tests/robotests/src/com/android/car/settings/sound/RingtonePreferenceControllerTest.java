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

package com.android.car.settings.sound;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.content.Intent;
import android.media.RingtoneManager;
import android.net.Uri;

import androidx.lifecycle.Lifecycle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.ActivityResultCallback;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowRingtone;
import com.android.car.settings.testutils.ShadowRingtoneManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowRingtoneManager.class, ShadowRingtone.class})
public class RingtonePreferenceControllerTest {

    private static final int TEST_RINGTONE_TYPE = RingtoneManager.TYPE_RINGTONE;
    private static final String TEST_PATH = "/test/path/uri";
    private static final Uri TEST_URI = new Uri.Builder().appendPath(TEST_PATH).build();
    private static final String TEST_TITLE = "Test Preference Title";
    private static final String TEST_RINGTONE_TITLE = "Test Ringtone Title";

    // These are copied from android.app.Activity. That class is not accessible from this test
    // because there is another test Activity with the same package.
    private static final int ACTIVITY_RESULT_OK = -1;
    private static final int ACTIVITY_RESULT_CANCELLED = 0;

    private Context mContext;
    private PreferenceControllerTestHelper<RingtonePreferenceController>
            mPreferenceControllerHelper;
    private RingtonePreferenceController mController;
    private RingtonePreference mRingtonePreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mRingtonePreference = new RingtonePreference(mContext, null);
        mRingtonePreference.setTitle(TEST_TITLE);
        mRingtonePreference.setRingtoneType(TEST_RINGTONE_TYPE);
        mRingtonePreference.setShowSilent(true);  // Default value when instantiated via xml.
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                RingtonePreferenceController.class, mRingtonePreference);
        mController = mPreferenceControllerHelper.getController();
        mPreferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        // Set the Uri to be null at the beginning of each test.
        ShadowRingtoneManager.setActualDefaultRingtoneUri(mContext, TEST_RINGTONE_TYPE,
                /* ringtoneUri= */ null);
    }

    @After
    public void tearDown() {
        ShadowRingtoneManager.reset();
        ShadowRingtone.reset();
    }

    @Test
    public void testRefreshUi_ringtoneTitleSet() {
        ShadowRingtoneManager.setActualDefaultRingtoneUri(mContext, TEST_RINGTONE_TYPE, TEST_URI);
        ShadowRingtone.setExpectedTitleForUri(TEST_URI, TEST_RINGTONE_TITLE);
        mController.refreshUi();
        assertThat(mRingtonePreference.getSummary()).isEqualTo(TEST_RINGTONE_TITLE);
    }

    @Test
    public void testHandlePreferenceClicked_listenerTriggered() {
        mRingtonePreference.performClick();
        verify(mPreferenceControllerHelper.getMockFragmentController()).startActivityForResult(
                any(Intent.class), anyInt(), any(ActivityResultCallback.class));
    }

    @Test
    public void testHandlePreferenceClicked_captureIntent_checkDefaultUri() {
        ShadowRingtoneManager.setActualDefaultRingtoneUri(mContext, TEST_RINGTONE_TYPE, TEST_URI);
        mRingtonePreference.performClick();
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mPreferenceControllerHelper.getMockFragmentController()).startActivityForResult(
                intent.capture(), anyInt(), any(ActivityResultCallback.class));
        assertThat((Uri) intent.getValue().getParcelableExtra(
                RingtoneManager.EXTRA_RINGTONE_EXISTING_URI)).isEqualTo(TEST_URI);
    }

    @Test
    public void testHandlePreferenceClicked_captureIntent_checkDialogTitle() {
        mRingtonePreference.performClick();
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mPreferenceControllerHelper.getMockFragmentController()).startActivityForResult(
                intent.capture(), anyInt(), any(ActivityResultCallback.class));
        assertThat(
                intent.getValue().getStringExtra(RingtoneManager.EXTRA_RINGTONE_TITLE)).isEqualTo(
                TEST_TITLE);
    }

    @Test
    public void testHandlePreferenceClicked_captureIntent_checkRingtoneType() {
        mRingtonePreference.performClick();
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mPreferenceControllerHelper.getMockFragmentController()).startActivityForResult(
                intent.capture(), anyInt(), any(ActivityResultCallback.class));
        assertThat(
                intent.getValue().getIntExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, -1)).isEqualTo(
                TEST_RINGTONE_TYPE);
    }

    @Test
    public void testHandlePreferenceClicked_captureIntent_checkShowSilent() {
        mRingtonePreference.performClick();
        ArgumentCaptor<Intent> intent = ArgumentCaptor.forClass(Intent.class);
        verify(mPreferenceControllerHelper.getMockFragmentController()).startActivityForResult(
                intent.capture(), anyInt(), any(ActivityResultCallback.class));
        assertThat(intent.getValue().getBooleanExtra(RingtoneManager.EXTRA_RINGTONE_SHOW_SILENT,
                false)).isTrue();
    }

    @Test
    public void testProcessActivityResult_wrongResult_defaultRingtoneNotSet() {
        mController.processActivityResult(RingtonePreferenceController.REQUEST_CODE,
                ACTIVITY_RESULT_CANCELLED, new Intent());
        assertThat(ShadowRingtoneManager.getActualDefaultRingtoneUri(mContext,
                TEST_RINGTONE_TYPE)).isNull();
    }

    @Test
    public void testProcessActivityResult_correctResult_nullIntent_defaultRingtoneNotSet() {
        mController.processActivityResult(RingtonePreferenceController.REQUEST_CODE,
                ACTIVITY_RESULT_OK, null);
        assertThat(ShadowRingtoneManager.getActualDefaultRingtoneUri(mContext,
                TEST_RINGTONE_TYPE)).isNull();
    }

    @Test
    public void testProcessActivityResult_correctResult_validIntent_defaultRingtoneSet() {
        Intent data = new Intent();
        data.putExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, TEST_URI);
        mController.processActivityResult(RingtonePreferenceController.REQUEST_CODE,
                ACTIVITY_RESULT_OK, data);
        assertThat(ShadowRingtoneManager.getActualDefaultRingtoneUri(mContext,
                TEST_RINGTONE_TYPE)).isEqualTo(TEST_URI);
    }
}
