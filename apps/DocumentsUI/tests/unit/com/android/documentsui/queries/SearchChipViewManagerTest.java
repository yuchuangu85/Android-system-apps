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

package com.android.documentsui.queries;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.spy;

import android.content.Context;
import android.os.Bundle;

import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;

import androidx.test.filters.SmallTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.documentsui.R;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.Shared;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@RunWith(AndroidJUnit4.class)
@SmallTest
public final class SearchChipViewManagerTest {

    private static final String[] TEST_MIME_TYPES = new String[]{"image/*", "video/*"};
    private static int CHIP_TYPE = 1000;

    private Context mContext;
    private SearchChipViewManager mSearchChipViewManager;
    private LinearLayout mChipGroup;

    @Before
    public void setUp() {
        mContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
        mContext.setTheme(com.android.documentsui.R.style.DocumentsTheme);
        mContext.getTheme().applyStyle(R.style.DocumentsDefaultTheme, false);
        mChipGroup = spy(new LinearLayout(mContext));

        mSearchChipViewManager = new SearchChipViewManager(mChipGroup);
        mSearchChipViewManager.initChipSets(new String[] {"*/*"});
    }

    @Test
    public void testInitChipSets_HasCorrectValue() throws Exception {
        mSearchChipViewManager.initChipSets(TEST_MIME_TYPES);
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        assertThat(mChipGroup.getChildCount()).isEqualTo(TEST_MIME_TYPES.length);
    }

    @Test
    public void testUpdateChips_HasCorrectValue() throws Exception {
        mSearchChipViewManager.updateChips(TEST_MIME_TYPES);

        assertThat(mChipGroup.getChildCount()).isEqualTo(TEST_MIME_TYPES.length);
    }

    @Test
    public void testGetCheckedChipMimeTypes_HasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();

        final String[] checkedMimeTypes = mSearchChipViewManager.getCheckedMimeTypes();

        assertThat(MimeTypes.mimeMatches(TEST_MIME_TYPES, checkedMimeTypes[0])).isTrue();
        assertThat(MimeTypes.mimeMatches(TEST_MIME_TYPES, checkedMimeTypes[1])).isTrue();
    }

    @Test
    public void testRestoreCheckedChipItems_HasCorrectValue() throws Exception {
        Bundle bundle = new Bundle();
        bundle.putIntArray(Shared.EXTRA_QUERY_CHIPS, new int[]{2});

        mSearchChipViewManager.restoreCheckedChipItems(bundle);

        assertThat(mSearchChipViewManager.mCheckedChipItems.size()).isEqualTo(1);
        Iterator<SearchChipData> iterator = mSearchChipViewManager.mCheckedChipItems.iterator();
        assertThat(iterator.next().getChipType()).isEqualTo(2);
    }

    @Test
    public void testSaveInstanceState_HasCorrectValue() throws Exception {
        mSearchChipViewManager.mCheckedChipItems = getFakeSearchChipDataList();
        Bundle bundle = new Bundle();

        mSearchChipViewManager.onSaveInstanceState(bundle);

        final int[] chipTypes = bundle.getIntArray(Shared.EXTRA_QUERY_CHIPS);
        assertThat(chipTypes.length).isEqualTo(1);
        assertThat(chipTypes[0]).isEqualTo(CHIP_TYPE);
    }

    @Test
    public void testBindMirrorGroup_sameValue() throws Exception {
        mSearchChipViewManager.updateChips(new String[] {"*/*"});

        ViewGroup mirror = spy(new LinearLayout(mContext));
        mSearchChipViewManager.bindMirrorGroup(mirror);

        assertThat(View.VISIBLE).isEqualTo(mirror.getVisibility());
        assertThat(mChipGroup.getChildCount()).isEqualTo(mirror.getChildCount());
        assertThat(mChipGroup.getChildAt(0).getTag()).isEqualTo(mirror.getChildAt(0).getTag());
    }

    @Test
    public void testBindMirrorGroup_hideRow() throws Exception {
        mSearchChipViewManager.updateChips(new String[] {"image/*"});

        ViewGroup mirror = spy(new LinearLayout(mContext));
        mSearchChipViewManager.bindMirrorGroup(mirror);

        assertThat(View.GONE).isEqualTo(mirror.getVisibility());
    }

    private static Set<SearchChipData> getFakeSearchChipDataList() {
        final Set<SearchChipData> chipDataList = new HashSet<>();
        chipDataList.add(new SearchChipData(CHIP_TYPE, 0, TEST_MIME_TYPES));
        return chipDataList;
    }
}
