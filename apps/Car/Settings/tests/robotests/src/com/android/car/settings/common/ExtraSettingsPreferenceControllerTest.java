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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.ShadowApplicationPackageManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.HashMap;
import java.util.Map;

/** Unit test for {@link ExtraSettingsPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowApplicationPackageManager.class})
public class ExtraSettingsPreferenceControllerTest {

    private static final Intent FAKE_INTENT = new Intent();

    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private ExtraSettingsPreferenceController mController;
    private PreferenceControllerTestHelper<ExtraSettingsPreferenceController>
            mPreferenceControllerHelper;
    private Map<Preference, Bundle> mPreferenceBundleMapEmpty = new HashMap<>();
    private Map<Preference, Bundle> mPreferenceBundleMap = new HashMap<>();

    @Mock
    private ExtraSettingsLoader mExtraSettingsLoaderMock;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        mPreferenceGroup.setIntent(FAKE_INTENT);
        mPreferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                ExtraSettingsPreferenceController.class, mPreferenceGroup);
        mController = mPreferenceControllerHelper.getController();
        Preference preference = new Preference(mContext);

        Bundle bundle = new Bundle();
        mPreferenceBundleMap = new HashMap<>();
        mPreferenceBundleMap.put(preference, bundle);
    }

    @After
    public void tearDown() {
        ShadowApplicationPackageManager.reset();
    }

    @Test
    public void testRefreshUi_notInitializedYet() {
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
    }

    @Test
    public void testRefreshUi_initialized_noPreferenceAdded() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMapEmpty);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(0);
        assertThat(mPreferenceGroup.isVisible()).isEqualTo(false);
    }

    @Test
    public void testRefreshUi_noPreferenceAdded_shouldNotBeVisible() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMapEmpty);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(false);
    }

    @Test
    public void testRefreshUi_initialized_preferenceAdded() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMap);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void testRefreshUi_preferenceAdded_shouldBeVisible() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMap);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(true);
    }

    @Test
    public void testRefreshUi_refreshedTwice_shouldOnlyAddPreferenceOnce() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMap);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();
        mController.refreshUi();

        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void testRefreshUi_refreshedTwice_stillBeVisible() {
        when(mExtraSettingsLoaderMock.loadPreferences(FAKE_INTENT)).thenReturn(
                mPreferenceBundleMap);

        mController.setExtraSettingsLoader(mExtraSettingsLoaderMock);
        mPreferenceControllerHelper.markState(Lifecycle.State.CREATED);
        mController.refreshUi();
        mController.refreshUi();

        assertThat(mPreferenceGroup.isVisible()).isEqualTo(true);
    }
}
