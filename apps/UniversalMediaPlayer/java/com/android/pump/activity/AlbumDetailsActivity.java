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
import com.android.pump.util.Globals;

@UiThread
public class AlbumDetailsActivity extends AppCompatActivity implements MediaDb.UpdateCallback {
    private MediaDb mMediaDb;
    private Album mAlbum;

    public static void start(@NonNull Context context, @NonNull Album album) {
        Intent intent = new Intent(context, AlbumDetailsActivity.class);
        // TODO(b/123704452) Pass URI instead
        intent.putExtra("id", album.getId()); // TODO Add constant key
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_album_details);

        setSupportActionBar(findViewById(R.id.activity_album_details_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mMediaDb = Globals.getMediaDb(this);
        mMediaDb.addAlbumUpdateCallback(this);

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
        mMediaDb.removeAlbumUpdateCallback(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu); // TODO activity_album_details ?
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
            Album album = mMediaDb.getAlbums().get(i);
            if (album.equals(mAlbum)) {
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

            mAlbum = mMediaDb.getAlbumById(id);
        } else {
            mAlbum = null;
            // TODO This shouldn't happen -- throw exception?
        }

        mMediaDb.loadData(mAlbum);
        updateViews();
    }

    private void updateViews() {
        ImageView imageView = findViewById(R.id.activity_album_details_image);
        TextView nameView = findViewById(R.id.activity_album_details_name);
        TextView countView = findViewById(R.id.activity_album_details_count);

        imageView.setImageURI(mAlbum.getAlbumArtUri());
        nameView.setText(mAlbum.getTitle());
        // TODO(b/123037263) I18n -- Move to resource
        countView.setText(mAlbum.getAudios().size() + " songs");

        ImageView playView = findViewById(R.id.activity_album_details_play);
        playView.setOnClickListener((view) ->
                AudioPlayerActivity.start(view.getContext(), mAlbum));

        RecyclerView recyclerView = findViewById(R.id.activity_album_details_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(new AlbumAdapter(mMediaDb, mAlbum));

        // TODO(b/123707260) Enable view caching
        //recyclerView.setItemViewCacheSize(0);
        //recyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
    }

    private static class AlbumAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final MediaDb mMediaDb;
        private final Album mAlbum;

        private AlbumAdapter(@NonNull MediaDb mediaDb, @NonNull Album album) {
            setHasStableIds(true);
            mMediaDb = mediaDb;
            mAlbum = album;
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            return new AudioViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(viewType, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Audio audio = mAlbum.getAudios().get(position);
            mMediaDb.loadData(audio); // TODO Where should we call this? In bind()?
            ((AudioViewHolder) holder).bind(mAlbum, audio);
        }

        @Override
        public int getItemCount() {
            return mAlbum.getAudios().size();
        }

        @Override
        public long getItemId(int position) {
            return mAlbum.getAudios().get(position).getId();
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

        private void bind(@NonNull Album album, @NonNull Audio audio) {
            ImageView imageView = itemView.findViewById(R.id.audio_image);
            TextView titleView = itemView.findViewById(R.id.audio_title);
            TextView artistView = itemView.findViewById(R.id.audio_artist);

            imageView.setImageURI(album.getAlbumArtUri());
            titleView.setText(audio.getTitle());
            Artist artist = audio.getArtist();
            artistView.setText(artist == null ? null : artist.getName());

            itemView.setOnClickListener((view) ->
                    AudioPlayerActivity.start(view.getContext(), audio));
        }
    }
}
