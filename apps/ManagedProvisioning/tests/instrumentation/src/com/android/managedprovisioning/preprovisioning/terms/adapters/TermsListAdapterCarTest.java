/*
 * Copyright 2018, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.preprovisioning.terms.adapters;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.res.Resources;
import android.view.View;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;
import java.util.List;

@SmallTest
public class TermsListAdapterCarTest {
    @Mock private Context mContext;
    @Mock private Resources mResources;
    @Mock private View mView;

    private List<TermsDocument> mDocs;

    private String header1 = "h1";
    private String header2 = "h2";
    private String header3 = "h3";
    private String content1 = "c1";
    private String content2 = "c2";
    private String content3 = "c3";

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        TermsDocument doc1 = TermsDocument.createInstance(header1, content1);
        TermsDocument doc2 = TermsDocument.createInstance(header2, content2);
        TermsDocument doc3 = TermsDocument.createInstance(header3, content3);
        mDocs = Arrays.asList(doc1, doc2, doc3);
    }

    @Test
    public void returnsCorrectItemCount() {
        // given: an adapter
        TermsListAdapterCar adapter = new TermsListAdapterCar(mContext, mDocs,
                /* statusBarColor */ 1);

        assertThat(adapter.getItemCount()).isEqualTo(mDocs.size());
    }

    @Test
    public void onBindViewHolderSetsViewCorrectly() {
        String h2ContentDescription = "h2 content description";
        TextView contentTextView = new TextView(InstrumentationRegistry.getTargetContext());
        TextView headerTextView = new TextView(InstrumentationRegistry.getTargetContext());

        when(mView.findViewById(R.id.header_text)).thenReturn(headerTextView);
        when(mView.findViewById(R.id.disclaimer_content)).thenReturn(contentTextView);
        when(mContext.getResources()).thenReturn(mResources);
        when(mResources.getString(R.string.section_heading, header2))
                .thenReturn(h2ContentDescription);

        TermsListAdapterCar.ViewHolder mViewHolder = new TermsListAdapterCar.ViewHolder(mView);
        // given: an adapter
        TermsListAdapterCar adapter = new TermsListAdapterCar(mContext, mDocs,
                /* statusBarColor= */ 1);
        // Check if information from doc2 is extracted and set in the view correctly
        adapter.onBindViewHolder(mViewHolder, /* position= */ 1);

        assertThat(headerTextView.getText()).isEqualTo(header2);
        assertThat(headerTextView.getContentDescription())
                .isEqualTo(h2ContentDescription);
        assertThat(contentTextView.getText().toString()).isEqualTo(content2);
    }
}
