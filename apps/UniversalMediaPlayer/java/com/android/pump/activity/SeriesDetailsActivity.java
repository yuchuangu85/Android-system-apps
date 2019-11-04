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
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.RecyclerView;

import com.android.pump.R;
import com.android.pump.db.Episode;
import com.android.pump.db.MediaDb;
import com.android.pump.db.Series;
import com.android.pump.util.Globals;

import java.util.List;

@UiThread
public class SeriesDetailsActivity extends AppCompatActivity implements MediaDb.UpdateCallback {
    private MediaDb mMediaDb;
    private Series mSeries;

    public static void start(@NonNull Context context, @NonNull Series series) {
        Intent intent = new Intent(context, SeriesDetailsActivity.class);
        // TODO(b/123704452) Pass URI instead
        intent.putExtra("title", series.getTitle()); // TODO Add constant key
        if (series.hasYear()) {
            intent.putExtra("year", series.getYear()); // TODO Add constant key
        }
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_series_details);

        setSupportActionBar(findViewById(R.id.activity_series_details_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mMediaDb = Globals.getMediaDb(this);
        mMediaDb.addSeriesUpdateCallback(this);

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
        mMediaDb.removeSeriesUpdateCallback(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu); // TODO activity_series_details ?
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
            Series series = mMediaDb.getSeries().get(i);
            if (series.equals(mSeries)) {
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
            String title = extras.getString("title");
            int year = extras.getInt("year", Integer.MIN_VALUE);

            MediaDb mediaDb = Globals.getMediaDb(this);
            if (year > 0) {
                mSeries = mediaDb.getSeriesById(title, year);
            } else {
                mSeries = mediaDb.getSeriesById(title);
            }
        } else {
            mSeries = null;
            // TODO This shouldn't happen -- throw exception?
        }

        mMediaDb.loadData(mSeries);
        updateViews();
    }

    private void updateViews() {
        // TODO ImageView imageView = findViewById(R.id.activity_series_details_image);
        ImageView posterView = findViewById(R.id.activity_series_details_poster);
        TextView titleView = findViewById(R.id.activity_series_details_title);
        TextView attributesView = findViewById(R.id.activity_series_details_attributes);
        TextView synopsisView = findViewById(R.id.activity_series_details_description);

        // TODO imageView.setImageURI(mSeries.get???());
        posterView.setImageURI(mSeries.getPosterUri());
        titleView.setText(mSeries.getTitle());
        attributesView.setText("American Drama Series"); // TODO(b/123707108) Implement
        synopsisView.setText(mSeries.getDescription());

        Spinner spinner = findViewById(R.id.activity_series_details_spinner);
        spinner.setAdapter(new BaseAdapter() {
            @Override
            public boolean hasStableIds() {
                return true;
            }

            @Override
            public int getCount() {
                return mSeries.getSeasons().size();
            }

            @Override
            public @NonNull Integer getItem(int position) {
                return mSeries.getSeasons().get(position).get(0).getSeason();
            }

            @Override
            public long getItemId(int position) {
                return getItem(position);
            }

            @Override
            public View getView(int position, @Nullable View convertView,
                    @NonNull ViewGroup parent) {
                return getView(android.R.layout.simple_spinner_item, position, convertView, parent);
            }

            @Override
            public View getDropDownView(int position, @Nullable View convertView,
                    @NonNull ViewGroup parent) {
                return getView(R.layout.support_simple_spinner_dropdown_item,
                        position, convertView, parent);
            }

            private View getView(@LayoutRes int resource, int position, @Nullable View convertView,
                    @NonNull ViewGroup parent) {
                View view = convertView;
                if (view == null) {
                    view = getInflater().inflate(resource, parent, false);
                }

                TextView textView = view.findViewById(android.R.id.text1);
                // TODO(b/123037263) I18n -- Move to resource
                textView.setText("Season " + getItem(position));
                return view;
            }

            private @NonNull LayoutInflater getInflater() {
                return LayoutInflater.from(SeriesDetailsActivity.this);
            }
        });

        RecyclerView recyclerView = findViewById(R.id.activity_series_details_recycler_view);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(new SeriesAdapter(mMediaDb, mSeries));
        spinner.setOnItemSelectedListener((SeriesAdapter) recyclerView.getAdapter());

        // TODO(b/123707260) Enable view caching
        //recyclerView.setItemViewCacheSize(0);
        //recyclerView.setRecycledViewPool(Globals.getRecycledViewPool(requireContext()));
    }

    private static class SeriesAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder>
            implements AdapterView.OnItemSelectedListener{
        private final MediaDb mMediaDb;
        private final Series mSeries;
        private int mSeasonPosition;

        private SeriesAdapter(@NonNull MediaDb mediaDb, @NonNull Series series) {
            setHasStableIds(true);
            mMediaDb = mediaDb;
            mSeries = series;
        }

        @Override
        public @NonNull RecyclerView.ViewHolder onCreateViewHolder(
                @NonNull ViewGroup parent, int viewType) {
            return new EpisodeViewHolder(LayoutInflater.from(parent.getContext())
                    .inflate(viewType, parent, false));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Episode episode = getSeason().get(position);
            mMediaDb.loadData(episode); // TODO Where should we call this? In bind()?
            ((EpisodeViewHolder) holder).bind(episode);
        }

        @Override
        public int getItemCount() {
            return getSeason().size();
        }

        @Override
        public long getItemId(int position) {
            return getSeason().get(position).getId();
        }

        @Override
        public int getItemViewType(int position) {
            return R.layout.episode;
        }

        @Override
        public void onItemSelected(@NonNull AdapterView<?> parent, @NonNull View view,
                int position, long id) {
            mSeasonPosition = position;
            notifyDataSetChanged();
        }

        @Override
        public void onNothingSelected(@NonNull AdapterView<?> parent) { }

        private @NonNull List<Episode> getSeason() {
            return mSeries.getSeasons().get(mSeasonPosition);
        }
    }

    private static class EpisodeViewHolder extends RecyclerView.ViewHolder {
        private EpisodeViewHolder(@NonNull View itemView) {
            super(itemView);
        }

        private void bind(@NonNull Episode episode) {
            ImageView imageView = itemView.findViewById(R.id.episode_image);
            TextView textView = itemView.findViewById(R.id.episode_text);

            Uri posterUri = episode.getPosterUri();
            if (posterUri == null) {
                posterUri = episode.getThumbnailUri();
            }
            imageView.setImageURI(posterUri);
            // TODO(b/123037263) I18n -- Move to resource
            textView.setText("Episode " + episode.getEpisode());

            itemView.setOnClickListener((view) ->
                    VideoPlayerActivity.start(view.getContext(), episode));
        }
    }
}
