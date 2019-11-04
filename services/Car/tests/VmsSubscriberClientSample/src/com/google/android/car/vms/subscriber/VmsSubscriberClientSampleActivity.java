/*
 * Copyright (C) 2017 The Android Open Source Project
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

package com.google.android.car.vms.subscriber;

import android.app.Activity;
import android.car.Car;
import android.car.vms.VmsAvailableLayers;
import android.car.vms.VmsLayer;
import android.car.vms.VmsSubscriberManager;
import android.content.ComponentName;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.widget.TextView;

import java.util.concurrent.Executor;

/**
 * Connects to the Car service during onCreate. CarConnectionCallback.onConnected is invoked when
 * the connection is ready. Then, it subscribes to a VMS layer/version and updates the TextView when
 * a message is received.
 */
public class VmsSubscriberClientSampleActivity extends Activity {
    private static final String TAG = "VmsSampleActivity";
    // The layer id and version should match the ones defined in
    // com.google.android.car.vms.publisher.VmsPublisherClientSampleService
    public static final VmsLayer TEST_LAYER = new VmsLayer(0, 0, 0);

    private Car mCarApi;
    private TextView mTextView;
    private VmsSubscriberManager mVmsSubscriberManager;
    private Executor mExecutor;

    private class ThreadPerTaskExecutor implements Executor {
        public void execute(Runnable r) {
            new Thread(r).start();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        mExecutor = new ThreadPerTaskExecutor();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mTextView = (TextView) findViewById(R.id.textview);
        if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)) {
            mCarApi = Car.createCar(this, mCarServiceConnection);
            mCarApi.connect();
        } else {
            Log.d(TAG, "No automotive feature.");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mCarApi != null) {
            mCarApi.disconnect();
        }
        Log.i(TAG, "onDestroy");
    }

    private final ServiceConnection mCarServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.d(TAG, "Connected to Car Service");
            mVmsSubscriberManager = getVmsSubscriberManager();
            configureSubscriptions(mVmsSubscriberManager);
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.d(TAG, "Disconnect from Car Service");
            if (mVmsSubscriberManager != null) {
                mVmsSubscriberManager.clearVmsSubscriberClientCallback();
                mVmsSubscriberManager.unsubscribe(TEST_LAYER);
            }
        }

        private VmsSubscriberManager getVmsSubscriberManager() {
            return (VmsSubscriberManager) mCarApi.getCarManager(
                    Car.VMS_SUBSCRIBER_SERVICE);
        }

        private void configureSubscriptions(VmsSubscriberManager vmsSubscriberManager) {
            vmsSubscriberManager.setVmsSubscriberClientCallback(mExecutor, mClientCallback);
            vmsSubscriberManager.subscribe(TEST_LAYER);
        }

    };

    private final VmsSubscriberManager.VmsSubscriberClientCallback mClientCallback =
        new VmsSubscriberManager.VmsSubscriberClientCallback() {
            @Override
            public void onVmsMessageReceived(VmsLayer layer, byte[] payload) {
                mTextView.setText(String.valueOf(payload[0]));
            }

            @Override
            public void onLayersAvailabilityChanged(VmsAvailableLayers availableLayers) {
                mTextView.setText(String.valueOf(availableLayers));
            }
        };
}
