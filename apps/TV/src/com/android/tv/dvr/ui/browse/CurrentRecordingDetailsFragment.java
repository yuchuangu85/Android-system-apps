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

package com.android.tv.dvr.ui.browse;

import android.content.Context;
import android.content.res.Resources;
import android.media.tv.TvInputManager;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import com.android.tv.R;
import com.android.tv.TvSingletons;
import com.android.tv.common.flags.has.HasConcurrentDvrPlaybackFlags;
import com.android.tv.dialog.HalfSizedDialogFragment;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrWatchedPositionManager;
import com.android.tv.dvr.data.RecordedProgram;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrStopRecordingFragment;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.common.flags.ConcurrentDvrPlaybackFlags;

/** {@link RecordingDetailsFragment} for current recording in DVR. */
public class CurrentRecordingDetailsFragment extends RecordingDetailsFragment {
    private static final int ACTION_STOP_RECORDING = 1;
    private static final int ACTION_RESUME_PLAYING = 2;
    private static final int ACTION_PLAY_FROM_BEGINNING = 3;

    private DvrDataManager mDvrDataManger;
    private RecordedProgram mRecordedProgram;
    private DvrWatchedPositionManager mDvrWatchedPositionManager;
    private ConcurrentDvrPlaybackFlags mConcurrentDvrPlaybackFlags;
    private boolean mPaused;
    private final DvrDataManager.ScheduledRecordingListener mScheduledRecordingListener =
            new DvrDataManager.ScheduledRecordingListener() {
                @Override
                public void onScheduledRecordingAdded(ScheduledRecording... schedules) {}

                @Override
                public void onScheduledRecordingRemoved(ScheduledRecording... schedules) {
                    for (ScheduledRecording schedule : schedules) {
                        if (schedule.getId() == getRecording().getId()) {
                            getActivity().finish();
                            return;
                        }
                    }
                }

                @Override
                public void onScheduledRecordingStatusChanged(ScheduledRecording... schedules) {
                    for (ScheduledRecording schedule : schedules) {
                        if (schedule.getId() == getRecording().getId()
                                && schedule.getState()
                                        != ScheduledRecording.STATE_RECORDING_IN_PROGRESS) {
                            getActivity().finish();
                            return;
                        }
                    }
                }
            };

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mDvrDataManger = TvSingletons.getSingletons(context).getDvrDataManager();
        mDvrDataManger.addScheduledRecordingListener(mScheduledRecordingListener);
        mDvrWatchedPositionManager =
                TvSingletons.getSingletons(getActivity()).getDvrWatchedPositionManager();
        mConcurrentDvrPlaybackFlags = HasConcurrentDvrPlaybackFlags.fromContext(context);
    }

    @Override
    public void onResume() {
        super.onResume();
        if (mPaused) {
            updateActions();
            mPaused = false;
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        mPaused = true;
    }

    @Override
    protected SparseArrayObjectAdapter onCreateActionsAdapter() {
        Long recordedProgramId = getRecording().getRecordedProgramId();
        if (recordedProgramId != null) {
            mRecordedProgram = mDvrDataManger.getRecordedProgram(recordedProgramId);
        }
        SparseArrayObjectAdapter adapter =
                new SparseArrayObjectAdapter(new ActionPresenterSelector());
        Resources res = getResources();
        adapter.set(
                ACTION_STOP_RECORDING,
                new Action(
                        ACTION_STOP_RECORDING,
                        res.getString(R.string.dvr_detail_stop_recording),
                        null,
                        res.getDrawable(R.drawable.lb_ic_stop)));
        if (mConcurrentDvrPlaybackFlags.enabled()
                && mRecordedProgram != null
                && mRecordedProgram.isPartial()) {
            if (mDvrWatchedPositionManager.getWatchedStatus(mRecordedProgram)
                    == DvrWatchedPositionManager.DVR_WATCHED_STATUS_WATCHING) {
                adapter.set(
                        ACTION_RESUME_PLAYING,
                        new Action(
                                ACTION_RESUME_PLAYING,
                                res.getString(R.string.dvr_detail_resume_play),
                                null,
                                res.getDrawable(R.drawable.lb_ic_play)));
                adapter.set(
                        ACTION_PLAY_FROM_BEGINNING,
                        new Action(
                                ACTION_PLAY_FROM_BEGINNING,
                                res.getString(R.string.dvr_detail_play_from_beginning),
                                null,
                                res.getDrawable(R.drawable.lb_ic_replay)));
            } else {
                adapter.set(
                        ACTION_PLAY_FROM_BEGINNING,
                        new Action(
                                ACTION_PLAY_FROM_BEGINNING,
                                res.getString(R.string.dvr_detail_watch),
                                null,
                                res.getDrawable(R.drawable.lb_ic_play)));
            }
        }
        return adapter;
    }

    @Override
    protected OnActionClickedListener onCreateOnActionClickedListener() {
        return new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                if (action.getId() == ACTION_STOP_RECORDING) {
                    DvrUiHelper.showStopRecordingDialog(
                            getActivity(),
                            getRecording().getChannelId(),
                            DvrStopRecordingFragment.REASON_USER_STOP,
                            new HalfSizedDialogFragment.OnActionClickListener() {
                                @Override
                                public void onActionClick(long actionId) {
                                    if (actionId == DvrStopRecordingFragment.ACTION_STOP) {
                                        DvrManager dvrManager =
                                                TvSingletons.getSingletons(getContext())
                                                        .getDvrManager();
                                        dvrManager.stopRecording(getRecording());
                                        getActivity().finish();
                                    }
                                }
                            });
                } else if (action.getId() == ACTION_RESUME_PLAYING) {
                    startPlayback(
                            mRecordedProgram,
                            mDvrWatchedPositionManager.getWatchedPosition(
                                    mRecordedProgram.getId()));
                } else if (action.getId() == ACTION_PLAY_FROM_BEGINNING) {
                    startPlayback(mRecordedProgram, TvInputManager.TIME_SHIFT_INVALID_TIME);
                }
            }
        };
    }

    @Override
    public void onDetach() {
        if (mDvrDataManger != null) {
            mDvrDataManger.removeScheduledRecordingListener(mScheduledRecordingListener);
        }
        super.onDetach();
    }
}
