/*
 * Copyright 2016, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.provisioning;

import static android.app.admin.DevicePolicyManager.ACTION_PROVISION_MANAGED_PROFILE;

import static org.junit.Assert.assertTrue;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;
import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import androidx.test.filters.FlakyTest;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.ProvisioningAnalyticsTracker;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.model.ProvisioningParams;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.invocation.InvocationOnMock;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Unit tests for {@link ProvisioningManager}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
public class ProvisioningManagerTest {
    private final int TEST_PROGRESS_ID = 123;
    private final int TEST_ERROR_ID = 456;
    private final boolean TEST_FACTORY_RESET_REQUIRED = true;
    private final ComponentName TEST_ADMIN = new ComponentName("com.test.admin", ".AdminReceiver");
    private final ProvisioningParams TEST_PARAMS = new ProvisioningParams.Builder()
            .setProvisioningAction(ACTION_PROVISION_MANAGED_PROFILE)
            .setDeviceAdminComponentName(TEST_ADMIN)
            .build();

    @Mock private Context mContext;
    @Mock private ProvisioningControllerFactory mFactory;
    @Mock private ProvisioningAnalyticsTracker mAnalyticsTracker;
    @Mock private TimeLogger mTimeLogger;
    @Mock private Handler mUiHandler;
    @Mock private ProvisioningManagerCallback mCallback;
    @Mock private AbstractProvisioningController mController;

    private ProvisioningManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        // Immediately execute any message that is sent onto the handler
        when(mUiHandler.sendMessageAtTime(any(Message.class), anyLong())).thenAnswer(
                (InvocationOnMock invocation) -> {
                    Message msg = (Message) invocation.getArguments()[0];
                    msg.getCallback().run();
                    return null;
                });
        mManager = new ProvisioningManager(mContext, mUiHandler, mFactory, mAnalyticsTracker,
                mTimeLogger);
        when(mFactory.createProvisioningController(mContext, TEST_PARAMS, mManager))
                .thenReturn(mController);
    }

    @Test
    public void testMaybeStartProvisioning() {
        // GIVEN that provisioning is not currently running
        // WHEN calling maybeStartProvisioning
        mManager.maybeStartProvisioning(TEST_PARAMS);

        // THEN the factory should be called
        verify(mFactory).createProvisioningController(mContext, TEST_PARAMS, mManager);

        // THEN the controller should be started on a Looper that is not the main thread
        ArgumentCaptor<Looper> looperCaptor = ArgumentCaptor.forClass(Looper.class);
        verify(mController).start(looperCaptor.capture());
        assertTrue(looperCaptor.getValue() != Looper.getMainLooper());

        // WHEN trying to start provisioning again
        mManager.maybeStartProvisioning(TEST_PARAMS);

        // THEN nothing should happen
        verifyNoMoreInteractions(mFactory);
        verifyNoMoreInteractions(mController);
    }

    @Test
    public void testCancelProvisioning() {
        // GIVEN provisioning has been started
        mManager.maybeStartProvisioning(TEST_PARAMS);

        // WHEN cancelling provisioning
        mManager.cancelProvisioning();

        // THEN the controller should be cancelled
        verify(mController).cancel();
    }

    @FlakyTest(bugId = 131866915)
    @Test
    public void testListener_error() {
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.error(R.string.cant_set_up_device, TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);
        // THEN the listener should receive a callback
        verify(mCallback).error(R.string.cant_set_up_device, TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback, times(2)).error(R.string.cant_set_up_device, TEST_ERROR_ID, TEST_FACTORY_RESET_REQUIRED);
        verifyNoMoreInteractions(mCallback);
    }

    @Test
    public void testListener_cleanupCompleted() {
        // GIVEN provisioning has been started
        mManager.maybeStartProvisioning(TEST_PARAMS);

        // GIVEN a listener is registered
        mManager.registerListener(mCallback);
        // WHEN some progress has occurred previously
        mManager.cleanUpCompleted();
        // THEN no callback is sent
        verifyZeroInteractions(mCallback);
    }

    @FlakyTest(bugId = 131866915)
    @Test
    public void testListener_preFinalizationCompleted() throws InterruptedException {
        // GIVEN provisioning has been started
        mManager.maybeStartProvisioning(TEST_PARAMS);
        // GIVEN a listener is registered
        mManager.registerListener(mCallback);

        // prepare a semaphore to handle AsyncTask usage
        final Semaphore semaphore = new Semaphore(0);
        doAnswer((InvocationOnMock invocation) -> {
            semaphore.release(1);
            return null;
        }).when(mCallback).preFinalizationCompleted();

        // WHEN some progress has occurred previously
        mManager.preFinalizationCompleted();


        assertTrue(semaphore.tryAcquire(1, TimeUnit.SECONDS));


        // THEN the listener should receive a callback
        verify(mCallback).preFinalizationCompleted();

        // WHEN the listener is unregistered and registered again
        mManager.unregisterListener(mCallback);
        mManager.registerListener(mCallback);
        // THEN the listener should receive a callback again
        verify(mCallback).preFinalizationCompleted();
        verifyNoMoreInteractions(mCallback);
    }
}
