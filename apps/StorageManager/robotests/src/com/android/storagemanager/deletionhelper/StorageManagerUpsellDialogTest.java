/*
 * Copyright (C) 2016 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.android.storagemanager.deletionhelper;

import android.content.Context;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

import java.util.concurrent.TimeUnit;

import static android.content.DialogInterface.BUTTON_NEGATIVE;
import static com.google.common.truth.Truth.assertThat;
import static org.mockito.Mockito.when;
import static org.robolectric.util.FragmentTestUtil.startFragment;

@RunWith(RobolectricTestRunner.class)
public class StorageManagerUpsellDialogTest {
    @Mock private StorageManagerUpsellDialog.Clock mClock;

    @Test
    public void testNoThanksMaximumShownTimes() {
        MockitoAnnotations.initMocks(this);
        final Context context = RuntimeEnvironment.application;
        StorageManagerUpsellDialog fragment = StorageManagerUpsellDialog.newInstance(0);
        fragment.setClock(mClock);

        assertThat(StorageManagerUpsellDialog.shouldShow(context, TimeUnit.DAYS.toMillis(90)))
                .isTrue();

        startFragment(fragment);
        fragment.onClick(null, BUTTON_NEGATIVE);
        when(mClock.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(90 * 2));
        assertThat(StorageManagerUpsellDialog.shouldShow(context, TimeUnit.DAYS.toMillis(90 * 2)))
                .isTrue();

        startFragment(fragment);
        fragment.onClick(null, BUTTON_NEGATIVE);
        when(mClock.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(90 * 3));
        assertThat(StorageManagerUpsellDialog.shouldShow(context, TimeUnit.DAYS.toMillis(90 * 3)))
                .isTrue();

        startFragment(fragment);
        fragment.onClick(null, BUTTON_NEGATIVE);
        when(mClock.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(90 * 4));
        assertThat(StorageManagerUpsellDialog.shouldShow(context, TimeUnit.DAYS.toMillis(90 * 4)))
                .isTrue();

        startFragment(fragment);
        fragment.onClick(null, BUTTON_NEGATIVE);
        when(mClock.currentTimeMillis()).thenReturn(TimeUnit.DAYS.toMillis(90 * 5));
        assertThat(StorageManagerUpsellDialog.shouldShow(context, TimeUnit.DAYS.toMillis(90 * 5)))
                .isFalse();
    }
}
