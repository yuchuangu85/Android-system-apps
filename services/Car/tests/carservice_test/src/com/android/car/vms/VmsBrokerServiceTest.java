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

package com.android.car.vms;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.car.vms.IVmsSubscriberClient;
import android.car.vms.VmsLayer;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Process;

import androidx.test.filters.MediumTest;

import org.junit.Test;

import java.util.function.IntSupplier;

@MediumTest
public class VmsBrokerServiceTest {

    class MockIntProvider implements IntSupplier {
        private int[] mInts;
        private int mIdx;

        MockIntProvider(int... ints) {
            mInts = ints;
            mIdx = 0;
        }

        public int getAsInt() {
            int ret = mInts[mIdx];
            mIdx++;
            return ret;
        }
    }

    /**
     * Test that adding a subscriber to VmsBrokerService also keeps track of the package name for
     * a given subscriber. Also checks that if we remove a dead subscriber, we no longer track the
     * package name associated with it.
     */
    @Test
    public void testAddSubscription() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);
        Binder binder = mock(Binder.class);
        VmsLayer layer = mock(VmsLayer.class);
        when(packageManager.getNameForUid(0)).thenReturn("test.package1");
        when(subscriberClient.asBinder()).thenReturn(binder);

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 200, () -> 0);
        broker.addSubscription(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isEqualTo("test.package1");
        broker.removeDeadSubscriber(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isNull();
    }

    @Test
    public void testAddSubscriptionLayer() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);
        Binder binder = mock(Binder.class);
        VmsLayer layer = mock(VmsLayer.class);
        when(packageManager.getNameForUid(0)).thenReturn("test.package2");
        when(subscriberClient.asBinder()).thenReturn(binder);

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 200, () -> 0);
        broker.addSubscription(subscriberClient, layer);
        assertThat(broker.getPackageName(subscriberClient)).isEqualTo("test.package2");
        broker.removeDeadSubscriber(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isNull();
    }

    @Test
    public void testAddSubscriptionLayerVersion() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);
        Binder binder = mock(Binder.class);
        VmsLayer layer = mock(VmsLayer.class);
        when(packageManager.getNameForUid(0)).thenReturn("test.package3");
        when(subscriberClient.asBinder()).thenReturn(binder);

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 200, () -> 0);
        broker.addSubscription(subscriberClient, layer, 1234);
        assertThat(broker.getPackageName(subscriberClient)).isEqualTo("test.package3");
        broker.removeDeadSubscriber(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isNull();
    }

    @Test
    public void testMultipleSubscriptionsSameClientCallsPackageManagerOnce() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);
        Binder binder = mock(Binder.class);
        when(subscriberClient.asBinder()).thenReturn(binder);
        when(packageManager.getNameForUid(0)).thenReturn("test.package3");

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 0, () -> 0);
        broker.addSubscription(subscriberClient);
        broker.addSubscription(subscriberClient);
        // The second argument isn't necessary but is here for clarity.
        verify(packageManager, times(1)).getNameForUid(0);
    }

    @Test
    public void testUnknownPackageName() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);
        Binder binder = mock(Binder.class);
        when(subscriberClient.asBinder()).thenReturn(binder);
        when(packageManager.getNameForUid(0)).thenReturn(null);

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 0, () -> 0);
        broker.addSubscription(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isEqualTo(
                VmsBrokerService.UNKNOWN_PACKAGE);
    }

    /**
     * Tests that if the HAL is a subscriber, we record its package name as HalClient.
     */
    @Test
    public void testAddingHalSubscriberSavesPackageName() {
        PackageManager packageManager = mock(PackageManager.class);
        IVmsSubscriberClient subscriberClient = mock(IVmsSubscriberClient.class);

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> Process.myPid(),
                () -> Process.SYSTEM_UID);
        broker.addSubscription(subscriberClient);
        assertThat(broker.getPackageName(subscriberClient)).isEqualTo(VmsBrokerService.HAL_CLIENT);
    }

    @Test
    public void testMultipleSubscriptionsPackageManager() {
        PackageManager packageManager = mock(PackageManager.class);

        IVmsSubscriberClient subscriberClient1 = mock(IVmsSubscriberClient.class);
        Binder binder1 = mock(Binder.class);
        when(subscriberClient1.asBinder()).thenReturn(binder1);

        IVmsSubscriberClient subscriberClient2 = mock(IVmsSubscriberClient.class);
        Binder binder2 = mock(Binder.class);
        when(subscriberClient2.asBinder()).thenReturn(binder2);

        IVmsSubscriberClient subscriberClient3 = mock(IVmsSubscriberClient.class);
        Binder binder3 = mock(Binder.class);
        when(subscriberClient3.asBinder()).thenReturn(binder3);

        // Tests a client with a different UID but the same package as subscriberClient1
        IVmsSubscriberClient subscriberClient4 = mock(IVmsSubscriberClient.class);
        Binder binder4 = mock(Binder.class);
        when(subscriberClient4.asBinder()).thenReturn(binder4);

        when(packageManager.getNameForUid(0)).thenReturn("test.package0");
        when(packageManager.getNameForUid(1)).thenReturn("test.package1");
        when(packageManager.getNameForUid(2)).thenReturn("test.package2");
        when(packageManager.getNameForUid(3)).thenReturn("test.package0");

        VmsBrokerService broker = new VmsBrokerService(packageManager, () -> 10,
                new MockIntProvider(0, 1, 2, 3));

        broker.addSubscription(subscriberClient1);
        broker.addSubscription(subscriberClient2);
        broker.addSubscription(subscriberClient3);
        broker.addSubscription(subscriberClient4);

        verify(packageManager).getNameForUid(0);
        verify(packageManager).getNameForUid(1);
        verify(packageManager).getNameForUid(2);
        verify(packageManager).getNameForUid(3);

        assertThat(broker.getPackageName(subscriberClient1)).isEqualTo("test.package0");
        assertThat(broker.getPackageName(subscriberClient2)).isEqualTo("test.package1");
        assertThat(broker.getPackageName(subscriberClient3)).isEqualTo("test.package2");
        assertThat(broker.getPackageName(subscriberClient4)).isEqualTo("test.package0");
    }
}
