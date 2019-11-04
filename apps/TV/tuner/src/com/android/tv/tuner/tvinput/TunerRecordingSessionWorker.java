/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.tv.tuner.tvinput;

import static com.android.tv.tuner.features.TunerFeatures.TVPROVIDER_ALLOWS_COLUMN_CREATION;

import android.content.ContentResolver;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.media.tv.TvContract;
import android.media.tv.TvContract.RecordedPrograms;
import android.media.tv.TvInputManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.support.annotation.IntDef;
import android.support.annotation.MainThread;
import android.support.annotation.Nullable;
import android.util.Log;
import android.util.Pair;
import androidx.tvprovider.media.tv.Program;
import com.android.tv.common.BaseApplication;
import com.android.tv.common.data.RecordedProgramState;
import com.android.tv.common.recording.RecordingCapability;
import com.android.tv.common.recording.RecordingStorageStatusManager;
import com.android.tv.common.util.CommonUtils;
import com.android.tv.tuner.DvbDeviceAccessor;
import com.android.tv.tuner.data.PsipData;
import com.android.tv.tuner.data.PsipData.EitItem;
import com.android.tv.tuner.data.TunerChannel;
import com.android.tv.tuner.data.nano.Track.AtscCaptionTrack;
import com.android.tv.tuner.exoplayer.ExoPlayerSampleExtractor;
import com.android.tv.tuner.exoplayer.SampleExtractor;
import com.android.tv.tuner.exoplayer.buffer.BufferManager;
import com.android.tv.tuner.exoplayer.buffer.DvrStorageManager;
import com.android.tv.tuner.exoplayer.buffer.PlaybackBufferListener;
import com.android.tv.tuner.source.TsDataSource;
import com.android.tv.tuner.source.TsDataSourceManager;
import com.android.tv.tuner.ts.EventDetector.EventListener;
import com.android.tv.tuner.tvinput.datamanager.ChannelDataManager;
import com.google.android.exoplayer.C;
import com.android.tv.common.flags.ConcurrentDvrPlaybackFlags;
import java.io.File;
import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Implements a DVR feature. */
public class TunerRecordingSessionWorker
        implements PlaybackBufferListener,
                EventListener,
                SampleExtractor.OnCompletionListener,
                Handler.Callback {
    private static final String TAG = "TunerRecordingSessionW";
    private static final boolean DEBUG = false;

    private static final String SORT_BY_TIME =
            TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS
                    + ", "
                    + TvContract.Programs.COLUMN_CHANNEL_ID
                    + ", "
                    + TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS;
    private static final long TUNING_RETRY_INTERVAL_MS = TimeUnit.SECONDS.toMillis(4);
    private static final long STORAGE_MONITOR_INTERVAL_MS = TimeUnit.SECONDS.toMillis(4);
    private static final long MIN_PARTIAL_RECORDING_DURATION_MS = TimeUnit.SECONDS.toMillis(10);
    private static final long PREPARE_RECORDER_POLL_MS = 50;
    private static final int MSG_TUNE = 1;
    private static final int MSG_START_RECORDING = 2;
    private static final int MSG_PREPARE_RECODER = 3;
    private static final int MSG_STOP_RECORDING = 4;
    private static final int MSG_MONITOR_STORAGE_STATUS = 5;
    private static final int MSG_RELEASE = 6;
    private static final int MSG_UPDATE_CC_INFO = 7;
    private static final int MSG_UPDATE_PARTIAL_STATE = 8;
    private static final String COLUMN_SERIES_ID = "series_id";
    private static final String COLUMN_STATE = "state";

    private boolean mProgramHasSeriesIdColumn;
    private boolean mRecordedProgramHasSeriesIdColumn;
    private boolean mRecordedProgramHasStateColumn;

    private final RecordingCapability mCapabilities;

    private static final String[] PROGRAM_PROJECTION = {
        TvContract.Programs.COLUMN_CHANNEL_ID,
        TvContract.Programs.COLUMN_TITLE,
        TvContract.Programs.COLUMN_SEASON_TITLE,
        TvContract.Programs.COLUMN_EPISODE_TITLE,
        TvContract.Programs.COLUMN_SEASON_DISPLAY_NUMBER,
        TvContract.Programs.COLUMN_EPISODE_DISPLAY_NUMBER,
        TvContract.Programs.COLUMN_SHORT_DESCRIPTION,
        TvContract.Programs.COLUMN_POSTER_ART_URI,
        TvContract.Programs.COLUMN_THUMBNAIL_URI,
        TvContract.Programs.COLUMN_CANONICAL_GENRE,
        TvContract.Programs.COLUMN_CONTENT_RATING,
        TvContract.Programs.COLUMN_START_TIME_UTC_MILLIS,
        TvContract.Programs.COLUMN_END_TIME_UTC_MILLIS,
        TvContract.Programs.COLUMN_VIDEO_WIDTH,
        TvContract.Programs.COLUMN_VIDEO_HEIGHT,
        TvContract.Programs.COLUMN_INTERNAL_PROVIDER_DATA
    };

    private static final String[] PROGRAM_PROJECTION_WITH_SERIES_ID =
            createProjectionWithSeriesId();

    @IntDef({STATE_IDLE, STATE_TUNING, STATE_TUNED, STATE_RECORDING})
    @Retention(RetentionPolicy.SOURCE)
    public @interface DvrSessionState {}

    private static final int STATE_IDLE = 1;
    private static final int STATE_TUNING = 2;
    private static final int STATE_TUNED = 3;
    private static final int STATE_RECORDING = 4;

    private static final long CHANNEL_ID_NONE = -1;
    private static final int MAX_TUNING_RETRY = 6;
    private final ConcurrentDvrPlaybackFlags mConcurrentDvrPlaybackFlags;

    private final Context mContext;
    private final ChannelDataManager mChannelDataManager;
    private final RecordingStorageStatusManager mRecordingStorageStatusManager;
    private final Handler mHandler;
    private final TsDataSourceManager mSourceManager;
    private final Random mRandom = new Random();

    private TsDataSource mTunerSource;
    private TunerChannel mChannel;
    private File mStorageDir;
    private long mRecordStartTime;
    private long mRecordEndTime;
    private Uri mRecordedProgramUri;
    private boolean mRecorderRunning;
    private SampleExtractor mRecorder;
    private final TunerRecordingSession mSession;
    @DvrSessionState private int mSessionState = STATE_IDLE;
    private final String mInputId;
    private Uri mProgramUri;
    private String mSeriesId;

    private PsipData.EitItem mCurrenProgram;
    private List<AtscCaptionTrack> mCaptionTracks;
    private DvrStorageManager mDvrStorageManager;

    public TunerRecordingSessionWorker(
            Context context,
            String inputId,
            ChannelDataManager dataManager,
            TunerRecordingSession session,
            ConcurrentDvrPlaybackFlags concurrentDvrPlaybackFlags,
            TsDataSourceManager.Factory tsDataSourceManagerFactory) {
        mConcurrentDvrPlaybackFlags = concurrentDvrPlaybackFlags;
        mRandom.setSeed(System.nanoTime());
        mContext = context;
        HandlerThread handlerThread = new HandlerThread(TAG);
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper(), this);
        mRecordingStorageStatusManager =
                BaseApplication.getSingletons(context).getRecordingStorageStatusManager();
        mChannelDataManager = dataManager;
        mChannelDataManager.checkDataVersion(context);
        mSourceManager = tsDataSourceManagerFactory.create(true);
        mCapabilities = new DvbDeviceAccessor(context).getRecordingCapability(inputId);
        mInputId = inputId;
        if (DEBUG) Log.d(TAG, mCapabilities.toString());
        mSession = session;
    }

    // PlaybackBufferListener
    @Override
    public void onBufferStartTimeChanged(long startTimeMs) {}

    @Override
    public void onBufferStateChanged(boolean available) {}

    @Override
    public void onDiskTooSlow() {}

    // EventDetector.EventListener
    @Override
    public void onChannelDetected(TunerChannel channel, boolean channelArrivedAtFirstTime) {
        if (mChannel == null || mChannel.compareTo(channel) != 0) {
            return;
        }
        mChannelDataManager.notifyChannelDetected(channel, channelArrivedAtFirstTime);
    }

    @Override
    public void onEventDetected(TunerChannel channel, List<PsipData.EitItem> items) {
        if (mChannel == null || mChannel.compareTo(channel) != 0) {
            return;
        }
        mHandler.obtainMessage(MSG_UPDATE_CC_INFO, new Pair<>(channel, items)).sendToTarget();
        mChannelDataManager.notifyEventDetected(channel, items);
    }

    @Override
    public void onChannelScanDone() {
        // do nothing.
    }

    // SampleExtractor.OnCompletionListener
    @Override
    public void onCompletion(boolean success, long lastExtractedPositionUs) {
        onRecordingResult(success, lastExtractedPositionUs);
        reset();
    }

    /** Tunes to {@code channelUri}. */
    @MainThread
    public void tune(Uri channelUri) {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.obtainMessage(MSG_TUNE, 0, 0, channelUri).sendToTarget();
    }

    /** Starts recording. */
    @MainThread
    public void startRecording(@Nullable Uri programUri) {
        mHandler.obtainMessage(MSG_START_RECORDING, programUri).sendToTarget();
    }

    /** Stops recording. */
    @MainThread
    public void stopRecording() {
        mHandler.sendEmptyMessage(MSG_STOP_RECORDING);
    }

    /** Releases all resources. */
    @MainThread
    public void release() {
        mHandler.removeCallbacksAndMessages(null);
        mHandler.sendEmptyMessage(MSG_RELEASE);
    }

    @Override
    public boolean handleMessage(Message msg) {
        switch (msg.what) {
            case MSG_TUNE:
                {
                    Uri channelUri = (Uri) msg.obj;
                    int retryCount = msg.arg1;
                    if (DEBUG) Log.d(TAG, "Tune to " + channelUri);
                    if (doTune(channelUri)) {
                        if (mSessionState == STATE_TUNED) {
                            mSession.onTuned(channelUri);
                        } else {
                            Log.w(TAG, "Tuner stream cannot be created due to resource shortage.");
                            if (retryCount < MAX_TUNING_RETRY) {
                                Message tuneMsg =
                                        mHandler.obtainMessage(
                                                MSG_TUNE, retryCount + 1, 0, channelUri);
                                mHandler.sendMessageDelayed(tuneMsg, TUNING_RETRY_INTERVAL_MS);
                            } else {
                                mSession.onError(TvInputManager.RECORDING_ERROR_RESOURCE_BUSY);
                                reset();
                            }
                        }
                    }
                    return true;
                }
            case MSG_START_RECORDING:
                {
                    if (DEBUG) Log.d(TAG, "Start recording");
                    if (!doStartRecording((Uri) msg.obj)) {
                        reset();
                    }
                    return true;
                }
            case MSG_PREPARE_RECODER:
                {
                    if (DEBUG) Log.d(TAG, "Preparing recorder");
                    if (!mRecorderRunning) {
                        return true;
                    }
                    try {
                        if (!mRecorder.prepare()) {
                            mHandler.sendEmptyMessageDelayed(
                                    MSG_PREPARE_RECODER, PREPARE_RECORDER_POLL_MS);
                        }
                    } catch (IOException e) {
                        Log.w(TAG, "Failed to start recording. Couldn't prepare an extractor");
                        mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                        reset();
                    }
                    return true;
                }
            case MSG_STOP_RECORDING:
                {
                    if (DEBUG) Log.d(TAG, "Stop recording");
                    if (mSessionState != STATE_RECORDING) {
                        mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                        reset();
                        return true;
                    }
                    if (mRecorderRunning) {
                        stopRecorder();
                    }
                    return true;
                }
            case MSG_MONITOR_STORAGE_STATUS:
                {
                    if (mSessionState != STATE_RECORDING) {
                        return true;
                    }
                    if (!mRecordingStorageStatusManager.isStorageSufficient()) {
                        if (mRecorderRunning) {
                            stopRecorder();
                        }
                        new DeleteRecordingTask().execute(mStorageDir);
                        mSession.onError(TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
                        mContext.getContentResolver().delete(mRecordedProgramUri, null, null);
                        reset();
                    } else {
                        mHandler.sendEmptyMessageDelayed(
                                MSG_MONITOR_STORAGE_STATUS, STORAGE_MONITOR_INTERVAL_MS);
                    }
                    return true;
                }
            case MSG_RELEASE:
                {
                    // Since release was requested, current recording will be cancelled
                    // without notification.
                    reset();
                    mSourceManager.release();
                    mHandler.removeCallbacksAndMessages(null);
                    mHandler.getLooper().quitSafely();
                    return true;
                }
            case MSG_UPDATE_CC_INFO:
                {
                    Pair<TunerChannel, List<EitItem>> pair =
                            (Pair<TunerChannel, List<EitItem>>) msg.obj;
                    updateCaptionTracks(pair.first, pair.second);
                    return true;
                }
            case MSG_UPDATE_PARTIAL_STATE:
                {
                    updateRecordedProgram(RecordedProgramState.PARTIAL, -1, -1);
                    return true;
                }
        }
        return false;
    }

    @Nullable
    private TunerChannel getChannel(Uri channelUri) {
        if (channelUri == null) {
            return null;
        }
        long channelId;
        try {
            channelId = ContentUris.parseId(channelUri);
        } catch (UnsupportedOperationException | NumberFormatException e) {
            channelId = CHANNEL_ID_NONE;
        }
        return (channelId == CHANNEL_ID_NONE) ? null : mChannelDataManager.getChannel(channelId);
    }

    private String getStorageKey() {
        long prefix = System.currentTimeMillis();
        int suffix = mRandom.nextInt();
        return String.format(Locale.ENGLISH, "%016x_%016x", prefix, suffix);
    }

    private void reset() {
        if (mRecorder != null) {
            mRecorder.release();
            mRecorder = null;
        }
        if (mTunerSource != null) {
            mSourceManager.releaseDataSource(mTunerSource);
            mTunerSource = null;
        }
        mDvrStorageManager = null;
        mSessionState = STATE_IDLE;
        mRecorderRunning = false;
    }

    private boolean doTune(Uri channelUri) {
        if (mSessionState != STATE_IDLE && mSessionState != STATE_TUNING) {
            mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.e(TAG, "Tuning was requested from wrong status.");
            return false;
        }
        mChannel = getChannel(channelUri);
        if (mChannel == null) {
            mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.w(TAG, "Failed to start recording. Couldn't find the channel for " + mChannel);
            return false;
        } else if (mChannel.isRecordingProhibited()) {
            mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.w(TAG, "Failed to start recording. Not a recordable channel: " + mChannel);
            return false;
        }
        if (!mRecordingStorageStatusManager.isStorageSufficient()) {
            mSession.onError(TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
            Log.w(TAG, "Tuning failed due to insufficient storage.");
            return false;
        }
        mTunerSource = mSourceManager.createDataSource(mContext, mChannel, this);
        if (mTunerSource == null) {
            // Retry tuning in this case.
            mSessionState = STATE_TUNING;
            return true;
        }
        mSessionState = STATE_TUNED;
        return true;
    }

    private boolean doStartRecording(@Nullable Uri programUri) {
        if (mSessionState != STATE_TUNED) {
            mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            Log.e(TAG, "Recording session status abnormal");
            return false;
        }
        mStorageDir =
                mRecordingStorageStatusManager.isStorageSufficient()
                        ? new File(
                                mRecordingStorageStatusManager.getRecordingRootDataDirectory(),
                                getStorageKey())
                        : null;
        if (mStorageDir == null) {
            mSession.onError(TvInputManager.RECORDING_ERROR_INSUFFICIENT_SPACE);
            Log.w(TAG, "Failed to start recording due to insufficient storage.");
            return false;
        }
        // Since tuning might be happened a while ago, shifts the start position of tuned source.
        mTunerSource.shiftStartPosition(mTunerSource.getBufferedPosition());
        mRecordStartTime = System.currentTimeMillis();
        mDvrStorageManager = new DvrStorageManager(mStorageDir, true);
        mRecorder =
                new ExoPlayerSampleExtractor(
                        Uri.EMPTY,
                        mTunerSource,
                        new BufferManager(mDvrStorageManager),
                        this,
                        true,
                        mConcurrentDvrPlaybackFlags);
        mRecorder.setOnCompletionListener(this, mHandler);
        mProgramUri = programUri;
        mSessionState = STATE_RECORDING;
        mRecorderRunning = true;
        if (mConcurrentDvrPlaybackFlags.enabled()) {
            mRecordedProgramUri =
                    insertRecordedProgram(
                            getRecordedProgram(),
                            mChannel.getChannelId(),
                            Uri.fromFile(mStorageDir).toString(),
                            calculateRecordingSizeInBytes(),
                            mRecordStartTime,
                            mRecordStartTime);
            if (mRecordedProgramUri == null) {
                new DeleteRecordingTask().execute(mStorageDir);
                mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                Log.e(TAG, "Inserting a recording to DB failed");
                return false;
            }
            mSession.onRecordingUri(mRecordedProgramUri.toString());
            mHandler.sendEmptyMessageDelayed(
                    MSG_UPDATE_PARTIAL_STATE, MIN_PARTIAL_RECORDING_DURATION_MS);
        }
        mHandler.sendEmptyMessage(MSG_PREPARE_RECODER);
        mHandler.removeMessages(MSG_MONITOR_STORAGE_STATUS);
        mHandler.sendEmptyMessageDelayed(MSG_MONITOR_STORAGE_STATUS, STORAGE_MONITOR_INTERVAL_MS);
        return true;
    }

    private int calculateRecordingSizeInBytes() {
        // TODO(b/121153491): calcute recording size using mStorageDir
        return 1024 * 1024;
    }

    private void stopRecorder() {
        // Do not change session status.
        if (mRecorder != null) {
            mRecorder.release();
            mRecordEndTime = System.currentTimeMillis();
            mRecorder = null;
        }
        mRecorderRunning = false;
        mHandler.removeMessages(MSG_MONITOR_STORAGE_STATUS);
        Log.i(TAG, "Recording stopped");
    }

    private void updateCaptionTracks(TunerChannel channel, List<PsipData.EitItem> items) {
        if (mChannel == null
                || channel == null
                || mChannel.compareTo(channel) != 0
                || items == null
                || items.isEmpty()) {
            return;
        }
        PsipData.EitItem currentProgram = getCurrentProgram(items);
        if (currentProgram == null
                || !currentProgram.hasCaptionTrack()
                || (mCurrenProgram != null && mCurrenProgram.compareTo(currentProgram) == 0)) {
            return;
        }
        mCurrenProgram = currentProgram;
        mCaptionTracks = new ArrayList<>(currentProgram.getCaptionTracks());
        if (DEBUG) {
            Log.d(
                    TAG,
                    "updated " + mCaptionTracks.size() + " caption tracks for " + currentProgram);
        }
    }

    private PsipData.EitItem getCurrentProgram(List<PsipData.EitItem> items) {
        for (PsipData.EitItem item : items) {
            if (mRecordStartTime >= item.getStartTimeUtcMillis()
                    && mRecordStartTime < item.getEndTimeUtcMillis()) {
                return item;
            }
        }
        return null;
    }

    private Program getRecordedProgram() {
        ContentResolver resolver = mContext.getContentResolver();
        Uri programUri = mProgramUri;
        if (mProgramUri == null) {
            long avg = mRecordStartTime / 2 + mRecordEndTime / 2;
            programUri = TvContract.buildProgramsUriForChannel(mChannel.getChannelId(), avg, avg);
        }
        String[] projection =
                checkProgramTable() ? PROGRAM_PROJECTION_WITH_SERIES_ID : PROGRAM_PROJECTION;
        try (Cursor c = resolver.query(programUri, projection, null, null, SORT_BY_TIME)) {
            if (c != null && c.moveToNext()) {
                Program result = Program.fromCursor(c);
                int index;
                if ((index = c.getColumnIndex(COLUMN_SERIES_ID)) >= 0 && !c.isNull(index)) {
                    mSeriesId = c.getString(index);
                }
                if (DEBUG) {
                    Log.v(TAG, "Finished query for " + this);
                }
                return result;
            } else {
                if (c == null) {
                    Log.e(TAG, "Unknown query error for " + this);
                } else {
                    if (DEBUG) Log.d(TAG, "Can not find program:" + programUri);
                }
                return null;
            }
        }
    }

    private Uri insertRecordedProgram(
            Program program,
            long channelId,
            String storageUri,
            long totalBytes,
            long startTime,
            long endTime) {
        ContentValues values = new ContentValues();
        values.put(RecordedPrograms.COLUMN_INPUT_ID, mInputId);
        values.put(RecordedPrograms.COLUMN_CHANNEL_ID, channelId);
        values.put(RecordedPrograms.COLUMN_RECORDING_DATA_URI, storageUri);
        values.put(RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS, endTime - startTime);
        values.put(RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, totalBytes);
        // startTime could be overridden by program's start value.
        values.put(RecordedPrograms.COLUMN_START_TIME_UTC_MILLIS, startTime);
        values.put(RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, endTime);
        if (checkRecordedProgramTable(COLUMN_SERIES_ID)) {
            values.put(COLUMN_SERIES_ID, mSeriesId);
        }
        if (mConcurrentDvrPlaybackFlags.enabled() && checkRecordedProgramTable(COLUMN_STATE)) {
            values.put(COLUMN_STATE, RecordedProgramState.STARTED.name());
        }
        if (program != null) {
            values.putAll(program.toContentValues());
        }
        return mContext.getContentResolver()
                .insert(TvContract.RecordedPrograms.CONTENT_URI, values);
    }

    private void updateRecordedProgram(RecordedProgramState state, long endTime, long totalBytes) {
        ContentValues values = new ContentValues();
        if (checkRecordedProgramTable(COLUMN_STATE)) {
            values.put(COLUMN_STATE, state.name());
        }
        if (state.equals(RecordedProgramState.FINISHED)) {
            values.put(RecordedPrograms.COLUMN_RECORDING_DATA_BYTES, totalBytes);
            values.put(
                    RecordedPrograms.COLUMN_RECORDING_DURATION_MILLIS, endTime - mRecordStartTime);
            values.put(RecordedPrograms.COLUMN_END_TIME_UTC_MILLIS, endTime);
        }
        mContext.getContentResolver().update(mRecordedProgramUri, values, null, null);
    }

    private void onRecordingResult(boolean success, long lastExtractedPositionUs) {
        if (mSessionState != STATE_RECORDING) {
            // Error notification is not needed.
            Log.e(TAG, "Recording session status abnormal");
            return;
        }
        if (mRecorderRunning) {
            // In case of recorder not being stopped, because of premature termination of recording.
            stopRecorder();
        }
        if (!success
                && lastExtractedPositionUs
                        < TimeUnit.MILLISECONDS.toMicros(MIN_PARTIAL_RECORDING_DURATION_MS)) {
            new DeleteRecordingTask().execute(mStorageDir);
            mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
            mContext.getContentResolver().delete(mRecordedProgramUri, null, null);
            Log.w(TAG, "Recording failed during recording");
            return;
        }
        Log.i(TAG, "recording finished " + (success ? "completely" : "partially"));
        long recordEndTime =
                (lastExtractedPositionUs == C.UNKNOWN_TIME_US)
                        ? System.currentTimeMillis()
                        : mRecordStartTime + lastExtractedPositionUs / 1000;
        if (!mConcurrentDvrPlaybackFlags.enabled()) {
            mRecordedProgramUri =
                    insertRecordedProgram(
                            getRecordedProgram(),
                            mChannel.getChannelId(),
                            Uri.fromFile(mStorageDir).toString(),
                            calculateRecordingSizeInBytes(),
                            mRecordStartTime,
                            recordEndTime);
            if (mRecordedProgramUri == null) {
                new DeleteRecordingTask().execute(mStorageDir);
                mSession.onError(TvInputManager.RECORDING_ERROR_UNKNOWN);
                Log.e(TAG, "Inserting a recording to DB failed");
                return;
            }
        } else {
            updateRecordedProgram(
                    RecordedProgramState.FINISHED, recordEndTime, calculateRecordingSizeInBytes());
        }
        mDvrStorageManager.writeCaptionInfoFiles(mCaptionTracks);
        mSession.onRecordFinished(mRecordedProgramUri);
    }

    private boolean checkProgramTable() {
        boolean canCreateColumn = TVPROVIDER_ALLOWS_COLUMN_CREATION.isEnabled(mContext);
        if (!canCreateColumn) {
            return false;
        }
        Uri uri = TvContract.Programs.CONTENT_URI;
        if (!mProgramHasSeriesIdColumn) {
            if (getExistingColumns(uri).contains(COLUMN_SERIES_ID)) {
                mProgramHasSeriesIdColumn = true;
            } else if (addColumnToTable(uri, COLUMN_SERIES_ID)) {
                mProgramHasSeriesIdColumn = true;
            }
        }
        return mProgramHasSeriesIdColumn;
    }

    private boolean checkRecordedProgramTable(String column) {
        boolean canCreateColumn = TVPROVIDER_ALLOWS_COLUMN_CREATION.isEnabled(mContext);
        if (!canCreateColumn) {
            return false;
        }
        Uri uri = TvContract.RecordedPrograms.CONTENT_URI;
        switch (column) {
            case COLUMN_SERIES_ID:
                {
                    if (!mRecordedProgramHasSeriesIdColumn) {
                        if (getExistingColumns(uri).contains(COLUMN_SERIES_ID)) {
                            mRecordedProgramHasSeriesIdColumn = true;
                        } else if (addColumnToTable(uri, COLUMN_SERIES_ID)) {
                            mRecordedProgramHasSeriesIdColumn = true;
                        }
                    }
                    return mRecordedProgramHasSeriesIdColumn;
                }
            case COLUMN_STATE:
                {
                    if (!mRecordedProgramHasStateColumn) {
                        if (getExistingColumns(uri).contains(COLUMN_STATE)) {
                            mRecordedProgramHasStateColumn = true;
                        } else if (addColumnToTable(uri, COLUMN_STATE)) {
                            mRecordedProgramHasStateColumn = true;
                        }
                    }
                    return mRecordedProgramHasStateColumn;
                }
            default:
                return false;
        }
    }

    private Set<String> getExistingColumns(Uri uri) {
        Bundle result =
                mContext.getContentResolver()
                        .call(uri, TvContract.METHOD_GET_COLUMNS, uri.toString(), null);
        if (result != null) {
            String[] columns = result.getStringArray(TvContract.EXTRA_EXISTING_COLUMN_NAMES);
            if (columns != null) {
                return new HashSet<>(Arrays.asList(columns));
            }
        }
        Log.e(TAG, "Query existing column names from " + uri + " returned null");
        return Collections.emptySet();
    }

    /**
     * Add a column to the table
     *
     * @return {@code true} if the column is added successfully; {@code false} otherwise.
     */
    private boolean addColumnToTable(Uri contentUri, String columnName) {
        Bundle extra = new Bundle();
        extra.putCharSequence(TvContract.EXTRA_COLUMN_NAME, columnName);
        extra.putCharSequence(TvContract.EXTRA_DATA_TYPE, "TEXT");
        // If the add operation fails, the following just returns null without crashing.
        Bundle allColumns =
                mContext.getContentResolver()
                        .call(
                                contentUri,
                                TvContract.METHOD_ADD_COLUMN,
                                contentUri.toString(),
                                extra);
        if (allColumns == null) {
            Log.w(TAG, "Adding new column failed. Uri=" + contentUri);
        }
        return allColumns != null;
    }

    private static String[] createProjectionWithSeriesId() {
        List<String> projectionList = new ArrayList<>(Arrays.asList(PROGRAM_PROJECTION));
        projectionList.add(COLUMN_SERIES_ID);
        return projectionList.toArray(new String[0]);
    }

    private static class DeleteRecordingTask extends AsyncTask<File, Void, Void> {

        @Override
        public Void doInBackground(File... files) {
            if (files == null || files.length == 0) {
                return null;
            }
            for (File file : files) {
                if (!CommonUtils.deleteDirOrFile(file)) {
                    Log.w(TAG, "Unable to delete recording data at " + file);
                }
            }
            return null;
        }
    }
}
