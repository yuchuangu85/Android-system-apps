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
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Bundle;
import android.util.SparseIntArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.Adapter;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;

import com.android.pump.R;
import com.android.pump.activity.OtherDetailsActivity;
import com.android.pump.db.MediaDb;
import com.android.pump.db.Other;
import com.android.pump.util.Globals;
import com.android.pump.util.ImageLoader;
import com.android.pump.util.Orientation;
import com.android.pump.widget.UriImageView;

import java.util.List;

@UiThread
public class OtherFragment extends Fragment {
    private static final int SPAN_COUNT = 6;

    private RecyclerView mRecyclerView;

    public static @NonNull Fragment newInstance() {
        return new OtherFragment();
    }

    @Override
    public @NonNull View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_other, container, false);
        mRecyclerView = view.findViewById(R.id.fragment_other_recycler_view);
        mRecyclerView.setHasFixedSize(true);

        OtherAdapter otherAdapter = new OtherAdapter(requireContext());
        mRecyclerView.setAdapter(otherAdapter);

        GridLayoutManager gridLayoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        gridLayoutManager.setSpanSizeLookup(otherAdapter.getSpanSizeLookup());
        if (gridLayoutManager.getSpanCount() != SPAN_COUNT) {
            throw new IllegalArgumentException("Expected a span count of " + SPAN_COUNT +
                    ", found a span count of " + gridLayoutManager.getSpanCount() + ".");
        }

        mRecyclerView.setItemAnimator(null); // TODO Re-enable add/remove animations

        // TODO(b/123707260) Enable view caching
        //mRecyclerView.setItemViewCacheSize(0);
        //mRecyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
        return view;
    }

    private static class OtherAdapter extends Adapter<ViewHolder>
            implements MediaDb.UpdateCallback, ImageLoader.Callback {
        private final ImageLoader mImageLoader;
        private final MediaDb mMediaDb;
        private final List<Other> mOthers; // TODO(b/123710968) Use android.support.v7.util.SortedList/android.support.v7.widget.util.SortedListAdapterCallback instead
        private final SparseIntArray mSpanSize = new SparseIntArray();

        private OtherAdapter(@NonNull Context context) {
            setHasStableIds(true);

            mImageLoader = Globals.getImageLoader(context);
            mMediaDb = Globals.getMediaDb(context);
            mOthers = mMediaDb.getOthers();
            recalculateSpans();
        }

        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.addOtherUpdateCallback(this);
            mImageLoader.addCallback(this);
        }

        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.removeOtherUpdateCallback(this);
            mImageLoader.removeCallback(this);
        }

        @Override
        public @NonNull ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            if (viewType == R.layout.header) {
                return new ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false)) { };
            } else {
                return new OtherViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            if (position == 0) {
                // TODO Handle header view
            } else {
                Other other = mOthers.get(position - 1);
                mMediaDb.loadData(other); // TODO Where should we call this? In bind()?
                ((OtherViewHolder) holder).bind(other);
            }
        }

        @Override
        public int getItemCount() {
            return mOthers.size() + 1;
        }

        @Override
        public long getItemId(int position) {
            return position == 0 ? -1 : mOthers.get(position - 1).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? R.layout.header : R.layout.other;
        }

        @Override
        public void onImageLoaded(@NonNull Uri uri, @Nullable Bitmap bitmap) {
            // TODO Optimize this (only update necessary parts -- not the whole world)
            recalculateSpans();
            notifyItemRangeChanged(1, mOthers.size());
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

        private void recalculateSpans() {
            // TODO Recalculate when an image is loaded
            // TODO Recalculate when notifyXxx is called
            // TODO Optimize
            mSpanSize.clear();
            int current = 0;
            while (current < mOthers.size()) {
                int orientation = getOrientation(current);
                if (orientation == Orientation.LANDSCAPE) {
                    orientation = getOrientation(current + 1);
                    if (orientation == Orientation.LANDSCAPE) {
                        // L L
                        mSpanSize.append(current++, SPAN_COUNT / 2);
                        mSpanSize.append(current++, SPAN_COUNT / 2);
                    } else if (orientation == Orientation.PORTRAIT) {
                        // L P
                        mSpanSize.append(current++, SPAN_COUNT * 2 / 3);
                        mSpanSize.append(current++, SPAN_COUNT * 1 / 3);
                    } else {
                        // L
                        mSpanSize.append(current++, SPAN_COUNT);
                    }
                } else if (orientation == Orientation.PORTRAIT) {
                    orientation = getOrientation(current + 1);
                    if (orientation == Orientation.LANDSCAPE) {
                        // P L
                        mSpanSize.append(current++, SPAN_COUNT * 1 / 3);
                        mSpanSize.append(current++, SPAN_COUNT * 2 / 3);
                    } else if (orientation == Orientation.PORTRAIT &&
                            getOrientation(current + 2) == Orientation.PORTRAIT) {
                        // P P P
                        mSpanSize.append(current++, SPAN_COUNT / 3);
                        mSpanSize.append(current++, SPAN_COUNT / 3);
                        mSpanSize.append(current++, SPAN_COUNT / 3);
                    } else {
                        // P
                        mSpanSize.append(current++, SPAN_COUNT);
                    }
                } else {
                    // unknown
                    mSpanSize.append(current++, SPAN_COUNT);
                }
            }
        }

        private @Orientation int getOrientation(int index) {
            Uri thumbUri = index >= mOthers.size() ? null : mOthers.get(index).getThumbnailUri();
            if (thumbUri == null) {
                return Orientation.UNKNOWN;
            }
            return mImageLoader.getOrientation(thumbUri);
        }

        private @NonNull SpanSizeLookup getSpanSizeLookup() {
            return new SpanSizeLookup() {
                @Override
                public int getSpanSize(int position) {
                    return position == 0 ? SPAN_COUNT : mSpanSize.get(position - 1);
                }

                @Override
                public int getSpanIndex(int position, int spanCount) {
                    // TODO Optimize
                    return super.getSpanIndex(position, spanCount);
                }

                @Override
                public int getSpanGroupIndex(int adapterPosition, int spanCount) {
                    // TODO Optimize
                    return super.getSpanGroupIndex(adapterPosition, spanCount);
                }
            };
        }
    }

    private static class OtherViewHolder extends ViewHolder {
        private OtherViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Other other) {
            UriImageView imageView = itemView.findViewById(R.id.other_image);

            imageView.setImageURI(other.getThumbnailUri());

            itemView.setOnClickListener((view) ->
                    OtherDetailsActivity.start(view.getContext(), other));
        }
    }
}
