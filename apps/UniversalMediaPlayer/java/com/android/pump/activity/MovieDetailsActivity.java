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
import android.view.Menu;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;

import com.android.pump.R;
import com.android.pump.db.MediaDb;
import com.android.pump.db.Movie;
import com.android.pump.util.Globals;

@UiThread
public class MovieDetailsActivity extends AppCompatActivity implements MediaDb.UpdateCallback {
    private MediaDb mMediaDb;
    private Movie mMovie;

    public static void start(@NonNull Context context, @NonNull Movie movie) {
        Intent intent = new Intent(context, MovieDetailsActivity.class);
        // TODO(b/123704452) Pass URI instead
        intent.putExtra("id", movie.getId()); // TODO Add constant key
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_movie_details);

        setSupportActionBar(findViewById(R.id.activity_movie_details_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mMediaDb = Globals.getMediaDb(this);
        mMediaDb.addMovieUpdateCallback(this);

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
        mMediaDb.removeMovieUpdateCallback(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu); // TODO activity_movie_details ?
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
            Movie movie = mMediaDb.getMovies().get(i);
            if (movie.equals(mMovie)) {
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

            mMovie = mMediaDb.getMovieById(id);
        } else {
            mMovie = null;
            // TODO This shouldn't happen -- throw exception?
        }

        mMediaDb.loadData(mMovie);
        updateViews();
    }

    private void updateViews() {
        ImageView imageView = findViewById(R.id.activity_movie_details_image);
        ImageView posterView = findViewById(R.id.activity_movie_details_poster);
        TextView titleView = findViewById(R.id.activity_movie_details_title);
        TextView attributesView = findViewById(R.id.activity_movie_details_attributes);
        TextView synopsisView = findViewById(R.id.activity_movie_details_synopsis);

        imageView.setImageURI(mMovie.getThumbnailUri());
        posterView.setImageURI(mMovie.getPosterUri());
        titleView.setText(mMovie.getTitle());
        attributesView.setText("1h 20m"); // TODO(b/123707108) Implement
        synopsisView.setText(getSynopsis());

        ImageView playView = findViewById(R.id.activity_movie_details_play);
        playView.setOnClickListener((view) ->
                VideoPlayerActivity.start(view.getContext(), mMovie));
    }

    private String getSynopsis() {
        return (mMovie.getSynopsis() != null) ? mMovie.getSynopsis()
                : mMovie.getDescription();
    }
}
