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
import android.text.format.DateFormat;
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
import com.android.pump.db.Other;
import com.android.pump.util.Globals;

import java.util.Date;
import java.util.concurrent.TimeUnit;

@UiThread
public class OtherDetailsActivity extends AppCompatActivity implements MediaDb.UpdateCallback {
    private MediaDb mMediaDb;
    private Other mOther;

    public static void start(@NonNull Context context, @NonNull Other other) {
        Intent intent = new Intent(context, OtherDetailsActivity.class);
        // TODO(b/123704452) Pass URI instead
        intent.putExtra("id", other.getId()); // TODO Add constant key
        context.startActivity(intent);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_other_details);

        setSupportActionBar(findViewById(R.id.activity_other_details_toolbar));
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayShowTitleEnabled(false);
            actionBar.setDisplayShowHomeEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        mMediaDb = Globals.getMediaDb(this);
        mMediaDb.addOtherUpdateCallback(this);

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
        mMediaDb.removeOtherUpdateCallback(this);

        super.onDestroy();
    }

    @Override
    public boolean onCreateOptionsMenu(@NonNull Menu menu) {
        getMenuInflater().inflate(R.menu.activity_pump, menu); // TODO activity_other_details ?
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
            Other other = mMediaDb.getOthers().get(i);
            if (other.equals(mOther)) {
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

            mOther = mMediaDb.getOtherById(id);
        } else {
            // TODO This shouldn't happen -- throw exception?
            mOther = null;
        }

        mMediaDb.loadData(mOther);
        updateViews();
    }

    private void updateViews() {
        ImageView imageView = findViewById(R.id.activity_other_details_image);
        TextView titleView = findViewById(R.id.activity_other_details_title);
        TextView attributesView = findViewById(R.id.activity_other_details_attributes);

        imageView.setImageURI(mOther.getThumbnailUri());
        titleView.setText(mOther.getTitle());

        StringBuilder attributes = new StringBuilder();
        if (mOther.hasDuration()) {
            long dur = mOther.getDuration();
            // TODO(b/123706525) Move to string resource
            String duration = String.format("%dm %ds",
                    TimeUnit.MILLISECONDS.toMinutes(dur),
                    TimeUnit.MILLISECONDS.toSeconds(dur) -
                            TimeUnit.MINUTES.toSeconds(TimeUnit.MILLISECONDS.toMinutes(dur)));
            attributes.append(duration);
            attributes.append('\n');
        }
        if (mOther.hasDateTaken()) {
            // TODO(b/123707011) Better formatting
            String date = DateFormat.getLongDateFormat(this).format(new Date(mOther.getDateTaken()));
            attributes.append(date);
            attributes.append('\n');
        }
        if (mOther.hasLatLong()) {
            // TODO(b/123706523) Decode GPS coordinates
            double latitude = mOther.getLatitude();
            double longitude = mOther.getLongitude();
            String latlong = String.format("%f %f", latitude, longitude);
            attributes.append(latlong);
            attributes.append('\n');
        }
        attributesView.setText(attributes);

        ImageView playView = findViewById(R.id.activity_other_details_play);
        playView.setOnClickListener((view) ->
                VideoPlayerActivity.start(view.getContext(), mOther));
    }
}
