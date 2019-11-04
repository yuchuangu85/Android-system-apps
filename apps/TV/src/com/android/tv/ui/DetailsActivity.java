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

package com.android.tv.ui;

import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.v17.leanback.app.DetailsFragment;
import android.transition.Transition;
import android.transition.Transition.TransitionListener;
import android.util.Log;
import android.view.View;
import com.android.tv.R;
import com.android.tv.Starter;
import com.android.tv.TvSingletons;
import com.android.tv.dialog.PinDialogFragment;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.ui.browse.CurrentRecordingDetailsFragment;
import com.android.tv.dvr.ui.browse.RecordedProgramDetailsFragment;
import com.android.tv.dvr.ui.browse.ScheduledRecordingDetailsFragment;
import com.android.tv.dvr.ui.browse.SeriesRecordingDetailsFragment;

/** Activity to show details view. */
public class DetailsActivity extends Activity implements PinDialogFragment.OnPinCheckedListener {
    private static final String TAG = "DetailsActivity";

    private static final long INVALID_RECORD_ID = -1;

    /** Name of record id added to the Intent. */
    public static final String RECORDING_ID = "record_id";
    /** Name of program uri added to the Intent. */
    public static final String PROGRAM = "program";
    /** Name of channel id added to the Intent. */
    public static final String CHANNEL_ID = "channel_id";
    /** Name of input id added to the Intent. */
    public static final String INPUT_ID = "input_id";

    /**
     * Name of flag added to the Intent to determine if details view should hide "View schedule"
     * button.
     */
    public static final String HIDE_VIEW_SCHEDULE = "hide_view_schedule";

    /** Name of details view's type added to the intent. */
    public static final String DETAILS_VIEW_TYPE = "details_view_type";

    /** Name of shared element between activities. */
    public static final String SHARED_ELEMENT_NAME = "shared_element";

    /** CURRENT_RECORDING_VIEW refers to Current Recordings in DVR. */
    public static final int CURRENT_RECORDING_VIEW = 1;

    /** SCHEDULED_RECORDING_VIEW refers to Scheduled Recordings in DVR. */
    public static final int SCHEDULED_RECORDING_VIEW = 2;

    /** RECORDED_PROGRAM_VIEW refers to Recorded programs in DVR. */
    public static final int RECORDED_PROGRAM_VIEW = 3;

    /** SERIES_RECORDING_VIEW refers to series recording in DVR. */
    public static final int SERIES_RECORDING_VIEW = 4;

    /** SERIES_RECORDING_VIEW refers to program. */
    public static final int PROGRAM_VIEW = 5;

    public static final int REQUEST_DELETE = 1;

    private PinDialogFragment.OnPinCheckedListener mOnPinCheckedListener;
    private long mRecordId = INVALID_RECORD_ID;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        Starter.start(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dvr_details);
        long recordId = getIntent().getLongExtra(RECORDING_ID, INVALID_RECORD_ID);
        int detailsViewType = getIntent().getIntExtra(DETAILS_VIEW_TYPE, -1);
        boolean hideViewSchedule = getIntent().getBooleanExtra(HIDE_VIEW_SCHEDULE, false);
        long channelId = getIntent().getLongExtra(CHANNEL_ID, -1);
        DetailsFragment detailsFragment = null;
        Bundle args = new Bundle();
        if (detailsViewType != -1 && savedInstanceState == null) {
            if (recordId != INVALID_RECORD_ID) {
                mRecordId = recordId;
                args.putLong(RECORDING_ID, mRecordId);
                if (detailsViewType == CURRENT_RECORDING_VIEW) {
                    detailsFragment = new CurrentRecordingDetailsFragment();
                } else if (detailsViewType == SCHEDULED_RECORDING_VIEW) {
                    args.putBoolean(HIDE_VIEW_SCHEDULE, hideViewSchedule);
                    detailsFragment = new ScheduledRecordingDetailsFragment();
                } else if (detailsViewType == RECORDED_PROGRAM_VIEW) {
                    detailsFragment = new RecordedProgramDetailsFragment();
                } else if (detailsViewType == SERIES_RECORDING_VIEW) {
                    detailsFragment = new SeriesRecordingDetailsFragment();
                }
            } else if (detailsViewType == PROGRAM_VIEW && channelId != -1) {
                Parcelable program = getIntent().getParcelableExtra(PROGRAM);
                if (program != null) {
                    args.putLong(CHANNEL_ID, channelId);
                    args.putParcelable(PROGRAM, program);
                    args.putString(INPUT_ID, getIntent().getStringExtra(INPUT_ID));
                    detailsFragment = new ProgramDetailsFragment();
                }
            }
            if (detailsFragment != null) {
                detailsFragment.setArguments(args);
                getFragmentManager()
                        .beginTransaction()
                        .replace(R.id.dvr_details_view_frame, detailsFragment)
                        .commit();
            }
        }

        // This is a workaround for the focus on O device
        addTransitionListener();
    }

    @Override
    public void onPinChecked(boolean checked, int type, String rating) {
        if (mOnPinCheckedListener != null) {
            mOnPinCheckedListener.onPinChecked(checked, type, rating);
        }
    }

    public void setOnPinCheckListener(PinDialogFragment.OnPinCheckedListener listener) {
        mOnPinCheckedListener = listener;
    }

    private void addTransitionListener() {
        getWindow()
                .getSharedElementEnterTransition()
                .addListener(
                        new TransitionListener() {
                            @Override
                            public void onTransitionStart(Transition transition) {
                                // Do nothing
                            }

                            @Override
                            public void onTransitionEnd(Transition transition) {
                                View actions = findViewById(R.id.details_overview_actions);
                                if (actions != null) {
                                    actions.requestFocus();
                                }
                            }

                            @Override
                            public void onTransitionCancel(Transition transition) {
                                // Do nothing

                            }

                            @Override
                            public void onTransitionPause(Transition transition) {
                                // Do nothing
                            }

                            @Override
                            public void onTransitionResume(Transition transition) {
                                // Do nothing
                            }
                        });
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        switch (requestCode) {
            case REQUEST_DELETE:
                // If request is cancelled, the result arrays are empty.
                if (grantResults.length > 0
                        && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    delete(true);
                } else {
                    Log.i(
                            TAG,
                            "Write permission denied, Not trying to delete the file for "
                                    + mRecordId);
                    delete(false);
                }
                break;
            default:
                super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        }
    }

    private void delete(boolean deleteFile) {
        if (mRecordId != INVALID_RECORD_ID) {
            DvrManager dvrManager = TvSingletons.getSingletons(this).getDvrManager();
            dvrManager.removeRecordedProgram(mRecordId, deleteFile);
        }
        finish();
    }
}
