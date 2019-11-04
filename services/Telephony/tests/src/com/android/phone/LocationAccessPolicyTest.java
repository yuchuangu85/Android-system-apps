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

package com.android.phone;

import static org.junit.Assert.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.Manifest;
import android.app.AppOpsManager;
import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.location.LocationManager;
import android.os.Build;
import android.os.UserHandle;
import android.telephony.LocationAccessPolicy;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

@RunWith(Parameterized.class)
public class LocationAccessPolicyTest {
    private static class Scenario {
        static class Builder {
            private int mAppSdkLevel;
            private boolean mAppHasFineManifest = false;
            private boolean mAppHasCoarseManifest = false;
            private int mFineAppOp = AppOpsManager.MODE_IGNORED;
            private int mCoarseAppOp = AppOpsManager.MODE_IGNORED;
            private boolean mIsDynamicLocationEnabled;
            private LocationAccessPolicy.LocationPermissionQuery mQuery;
            private LocationAccessPolicy.LocationPermissionResult mExpectedResult;
            private String mName;

            public Builder setAppSdkLevel(int appSdkLevel) {
                mAppSdkLevel = appSdkLevel;
                return this;
            }

            public Builder setAppHasFineManifest(boolean appHasFineManifest) {
                mAppHasFineManifest = appHasFineManifest;
                return this;
            }

            public Builder setAppHasCoarseManifest(
                    boolean appHasCoarseManifest) {
                mAppHasCoarseManifest = appHasCoarseManifest;
                return this;
            }

            public Builder setFineAppOp(int fineAppOp) {
                mFineAppOp = fineAppOp;
                return this;
            }

            public Builder setCoarseAppOp(int coarseAppOp) {
                mCoarseAppOp = coarseAppOp;
                return this;
            }

            public Builder setIsDynamicLocationEnabled(
                    boolean isDynamicLocationEnabled) {
                mIsDynamicLocationEnabled = isDynamicLocationEnabled;
                return this;
            }

            public Builder setQuery(
                    LocationAccessPolicy.LocationPermissionQuery query) {
                mQuery = query;
                return this;
            }

            public Builder setExpectedResult(
                    LocationAccessPolicy.LocationPermissionResult expectedResult) {
                mExpectedResult = expectedResult;
                return this;
            }

            public Builder setName(String name) {
                mName = name;
                return this;
            }

            public Scenario build() {
                return new Scenario(mAppSdkLevel, mAppHasFineManifest, mAppHasCoarseManifest,
                        mFineAppOp, mCoarseAppOp, mIsDynamicLocationEnabled, mQuery,
                        mExpectedResult, mName);
            }
        }
        int appSdkLevel;
        boolean appHasFineManifest;
        boolean appHasCoarseManifest;
        int fineAppOp;
        int coarseAppOp;
        boolean isDynamicLocationEnabled;
        LocationAccessPolicy.LocationPermissionQuery query;
        LocationAccessPolicy.LocationPermissionResult expectedResult;
        String name;

        private Scenario(int appSdkLevel, boolean appHasFineManifest, boolean appHasCoarseManifest,
                int fineAppOp, int coarseAppOp,
                boolean isDynamicLocationEnabled,
                LocationAccessPolicy.LocationPermissionQuery query,
                LocationAccessPolicy.LocationPermissionResult expectedResult,
                String name) {
            this.appSdkLevel = appSdkLevel;
            this.appHasFineManifest = appHasFineManifest;
            this.appHasCoarseManifest = appHasFineManifest || appHasCoarseManifest;
            this.fineAppOp = fineAppOp;
            this.coarseAppOp = coarseAppOp == AppOpsManager.MODE_ALLOWED ? coarseAppOp : fineAppOp;
            this.isDynamicLocationEnabled = isDynamicLocationEnabled;
            this.query = query;
            this.expectedResult = expectedResult;
            this.name = name;
        }

        @Override
        public String toString() {
            return name;
        }
    }

    @Mock Context mContext;
    @Mock AppOpsManager mAppOpsManager;
    @Mock LocationManager mLocationManager;
    @Mock PackageManager mPackageManager;
    Scenario mScenario;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mockContextSystemService(AppOpsManager.class, mAppOpsManager);
        mockContextSystemService(LocationManager.class, mLocationManager);
        mockContextSystemService(PackageManager.class, mPackageManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
    }

    private <T> void mockContextSystemService(Class<T> clazz , T obj) {
        when(mContext.getSystemServiceName(eq(clazz))).thenReturn(clazz.getSimpleName());
        when(mContext.getSystemService(clazz.getSimpleName())).thenReturn(obj);
    }

    public LocationAccessPolicyTest(Scenario scenario) {
        mScenario = scenario;
    }


    @Test
    public void test() {
        setupScenario(mScenario);
        assertEquals(mScenario.expectedResult,
                LocationAccessPolicy.checkLocationPermission(mContext, mScenario.query));
    }

    private void setupScenario(Scenario s) {
        when(mContext.checkPermission(eq(Manifest.permission.ACCESS_FINE_LOCATION),
                anyInt(), anyInt())).thenReturn(s.appHasFineManifest
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);

        when(mContext.checkPermission(eq(Manifest.permission.ACCESS_COARSE_LOCATION),
                anyInt(), anyInt())).thenReturn(s.appHasCoarseManifest
                ? PackageManager.PERMISSION_GRANTED : PackageManager.PERMISSION_DENIED);

        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_FINE_LOCATION),
                anyInt(), anyString()))
                .thenReturn(s.fineAppOp);
        when(mAppOpsManager.noteOpNoThrow(eq(AppOpsManager.OP_COARSE_LOCATION),
                anyInt(), anyString()))
                .thenReturn(s.coarseAppOp);

        if (s.isDynamicLocationEnabled) {
            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class))).thenReturn(true);
            when(mContext.checkPermission(eq(Manifest.permission.INTERACT_ACROSS_USERS_FULL),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_GRANTED);
        } else {
            when(mLocationManager.isLocationEnabledForUser(any(UserHandle.class)))
                    .thenReturn(false);
            when(mContext.checkPermission(eq(Manifest.permission.INTERACT_ACROSS_USERS_FULL),
                    anyInt(), anyInt())).thenReturn(PackageManager.PERMISSION_DENIED);
        }

        ApplicationInfo fakeAppInfo = new ApplicationInfo();
        fakeAppInfo.targetSdkVersion = s.appSdkLevel;

        try {
            when(mPackageManager.getApplicationInfo(anyString(), anyInt()))
                    .thenReturn(fakeAppInfo);
        } catch (Exception e) {
            // this is a formality
        }
    }

    private static LocationAccessPolicy.LocationPermissionQuery.Builder getDefaultQueryBuilder() {
        return new LocationAccessPolicy.LocationPermissionQuery.Builder()
                .setMethod("test")
                .setCallingPackage("com.android.test")
                .setCallingPid(10001)
                .setCallingUid(10001);
    }

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Scenario> getScenarios() {
        List<Scenario> scenarios = new ArrayList<>();
        scenarios.add(new Scenario.Builder()
                .setName("System location is off")
                .setAppHasFineManifest(true)
                .setFineAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(false)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.N)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.N).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.DENIED_SOFT)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on latest SDK level has all proper permissions for fine")
                .setAppHasFineManifest(true)
                .setFineAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.N)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.N).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.ALLOWED)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on older SDK level missing permissions for fine but has coarse")
                .setAppHasCoarseManifest(true)
                .setCoarseAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(Build.VERSION_CODES.JELLY_BEAN)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.M)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.JELLY_BEAN).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.ALLOWED)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on latest SDK level missing fine app ops permission")
                .setAppHasFineManifest(true)
                .setFineAppOp(AppOpsManager.MODE_ERRORED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.N)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.N).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.DENIED_HARD)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App has coarse permission but fine permission isn't being enforced yet")
                .setAppHasCoarseManifest(true)
                .setCoarseAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(LocationAccessPolicy.MAX_SDK_FOR_ANY_ENFORCEMENT + 1)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(
                                LocationAccessPolicy.MAX_SDK_FOR_ANY_ENFORCEMENT + 1)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.N).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.ALLOWED)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on latest SDK level has coarse but missing fine when fine is req.")
                .setAppHasCoarseManifest(true)
                .setCoarseAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.P)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.N).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.DENIED_HARD)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on latest SDK level has MODE_IGNORED for app ops on fine")
                .setAppHasCoarseManifest(true)
                .setCoarseAppOp(AppOpsManager.MODE_ALLOWED)
                .setFineAppOp(AppOpsManager.MODE_IGNORED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.P)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.O).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.DENIED_HARD)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App has no permissions but it's sdk level grandfathers it in")
                .setAppSdkLevel(Build.VERSION_CODES.N)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForFine(Build.VERSION_CODES.Q)
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.O).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.ALLOWED)
                .build());

        scenarios.add(new Scenario.Builder()
                .setName("App on latest SDK level has proper permissions for coarse")
                .setAppHasCoarseManifest(true)
                .setCoarseAppOp(AppOpsManager.MODE_ALLOWED)
                .setAppSdkLevel(Build.VERSION_CODES.P)
                .setIsDynamicLocationEnabled(true)
                .setQuery(getDefaultQueryBuilder()
                        .setMinSdkVersionForCoarse(Build.VERSION_CODES.P).build())
                .setExpectedResult(LocationAccessPolicy.LocationPermissionResult.ALLOWED)
                .build());
        return scenarios;
    }
}
