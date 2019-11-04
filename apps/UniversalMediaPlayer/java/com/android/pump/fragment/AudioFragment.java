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
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.RecyclerView;

import com.android.pump.R;
import com.android.pump.activity.AudioPlayerActivity;
import com.android.pump.db.Album;
import com.android.pump.db.Artist;
import com.android.pump.db.Audio;
import com.android.pump.db.MediaDb;
import com.android.pump.util.Globals;

import java.util.List;

@UiThread
public class AudioFragment extends Fragment {
    private RecyclerView mRecyclerView;

    public static @NonNull Fragment newInstance() {
        return new AudioFragment();
    }

    @Override
    public @NonNull View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_audio, container, false);
        mRecyclerView = view.findViewById(R.id.fragment_audio_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(new AudioAdapter(requireContext()));

        // TODO(b/123707260) Enable view caching
        //mRecyclerView.setItemViewCacheSize(0);
        //mRecyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
        return view;
    }

    private static class AudioAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements MediaDb.UpdateCallback {
        private final MediaDb mMediaDb;
        private final List<Audio> mAudios; // TODO(b/123710968) Use android.support.v7.util.SortedList/android.support.v7.widget.util.SortedListAdapterCallback instead

        private AudioAdapter(@NonNull Context context) {
            setHasStableIds(true);
            mMediaDb = Globals.getMediaDb(context);
            mAudios = mMediaDb.getAudios();
        }

        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.addAudioUpdateCallback(this);
        }

        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.removeAudioUpdateCallback(this);
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            if (viewType == R.layout.header) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false)) { };
            } else {
                return new AudioViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                // TODO Handle header view
            } else {
                Audio audio = mAudios.get(position - 1);
                mMediaDb.loadData(audio); // TODO Where should we call this? In bind()?
                ((AudioViewHolder) holder).bind(audio);
            }
        }

        @Override
        public int getItemCount() {
            return mAudios.size() + 1;
        }

        @Override
        public long getItemId(int position) {
            return position == 0 ? -1 : mAudios.get(position - 1).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? R.layout.header : R.layout.audio;
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

    private static class AudioViewHolder extends RecyclerView.ViewHolder {
        private AudioViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Audio audio) {
            ImageView imageView = itemView.findViewById(R.id.audio_image);
            TextView titleView = itemView.findViewById(R.id.audio_title);
            TextView artistView = itemView.findViewById(R.id.audio_artist);

            Album album = audio.getAlbum();
            imageView.setImageURI(album == null ? null : album.getAlbumArtUri());
            titleView.setText(audio.getTitle());
            Artist artist = audio.getArtist();
            artistView.setText(artist == null ? null : artist.getName());

            itemView.setOnClickListener((view) ->
                    AudioPlayerActivity.start(view.getContext(), audio));
        }
    }
}
