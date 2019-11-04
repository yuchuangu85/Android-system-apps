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

import static com.android.car.MockedVmsTestBase.PUBLISHER_BIND_TIMEOUT_SECS;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.car.vms.VmsPublisherClientService;
import android.car.vms.VmsSubscriptionState;
import android.content.Intent;
import android.os.UserHandle;

import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class VmsPublisherClientPermissionTest extends MockedCarTestBase {
    private static CountDownLatch sPublisherExpectedPermission = new CountDownLatch(1);
    private static CountDownLatch sPublisherWrongPermission = new CountDownLatch(1);
    private static CountDownLatch sPublisherMissingPermission = new CountDownLatch(1);

    private static class MockPublisherClient extends VmsPublisherClientService {
        private CountDownLatch mReadyLatch;
        MockPublisherClient(CountDownLatch readyLatch) {
            mReadyLatch = readyLatch;
        }
        @Override
        protected void onVmsPublisherServiceReady() {
            mReadyLatch.countDown();
        }

        @Override
        public void onVmsSubscriptionChange(VmsSubscriptionState subscriptionState) {}
    }


    // AndroidManifest.xml:
    // <service android:name="com.android.car.VmsPublisherClientPermissionTest$PublisherClientExpectedPermission"
    //         android:exported="true"
    //         android:permission="android.car.permission.BIND_VMS_CLIENT"/>
    public static class PublisherClientExpectedPermission extends MockPublisherClient {
        public PublisherClientExpectedPermission() {
            super(sPublisherExpectedPermission);
        }
    }

    // AndroidManifest.xml:
    // <service android:name="com.android.car.VmsPublisherClientPermissionTest$PublisherClientWrongPermission"
    //         android:exported="true"
    //         android:permission="android.car.permission.VMS_PUBLISHER"/>
    public static class PublisherClientWrongPermission extends MockPublisherClient {
        public PublisherClientWrongPermission() {
            super(sPublisherWrongPermission);
        }
    }

    // AndroidManifest.xml:
    // <service android:name="com.android.car.VmsPublisherClientPermissionTest$PublisherClientMissingPermission"
    //         android:exported="true"/>
    public static class PublisherClientMissingPermission extends MockPublisherClient {
        public PublisherClientMissingPermission() {
            super(sPublisherMissingPermission);
        }
    }

    @Override
    protected synchronized void configureResourceOverrides(MockResources resources) {
        super.configureResourceOverrides(resources);
        resources.overrideResource(com.android.car.R.array.vmsPublisherSystemClients, new String[]{
                getFlattenComponent(PublisherClientExpectedPermission.class),
                getFlattenComponent(PublisherClientWrongPermission.class),
                getFlattenComponent(PublisherClientMissingPermission.class)
        });
        resources.overrideResource(com.android.car.R.array.vmsPublisherUserClients, new String[]{
                getFlattenComponent(PublisherClientExpectedPermission.class),
                getFlattenComponent(PublisherClientWrongPermission.class),
                getFlattenComponent(PublisherClientMissingPermission.class)
        });
    }

    @Before
    public void triggerClientBinding() {
        getContext().sendBroadcastAsUser(new Intent(Intent.ACTION_USER_UNLOCKED), UserHandle.ALL);
    }

    @Test
    public void testExpectedPermission() throws Exception {
        assertTrue(
                "Timeout while waiting for publisher client to be ready",
                sPublisherExpectedPermission.await(PUBLISHER_BIND_TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    @Test
    public void testWrongPermission() throws Exception {
        assertFalse(
                "Publisher with wrong android:permission was bound unexpectedly",
                sPublisherWrongPermission.await(PUBLISHER_BIND_TIMEOUT_SECS, TimeUnit.SECONDS));
    }

    @Test
    public void testMissingPermission() throws Exception {
        assertFalse(
                "Publisher with missing android:permission was bound unexpectedly",
                sPublisherMissingPermission.await(PUBLISHER_BIND_TIMEOUT_SECS, TimeUnit.SECONDS));
    }
}
