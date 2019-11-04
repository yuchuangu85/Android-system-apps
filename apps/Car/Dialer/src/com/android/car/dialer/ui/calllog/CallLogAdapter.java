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
package com.android.car.dialer.ui.calllog;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.ui.common.entity.HeaderViewHolder;
import com.android.car.dialer.ui.common.entity.UiCallLog;
import com.android.car.telephony.common.Contact;

import java.util.ArrayList;
import java.util.List;

/** Adapter for call history list. */
public class CallLogAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

    private static final String TAG = "CD.CallLogAdapter";

    /** IntDef for the different groups of calllog lists separated by time periods. */
    @IntDef({
            EntryType.TYPE_HEADER,
            EntryType.TYPE_CALLLOG,
    })
    private @interface EntryType {
        /** Entry typre is header. */
        int TYPE_HEADER = 1;

        /** Entry type is calllog. */
        int TYPE_CALLLOG = 2;
    }

    public interface OnShowContactDetailListener {
        void onShowContactDetail(Contact contact);
    }

    private List<Object> mUiCallLogs = new ArrayList<>();
    private Context mContext;
    private CallLogAdapter.OnShowContactDetailListener mOnShowContactDetailListener;

    public CallLogAdapter(Context context,
            CallLogAdapter.OnShowContactDetailListener onShowContactDetailListener) {
        mContext = context;
        mOnShowContactDetailListener = onShowContactDetailListener;
    }

    /**
     * Sets calllogs.
     */
    public void setUiCallLogs(@NonNull List<Object> uiCallLogs) {
        L.d(TAG, "setUiCallLogs: %d", uiCallLogs.size());
        mUiCallLogs.clear();
        mUiCallLogs.addAll(uiCallLogs);
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == EntryType.TYPE_CALLLOG) {
            View rootView = LayoutInflater.from(mContext)
                    .inflate(R.layout.call_history_list_item, parent, false);
            return new CallLogViewHolder(rootView, mOnShowContactDetailListener);
        }

        View rootView = LayoutInflater.from(mContext)
                .inflate(R.layout.header_item, parent, false);
        return new HeaderViewHolder(rootView);
    }

    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        if (holder instanceof  CallLogViewHolder) {
            ((CallLogViewHolder) holder).onBind((UiCallLog) mUiCallLogs.get(position));
        } else {
            ((HeaderViewHolder) holder).setHeaderTitle((String) mUiCallLogs.get(position));
        }
    }

    @Override
    @EntryType
    public int getItemViewType(int position) {
        if (mUiCallLogs.get(position) instanceof UiCallLog) {
            return EntryType.TYPE_CALLLOG;
        } else {
            return EntryType.TYPE_HEADER;
        }
    }

    @Override
    public void onViewRecycled(@NonNull RecyclerView.ViewHolder holder) {
        if (holder instanceof CallLogViewHolder) {
            ((CallLogViewHolder) holder).onRecycle();
        }
    }

    @Override
    public int getItemCount() {
        return mUiCallLogs.size();
    }
}
