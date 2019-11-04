/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.common.source;

import static com.google.common.truth.Truth.assertThat;

import static org.robolectric.RuntimeEnvironment.application;
import static org.robolectric.Shadows.shadowOf;

import android.annotation.NonNull;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Bundle;
import android.service.media.MediaBrowserService;

import com.android.car.arch.common.testing.InstantTaskExecutorRule;
import com.android.car.arch.common.testing.TestLifecycleOwner;
import com.android.car.media.common.TestConfig;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import org.robolectric.shadows.ShadowApplication;
import org.robolectric.shadows.ShadowPackageManager;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class MediaSourcesLiveDataTest {

    @Rule
    public final MockitoRule mMockitoRule = MockitoJUnit.rule();
    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    private static final String TEST_ACTIVITY_PACKAGE_1 = "activity_package1";
    private static final String TEST_ACTIVITY_PACKAGE_2 = "activity_package2";
    private static final String TEST_SERVICE_PACKAGE_1 = "service_package1";
    private static final String TEST_SERVICE_PACKAGE_2 = "service_package2";
    private static final String TEST_SERVICE_PACKAGE_WITH_METADATA = "service_package3";

    private MediaSourcesLiveData mMediaSources;
    private Intent mActivityIntent;
    private Intent mServiceIntent;

    @Before
    public void setUp() {
        mMediaSources = MediaSourcesLiveData.createForTesting(application);

        mActivityIntent = new Intent(Intent.ACTION_MAIN, null);
        mActivityIntent.addCategory(Intent.CATEGORY_APP_MUSIC);

        mServiceIntent = new Intent(MediaBrowserService.SERVICE_INTERFACE);
        ShadowPackageManager packageManager = shadowOf(application.getPackageManager());

        List<ResolveInfo> activityResolveInfo = buildActivityResolveInfo();
        List<ResolveInfo> allActivityResolveInfo = new ArrayList<>(activityResolveInfo);
        allActivityResolveInfo.add(newActivityResolveInfo(TEST_ACTIVITY_PACKAGE_2));
        for (ResolveInfo info : allActivityResolveInfo) {
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.activities = new ActivityInfo[]{info.activityInfo};
            packageInfo.packageName = info.activityInfo.packageName;
            packageInfo.applicationInfo = info.activityInfo.applicationInfo;
            packageManager.addPackage(packageInfo);
        }
        List<ResolveInfo> serviceResolveInfo = buildServiceResolveInfo();
        List<ResolveInfo> allServiceResolveInfo = new ArrayList<>(serviceResolveInfo);
        allServiceResolveInfo.add(newServiceResolveInfo(TEST_SERVICE_PACKAGE_2));
        for (ResolveInfo info : allServiceResolveInfo) {
            PackageInfo packageInfo = new PackageInfo();
            packageInfo.services = new ServiceInfo[]{info.serviceInfo};
            packageInfo.packageName = info.serviceInfo.packageName;
            packageInfo.applicationInfo = info.serviceInfo.applicationInfo;
            packageManager.addPackage(packageInfo);
        }
        setPackageManagerResolveInfos(activityResolveInfo, serviceResolveInfo);
    }

    @Test
    public void testGetAppsOnActive() {
        List<MediaSource> observedValue = mMediaSources.getList();
        assertThat(observedValue).isNotNull();
        assertThat(
                observedValue.stream().map(source -> source.getPackageName())
                        .collect(Collectors.toList()))
                .containsExactly(TEST_SERVICE_PACKAGE_1, TEST_SERVICE_PACKAGE_WITH_METADATA);
    }

    @Test
    public void testGetAppsOnPackageAdded() {
        List<ResolveInfo> activityResolveInfo = buildActivityResolveInfo();
        activityResolveInfo.add(newActivityResolveInfo(TEST_ACTIVITY_PACKAGE_2));
        List<ResolveInfo> serviceResolveInfo = buildServiceResolveInfo();
        serviceResolveInfo.add(newServiceResolveInfo(TEST_SERVICE_PACKAGE_2));
        setPackageManagerResolveInfos(activityResolveInfo, serviceResolveInfo);

        Intent packageAdded = new Intent(Intent.ACTION_PACKAGE_ADDED);
        ShadowApplication.getInstance().getRegisteredReceivers().stream()
                .filter(wrapper -> wrapper.intentFilter.hasAction(Intent.ACTION_PACKAGE_ADDED))
                .map(wrapper -> wrapper.broadcastReceiver)
                .forEach(broadcastReceiver -> broadcastReceiver.onReceive(application,
                        packageAdded));

        List<MediaSource> observedValue = mMediaSources.getList();
        assertThat(observedValue).isNotNull();
        assertThat(
                observedValue.stream().map(source -> source.getPackageName())
                        .collect(Collectors.toList()))
                .containsExactly(TEST_SERVICE_PACKAGE_1, TEST_SERVICE_PACKAGE_2,
                        TEST_SERVICE_PACKAGE_WITH_METADATA);
    }

    @Test
    public void testGetAppsOnPackageRemoved() {
        List<ResolveInfo> activityResolveInfo = buildActivityResolveInfo();
        List<ResolveInfo> serviceResolveInfo = buildServiceResolveInfo();
        serviceResolveInfo.remove(0);
        setPackageManagerResolveInfos(activityResolveInfo, serviceResolveInfo);

        Intent packageRemoved = new Intent(Intent.ACTION_PACKAGE_REMOVED);
        ShadowApplication.getInstance().getRegisteredReceivers().stream()
                .filter(wrapper -> wrapper.intentFilter.hasAction(Intent.ACTION_PACKAGE_REMOVED))
                .map(wrapper -> wrapper.broadcastReceiver)
                .forEach(broadcastReceiver ->
                        broadcastReceiver.onReceive(application, packageRemoved));

        List<MediaSource> observedValue = mMediaSources.getList();
        assertThat(observedValue).isNotNull();
        assertThat(
                observedValue.stream().map(source -> source.getPackageName())
                        .collect(Collectors.toList()))
                .containsExactly(TEST_SERVICE_PACKAGE_WITH_METADATA);
    }

    @NonNull
    private List<ResolveInfo> buildActivityResolveInfo() {
        List<ResolveInfo> activityResolveInfo = new ArrayList<>();
        activityResolveInfo.add(newActivityResolveInfo(TEST_ACTIVITY_PACKAGE_1));
        return activityResolveInfo;
    }

    @NonNull
    private List<ResolveInfo> buildServiceResolveInfo() {
        List<ResolveInfo> serviceResolveInfo = new ArrayList<>();
        serviceResolveInfo.add(newServiceResolveInfo(TEST_SERVICE_PACKAGE_1));
        ResolveInfo withMetadata = newServiceResolveInfo(TEST_SERVICE_PACKAGE_WITH_METADATA);
        withMetadata.serviceInfo.applicationInfo.metaData = new Bundle();
        serviceResolveInfo.add(withMetadata);
        return serviceResolveInfo;
    }

    private void setPackageManagerResolveInfos(List<ResolveInfo> activityResolveInfo,
            List<ResolveInfo> serviceResolveInfo) {
        ShadowPackageManager packageManager = shadowOf(application.getPackageManager());
        packageManager.removeResolveInfosForIntent(mActivityIntent, TEST_ACTIVITY_PACKAGE_1);
        packageManager.removeResolveInfosForIntent(mActivityIntent, TEST_ACTIVITY_PACKAGE_2);
        packageManager.removeResolveInfosForIntent(mServiceIntent, TEST_SERVICE_PACKAGE_1);
        packageManager.removeResolveInfosForIntent(mServiceIntent, TEST_SERVICE_PACKAGE_2);
        packageManager.removeResolveInfosForIntent(mServiceIntent,
                TEST_SERVICE_PACKAGE_WITH_METADATA);

        packageManager.addResolveInfoForIntent(mActivityIntent, activityResolveInfo);

        packageManager.addResolveInfoForIntent(mServiceIntent, serviceResolveInfo);
        for (ResolveInfo info : serviceResolveInfo) {
            Intent intent = new Intent(mServiceIntent);
            intent.setPackage(info.serviceInfo.packageName);
            packageManager.addResolveInfoForIntent(intent, info);
        }
        mMediaSources.reset();
    }


    private ResolveInfo newActivityResolveInfo(String packageName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        ActivityInfo activityInfo = new ActivityInfo();
        activityInfo.packageName = packageName;
        activityInfo.name = "activity";
        activityInfo.nonLocalizedLabel = "Activity Label " + packageName;
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.nonLocalizedLabel = "Activity Label " + packageName;
        activityInfo.applicationInfo = applicationInfo;
        resolveInfo.activityInfo = activityInfo;
        return resolveInfo;
    }

    private ResolveInfo newServiceResolveInfo(String packageName) {
        ResolveInfo resolveInfo = new ResolveInfo();
        ServiceInfo serviceInfo = new ServiceInfo();
        serviceInfo.packageName = packageName;
        serviceInfo.name = "service";
        serviceInfo.nonLocalizedLabel = "Service Label " + packageName;
        ApplicationInfo applicationInfo = new ApplicationInfo();
        applicationInfo.packageName = packageName;
        applicationInfo.nonLocalizedLabel = "Service Label " + packageName;
        serviceInfo.applicationInfo = applicationInfo;
        resolveInfo.serviceInfo = serviceInfo;
        // ShadowPackageManager#removeResolveInfosForIntent requires activityInfo to be set...
        resolveInfo.activityInfo = newActivityResolveInfo(packageName).activityInfo;
        return resolveInfo;
    }
}
