package com.android.managedprovisioning.task;

import static android.app.admin.DevicePolicyManager
        .ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.provider.Settings;

import com.android.managedprovisioning.model.ProvisioningParams;
import com.android.managedprovisioning.task.AbstractProvisioningTask.Callback;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowContextImpl;
import org.robolectric.shadows.ShadowLooper;

import java.util.HashMap;
import java.util.Map;

/** Tests {@link ConnectMobileNetworkTask}. */
@RunWith(RobolectricTestRunner.class)
public class ConnectMobileNetworkTaskRoboTest {
    private final Context mContext = RuntimeEnvironment.application;
    private final ContentResolver mContentResolver = mContext.getContentResolver();

    @Mock private ConnectivityManager mMockConnectivityManager;
    @Mock private NetworkInfo mMockNetworkInfo;

    @Before
    public void setUpMocks() {
        MockitoAnnotations.initMocks(this);
        mockNoNetwork();
        when(mMockConnectivityManager.getActiveNetworkInfo()).thenReturn(mMockNetworkInfo);
        ShadowContextImpl shadowContext =
                Shadow.extract(RuntimeEnvironment.application.getBaseContext());
        shadowContext.setSystemService(Context.CONNECTIVITY_SERVICE, mMockConnectivityManager);
    }

    @Test
    public void run_enablesMobileDataDeviceProvisioningSetting() throws Exception {
        runTask(buildTask(new FakeAbstractProvisioningTaskCallback()));

        int mobileDataDeviceProvisioningSetting = Settings.Global.getInt(
                mContentResolver, Settings.Global.DEVICE_PROVISIONING_MOBILE_DATA_ENABLED);
        assertThat(mobileDataDeviceProvisioningSetting).isEqualTo(1);
    }

    @Test
    public void run_withoutConnection_noCallbackSuccess() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        runTask(task);

        assertThat(callback.getSuccessCount(task)).isEqualTo(0);
    }

    @Test
    public void connectToNetwork_afterRun_callbackSuccess() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        runTask(task);
        mockConnectToNetwork();
        sendConnectionBroadcast();

        assertThat(callback.getSuccessCount(task)).isEqualTo(1);
        assertThat(callback.getErrorCount(task)).isEqualTo(0);
    }

    @Test
    public void connectToNetworkTwice_afterRun_oneCallbackSuccess() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        mockConnectToNetwork();
        sendConnectionBroadcast();
        mockNoNetwork();
        mockConnectToNetwork();
        sendConnectionBroadcast();
        runTask(task);

        assertThat(callback.getSuccessCount(task)).isEqualTo(1);
        assertThat(callback.getErrorCount(task)).isEqualTo(0);
    }

    @Test
    public void connectToNetwork_beforeRun_callbackSuccess() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        mockConnectToNetwork();
        sendConnectionBroadcast();
        runTask(task);

        assertThat(callback.getSuccessCount(task)).isEqualTo(1);
        assertThat(callback.getErrorCount(task)).isEqualTo(0);
    }

    @Test
    public void connectToNetworkBroadcast_withoutConnectionAfterRun_noCallbackSuccess() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        runTask(task);
        // Don't mock the network info to be connected.
        sendConnectionBroadcast();

        assertThat(callback.getSuccessCount(task)).isEqualTo(0);
    }

    @Test
    public void timeoutWaitingForConnection_afterRun_callbackError() {
        FakeAbstractProvisioningTaskCallback callback = new FakeAbstractProvisioningTaskCallback();
        ConnectMobileNetworkTask task = buildTask(callback);

        runTask(task);
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks();

        assertThat(callback.getSuccessCount(task)).isEqualTo(0);
        assertThat(callback.getErrorCount(task)).isEqualTo(1);
    }

    private void runTask(ConnectMobileNetworkTask task) {
        task.run(/* userId= */ 1);
    }

    private ConnectMobileNetworkTask buildTask(Callback callback) {
        return new ConnectMobileNetworkTask(mContext, buildProvisioningParams(), callback);
    }

    private ProvisioningParams buildProvisioningParams() {
        return ProvisioningParams.Builder.builder()
                .setProvisioningAction(ACTION_PROVISION_MANAGED_DEVICE_FROM_TRUSTED_SOURCE)
                .setDeviceAdminComponentName(new ComponentName("package.name", ".AdminReceiver"))
                .build();
    }

    private void mockConnectToNetwork() {
        when(mMockNetworkInfo.isConnected()).thenReturn(true);
    }

    private void mockNoNetwork() {
        when(mMockNetworkInfo.isConnected()).thenReturn(false);
    }

    private void sendConnectionBroadcast() {
        mContext.sendBroadcast(new Intent(ConnectivityManager.INET_CONDITION_ACTION));
    }

    // TODO(http://b/110676015): Turn into the official supported fake for
    // AbstractProvisioningTask.Callback.
    private static class FakeAbstractProvisioningTaskCallback implements Callback {

        Map<AbstractProvisioningTask, Integer> successTaskCounts = new HashMap<>();
        Map<AbstractProvisioningTask, Integer> errorTaskCounts = new HashMap<>();

        @Override
        public void onSuccess(AbstractProvisioningTask task) {
            successTaskCounts.put(task, getSuccessCount(task) + 1);
        }

        @Override
        public void onError(AbstractProvisioningTask task, int errorCode) {
            errorTaskCounts.put(task, getErrorCount(task) + 1);
        }

        private int getSuccessCount(AbstractProvisioningTask task) {
            return successTaskCounts.getOrDefault(task, 0);
        }

        private int getErrorCount(AbstractProvisioningTask task) {
            return errorTaskCounts.getOrDefault(task, 0);
        }
    }
}
