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

package com.android.car.settings.testutils;

import android.os.Bundle;

import androidx.fragment.app.Fragment;

import com.android.car.settings.R;

import org.robolectric.android.controller.ActivityController;
import org.robolectric.android.controller.ComponentController;

/**
 * Version of FragmentController that can be used for {@link androidx.fragment.app.Fragment} until
 * upstream support is ready.
 */
public class FragmentController<F extends Fragment> extends
        ComponentController<FragmentController<F>, F> {

    private final F mFragment;
    private final ActivityController<BaseTestActivity> mActivityController;

    private FragmentController(F fragment) {
        super(fragment);
        mFragment = fragment;
        mActivityController = ActivityController.of(new BaseTestActivity());
    }

    public static <F extends Fragment> FragmentController<F> of(F fragment) {
        return new FragmentController<>(fragment);
    }

    /**
     * Returns the fragment after attaching it to an activity, calling its onCreate() through
     * onResume() lifecycle methods and making it visible.
     */
    public F setup() {
        return create().start().resume().visible().get();
    }

    /**
     * Creates the activity with {@link Bundle} and adds the fragment to it.
     */
    public FragmentController<F> create(final Bundle bundle) {
        shadowMainLooper.runPaused(
                () -> mActivityController
                        .create(bundle)
                        .get()
                        .getSupportFragmentManager()
                        .beginTransaction()
                        .add(R.id.fragment_container, mFragment)
                        .commitNow());
        return this;
    }

    @Override
    public FragmentController<F> create() {
        return create(null);
    }

    @Override
    public FragmentController<F> destroy() {
        shadowMainLooper.runPaused(mActivityController::destroy);
        return this;
    }

    public FragmentController<F> start() {
        shadowMainLooper.runPaused(mActivityController::start);
        return this;
    }

    public FragmentController<F> resume() {
        shadowMainLooper.runPaused(mActivityController::resume);
        return this;
    }

    public FragmentController<F> pause() {
        shadowMainLooper.runPaused(mActivityController::pause);
        return this;
    }

    public FragmentController<F> stop() {
        shadowMainLooper.runPaused(mActivityController::stop);
        return this;
    }

    public FragmentController<F> visible() {
        shadowMainLooper.runPaused(mActivityController::visible);
        return this;
    }
}
