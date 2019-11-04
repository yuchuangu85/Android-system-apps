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

package com.android.pump.activity;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.android.pump.R;
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
public class PlaylistDetailsActivity extends AppCompatActivity implements MediaDb.UpdateCallback {
    private MediaDb mMediaDb;
    private Playlist mPlaylist;

    public static void start(@NonNull Context context, @NonNull Playlist playlist) {
        Intent intent = new Intent(context, PlaylistDetailsActivity.class);
        // TODO(b/123704452) Pass URI instead
        intent.putExtra("id", playlist.getId()); // TODO Add constant key
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_playlist_details);

        setSupportActionBar(findViewById(R.id.activity_playlist_details_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mMediaDb = Globals.getMediaDb(this);
        mMediaDb.addPlaylistUpdateCallback(this);

        handleIntent();
    }

    @Override
    protected void onNewIntent(@Nullable Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);

        handleIntent();
    }

    @Override
    protected void onDestroy() {
        mMediaDb.removePlaylistUpdateCallback(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu); // TODO activity_playlist_details ?
        return true;
    }

    @Override
    public boolean onSupportNavigateUp() {
        // TODO It should not be necessary to override this method
        onBackPressed();
        return true;
    }

    @Override
    public void onItemsInserted(int index, int count) { }

    @Override
    public void onItemsUpdated(int index, int count) {
        for (int i = index; i < index + count; ++i) {
            Playlist playlist = mMediaDb.getPlaylists().get(i);
            if (playlist.equals(mPlaylist)) {
                updateViews();
                break;
            }
        }
    }

    @Override
    public void onItemsRemoved(int index, int count) { }

    private void handleIntent() {
        Intent intent = getIntent();
        Bundle extras = intent != null ? intent.getExtras() : null;
        if (extras != null) {
            long id = extras.getLong("id");

            mPlaylist = mMediaDb.getPlaylistById(id);
        } else {
            mPlaylist = null;
            // TODO This shouldn't happen -- throw exception?
        }

        mMediaDb.loadData(mPlaylist);
        updateViews();
    }

    private void updateViews() {
        ImageView image0View = findViewById(R.id.activity_playlist_details_image_0);
        ImageView image1View = findViewById(R.id.activity_playlist_details_image_1);
        ImageView image2View = findViewById(R.id.activity_playlist_details_image_2);
        ImageView image3View = findViewById(R.id.activity_playlist_details_image_3);
        TextView nameView = findViewById(R.id.activity_playlist_details_name);
        TextView countView = findViewById(R.id.activity_playlist_details_count);

        // TODO Find a better way to handle 2x2 art
        Set<Uri> albumArtUris = new HashSet<>();
        List<Audio> audios = mPlaylist.getAudios();
        for (Audio audio : audios) {
            Album album = audio.getAlbum();
            if (album != null && album.getAlbumArtUri() != null) {
                albumArtUris.add(album.getAlbumArtUri());
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
        nameView.setText(mPlaylist.getName());
        // TODO(b/123037263) I18n -- Move to resource
        countView.setText(mPlaylist.getAudios().size() + " songs");

        ImageView playView = findViewById(R.id.activity_playlist_details_play);
        playView.setOnClickListener((view) ->
                AudioPlayerActivity.start(view.getContext(), mPlaylist));

        RecyclerView recyclerView = findViewById(R.id.activity_playlist_details_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(new PlaylistAdapter(mMediaDb, mPlaylist));

        // TODO(b/123707260) Enable view caching
        //recyclerView.setItemViewCacheSize(0);
        //recyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
    }

    private static class PlaylistAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final MediaDb mMediaDb;
        private final Playlist mPlaylist;

        private PlaylistAdapter(@NonNull MediaDb mediaDb, @NonNull Playlist playlist) {
            setHasStableIds(true);
            mMediaDb = mediaDb;
            mPlaylist = playlist;
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            return new AudioViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(viewType, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Audio audio = mPlaylist.getAudios().get(position);
            mMediaDb.loadData(audio); // TODO Where should we call this? In bind()?
            ((AudioViewHolder) holder).bind(mPlaylist, audio);
        }

        @Override
        public int getItemCount() {
            return mPlaylist.getAudios().size();
        }

        @Override
        public long getItemId(int position) {
            return mPlaylist.getAudios().get(position).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return R.layout.audio;
        }
    }

    private static class AudioViewHolder extends RecyclerView.ViewHolder {
        private AudioViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Playlist playlist, @NonNull Audio audio) {
            ImageView imageView = itemView.findViewById(R.id.audio_image);
            TextView titleView = itemView.findViewById(R.id.audio_title);
            TextView artistView = itemView.findViewById(R.id.audio_artist);

            Album album = audio.getAlbum();
            imageView.setImageURI(album == null ? null : album.getAlbumArtUri());
            titleView.setText(audio.getTitle());
            Artist artist = audio.getArtist();
            artistView.setText(artist == null ? null : artist.getName());

            itemView.setOnClickListener((view) ->
                    AudioPlayerActivity.start(view.getContext(), playlist, getAdapterPosition()));
        }
    }
}
