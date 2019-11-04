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

package com.android.car.pm;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertTrue;

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.CarUxRestrictionsManagerService;
import com.android.car.SystemActivityMonitoringService;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Tests for {@link CarPackageManagerService}.
 */
@RunWith(AndroidJUnit4.class)
public class CarPackageManagerServiceTest {
    CarPackageManagerService mService;

    private Context mContext;
    @Mock
    private CarUxRestrictionsManagerService mMockUxrService;
    @Mock
    private SystemActivityMonitoringService mMockSamService;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = InstrumentationRegistry.getTargetContext();

        mService = new CarPackageManagerService(mContext, mMockUxrService, mMockSamService,
                new CarUserManagerHelper(mContext));
    }

    @Test
    public void testParseConfigList_SingleActivity() {
        String config = "com.android.test/.TestActivity";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertTrue(map.get("com.android.test").size() == 1);
        assertEquals(".TestActivity", map.get("com.android.test").iterator().next());
    }

    @Test
    public void testParseConfigList_Package() {
        String config = "com.android.test";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertTrue(map.get("com.android.test").size() == 0);
    }

    @Test
    public void testParseConfigList_MultipleActivities() {
        String config = "com.android.test/.TestActivity0,com.android.test/.TestActivity1";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertTrue(map.get("com.android.test").size() == 2);
        assertTrue(map.get("com.android.test").contains(".TestActivity0"));
        assertTrue(map.get("com.android.test").contains(".TestActivity1"));
    }

    @Test
    public void testParseConfigList_PackageAndActivity() {
        String config = "com.android.test/.TestActivity0,com.android.test";
        Map<String, Set<String>> map = new HashMap<>();

        mService.parseConfigList(config, map);

        assertTrue(map.get("com.android.test").size() == 0);
    }
}
