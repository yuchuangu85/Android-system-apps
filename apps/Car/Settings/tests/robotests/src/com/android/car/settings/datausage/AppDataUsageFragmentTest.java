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

package com.android.car.settings.datausage;

import static com.google.common.truth.Truth.assertThat;

import android.net.INetworkStatsService;
import android.net.NetworkPolicy;
import android.net.NetworkPolicyManager;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.util.Pair;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.testutils.FragmentController;
import com.android.car.settings.testutils.ShadowINetworkStatsServiceStub;
import com.android.car.settings.testutils.ShadowNetworkPolicyEditor;
import com.android.car.settings.testutils.ShadowNetworkPolicyManager;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.annotation.Config;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Iterator;

/** Unit test for {@link AppDataUsageFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowNetworkPolicyEditor.class, ShadowNetworkPolicyManager.class,
        ShadowINetworkStatsServiceStub.class})
public class AppDataUsageFragmentTest {

    private static final String KEY_START = "start";
    private static final String KEY_END = "end";

    private AppDataUsageFragment mFragment;
    private FragmentController<AppDataUsageFragment> mFragmentController;

    @Mock
    private NetworkPolicy mNetworkPolicy;

    @Mock
    private NetworkPolicyManager mNetworkPolicyManager;

    @Mock
    private INetworkStatsService mINetworkStatsService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mFragment = new AppDataUsageFragment();
        mFragmentController = FragmentController.of(mFragment);

        ShadowNetworkPolicyManager.setNetworkPolicyManager(mNetworkPolicyManager);
        ShadowINetworkStatsServiceStub.setINetworkStatsSession(mINetworkStatsService);
    }

    @After
    public void tearDown() {
        ShadowNetworkPolicyEditor.reset();
        ShadowINetworkStatsServiceStub.reset();
        ShadowNetworkPolicyManager.reset();
    }

    @Test
    public void onActivityCreated_policyIsNull_startAndEndDateShouldHaveFourWeeksDifference() {
        mFragmentController.create();

        Bundle bundle = mFragment.getBundle();
        long start = bundle.getLong(KEY_START);
        long end = bundle.getLong(KEY_END);
        long timeDiff = end - start;

        assertThat(timeDiff).isEqualTo(DateUtils.WEEK_IN_MILLIS * 4);
    }

    @Test
    public void onActivityCreated_iteratorIsEmpty_startAndEndDateShouldHaveFourWeeksDifference() {
        ShadowNetworkPolicyEditor.setNetworkPolicy(mNetworkPolicy);

        ArrayList<Pair<ZonedDateTime, ZonedDateTime>> list = new ArrayList<>();
        Iterator iterator = list.iterator();
        ShadowNetworkPolicyManager.setCycleIterator(iterator);
        mFragmentController.create();

        Bundle bundle = mFragment.getBundle();
        long start = bundle.getLong(KEY_START);
        long end = bundle.getLong(KEY_END);
        long timeDiff = end - start;

        assertThat(timeDiff).isEqualTo(DateUtils.WEEK_IN_MILLIS * 4);
    }

    @Test
    public void onActivityCreated_iteratorIsNotEmpty_startAndEndDateShouldBeLastOneInIterator() {
        ShadowNetworkPolicyEditor.setNetworkPolicy(mNetworkPolicy);

        ZonedDateTime start1 = ZonedDateTime.now();
        ZonedDateTime end1 = ZonedDateTime.now();
        ZonedDateTime start2 = ZonedDateTime.now();
        ZonedDateTime end2 = ZonedDateTime.now();

        Pair pair1 = new Pair(start1, end1);
        Pair pair2 = new Pair(start2, end2);

        ArrayList<Pair<ZonedDateTime, ZonedDateTime>> list = new ArrayList<>();
        list.add(pair1);
        list.add(pair2);

        Iterator iterator = list.iterator();
        ShadowNetworkPolicyManager.setCycleIterator(iterator);
        mFragmentController.create();

        Bundle bundle = mFragment.getBundle();
        long start = bundle.getLong(KEY_START);
        long end = bundle.getLong(KEY_END);

        assertThat(start).isEqualTo(start2.toInstant().toEpochMilli());
        assertThat(end).isEqualTo(end2.toInstant().toEpochMilli());
    }
}
