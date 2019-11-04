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

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.LogicalPreferenceGroup;
import com.android.car.settings.common.PreferenceControllerTestHelper;
import com.android.car.settings.testutils.ShadowLocalePicker;
import com.android.car.settings.testutils.ShadowLocaleStore;
import com.android.internal.app.LocaleStore;
import com.android.internal.app.SuggestedLocaleAdapter;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import java.util.Locale;

@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowLocalePicker.class, ShadowLocaleStore.class})
public class LanguageBasePreferenceControllerTest {

    private static final LocaleStore.LocaleInfo TEST_LOCALE_INFO = LocaleStore.getLocaleInfo(
            Locale.FRENCH);
    private static final Locale HAS_MULTIPLE_CHILD_LOCALE = Locale.ENGLISH;
    private static final Locale HAS_CHILD_LOCALE = Locale.KOREAN;
    private static final Locale NO_CHILD_LOCALE = Locale.FRANCE;

    private static class TestLanguageBasePreferenceController extends
            LanguageBasePreferenceController {

        private SuggestedLocaleAdapter mAdapter;

        TestLanguageBasePreferenceController(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        public void setAdapter(SuggestedLocaleAdapter adapter) {
            mAdapter = adapter;
        }

        @Override
        protected LocalePreferenceProvider defineLocaleProvider() {
            return new LocalePreferenceProvider(getContext(), mAdapter);
        }
    }

    private TestLanguageBasePreferenceController mController;
    private Context mContext;
    private PreferenceGroup mPreferenceGroup;
    private FragmentController mFragmentController;
    @Mock
    private SuggestedLocaleAdapter mSuggestedLocaleAdapter;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mPreferenceGroup = new LogicalPreferenceGroup(mContext);
        PreferenceControllerTestHelper<TestLanguageBasePreferenceController>
                preferenceControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestLanguageBasePreferenceController.class, mPreferenceGroup);
        mController = preferenceControllerHelper.getController();
        mController.setAdapter(mSuggestedLocaleAdapter);
        mFragmentController = preferenceControllerHelper.getMockFragmentController();
        preferenceControllerHelper.sendLifecycleEvent(Lifecycle.Event.ON_CREATE);

        // Note that ENGLISH has 2 child locales.
        ShadowLocaleStore.addLocaleRelationship(Locale.ENGLISH, Locale.CANADA);
        ShadowLocaleStore.addLocaleRelationship(Locale.ENGLISH, Locale.US);

        // Note that KOREAN has 1 child locale.
        ShadowLocaleStore.addLocaleRelationship(Locale.KOREAN, Locale.KOREA);
    }

    @After
    public void tearDown() {
        ShadowLocaleStore.reset();
        ShadowLocalePicker.reset();
    }

    @Test
    public void testRefreshUi_groupConstructed() {
        when(mSuggestedLocaleAdapter.getCount()).thenReturn(1);
        when(mSuggestedLocaleAdapter.getItemViewType(0)).thenReturn(
                LocalePreferenceProvider.TYPE_LOCALE);
        when(mSuggestedLocaleAdapter.getItem(0)).thenReturn(TEST_LOCALE_INFO);
        mController.refreshUi();
        assertThat(mPreferenceGroup.getPreferenceCount()).isEqualTo(1);
    }

    @Test
    public void testOnPreferenceClick_noLocale_returnsFalse() {
        assertThat(mController.onPreferenceClick(new Preference(mContext))).isFalse();
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_returnsTrue() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_MULTIPLE_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_localeNotUpdated() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_MULTIPLE_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        assertThat(ShadowLocalePicker.localeWasUpdated()).isFalse();
    }

    @Test
    public void testOnPreferenceClick_hasMultipleChildLocales_neverCallsGoBack() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_MULTIPLE_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        verify(mFragmentController, never()).goBack();
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_returnsTrue() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_localeUpdated() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        assertThat(ShadowLocalePicker.localeWasUpdated()).isTrue();
    }

    @Test
    public void testOnPreferenceClick_hasSingleChildLocale_callsGoBack() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(HAS_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        verify(mFragmentController).goBack();
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_returnsTrue() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(NO_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        assertThat(mController.onPreferenceClick(preference)).isTrue();
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_localeUpdated() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(NO_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        assertThat(ShadowLocalePicker.localeWasUpdated()).isTrue();
    }

    @Test
    public void testOnPreferenceClick_noChildLocale_callsGoBack() {
        LocaleStore.LocaleInfo localeInfo = LocaleStore.getLocaleInfo(NO_CHILD_LOCALE);
        Preference preference = new Preference(mContext);
        LocaleUtil.setLocaleArgument(preference, localeInfo);
        mController.onPreferenceClick(preference);
        verify(mFragmentController).goBack();
    }
}
