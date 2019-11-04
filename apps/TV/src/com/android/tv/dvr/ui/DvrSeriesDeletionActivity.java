/*
 * Copyright (C) 2016 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.dvr.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.GuidedStepFragment;
import android.util.Log;
import android.widget.Toast;

import com.android.tv.R;
import com.android.tv.Starter;
import com.android.tv.TvSingletons;
import com.android.tv.dvr.DvrManager;

import java.util.ArrayList;
import java.util.List;

/** Activity to show details view in DVR. */
public class DvrSeriesDeletionActivity extends Activity {
    private static final String TAG = "DvrSeriesDeletionActivity";

    /** Name of series id added to the Intent. */
    public static final String SERIES_RECORDING_ID = "series_recording_id";

    public static final int REQUEST_DELETE = 1;
    public static final long INVALID_SERIES_RECORDING_ID = -1;

    private long mSeriesRecordingId = INVALID_SERIES_RECORDING_ID;
    private final List<Long> mIdsToDelete = new ArrayList<>();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Starter.start(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_series_settings);
        // Check savedInstanceState to prevent that activity is being showed with animation.
        if (savedInstanceState == null) {
            mSeriesRecordingId =
                    getIntent().getLongExtra(SERIES_RECORDING_ID, INVALID_SERIES_RECORDING_ID);
            DvrSeriesDeletionFragment deletionFragment = new DvrSeriesDeletionFragment();
            deletionFragment.setArguments(getIntent().getExtras());
            GuidedStepFragment.addAsRoot(this, deletionFragment, R.id.dvr_settings_view_frame);
        }
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_DELETE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    deleteSelectedIds(true);
                } else {
                    // NOTE: If Live TV ever supports both embedded and separate DVR inputs
                    // then we should try to do the delete regardless.
                    Log.i(
                            TAG,
                            "Write permission denied, Not trying to delete the files for series "
                                    + mSeriesRecordingId);
                    deleteSelectedIds(false);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void deleteSelectedIds(boolean deleteFiles) {
        TvSingletons singletons = TvSingletons.getSingletons(this);
        int recordingSize =
                singletons.getDvrDataManager().getRecordedPrograms(mSeriesRecordingId).size();
        if (!mIdsToDelete.isEmpty()) {
            DvrManager dvrManager = singletons.getDvrManager();
            dvrManager.removeRecordedPrograms(mIdsToDelete, deleteFiles);
        }
        Toast.makeText(
                this,
                getResources()
                        .getQuantityString(
                                R.plurals.dvr_msg_episodes_deleted,
                                mIdsToDelete.size(),
                                mIdsToDelete.size(),
                                recordingSize),
                Toast.LENGTH_LONG)
                .show();
        finish();
    }

    void setIdsToDelete(List<Long> ids) {
        mIdsToDelete.clear();
        mIdsToDelete.addAll(ids);
    }
}
