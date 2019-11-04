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

import android.content.Context;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;

@RunWith(CarSettingsRobolectricTestRunner.class)
public class ProgressBarPreferenceTest {

    private static final String TEST_LABEL = "TEST_LABEL";

    private Context mContext;
    private PreferenceViewHolder mViewHolder;
    private ProgressBarPreference mProgressBarPreference;

    @Before
    public void setUp() {
        mContext = RuntimeEnvironment.application;
        View rootView = View.inflate(mContext, R.layout.progress_bar_preference,
                /* root= */ null);
        mViewHolder = PreferenceViewHolder.createInstanceForTests(rootView);
        mProgressBarPreference = new ProgressBarPreference(mContext);
    }

    @Test
    public void setMinLabel_setsText() {
        mProgressBarPreference.setMinLabel(TEST_LABEL);
        mProgressBarPreference.onBindViewHolder(mViewHolder);

        assertThat(getMinLabel().getText()).isEqualTo(TEST_LABEL);
    }

    @Test
    public void setMaxLabel_setsText() {
        mProgressBarPreference.setMaxLabel(TEST_LABEL);
        mProgressBarPreference.onBindViewHolder(mViewHolder);

        assertThat(getMaxLabel().getText()).isEqualTo(TEST_LABEL);
    }

    @Test
    public void setMin_setsMin() {
        mProgressBarPreference.setMin(10);
        mProgressBarPreference.onBindViewHolder(mViewHolder);

        assertThat(getProgressBar().getMin()).isEqualTo(10);
    }

    @Test
    public void setMax_setsMax() {
        mProgressBarPreference.setMax(1000);
        mProgressBarPreference.onBindViewHolder(mViewHolder);

        assertThat(getProgressBar().getMax()).isEqualTo(1000);
    }

    @Test
    public void setProgress_setsProgress() {
        mProgressBarPreference.setProgress(40);
        mProgressBarPreference.onBindViewHolder(mViewHolder);

        assertThat(getProgressBar().getProgress()).isEqualTo(40);
    }

    private ProgressBar getProgressBar() {
        return (ProgressBar) mViewHolder.findViewById(android.R.id.progress);
    }

    private TextView getMinLabel() {
        return (TextView) mViewHolder.findViewById(android.R.id.text1);
    }

    private TextView getMaxLabel() {
        return (TextView) mViewHolder.findViewById(android.R.id.text2);
    }
}
