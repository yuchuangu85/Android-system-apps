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

package com.android.car.carlauncher;

import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.ActivityView;
import android.app.UserSwitchObserver;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.IRemoteCallback;
import android.os.RemoteException;
import android.util.Log;
import android.widget.FrameLayout;

import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.android.car.media.common.PlaybackFragment;

import java.util.Set;

/**
 * Basic Launcher for Android Automotive which demonstrates the use of {@link ActivityView} to host
 * maps content.
 *
 * <p>Note: On some devices, the ActivityView may render with a width, height, and/or aspect
 * ratio that does not meet Android compatibility definitions. Developers should work with content
 * owners to ensure content renders correctly when extending or emulating this class.
 *
 * <p>Note: Since the hosted maps Activity in ActivityView is currently in a virtual display, the
 * system considers the Activity to always be in front. Launching the maps Activity with a direct
 * Intent will not work. To start the maps Activity on the real display, send the Intent to the
 * Launcher with the {@link Intent#CATEGORY_APP_MAPS} category, and the launcher will start the
 * Activity on the real display.
 *
 * <p>Note: The state of the virtual display in the ActivityView is nondeterministic when
 * switching away from and back to the current user. To avoid a crash, this Activity will finish
 * when switching users.
 */
public class CarLauncher extends FragmentActivity {
    private static final String TAG = "CarLauncher";

    private ActivityView mActivityView;
    private boolean mActivityViewReady = false;
    private boolean mIsStarted = false;

    private final ActivityView.StateCallback mActivityViewCallback =
            new ActivityView.StateCallback() {
                @Override
                public void onActivityViewReady(ActivityView view) {
                    mActivityViewReady = true;
                    startMapsInActivityView();
                }

                @Override
                public void onActivityViewDestroyed(ActivityView view) {
                    mActivityViewReady = false;
                }

                @Override
                public void onTaskMovedToFront(int taskId) {
                    try {
                        if (mIsStarted) {
                            ActivityManager am =
                                    (ActivityManager) getSystemService(ACTIVITY_SERVICE);
                            am.moveTaskToFront(CarLauncher.this.getTaskId(), /* flags= */ 0);
                        }
                    } catch (RuntimeException e) {
                        Log.w(TAG, "Failed to move CarLauncher to front.");
                    }
                }
            };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Don't show the maps panel in multi window mode.
        // NOTE: CTS tests for split screen are not compatible with activity views on the default
        // activity of the launcher
        if (isInMultiWindowMode() || isInPictureInPictureMode()) {
            setContentView(R.layout.car_launcher_multiwindow);
        } else {
            setContentView(R.layout.car_launcher);
        }
        initializeFragments();
        mActivityView = findViewById(R.id.maps);
        if (mActivityView != null) {
            mActivityView.setCallback(mActivityViewCallback);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        Set<String> categories = intent.getCategories();
        if (categories != null && categories.size() == 1 && categories.contains(
                Intent.CATEGORY_APP_MAPS)) {
            launchMapsActivity();
        }
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        startMapsInActivityView();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mIsStarted = true;
    }

    @Override
    protected void onStop() {
        super.onStop();
        mIsStarted = false;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mActivityView != null && mActivityViewReady) {
            mActivityView.release();
        }
    }

    private void startMapsInActivityView() {
        // If we happen to be be resurfaced into a multi display mode we skip launching content
        // in the activity view as we will get recreated anyway.
        if (!mActivityViewReady || isInMultiWindowMode() || isInPictureInPictureMode()) {
            return;
        }
        if (mActivityView != null) {
            mActivityView.startActivity(getMapsIntent());
        }
    }

    private void launchMapsActivity() {
        // Make sure the Activity launches on the current display instead of in the ActivityView
        // virtual display.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchDisplayId(getDisplay().getDisplayId());
        startActivity(getMapsIntent(), options.toBundle());
    }

    private Intent getMapsIntent() {
        return Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_MAPS);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        initializeFragments();
    }

    private void initializeFragments() {
        PlaybackFragment playbackFragment = new PlaybackFragment();
        ContextualFragment contextualFragment = null;
        FrameLayout contextual = findViewById(R.id.contextual);
        if(contextual != null) {
            contextualFragment = new ContextualFragment();
        }

        FragmentTransaction fragmentTransaction =
                getSupportFragmentManager().beginTransaction();
        fragmentTransaction.replace(R.id.playback, playbackFragment);
        if(contextual != null) {
            fragmentTransaction.replace(R.id.contextual, contextualFragment);
        }
        fragmentTransaction.commitNow();
    }
}
