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

package com.android.car.arch.common;

import static com.android.car.arch.common.LiveDataFunctions.loadingSwitchMap;

import static com.google.common.truth.Truth.assertThat;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.arch.common.testing.CaptureObserver;
import com.android.car.arch.common.testing.InstantTaskExecutorRule;
import com.android.car.arch.common.testing.TestLifecycleOwner;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(manifest = TestConfig.MANIFEST_PATH, sdk = TestConfig.SDK_VERSION)
public class LoadingSwitchMapTest {

    @Rule
    public final InstantTaskExecutorRule mTaskExecutorRule = new InstantTaskExecutorRule();
    @Rule
    public final TestLifecycleOwner mLifecycleOwner = new TestLifecycleOwner();

    private MutableLiveData<Object> mTrigger;
    private MutableLiveData<Integer> mOutput;
    private CaptureObserver<FutureData<Integer>> mObserver;

    @Before
    public void setUp() {
        mTrigger = new MutableLiveData<>();
        mOutput = new MutableLiveData<>();
        mObserver = new CaptureObserver<>();
    }

    @Test
    public void testIsLoading_uninitialized() {
        LiveData<FutureData<Integer>> underTest = loadingSwitchMap(mTrigger,
                (data) -> mOutput);
        underTest.observe(mLifecycleOwner, mObserver);

        assertThat(mObserver.hasBeenNotified()).isFalse();
    }

    @Test
    public void testIsLoading_initializedTrigger() {
        mTrigger.setValue(new Object());

        LiveData<FutureData<Integer>> underTest = loadingSwitchMap(mTrigger,
                (data) -> mOutput);
        underTest.observe(mLifecycleOwner, mObserver);

        assertThat(mObserver.hasBeenNotified()).isTrue();
        assertThat(mObserver.getObservedValue().isLoading()).isTrue();
        assertThat(mObserver.getObservedValue().getData()).isNull();
    }

    @Test
    public void testIsLoading_alreadyLoaded() {
        mTrigger.setValue(new Object());
        mOutput.setValue(1);

        LiveData<FutureData<Integer>> underTest = loadingSwitchMap(mTrigger,
                (data) -> mOutput);
        underTest.observe(mLifecycleOwner, mObserver);

        assertThat(mObserver.hasBeenNotified()).isTrue();
        assertThat(mObserver.getObservedValue().isLoading()).isFalse();
        assertThat(mObserver.getObservedValue().getData()).isEqualTo(1);
    }

    @Test
    public void testIsLoading_normalFlow() {
        LiveData<FutureData<Integer>> underTest = loadingSwitchMap(mTrigger,
                (data) -> mOutput);
        underTest.observe(mLifecycleOwner, mObserver);

        mTrigger.setValue(new Object());

        assertThat(mObserver.hasBeenNotified()).isTrue();
        assertThat(mObserver.getObservedValue().isLoading()).isTrue();

        mOutput.setValue(1);

        assertThat(mObserver.getObservedValue().isLoading()).isFalse();
        assertThat(mObserver.getObservedValue().getData()).isEqualTo(1);
    }

    @Test
    public void testIsLoading_secondLoad() {
        mTrigger.setValue(new Object());
        mOutput.setValue(1);

        LiveData<FutureData<Integer>> underTest = loadingSwitchMap(mTrigger,
                (data) -> mOutput);
        underTest.observe(mLifecycleOwner, mObserver);

        mTrigger.setValue(new Object());

        assertThat(mObserver.hasBeenNotified()).isTrue();
        assertThat(mObserver.getObservedValue().isLoading()).isTrue();
        assertThat(mObserver.getObservedValue().getData()).isNull();

        mOutput.setValue(2);

        assertThat(mObserver.getObservedValue().isLoading()).isFalse();
        assertThat(mObserver.getObservedValue().getData()).isEqualTo(2);
    }
}
