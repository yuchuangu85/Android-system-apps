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

package com.android.pump.fragment;

import android.content.Context;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.core.view.ViewCompat;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.SimpleItemAnimator;

import com.android.pump.R;
import com.android.pump.activity.SeriesDetailsActivity;
import com.android.pump.db.MediaDb;
import com.android.pump.db.Series;
import com.android.pump.util.Globals;

import java.util.List;

@UiThread
public class SeriesFragment extends Fragment {
    private RecyclerView mRecyclerView;

    public static @NonNull Fragment newInstance() {
        return new SeriesFragment();
    }

    @Override
    public @NonNull View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_series, container, false);
        mRecyclerView = view.findViewById(R.id.fragment_series_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(new SeriesAdapter(requireContext()));
        mRecyclerView.addItemDecoration(new SpaceItemDecoration(4, 16));

        GridLayoutManager gridLayoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        gridLayoutManager.setSpanSizeLookup(
                new HeaderSpanSizeLookup(gridLayoutManager.getSpanCount()));

        ((SimpleItemAnimator) mRecyclerView.getItemAnimator()).setSupportsChangeAnimations(false);

        // TODO(b/123707260) Enable view caching
        //mRecyclerView.setItemViewCacheSize(0);
        //mRecyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
        return view;
    }

    private static class SeriesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements MediaDb.UpdateCallback {
        private final MediaDb mMediaDb;
        private final List<Series> mSeries; // TODO(b/123710968) Use android.support.v7.util.SortedList/android.support.v7.widget.util.SortedListAdapterCallback instead

        private SeriesAdapter(@NonNull Context context) {
            // TODO setHasStableIds(true);
            mMediaDb = Globals.getMediaDb(context);
            mSeries = mMediaDb.getSeries();
        }

        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.addSeriesUpdateCallback(this);
        }

        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.removeSeriesUpdateCallback(this);
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            if (viewType == R.layout.header) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false)) { };
            } else {
                return new SeriesViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                // TODO Handle header view
            } else {
                Series series = mSeries.get(position - 1);
                mMediaDb.loadData(series); // TODO Where should we call this? In bind()?
                ((SeriesViewHolder) holder).bind(series);
            }
        }

        @Override
        public int getItemCount() {
            return mSeries.size() + 1;
        }

        @Override
        public long getItemId(int position) {
            return 0; // TODO return position == 0 ? -1 : mSeries.get(position - 1).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? R.layout.header : R.layout.series;
        }

        @Override
        public void onItemsInserted(int index, int count) {
            notifyItemRangeInserted(index + 1, count);
        }

        @Override
        public void onItemsUpdated(int index, int count) {
            notifyItemRangeChanged(index + 1, count);
        }

        @Override
        public void onItemsRemoved(int index, int count) {
            notifyItemRangeRemoved(index + 1, count);
        }
    }

    private static class SeriesViewHolder extends RecyclerView.ViewHolder {
        private SeriesViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Series series) {
            ImageView imageView = itemView.findViewById(R.id.series_image);
            TextView textView = itemView.findViewById(R.id.series_text);

            imageView.setImageURI(series.getPosterUri());
            textView.setText(series.getTitle());

            itemView.setOnClickListener((view) ->
                    SeriesDetailsActivity.start(view.getContext(), series));
        }
    }

    private static class SpaceItemDecoration extends RecyclerView.ItemDecoration {
        private final int mXOffset;
        private final int mYOffset;

        private SpaceItemDecoration(int xOffset, int yOffset) {
            mXOffset = xOffset;
            mYOffset = yOffset;
        }

        @Override
        public void getItemOffsets(@NonNull Rect outRect, @NonNull View view, @NonNull RecyclerView parent, @NonNull RecyclerView.State state) {
            DisplayMetrics displayMetrics = new DisplayMetrics();
            ViewCompat.getDisplay(parent).getMetrics(displayMetrics);
            outRect.left = outRect.right = (int) Math.ceil(mXOffset * displayMetrics.density);
            if (parent.getChildAdapterPosition(view) > 0) {
                outRect.bottom = (int) Math.ceil(mYOffset * displayMetrics.density);
            }
        }
    }

    private static class HeaderSpanSizeLookup extends SpanSizeLookup {
        private final int mSpanCount;

        private HeaderSpanSizeLookup(int spanCount) {
            mSpanCount = spanCount;
        }

        @Override
        public int getSpanSize(int position) {
            return position == 0 ? mSpanCount : 1;
        }

        @Override
        public int getSpanIndex(int position, int spanCount) {
            return position == 0 ? 0 : (position - 1) % spanCount;
        }

        @Override
        public int getSpanGroupIndex(int adapterPosition, int spanCount) {
            return adapterPosition == 0 ? 0 : ((adapterPosition - 1) / spanCount) + 1;
        }
    }
}
