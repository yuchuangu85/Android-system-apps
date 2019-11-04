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

package com.android.car;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.location.Location;
import android.location.LocationManager;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * This class contains unit tests for {@link LocationManagerProxy}.
 *
 * Mocks are used for {@link Context} and {@link LocationManager}.
 */
public class LocationManagerProxyTest {
    private static final String TAG = "LocationManagerProxyTest";
    private LocationManagerProxy mLocationManagerProxy;
    @Mock
    private Context mMockContext;
    @Mock
    private LocationManager mMockLocationManager;

    /** Initialize all of the objects with the @Mock annotation. */
    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockContext.getSystemService(Context.LOCATION_SERVICE))
                .thenReturn(mMockLocationManager);
        mLocationManagerProxy = new LocationManagerProxy(mMockContext);
    }

    @Test
    public void testLocationManagerProxyCanInjectLocation() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        mLocationManagerProxy.injectLocation(location);
        verify(mMockLocationManager).injectLocation(location);
    }

    @Test
    public void testLocationManagerProxyCanGetLastKnownLocation() {
        Location location = new Location(LocationManager.GPS_PROVIDER);
        when(mMockLocationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER))
                .thenReturn(location);
        Location locationFromProxy = mLocationManagerProxy
                .getLastKnownLocation(LocationManager.GPS_PROVIDER);
        assertThat(locationFromProxy).isEqualTo(location);
    }

    @Test
    public void testLocationManagerProxyCanCheckIfLocationIsEnabled() {
        when(mMockLocationManager.isLocationEnabled()).thenReturn(true);
        assertThat(mLocationManagerProxy.isLocationEnabled()).isTrue();

        when(mMockLocationManager.isLocationEnabled()).thenReturn(false);
        assertThat(mLocationManagerProxy.isLocationEnabled()).isFalse();
    }
}
