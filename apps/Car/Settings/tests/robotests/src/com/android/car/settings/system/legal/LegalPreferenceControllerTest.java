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

package com.android.car.settings.system.legal;

import static com.google.common.truth.Truth.assertThat;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.lifecycle.Lifecycle;
import androidx.preference.Preference;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceControllerTestHelper;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.Collections;
import java.util.LinkedList;
import java.util.List;

/** Unit test for {@link LegalPreferenceController}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class LegalPreferenceControllerTest {
    private static class TestLegalPreferenceControllerTest extends
            LegalPreferenceController {

        private static final Intent INTENT = new Intent("test_intent");

        TestLegalPreferenceControllerTest(Context context, String preferenceKey,
                FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
            super(context, preferenceKey, fragmentController, uxRestrictions);
        }

        @Override
        protected Intent getIntent() {
            return INTENT;
        }
    }

    private static final String TEST_LABEL = "test_label";
    private Context mContext;
    private PreferenceControllerTestHelper<TestLegalPreferenceControllerTest> mControllerHelper;
    private Preference mPreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        mPreference = new Preference(mContext);
        mControllerHelper = new PreferenceControllerTestHelper<>(mContext,
                TestLegalPreferenceControllerTest.class, mPreference);
    }

    @Test
    public void refreshUi_intentResolvesToActivity_isVisible() {
        Intent intent = mControllerHelper.getController().getIntent();

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = "some.test.package";
        activityInfo.name = "SomeActivity";
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;

        ResolveInfo resolveInfo = new ResolveInfo() {
            @Override
            public CharSequence loadLabel(PackageManager pm) {
                return TEST_LABEL;
            }
        };
        resolveInfo.activityInfo = activityInfo;
        List<ResolveInfo> list = new LinkedList();
        list.add(resolveInfo);

        ShadowPackageManager packageManager = Shadows.shadowOf(mContext.getPackageManager());
        packageManager.addResolveInfoForIntent(intent, list);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mControllerHelper.getController().refreshUi();

        assertThat(mPreference.isVisible()).isTrue();
    }

    @Test
    public void refreshUi_intentResolvesToActivity_updatesTitle() {
        Intent intent = mControllerHelper.getController().getIntent();

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = "some.test.package";
        activityInfo.name = "SomeActivity";
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        activityInfo.applicationInfo.nonLocalizedLabel = TEST_LABEL;

        ResolveInfo resolveInfo = new ResolveInfo();
        resolveInfo.activityInfo = activityInfo;

        List<ResolveInfo> list = new LinkedList();
        list.add(resolveInfo);

        ShadowPackageManager packageManager = Shadows.shadowOf(mContext.getPackageManager());
        packageManager.addResolveInfoForIntent(intent, list);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mControllerHelper.getController().refreshUi();

        assertThat(mPreference.getTitle()).isEqualTo(TEST_LABEL);
    }

    @Test
    public void refreshUi_intentResolvesToActivity_updatesIntentToSpecificActivity() {
        Intent intent = mControllerHelper.getController().getIntent();

        String packageName = "com.android.car.settings.testutils";
        String activityName = "BaseTestActivity";

        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = activityName;
        activityInfo.applicationInfo = new ApplicationInfo();
        activityInfo.applicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;

        ResolveInfo resolveInfo = new ResolveInfo() {
            @Override
            public CharSequence loadLabel(PackageManager pm) {
                return TEST_LABEL;
            }
        };
        resolveInfo.activityInfo = activityInfo;
        List<ResolveInfo> list = new LinkedList();
        list.add(resolveInfo);

        ShadowPackageManager packageManager = Shadows.shadowOf(mContext.getPackageManager());
        packageManager.addResolveInfoForIntent(intent, list);

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mControllerHelper.getController().refreshUi();

        assertThat(mPreference.getIntent().getComponent().flattenToString()).isEqualTo(
                packageName + "/" + activityName);
    }

    @Test
    public void refreshUi_intentResolvesToNull_isNotVisible() {
        ShadowPackageManager packageManager = Shadows.shadowOf(mContext.getPackageManager());

        packageManager.addResolveInfoForIntent(mControllerHelper.getController().getIntent(),
                Collections.emptyList());

        mControllerHelper.markState(Lifecycle.State.CREATED);
        mControllerHelper.getController().refreshUi();

        assertThat(mPreference.isVisible()).isFalse();
    }
}
