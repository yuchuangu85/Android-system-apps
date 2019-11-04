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

package com.android.car;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.junit.Before;
import org.junit.Test;

public class VmsPublishersInfoTest {
    public static final byte[] MOCK_INFO_0 = new byte[]{2, 3, 5, 7, 11, 13, 17};
    public static final byte[] SAME_MOCK_INFO_0 = new byte[]{2, 3, 5, 7, 11, 13, 17};
    public static final byte[] MOCK_INFO_1 = new byte[]{2, 3, 5, 7, 11, 13, 17, 19};

    private VmsPublishersInfo mVmsPublishersInfo;

    @Before
    public void setUp() throws Exception {
        mVmsPublishersInfo = new VmsPublishersInfo();
    }

    @Test
    public void testSingleInfo() throws Exception {
        int id = mVmsPublishersInfo.getIdForInfo(MOCK_INFO_0);
        assertEquals(0, id);
        assertArrayEquals(MOCK_INFO_0, mVmsPublishersInfo.getPublisherInfo(id));
    }

    @Test
    public void testSingleInfo_NoSuchId() throws Exception {
        assertEquals(0, mVmsPublishersInfo.getPublisherInfo(12345).length);
    }

    @Test
    public void testTwoInfos() throws Exception {
        int id0 = mVmsPublishersInfo.getIdForInfo(MOCK_INFO_0);
        int id1 = mVmsPublishersInfo.getIdForInfo(MOCK_INFO_1);
        assertEquals(0, id0);
        assertEquals(1, id1);
        assertArrayEquals(MOCK_INFO_0, mVmsPublishersInfo.getPublisherInfo(id0));
        assertArrayEquals(MOCK_INFO_1, mVmsPublishersInfo.getPublisherInfo(id1));
    }

    @Test
    public void testSingleInfoInsertedTwice() throws Exception {
        int id = mVmsPublishersInfo.getIdForInfo(MOCK_INFO_0);
        assertEquals(0, id);

        int sameId = mVmsPublishersInfo.getIdForInfo(SAME_MOCK_INFO_0);
        assertEquals(sameId, id);
    }
}
