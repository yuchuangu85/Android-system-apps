/*
 * Copyright (C) 2015 The Android Open Source Project
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

import android.app.FragmentManager;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.view.KeyEvent;
import com.android.tv.common.util.PostalCodeUtils;
import dagger.android.ContributesAndroidInjector;

/** An activity that serves tuner setup process. */
public class LiveTvTunerSetupActivity extends BaseTunerSetupActivity {
    private static final String TAG = "LiveTvTunerSetupActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // TODO(shubang): use LocationFragment
        if (checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            // No need to check the request result.
            requestPermissions(
                    new String[] {android.Manifest.permission.ACCESS_COARSE_LOCATION},
                    PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION);
        }
    }

    @Override
    protected void executeGetTunerTypeAndCountAsyncTask() {
        new AsyncTask<Void, Void, Integer>() {
            @Override
            protected Integer doInBackground(Void... arg0) {
                return mTunerFactory.getTunerTypeAndCount(LiveTvTunerSetupActivity.this).first;
            }

            @Override
            protected void onPostExecute(Integer result) {
                if (!LiveTvTunerSetupActivity.this.isDestroyed()) {
                    mTunerType = result;
                    if (result == null) {
                        finish();
                    } else if (!mActivityStopped) {
                        showInitialFragment();
                    } else {
                        mPendingShowInitialFragment = true;
                    }
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            FragmentManager manager = getFragmentManager();
            int count = manager.getBackStackEntryCount();
            if (count > 0) {
                String lastTag = manager.getBackStackEntryAt(count - 1).getName();
                if (ScanResultFragment.class.getCanonicalName().equals(lastTag) && count >= 2) {
                    String secondLastTag = manager.getBackStackEntryAt(count - 2).getName();
                    if (ScanFragment.class.getCanonicalName().equals(secondLastTag)) {
                        // Pops fragment including ScanFragment.
                        manager.popBackStack(
                                secondLastTag, FragmentManager.POP_BACK_STACK_INCLUSIVE);
                        return true;
                    }
                } else if (ScanFragment.class.getCanonicalName().equals(lastTag)) {
                    mLastScanFragment.finishScan(true);
                    return true;
                }
            }
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == PERMISSIONS_REQUEST_ACCESS_COARSE_LOCATION) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                try {
                    // Updating postal code takes time, therefore we should update postal code
                    // right after the permission is granted, so that the subsequent operations,
                    // especially EPG fetcher, could get the newly updated postal code.
                    PostalCodeUtils.updatePostalCode(this);
                } catch (Exception e) {
                    // Do nothing
                }
            }
        }
    }

    /**
     * Exports {@link LiveTvTunerSetupActivity} for Dagger codegen to create the appropriate
     * injector.
     */
    @dagger.Module
    public abstract static class Module {
        @ContributesAndroidInjector
        abstract LiveTvTunerSetupActivity contributeLiveTvTunerSetupActivityInjector();
    }
}
