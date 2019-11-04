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
package com.android.car.notification;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;

/**
 * Displays all undismissed notifications.
 */
public class CarNotificationCenterActivity extends Activity {

    private static final String TAG = "CarNotificationActivity";

    private boolean mNotificationListenerBound;
    private CarNotificationListener mNotificationListener;
    private PreprocessingManager mPreprocessingManager;
    private CarNotificationView mCarNotificationView;
    private NotificationViewController mNotificationViewController;

    private ServiceConnection mNotificationListenerConnectionListener = new ServiceConnection() {
        public void onServiceConnected(ComponentName className, IBinder binder) {
            Log.d(TAG, "onServiceConnected");
            mNotificationListener = ((CarNotificationListener.LocalBinder) binder).getService();
            NotificationApplication app = (NotificationApplication) getApplication();
            mNotificationViewController =
                    new NotificationViewController(mCarNotificationView,
                            mPreprocessingManager,
                            mNotificationListener,
                            app.getCarUxRestrictionWrapper(),
                            new NotificationDataManager()
                    );
            mNotificationViewController.enable();
            mNotificationListenerBound = true;
        }

        public void onServiceDisconnected(ComponentName className) {
            Log.d(TAG, "onServiceDisconnected");
            mNotificationViewController.disable();
            mNotificationViewController = null;
            mNotificationListener = null;
            mNotificationListenerBound = false;
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPreprocessingManager = PreprocessingManager.getInstance(getApplicationContext());
        NotificationApplication app = (NotificationApplication) getApplication();
        setContentView(R.layout.notification_center_activity);
        mCarNotificationView = findViewById(R.id.notification_view);
        mCarNotificationView.setClickHandlerFactory(app.getClickHandlerFactory());
        findViewById(R.id.exit_button_container).setOnClickListener(v -> finish());
    }

    @Override
    protected void onStart() {
        super.onStart();
        // Bind notification listener
        Intent intent = new Intent(this, CarNotificationListener.class);
        intent.setAction(CarNotificationListener.ACTION_LOCAL_BINDING);
        bindService(intent, mNotificationListenerConnectionListener, Context.BIND_AUTO_CREATE);
        mNotificationViewController.setIsInForeground(true);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mNotificationViewController.setIsInForeground(false);
        // Unbind notification listener
        if (mNotificationListenerBound) {
            mNotificationViewController.disable();
            mNotificationViewController = null;
            mNotificationListener = null;
            unbindService(mNotificationListenerConnectionListener);
            mNotificationListenerBound = false;
        }
    }

}
