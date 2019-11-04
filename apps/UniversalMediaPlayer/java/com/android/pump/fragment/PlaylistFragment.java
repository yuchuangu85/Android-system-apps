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
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
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
import androidx.recyclerview.widget.RecyclerView;

import com.android.pump.R;
import com.android.pump.activity.PlaylistDetailsActivity;
import com.android.pump.db.Album;
import com.android.pump.db.Artist;
import com.android.pump.db.Audio;
import com.android.pump.db.MediaDb;
import com.android.pump.db.Playlist;
import com.android.pump.util.Globals;

import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

@UiThread
public class PlaylistFragment extends Fragment {
    private RecyclerView mRecyclerView;

    public static @NonNull Fragment newInstance() {
        return new PlaylistFragment();
    }

    @Override
    public @NonNull View onCreateView(@NonNull LayoutInflater inflater,
            @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_playlist, container, false);
        mRecyclerView = view.findViewById(R.id.fragment_playlist_recycler_view);
        mRecyclerView.setHasFixedSize(true);
        mRecyclerView.setAdapter(new PlaylistAdapter(requireContext()));
        mRecyclerView.addItemDecoration(new SpaceItemDecoration(4, 16));

        GridLayoutManager gridLayoutManager = (GridLayoutManager) mRecyclerView.getLayoutManager();
        gridLayoutManager.setSpanSizeLookup(
                new HeaderSpanSizeLookup(gridLayoutManager.getSpanCount()));

        // TODO(b/123707260) Enable view caching
        //mRecyclerView.setItemViewCacheSize(0);
        //mRecyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
        return view;
    }

    private static class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements MediaDb.UpdateCallback {
        private final MediaDb mMediaDb;
        private final List<Playlist> mPlaylists; // TODO(b/123710968) Use android.support.v7.util.SortedList/android.support.v7.widget.util.SortedListAdapterCallback instead

        private PlaylistAdapter(@NonNull Context context) {
            setHasStableIds(true);
            mMediaDb = Globals.getMediaDb(context);
            mPlaylists = mMediaDb.getPlaylists();
        }

        public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.addPlaylistUpdateCallback(this);
        }

        public void onDetachedFromRecyclerView(@NonNull RecyclerView recyclerView) {
            mMediaDb.removePlaylistUpdateCallback(this);
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            if (viewType == R.layout.header) {
                return new RecyclerView.ViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false)) { };
            } else {
                return new PlaylistViewHolder(LayoutInflater.from(parent.getContext())
                        .inflate(viewType, parent, false));
            }
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            if (position == 0) {
                // TODO Handle header view
            } else {
                Playlist playlist = mPlaylists.get(position - 1);
                mMediaDb.loadData(playlist); // TODO Where should we call this? In bind()?
                ((PlaylistViewHolder) holder).bind(playlist);
            }
        }

        @Override
        public int getItemCount() {
            return mPlaylists.size() + 1;
        }

        @Override
        public long getItemId(int position) {
            return position == 0 ? -1 : mPlaylists.get(position - 1).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return position == 0 ? R.layout.header : R.layout.playlist;
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

    private static class PlaylistViewHolder extends RecyclerView.ViewHolder {
        private PlaylistViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Playlist playlist) {
            ImageView image0View = itemView.findViewById(R.id.playlist_image_0);
            ImageView image1View = itemView.findViewById(R.id.playlist_image_1);
            ImageView image2View = itemView.findViewById(R.id.playlist_image_2);
            ImageView image3View = itemView.findViewById(R.id.playlist_image_3);
            TextView titleView = itemView.findViewById(R.id.playlist_title);
            TextView artistsView = itemView.findViewById(R.id.playlist_artists);

            // TODO Find a better way to handle 2x2 art
            Set<Uri> albumArtUris = new HashSet<>();
            Set<String> artistNames = new HashSet<>();
            List<Audio> audios = playlist.getAudios();
            for (Audio audio : audios) {
                Album album = audio.getAlbum();
                if (album != null && album.getAlbumArtUri() != null) {
                    albumArtUris.add(album.getAlbumArtUri());
                }

                Artist artist = audio.getArtist();
                if (artist != null && artist.getName() != null) {
                    artistNames.add(artist.getName());
                }
            }

            int numAlbumArt = albumArtUris.size();
            if (numAlbumArt == 0) {
                image0View.setImageURI(null);
                image1View.setImageURI(null);
                image2View.setImageURI(null);
                image3View.setImageURI(null);
                image0View.setVisibility(View.VISIBLE);
                image1View.setVisibility(View.GONE);
                image2View.setVisibility(View.GONE);
                image3View.setVisibility(View.GONE);
            } else if (numAlbumArt < 4) {
                Iterator<Uri> iterator = albumArtUris.iterator();
                image0View.setImageURI(iterator.next());
                image1View.setImageURI(null);
                image2View.setImageURI(null);
                image3View.setImageURI(null);
                image0View.setVisibility(View.VISIBLE);
                image1View.setVisibility(View.GONE);
                image2View.setVisibility(View.GONE);
                image3View.setVisibility(View.GONE);
            } else {
                Iterator<Uri> iterator = albumArtUris.iterator();
                image0View.setImageURI(iterator.next());
                image1View.setImageURI(iterator.next());
                image2View.setImageURI(iterator.next());
                image3View.setImageURI(iterator.next());
                image0View.setVisibility(View.VISIBLE);
                image1View.setVisibility(View.VISIBLE);
                image2View.setVisibility(View.VISIBLE);
                image3View.setVisibility(View.VISIBLE);
            }
            titleView.setText(playlist.getName());
            // TODO Fix comma separation for i18n/l11n
            artistsView.setText(artistNames.isEmpty() ? null : TextUtils.join(", ", artistNames));

            itemView.setOnClickListener((view) ->
                    PlaylistDetailsActivity.start(view.getContext(), playlist));
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

    private static class HeaderSpanSizeLookup extends GridLayoutManager.SpanSizeLookup {
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
