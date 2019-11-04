/*
 * Copyright (C) 2019 The Android Open Source Project
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

package com.android.documentsui.queries;

import static com.google.common.truth.Truth.assertThat;

import android.content.Context;
import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.queries.SearchHistoryManager;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.List;

import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
@MediumTest
public final class SearchHistoryManagerTest {

    private Context mContext;
    private CountDownLatch mLatch ;
    private SearchHistoryManager mManager;
    private SearchHistoryManager.DatabaseChangedListener mListener;
    private int mIntResult;
    private long mLongResult;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
        mManager = SearchHistoryManager.getInstance(mContext);
        clearData();
        mIntResult = -1;
        mLongResult = -1;
    }

    @After
    public void tearDown() {
        mListener = null;
        clearData();
    }

    private void clearData() {
        final List<String> list = mManager.getHistoryList(null);
        for (int i = 0; i < list.size(); i++) {
            mManager.deleteHistory(list.get(i));
        }
    }

    @Test
    public void testAddHistory() throws Exception {
        mLatch = new CountDownLatch(2);
        mListener = new SearchHistoryManager.DatabaseChangedListener() {
            @Override
            public void onAddChangedListener(long longResult) {
                mLongResult = longResult;
                mLatch.countDown();
            }
            @Override
            public void onDeleteChangedListener(int intResult) { }
            @Override
            public void onPostExecute() { }

            };
        mManager.setDatabaseListener(mListener);
        mManager.addHistory("testKeyword");
        mLatch.await(1, TimeUnit.SECONDS);

        assertThat(mLongResult).isGreaterThan(0L);
    }

    @Test
    public void testDeleteHistory() throws Exception {
        mLatch = new CountDownLatch(2);
        mListener = new SearchHistoryManager.DatabaseChangedListener() {
            @Override
            public void onAddChangedListener(long longResult) {
                mLongResult = longResult;
                mLatch.countDown();
            }
            @Override
            public void onPostExecute() { }

            @Override public void onDeleteChangedListener(int intResult) {
                mIntResult = intResult;
                mLatch.countDown();
            }
        };
        mManager.setDatabaseListener(mListener);

        mManager.addHistory("testDeleteKeyword");
        mLatch.await(1, TimeUnit.SECONDS);
        assertThat(mLongResult).isGreaterThan(0L);

        // TODO: Solving this tricky usage of new CountDownLatch(2) count with bg/127610355
        // Using this tricky way is for making sure the result synchronization of public APIs
        // getHistoryList()/addHistory()/deleteHistory() with database processing.
        // From design contract and non-blocking UI design, not necessarily doing synchronization
        // from code level, therefore doing this tricky usage of new CountDownLatch in test case for
        // guarantee the synchronization.
        mLatch = new CountDownLatch(2);
        mManager.deleteHistory("testDeleteKeyword");
        mLatch.await(1, TimeUnit.SECONDS);
        assertThat(mIntResult).isGreaterThan(0);
    }

    @Test
    public void testGetHistoryList() throws Exception {
        mLatch = new CountDownLatch(2);
        mListener = new SearchHistoryManager.DatabaseChangedListener() {
            @Override
            public void onAddChangedListener(long longResult) { }
            @Override
            public void onDeleteChangedListener(int intResult) { }
            @Override
            public void onPostExecute() {
                mLatch.countDown();
            }
        };
        mManager.setDatabaseListener(mListener);

        mManager.addHistory("abcdefghijk");
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(2);
        mManager.addHistory("lmnop");
        mLatch.await(1, TimeUnit.SECONDS);

        mLatch = new CountDownLatch(2);
        mManager.addHistory("qrstuv");
        mLatch.await(1, TimeUnit.SECONDS);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(mManager.getHistoryList(null).size()).isEqualTo(3);

        // Test the last adding history should be the first item in the list.
        assertThat(mManager.getHistoryList(null).get(0)).contains("qrstuv");

        assertThat(mManager.getHistoryList(null).get(2)).contains("abcdefghijk");
    }
}
