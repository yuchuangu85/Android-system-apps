/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.arch.common.testing;

import android.annotation.NonNull;

import androidx.annotation.RestrictTo;
import androidx.lifecycle.Lifecycle;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LifecycleRegistry;

import org.junit.rules.ExternalResource;

/**
 * A LifecycleOwner whose {@link Lifecycle} is RESUMED during the test and DESTROYED after. The
 * {@code Lifecycle} can be changed during the test using {@link #markState(Lifecycle.State)}.
 */
@RestrictTo(RestrictTo.Scope.TESTS)
public class TestLifecycleOwner extends ExternalResource implements LifecycleOwner {

    private final LifecycleRegistry mRegistry = new LifecycleRegistry(this);

    @NonNull
    @Override
    public Lifecycle getLifecycle() {
        return mRegistry;
    }

    @Override
    protected void before() throws Throwable {
        super.before();
        mRegistry.markState(Lifecycle.State.RESUMED);
    }

    @Override
    protected void after() {
        mRegistry.markState(Lifecycle.State.DESTROYED);
        super.after();
    }

    /**
     * Move the {@code Lifecycle} to the specified state.
     *
     * @see LifecycleRegistry#markState(Lifecycle.State)
     */
    public void markState(Lifecycle.State state) {
        mRegistry.markState(state);
    }
}
