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

package com.android.car.radio;

import android.car.Car;
import android.content.Intent;
import android.os.Bundle;
import android.view.KeyEvent;

import androidx.annotation.NonNull;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;

import com.android.car.apps.common.widget.CarTabLayout;
import com.android.car.media.common.MediaAppSelectorWidget;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.car.radio.bands.ProgramType;
import com.android.car.radio.util.Log;
import com.android.car.radio.widget.BandSelector;

import java.util.List;

/**
 * The main activity for the radio app.
 */
public class RadioActivity extends FragmentActivity {
    private static final String TAG = "BcRadioApp.activity";

    /**
     * Intent action for notifying that the radio state has changed.
     */
    private static final String ACTION_RADIO_APP_STATE_CHANGE =
            "android.intent.action.RADIO_APP_STATE_CHANGE";

    /**
     * Boolean Intent extra indicating if the radio is the currently in the foreground.
     */
    private static final String EXTRA_RADIO_APP_FOREGROUND =
            "android.intent.action.RADIO_APP_STATE";

    private RadioController mRadioController;
    private BandSelector mBandSelector;

    private CarTabLayout mCarTabLayout;
    private RadioPagerAdapter mRadioPagerAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Log.d(TAG, "Radio app main activity created");

        setContentView(R.layout.radio_activity);
        mBandSelector = findViewById(R.id.band_toggle_button);

        MediaAppSelectorWidget appSelector = findViewById(R.id.app_switch_container);
        appSelector.setFragmentActivity(this);

        mRadioController = new RadioController(this);
        mBandSelector.setCallback(mRadioController::switchBand);
        mRadioController.getCurrentProgram().observe(this, info ->
                mBandSelector.setType(ProgramType.fromSelector(info.getSelector())));

        mRadioPagerAdapter =
                new RadioPagerAdapter(this, getSupportFragmentManager(), mRadioController);
        ViewPager viewPager = findViewById(R.id.viewpager);
        viewPager.setAdapter(mRadioPagerAdapter);
        mCarTabLayout = findViewById(R.id.tabs);
        setupTabsWithViewPager(mCarTabLayout, viewPager);

        MediaSourceViewModel model = MediaSourceViewModel.get(getApplication());
        model.getPrimaryMediaSource().observe(this, source -> {
            if (source != null) {
                // Always go through the trampoline activity to keep all the dispatching logic
                // there.
                startActivity(new Intent(Car.CAR_INTENT_ACTION_MEDIA_TEMPLATE));
            }
        });
    }

    @Override
    protected void onStart() {
        super.onStart();

        mRadioController.start();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, true);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onStop() {
        super.onStop();

        Intent broadcast = new Intent(ACTION_RADIO_APP_STATE_CHANGE);
        broadcast.putExtra(EXTRA_RADIO_APP_FOREGROUND, false);
        sendBroadcast(broadcast);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        mRadioController.shutdown();

        Log.d(TAG, "Radio app main activity destroyed");
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_MEDIA_STEP_FORWARD:
                mRadioController.step(true);
                return true;
            case KeyEvent.KEYCODE_MEDIA_STEP_BACKWARD:
                mRadioController.step(false);
                return true;
            default:
                return super.onKeyDown(keyCode, event);
        }
    }

    /**
     * Set whether background scanning is supported, to know whether to show the browse tab or not
     */
    public void setProgramListSupported(boolean supported) {
        if (supported && mRadioPagerAdapter.addBrowseTab()) {
            buildTabs();
        }
    }

    /**
     * Sets supported program types.
     */
    public void setSupportedProgramTypes(@NonNull List<ProgramType> supported) {
        mBandSelector.setSupportedProgramTypes(supported);
    }

    private void setupTabsWithViewPager(CarTabLayout carTabLayout, ViewPager viewPager) {
        carTabLayout.addOnCarTabSelectedListener(new CarTabLayout.SimpleOnCarTabSelectedListener() {
            @Override
            public void onCarTabSelected(CarTabLayout.CarTab carTab) {
                viewPager.setCurrentItem(carTabLayout.getCarTabPosition(carTab));
            }
        });
        viewPager.addOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                carTabLayout.selectCarTab(position);
            }
        });
        buildTabs();
    }

    private void buildTabs() {
        mCarTabLayout.clearAllCarTabs();
        for (int i = 0; i < mRadioPagerAdapter.getCount(); i++) {
            CarTabLayout.CarTab carTab = new CarTabLayout.CarTab(mRadioPagerAdapter.getPageIcon(i),
                    mRadioPagerAdapter.getPageTitle(i));
            mCarTabLayout.addCarTab(carTab);
        }
    }

}
