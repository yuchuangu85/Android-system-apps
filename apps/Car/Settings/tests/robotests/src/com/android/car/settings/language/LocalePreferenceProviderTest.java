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

package com.android.car.settings.language;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceCategory;
import androidx.preference.PreferenceManager;
import androidx.preference.PreferenceScreen;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settingslib.R;
import com.android.internal.app.LocaleStore;
import com.android.internal.app.SuggestedLocaleAdapter;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class LocalePreferenceProviderTest {

    private static class Pair {
        int mItemType;
        LocaleStore.LocaleInfo mLocaleInfo;

        Pair(int itemType, LocaleStore.LocaleInfo localeInfo) {
            mItemType = itemType;
            mLocaleInfo = localeInfo;
        }
    }

    private Context mContext;
    private LocalePreferenceProvider mLocalePreferenceProvider;
    private LogicalPreferenceGroup mPreferenceGroup;
    // This list includes the expected values that should be returned by the SuggestedLocaleAdapter.
    // The index i in this list represents position, the itemType represents the return value for
    // getItemViewType given the index i, and mLocaleInfo represents the return value for getItem
    // given the index i.
    private List<Pair> mLocaleAdapterExpectedValues;
    @Mock
    private SuggestedLocaleAdapter mSuggestedLocaleAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mLocalePreferenceProvider = new LocalePreferenceProvider(mContext, mSuggestedLocaleAdapter);
        mLocaleAdapterExpectedValues = new ArrayList<>();

        // LogicalPreferenceGroup needs to be part of a PreferenceScreen in order for it to add
        // additional preferences.
        PreferenceScreen screen = new PreferenceManager(mContext).createPreferenceScreen(mContext);
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        screen.addPreference(mPreferenceGroup);
    }

    @Test
    public void testPopulateBasePreference_noSubSections() {
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.US)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.UK)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.CANADA)));
        prepareSuggestedLocaleAdapterMock();

        mLocalePreferenceProvider.populateBasePreference(mPreferenceGroup, mock(
                Preference.OnPreferenceClickListener.class));
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(3);
    }

    @Test
    public void testPopulateBasePreference_withSubSections() {
        mLocaleAdapterExpectedValues.add(
                new Pair(LocalePreferenceProvider.TYPE_HEADER_SUGGESTED, null));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.US)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.FRANCE)));
        mLocaleAdapterExpectedValues.add(
                new Pair(LocalePreferenceProvider.TYPE_HEADER_ALL_OTHERS, null));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.UK)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.CANADA)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.KOREA)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.CHINA)));
        prepareSuggestedLocaleAdapterMock();

        mLocalePreferenceProvider.populateBasePreference(mPreferenceGroup, mock(
                Preference.OnPreferenceClickListener.class));
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(2);

        PreferenceCategory firstCategory = (PreferenceCategory) mPreferenceGroup.getPreference(0);
        assertThat(firstCategory.getTitle()).isEqualTo(
                mContext.getString(R.string.language_picker_list_suggested_header));
        assertThat(firstCategory.getPreferenceCount()).isEqualTo(2);

        PreferenceCategory secondCategory = (PreferenceCategory) mPreferenceGroup.getPreference(1);
        assertThat(secondCategory.getTitle()).isEqualTo(
                mContext.getString(R.string.language_picker_list_all_header));
        assertThat(secondCategory.getPreferenceCount()).isEqualTo(4);
    }

    @Test
    public void testClickListenerTriggered() {
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.US)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.UK)));
        mLocaleAdapterExpectedValues.add(new Pair(LocalePreferenceProvider.TYPE_LOCALE,
                LocaleStore.getLocaleInfo(Locale.CANADA)));
        prepareSuggestedLocaleAdapterMock();

        Preference.OnPreferenceClickListener listener = mock(
                Preference.OnPreferenceClickListener.class);
        mLocalePreferenceProvider.populateBasePreference(mPreferenceGroup, listener);

        mPreferenceGroup.getPreference(0).performClick();
        verify(listener).onPreferenceClick(mPreferenceGroup.getPreference(0));
        mPreferenceGroup.getPreference(1).performClick();
        verify(listener).onPreferenceClick(mPreferenceGroup.getPreference(1));
        mPreferenceGroup.getPreference(2).performClick();
        verify(listener).onPreferenceClick(mPreferenceGroup.getPreference(2));
    }

    private void prepareSuggestedLocaleAdapterMock() {
        for (int i = 0; i < mLocaleAdapterExpectedValues.size(); i++) {
            Pair entry = mLocaleAdapterExpectedValues.get(i);
            int itemType = entry.mItemType;
            LocaleStore.LocaleInfo localeInfo = entry.mLocaleInfo;

            when(mSuggestedLocaleAdapter.getItemViewType(i)).thenReturn(itemType);
            when(mSuggestedLocaleAdapter.getItem(i)).thenReturn(localeInfo);
        }

        when(mSuggestedLocaleAdapter.getCount()).thenReturn(mLocaleAdapterExpectedValues.size());
    }
}
