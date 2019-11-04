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
package com.google.android.car.bugreport;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class BugInfoAdapter extends RecyclerView.Adapter<BugInfoAdapter.BugInfoViewHolder> {

    static final int BUTTON_TYPE_UPLOAD = 0;
    static final int BUTTON_TYPE_MOVE = 1;

    /** Provides a handler for click events*/
    interface ItemClickedListener {
        /** onItemClicked handles click events differently depending on provided buttonType and
         * uses additional information provided in metaBugReport. */
        void onItemClicked(int buttonType, MetaBugReport metaBugReport);
    }

    /**
     * Reference to each bug report info views.
     */
    public static class BugInfoViewHolder extends RecyclerView.ViewHolder {
        /** Title view */
        public TextView titleView;

        /** User view */
        public TextView userView;

        /** TimeStamp View */
        public TextView timestampView;

        /** Status View */
        public TextView statusView;

        /** Message View */
        public TextView messageView;

        /** Move Button */
        public Button moveButton;

        /** Upload Button */
        public Button uploadButton;

        BugInfoViewHolder(View v) {
            super(v);
            titleView = itemView.findViewById(R.id.bug_info_row_title);
            userView = itemView.findViewById(R.id.bug_info_row_user);
            timestampView = itemView.findViewById(R.id.bug_info_row_timestamp);
            statusView = itemView.findViewById(R.id.bug_info_row_status);
            messageView = itemView.findViewById(R.id.bug_info_row_message);
            moveButton = itemView.findViewById(R.id.bug_info_move_button);
            uploadButton = itemView.findViewById(R.id.bug_info_upload_button);
        }
    }

    private final List<MetaBugReport> mDataset;
    private final ItemClickedListener mItemClickedListener;

    BugInfoAdapter(List<MetaBugReport> dataSet, ItemClickedListener itemClickedListener) {
        mDataset = dataSet;
        mItemClickedListener = itemClickedListener;
    }

    @Override
    public BugInfoViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        // create a new view
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.bug_info_view, parent, false);
        return new BugInfoViewHolder(v);
    }

    @Override
    public void onBindViewHolder(BugInfoViewHolder holder, int position) {
        MetaBugReport bugreport = mDataset.get(position);
        holder.titleView.setText(mDataset.get(position).getTitle());
        holder.userView.setText(mDataset.get(position).getUsername());
        holder.timestampView.setText(mDataset.get(position).getTimestamp());
        holder.statusView.setText(Status.toString(mDataset.get(position).getStatus()));
        holder.messageView.setText(mDataset.get(position).getStatusMessage());
        if (bugreport.getStatus() == Status.STATUS_PENDING_USER_ACTION.getValue()
                || bugreport.getStatus() == Status.STATUS_MOVE_FAILED.getValue()
                || bugreport.getStatus() == Status.STATUS_UPLOAD_FAILED.getValue()) {
            holder.moveButton.setOnClickListener(
                    view -> mItemClickedListener.onItemClicked(BUTTON_TYPE_MOVE, bugreport));
            holder.uploadButton.setOnClickListener(
                    view -> mItemClickedListener.onItemClicked(BUTTON_TYPE_UPLOAD, bugreport));
        } else {
            holder.moveButton.setEnabled(false);
            holder.uploadButton.setEnabled(false);
        }
    }

    @Override
    public int getItemCount() {
        return mDataset.size();
    }
}
