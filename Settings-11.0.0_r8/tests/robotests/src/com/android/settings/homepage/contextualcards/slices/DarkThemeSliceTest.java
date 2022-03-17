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

package com.android.settings.homepage.contextualcards.slices;

import static android.content.res.Configuration.UI_MODE_NIGHT_NO;
import static android.content.res.Configuration.UI_MODE_NIGHT_YES;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.app.UiModeManager;
import android.content.Context;
import android.net.Uri;
import android.os.BatteryManager;
import android.os.PowerManager;

import androidx.slice.Slice;
import androidx.slice.SliceMetadata;
import androidx.slice.SliceProvider;
import androidx.slice.widget.SliceLiveData;

import com.android.settings.R;
import com.android.settings.slices.CustomSliceRegistry;
import com.android.settings.slices.SlicesFeatureProviderImpl;
import com.android.settings.testutils.FakeFeatureFactory;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class DarkThemeSliceTest {
    @Mock
    private BatteryManager mBatteryManager;
    @Mock
    private PowerManager mPowerManager;
    @Mock
    private UiModeManager mUiModeManager;

    private Context mContext;
    private DarkThemeSlice mDarkThemeSlice;
    private FakeFeatureFactory mFeatureFactory;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        mFeatureFactory = FakeFeatureFactory.setupForTest();
        mFeatureFactory.slicesFeatureProvider = new SlicesFeatureProviderImpl();
        mFeatureFactory.slicesFeatureProvider.newUiSession();
        doReturn(mPowerManager).when(mContext).getSystemService(PowerManager.class);
        when(mPowerManager.isPowerSaveMode()).thenReturn(false);
        doReturn(mUiModeManager).when(mContext).getSystemService(UiModeManager.class);

        // Set-up specs for SliceMetadata.
        SliceProvider.setSpecs(SliceLiveData.SUPPORTED_SPECS);
        mDarkThemeSlice = new DarkThemeSlice(mContext);
        mDarkThemeSlice.sKeepSliceShow = false;
    }

    @Test
    public void getUri_shouldBeDarkThemeSliceUri() {
        final Uri uri = mDarkThemeSlice.getUri();

        assertThat(uri).isEqualTo(CustomSliceRegistry.DARK_THEME_SLICE_URI);
    }

    @Test
    public void isAvailable_inDarkThemeMode_returnFalse() {
        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_YES;

        assertThat(mDarkThemeSlice.isAvailable(mContext)).isFalse();
    }

    @Test
    public void isAvailable_nonDarkTheme_autoNightMode_returnFalse() {
        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_NO;
        setNightMode(UiModeManager.MODE_NIGHT_AUTO);

        assertThat(mDarkThemeSlice.isAvailable(mContext)).isFalse();
    }

    @Test
    public void isAvailable_nonDarkTheme_customNightMode_returnFalse() {
        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_NO;
        setNightMode(UiModeManager.MODE_NIGHT_CUSTOM);

        assertThat(mDarkThemeSlice.isAvailable(mContext)).isFalse();
    }

    @Test
    public void isAvailable_nonDarkThemeBatteryCapacityEq100_returnFalse() {
        setBatteryCapacityLevel(100);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        assertThat(mDarkThemeSlice.isAvailable(mContext)).isFalse();
    }

    @Test
    public void isAvailable_nonDarkThemeBatteryCapacityLt50_returnTrue() {
        setBatteryCapacityLevel(40);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        assertThat(mDarkThemeSlice.isAvailable(mContext)).isTrue();
    }

    @Test
    public void getSlice_batterySaver_returnErrorSlice() {
        when(mPowerManager.isPowerSaveMode()).thenReturn(true);

        final Slice mediaSlice = mDarkThemeSlice.getSlice();
        final SliceMetadata metadata = SliceMetadata.from(mContext, mediaSlice);
        assertThat(metadata.isErrorSlice()).isTrue();
    }

    @Test
    public void getSlice_notAvailable_returnErrorSlice() {
        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_YES;

        final Slice mediaSlice = mDarkThemeSlice.getSlice();
        final SliceMetadata metadata = SliceMetadata.from(mContext, mediaSlice);
        assertThat(metadata.isErrorSlice()).isTrue();
    }

    @Test
    public void getSlice_newSession_notAvailable_returnErrorSlice() {
        // previous displayed: yes
        mDarkThemeSlice.sKeepSliceShow = true;
        // Session: use original value + 1 to become a new session
        mDarkThemeSlice.sActiveUiSession =
                mFeatureFactory.slicesFeatureProvider.getUiSessionToken() + 1;

        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_YES;

        final Slice mediaSlice = mDarkThemeSlice.getSlice();
        final SliceMetadata metadata = SliceMetadata.from(mContext, mediaSlice);
        assertThat(metadata.isErrorSlice()).isTrue();
    }

    @Test
    public void getSlice_previouslyDisplayed_isAvailable_returnSlice() {
        mDarkThemeSlice.sActiveUiSession =
                mFeatureFactory.slicesFeatureProvider.getUiSessionToken();
        mDarkThemeSlice.sKeepSliceShow = true;
        setBatteryCapacityLevel(40);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        assertThat(mDarkThemeSlice.getSlice()).isNotNull();
    }

    @Test
    public void getSlice_sliceNotClicked_notAvailable_returnErrorSlice() {
        mDarkThemeSlice.sSliceClicked = false;

        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_YES;

        final Slice mediaSlice = mDarkThemeSlice.getSlice();
        final SliceMetadata metadata = SliceMetadata.from(mContext, mediaSlice);
        assertThat(metadata.isErrorSlice()).isTrue();
    }

    @Test
    public void getSlice_sliceClicked_isAvailable_returnSlice() {
        mDarkThemeSlice.sSliceClicked = true;

        setBatteryCapacityLevel(40);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        assertThat(mDarkThemeSlice.getSlice()).isNotNull();
    }

    @Test
    public void getSlice_isAvailable_returnSlice() {
        setBatteryCapacityLevel(40);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        assertThat(mDarkThemeSlice.getSlice()).isNotNull();
    }

    @Test
    public void getSlice_isAvailable_showTitleSubtitle() {
        setBatteryCapacityLevel(40);
        setNightMode(UiModeManager.MODE_NIGHT_NO);

        final Slice slice = mDarkThemeSlice.getSlice();
        final SliceMetadata metadata = SliceMetadata.from(mContext, slice);
        assertThat(metadata.getTitle()).isEqualTo(
                mContext.getString(R.string.dark_theme_slice_title));
        assertThat(metadata.getSubtitle()).isEqualTo(
                mContext.getString(R.string.dark_theme_slice_subtitle));
    }

    private void setBatteryCapacityLevel(int power_level) {
        mContext.getResources().getConfiguration().uiMode = UI_MODE_NIGHT_NO;
        doReturn(mBatteryManager).when(mContext).getSystemService(BatteryManager.class);
        when(mBatteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
                .thenReturn(power_level);
    }

    private void setNightMode(int mode) {
        when(mUiModeManager.getNightMode()).thenReturn(mode);
    }
}
