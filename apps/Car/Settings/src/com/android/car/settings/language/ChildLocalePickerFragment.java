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

import android.content.Context;
import android.os.Bundle;
import android.widget.TextView;

import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;
import com.android.internal.app.LocaleStore;

import java.util.Locale;

/** Secondary screen for language selection, when a language has multiple locales. */
public class ChildLocalePickerFragment extends SettingsFragment {

    /**
     * Creates a ChildLocalePickerFragment with the parent locale info included in the arguments.
     */
    public static ChildLocalePickerFragment newInstance(LocaleStore.LocaleInfo parentLocaleInfo) {
        Bundle bundle = new Bundle();
        bundle.putSerializable(LocaleUtil.LOCALE_BUNDLE_KEY, parentLocaleInfo.getLocale());
        ChildLocalePickerFragment fragment = new ChildLocalePickerFragment();
        fragment.setArguments(bundle);
        return fragment;
    }

    private LanguageBasePreferenceController.LocaleSelectedListener mLocaleSelectedListener;
    private LocaleStore.LocaleInfo mParentLocaleInfo;

    /**
     * Allows the creator of ChildLocalePickerFragment to include a listener for when the child
     * locale is selected.
     */
    public void registerChildLocaleSelectedListener(
            LanguageBasePreferenceController.LocaleSelectedListener listener) {
        mLocaleSelectedListener = listener;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        Locale locale = (Locale) getArguments().getSerializable(LocaleUtil.LOCALE_BUNDLE_KEY);
        mParentLocaleInfo = LocaleStore.getLocaleInfo(locale);
        use(ChildLocalePickerPreferenceController.class,
                R.string.pk_child_locale_picker).setParentLocaleInfo(mParentLocaleInfo);
        use(ChildLocalePickerPreferenceController.class,
                R.string.pk_child_locale_picker).setLocaleSelectedListener(
                mLocaleSelectedListener);
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        TextView titleView = getActivity().findViewById(R.id.title);
        titleView.setText(mParentLocaleInfo.getFullNameNative());
    }

    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.child_locale_picker_fragment;
    }
}
