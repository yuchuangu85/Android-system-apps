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

import android.annotation.ColorInt;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.preprovisioning.terms.TermsDocument;

/**
 * Allows for displaying {@link TermsDocument} objects in an
 * {@link androidx.car.widget.PagedListView}.
 */
public class TermsListAdapterCar extends RecyclerView.Adapter<TermsListAdapterCar.ViewHolder> {

    private final List<TermsDocument> mTerms;
    private final Context mContext;
    private final int mStatusBarColor;

    public TermsListAdapterCar(Context context, List<TermsDocument> terms,
            @ColorInt int statusBarColor) {
        mTerms = terms;
        mContext = context;
        mStatusBarColor = statusBarColor;
    }

    @Override
    public TermsListAdapterCar.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.terms_disclaimer, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(TermsListAdapterCar.ViewHolder holder, int position) {
        TermsDocument disclaimer = mTerms.get(position);

        String heading = disclaimer.getHeading();
        holder.mHeaderTextView.setText(heading);
        holder.mHeaderTextView.setContentDescription(mContext.getResources()
                .getString(R.string.section_heading, disclaimer.getHeading()));

        TermsAdapterUtils.populateContentTextView(mContext, holder.mContentTextView, disclaimer,
                mStatusBarColor);
    }

    @Override
    public int getItemCount() {
        return mTerms.size();
    }

    /**
     * ViewHolder for TermsListAdapterCar.
     */
    public static class ViewHolder extends RecyclerView.ViewHolder {
        final TextView mHeaderTextView;
        final TextView mContentTextView;

        public ViewHolder(View itemView) {
            super(itemView);
            mHeaderTextView = itemView.findViewById(R.id.header_text);
            mContentTextView = itemView.findViewById(R.id.disclaimer_content);
        }
    }
}
