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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.view.View;

import androidx.appcompat.view.ContextThemeWrapper;
import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class MasterSwitchPreferenceTest {

    private PreferenceViewHolder mViewHolder;
    private MasterSwitchPreference mMasterSwitchPreference;
    @Mock
    private MasterSwitchPreference.OnSwitchToggleListener mListener;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        Context context = RuntimeEnvironment.application;
        Context themedContext = new ContextThemeWrapper(context, R.style.CarSettingTheme);
        View rootView = View.inflate(themedContext, R.layout.two_action_preference, null);
        View.inflate(themedContext, R.layout.master_switch_widget,
                rootView.findViewById(android.R.id.widget_frame));
        mViewHolder = PreferenceViewHolder.createInstanceForTests(rootView);
        mMasterSwitchPreference = new MasterSwitchPreference(context);

        mMasterSwitchPreference.onBindViewHolder(mViewHolder);
        mMasterSwitchPreference.setSwitchChecked(false);
        mMasterSwitchPreference.setSwitchToggleListener(mListener);
    }

    @Test
    public void widgetClicked_callsListener() {
        mViewHolder.findViewById(android.R.id.widget_frame).performClick();

        verify(mListener).onToggle(mMasterSwitchPreference, true);
    }

    @Test
    public void widgetClicked_togglesSwitchState() {
        mViewHolder.findViewById(android.R.id.widget_frame).performClick();

        assertThat(mMasterSwitchPreference.isSwitchChecked()).isTrue();
    }

    @Test
    public void setSwitchState_listenerSetAndButtonVisible_oppositeBool_callsListener() {
        mMasterSwitchPreference.setSwitchChecked(true);
        verify(mListener).onToggle(mMasterSwitchPreference, true);
    }

    @Test
    public void setSwitchState_listenerSetAndButtonVisible_oppositeBool_togglesSwitchState() {
        mMasterSwitchPreference.setSwitchChecked(true);
        assertThat(mMasterSwitchPreference.isSwitchChecked()).isTrue();
    }

    @Test
    public void setSwitchState_listenerSetAndButtonVisible_sameBool_listenerNotCalled() {
        mMasterSwitchPreference.setSwitchChecked(false);
        verify(mListener, never()).onToggle(eq(mMasterSwitchPreference), anyBoolean());
    }

    @Test
    public void setSwitchState_listenerSetAndButtonInvisible_oppositeBool_listenerNotCalled() {
        mMasterSwitchPreference.showAction(false);

        mMasterSwitchPreference.setSwitchChecked(true);
        verify(mListener, never()).onToggle(eq(mMasterSwitchPreference), anyBoolean());
    }
}
