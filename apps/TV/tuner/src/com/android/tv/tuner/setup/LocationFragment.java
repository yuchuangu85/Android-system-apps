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

package com.android.tv.tuner.setup;

import static com.android.tv.tuner.setup.BaseTunerSetupActivity.PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION;

import android.content.pm.PackageManager;
import android.location.Address;
import android.os.Bundle;
import android.os.Handler;
import android.support.annotation.NonNull;
import android.support.v17.leanback.widget.GuidanceStylist.Guidance;
import android.support.v17.leanback.widget.GuidedAction;
import android.util.Log;

import com.android.tv.common.ui.setup.SetupActionHelper;
import com.android.tv.common.ui.setup.SetupGuidedStepFragment;
import com.android.tv.common.ui.setup.SetupMultiPaneFragment;
import com.android.tv.common.util.LocationUtils;
import com.android.tv.tuner.R;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** A fragment shows the rationale of location permission */
public class LocationFragment extends SetupMultiPaneFragment {
    private static final String TAG = "com.android.tv.tuner.setup.LocationFragment";
    private static final boolean DEBUG = true;

    public static final String ACTION_CATEGORY = "com.android.tv.tuner.setup.LocationFragment";
    public static final String KEY_POSTAL_CODE = "key_postal_code";

    public static final int ACTION_ALLOW_PERMISSION = 1;
    public static final int ENTER_ZIP_CODE = 2;
    public static final int ACTION_GETTING_LOCATION = 3;
    public static final int GET_LOCATION_TIMEOUT_MS = 3000;

    @Override
    protected SetupGuidedStepFragment onCreateContentFragment() {
        return new ContentFragment();
    }

    @Override
    protected String getActionCategory() {
        return ACTION_CATEGORY;
    }

    @Override
    protected boolean needsDoneButton() {
        return false;
    }

    /** The content fragment of {@link LocationFragment}. */
    public static class ContentFragment extends SetupGuidedStepFragment
            implements LocationUtils.OnUpdateAddressListener {
        private final List<GuidedAction> mGettingLocationAction = new ArrayList<>();
        private final Handler mHandler = new Handler();
        private final Object mPostalCodeLock = new Object();

        private String mPostalCode;
        private boolean mPermissionGranted;

        private final Runnable mTimeoutRunnable =
                () -> {
                    synchronized (mPostalCodeLock) {
                        if (DEBUG) {
                            Log.d(TAG,
                                    "get location timeout. mPostalCode=" + mPostalCode);
                        }
                        if (mPostalCode == null) {
                            // timeout. setup activity will get null postal code
                            LocationUtils.removeOnUpdateAddressListener(this);
                            passPostalCode();
                        }
                    }
                };

        @NonNull
        @Override
        public Guidance onCreateGuidance(Bundle savedInstanceState) {
            String title = getString(R.string.location_guidance_title);
            String description = getString(R.string.location_guidance_description);
            return new Guidance(title, description, getString(R.string.ut_setup_breadcrumb), null);
        }

        @Override
        public void onCreateActions(
                @NonNull List<GuidedAction> actions, Bundle savedInstanceState) {
            actions.add(
                    new GuidedAction.Builder(getActivity())
                            .id(ACTION_ALLOW_PERMISSION)
                            .title(getString(R.string.location_choices_allow_permission))
                            .build());
            actions.add(
                    new GuidedAction.Builder(getActivity())
                            .id(ENTER_ZIP_CODE)
                            .title(getString(R.string.location_choices_enter_zip_code))
                            .build());
            actions.add(
                    new GuidedAction.Builder(getActivity())
                            .id(ACTION_SKIP)
                            .title(getString(com.android.tv.common.R.string.action_text_skip))
                            .build());
            mGettingLocationAction.add(
                    new GuidedAction.Builder(getActivity())
                            .id(ACTION_GETTING_LOCATION)
                            .title(getString(R.string.location_choices_getting_location))
                            .focusable(false)
                            .build()
            );
        }

        @Override
        public void onGuidedActionClicked(GuidedAction action) {
            if (DEBUG) {
                Log.d(TAG, "onGuidedActionClicked. Action ID = " + action.getId());
            }
            if (action.getId() == ACTION_ALLOW_PERMISSION) {
                // request permission when users click this action
                mPermissionGranted = false;
                requestPermissions(
                        new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                        PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
            } else {
                super.onGuidedActionClicked(action);
            }
        }

        @Override
        protected String getActionCategory() {
            return ACTION_CATEGORY;
        }

        @Override
        public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                @NonNull int[] grantResults) {
            if (requestCode == PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION) {
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    synchronized (mPostalCodeLock) {
                        mPermissionGranted = true;
                        if (mPostalCode == null) {
                            // get postal code immediately if available
                            try {
                                Address address = LocationUtils.getCurrentAddress(getActivity());
                                if (address != null) {
                                    mPostalCode = address.getPostalCode();
                                }
                            } catch (IOException e) {
                                // do nothing
                            }
                        }
                        if (DEBUG) {
                            Log.d(TAG, "permission granted. mPostalCode=" + mPostalCode);
                        }
                        if (mPostalCode != null) {
                            // if postal code is known, pass it the setup activity
                            LocationUtils.removeOnUpdateAddressListener(this);
                            passPostalCode();
                        } else {
                            // show "getting location" message
                            setActions(mGettingLocationAction);
                            // post timeout runnable
                            mHandler.postDelayed(mTimeoutRunnable, GET_LOCATION_TIMEOUT_MS);
                        }
                    }
                }
            }
        }

        @Override
        public boolean onUpdateAddress(Address address) {
            synchronized (mPostalCodeLock) {
                // it takes time to get location after the permission is granted,
                // so this listener is needed
                mPostalCode = address.getPostalCode();
                if (DEBUG) {
                    Log.d(TAG, "onUpdateAddress. mPostalCode=" + mPostalCode);
                }
                if (mPermissionGranted && mPostalCode != null) {
                    // pass the postal code only if permission is granted
                    passPostalCode();
                    return true;
                }
                return false;
            }
        }

        @Override
        public void onResume() {
            if (DEBUG) {
                Log.d(TAG, "onResume");
            }
            super.onResume();
            LocationUtils.addOnUpdateAddressListener(this);
        }

        @Override
        public void onPause() {
            if (DEBUG) {
                Log.d(TAG, "onPause");
            }
            LocationUtils.removeOnUpdateAddressListener(this);
            mHandler.removeCallbacks(mTimeoutRunnable);
            super.onPause();
        }

        private void passPostalCode() {
            synchronized (mPostalCodeLock) {
                mHandler.removeCallbacks(mTimeoutRunnable);
                Bundle params = new Bundle();
                if (mPostalCode != null) {
                    params.putString(KEY_POSTAL_CODE, mPostalCode);
                }
                SetupActionHelper.onActionClick(
                        this, ACTION_CATEGORY, ACTION_ALLOW_PERMISSION, params);
            }
        }
    }
}
