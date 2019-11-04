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

package com.google.android.car.kitchensink.cluster;

import android.app.Activity;
import android.car.Car;
import android.car.cluster.ClusterActivityState;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.RelativeLayout;

import com.google.android.car.kitchensink.R;

/**
 * Fake navigation activity for instrument cluster.
 */
public class FakeClusterNavigationActivity extends Activity {
    private final static String TAG = FakeClusterNavigationActivity.class.getSimpleName();

    private ImageView mUnobscuredArea;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.i(TAG, "onCreate");
        setContentView(R.layout.fake_cluster_navigation_activity);
        mUnobscuredArea = findViewById(R.id.unobscuredArea);

        handleIntent(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) {
            Log.w(TAG, "Received a null intent");
            return;
        }
        Bundle bundle = intent.getBundleExtra(Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE);
        if (bundle == null) {
            Log.w(TAG, "Received an intent without " + Car.CAR_EXTRA_CLUSTER_ACTIVITY_STATE);
            return;
        }
        ClusterActivityState state = ClusterActivityState.fromBundle(bundle);
        Log.i(TAG, "handling intent with state: " + state);

        Rect unobscured = state.getUnobscuredBounds();
        RelativeLayout.LayoutParams lp = new RelativeLayout.LayoutParams(
                unobscured.width(), unobscured.height());
        lp.setMargins(unobscured.left, unobscured.top, 0, 0);
        mUnobscuredArea.setLayoutParams(lp);
    }
}