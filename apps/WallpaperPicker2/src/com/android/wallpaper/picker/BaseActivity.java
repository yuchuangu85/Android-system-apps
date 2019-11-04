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
package com.android.wallpaper.picker;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Base activity that keeps track of whether fragment transactions are safe to commit given the
 * activity's current lifecycle state.
 */
public class BaseActivity extends AppCompatActivity {

    private boolean mIsSafeToCommitFragmentTransaction;

    @Override
    protected void onResume() {
        super.onResume();
        mIsSafeToCommitFragmentTransaction = true;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mIsSafeToCommitFragmentTransaction = false;
    }

    public boolean isSafeToCommitFragmentTransaction() {
        return mIsSafeToCommitFragmentTransaction;
    }
}
