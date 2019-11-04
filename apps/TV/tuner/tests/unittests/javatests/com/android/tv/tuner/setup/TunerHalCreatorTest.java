/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import android.os.AsyncTask;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;
import com.android.tv.tuner.api.Tuner;
import com.android.tv.tuner.setup.BaseTunerSetupActivity.TunerHalCreator;
import java.util.concurrent.Executor;
import org.junit.Test;
import org.junit.runner.RunWith;

/** Tests for {@link TunerHalCreator}. */
@SmallTest
@RunWith(AndroidJUnit4.class)
public class TunerHalCreatorTest {
    private final FakeExecutor mFakeExecutor = new FakeExecutor();

    private static class TestTunerHalCreator extends TunerHalCreator {
        private TestTunerHalCreator(Executor executor) {
            super(null, executor);
        }

        @Override
        protected Tuner createInstance() {
            return new com.android.tv.tuner.FakeTunerHal() {};
        }
    }

    private static class FakeExecutor implements Executor {
        Runnable mCommand;

        @Override
        public synchronized void execute(final Runnable command) {
            mCommand = command;
        }

        private synchronized void executeActually() {
            mCommand.run();
        }
    }

    @Test
    public void test_asyncGet() {
        TunerHalCreator tunerHalCreator = new TestTunerHalCreator(mFakeExecutor);
        assertNull(tunerHalCreator.mTunerHal);
        tunerHalCreator.generate();
        assertNull(tunerHalCreator.mTunerHal);
        mFakeExecutor.executeActually();
        Tuner tunerHal = tunerHalCreator.getOrCreate();
        assertNotNull(tunerHal);
        assertSame(tunerHal, tunerHalCreator.getOrCreate());
        tunerHalCreator.clear();
    }

    @Test
    public void test_syncGet() {
        TunerHalCreator tunerHalCreator = new TestTunerHalCreator(AsyncTask.SERIAL_EXECUTOR);
        assertNull(tunerHalCreator.mTunerHal);
        tunerHalCreator.generate();
        assertNotNull(tunerHalCreator.getOrCreate());
    }

    @Test
    public void test_syncGetWithoutGenerate() {
        TunerHalCreator tunerHalCreator = new TestTunerHalCreator(mFakeExecutor);
        assertNull(tunerHalCreator.mTunerHal);
        assertNotNull(tunerHalCreator.getOrCreate());
    }
}
