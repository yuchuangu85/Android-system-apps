/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.google.android.car.bugreport;

import static com.google.android.car.bugreport.BugReportService.EXTRA_META_BUG_REPORT;
import static com.google.android.car.bugreport.BugReportService.MAX_PROGRESS_VALUE;

import android.Manifest;
import android.app.Activity;
import android.car.Car;
import android.car.CarNotConnectedException;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarDrivingStateManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.UserManager;
import android.util.Log;
import android.view.View;
import android.view.Window;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.io.IOException;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;

/**
 * Activity that shows two types of dialogs: starting a new bug report and current status of already
 * in progress bug report.
 *
 * <p>If there is no in-progress bug report, it starts recording voice message. After clicking
 * submit button it initiates {@link BugReportService}.
 *
 * <p>If bug report is in-progress, it shows a progress bar.
 */
public class BugReportActivity extends Activity {
    private static final String TAG = BugReportActivity.class.getSimpleName();

    private static final int VOICE_MESSAGE_MAX_DURATION_MILLIS = 60 * 1000;
    private static final int AUDIO_PERMISSIONS_REQUEST_ID = 1;

    private static final DateFormat BUG_REPORT_TIMESTAMP_DATE_FORMAT =
            new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");

    private final Handler mHandler = new Handler(Looper.getMainLooper());

    private TextView mInProgressTitleText;
    private ProgressBar mProgressBar;
    private TextView mProgressText;
    private VoiceRecordingView mVoiceRecordingView;
    private View mVoiceRecordingFinishedView;
    private View mSubmitBugReportLayout;
    private View mInProgressLayout;
    private View mShowBugReportsButton;

    private boolean mBound;
    private boolean mAudioRecordingStarted;
    private boolean mBugReportServiceStarted;
    private BugReportService mService;
    private MediaRecorder mRecorder;
    private MetaBugReport mMetaBugReport;
    private Car mCar;
    private CarDrivingStateManager mDrivingStateManager;
    private AudioManager mAudioManager;
    private AudioFocusRequest mLastAudioFocusRequest;

    /** Defines callbacks for service binding, passed to bindService() */
    private ServiceConnection mConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName className, IBinder service) {
            BugReportService.ServiceBinder binder = (BugReportService.ServiceBinder) service;
            mService = binder.getService();
            mBound = true;
            startAudioMessageRecording();
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            // called when service connection breaks unexpectedly.
            mBound = false;
        }
    };

    private final ServiceConnection mServiceConnection = new ServiceConnection() {
        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            try {
                mDrivingStateManager = (CarDrivingStateManager) mCar.getCarManager(
                        Car.CAR_DRIVING_STATE_SERVICE);
                mDrivingStateManager.registerListener(
                        BugReportActivity.this::onCarDrivingStateChanged);
            } catch (CarNotConnectedException e) {
                Log.w(TAG, "Failed to get CarDrivingStateManager.", e);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
        }
    };

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.bug_report_activity);

        mInProgressTitleText = findViewById(R.id.in_progress_title_text);
        mProgressBar = findViewById(R.id.progress_bar);
        mProgressText = findViewById(R.id.progress_text);
        mVoiceRecordingView = findViewById(R.id.voice_recording_view);
        mVoiceRecordingFinishedView = findViewById(R.id.voice_recording_finished_text_view);
        mSubmitBugReportLayout = findViewById(R.id.submit_bug_report_layout);
        mInProgressLayout = findViewById(R.id.in_progress_layout);
        mShowBugReportsButton = findViewById(R.id.button_show_bugreports);

        mShowBugReportsButton.setOnClickListener(this::buttonShowBugReportsClick);
        findViewById(R.id.button_submit).setOnClickListener(this::buttonSubmitClick);
        findViewById(R.id.button_cancel).setOnClickListener(this::buttonCancelClick);
        findViewById(R.id.button_close).setOnClickListener(this::buttonCancelClick);

        mCar = Car.createCar(this, mServiceConnection);
        mCar.connect();
        mAudioManager = getSystemService(AudioManager.class);

        // Bind to BugReportService.
        Intent intent = new Intent(this, BugReportService.class);
        bindService(intent, mConnection, BIND_AUTO_CREATE);
    }

    @Override
    protected void onStart() {
        super.onStart();

        if (mBound) {
            startAudioMessageRecording();
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (!mBugReportServiceStarted && mAudioRecordingStarted) {
            cancelAudioMessageRecording();
        }
        if (mBound) {
            mService.removeBugReportProgressListener();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        mHandler.removeCallbacksAndMessages(null);
        if (mBound) {
            unbindService(mConnection);
            mBound = false;
        }
        if (mCar != null && mCar.isConnected()) {
            mCar.disconnect();
            mCar = null;
        }
    }

    private void onCarDrivingStateChanged(CarDrivingStateEvent event) {
        if (event.eventValue == CarDrivingStateEvent.DRIVING_STATE_PARKED) {
            mShowBugReportsButton.setVisibility(View.VISIBLE);
        } else {
            mShowBugReportsButton.setVisibility(View.GONE);
        }
    }

    private void onProgressChanged(float progress) {
        int progressValue = (int) progress;
        mProgressBar.setProgress(progressValue);
        mProgressText.setText(progressValue + "%");
        if (progressValue == MAX_PROGRESS_VALUE) {
            mInProgressTitleText.setText(R.string.bugreport_dialog_in_progress_title_finished);
        }
    }

    private void showInProgressUi() {
        mSubmitBugReportLayout.setVisibility(View.GONE);
        mInProgressLayout.setVisibility(View.VISIBLE);
        mInProgressTitleText.setText(R.string.bugreport_dialog_in_progress_title);
        onProgressChanged(mService.getBugReportProgress());
    }

    private void showSubmitBugReportUi(boolean isRecording) {
        mSubmitBugReportLayout.setVisibility(View.VISIBLE);
        mInProgressLayout.setVisibility(View.GONE);
        if (isRecording) {
            mVoiceRecordingFinishedView.setVisibility(View.GONE);
            mVoiceRecordingView.setVisibility(View.VISIBLE);
        } else {
            mVoiceRecordingFinishedView.setVisibility(View.VISIBLE);
            mVoiceRecordingView.setVisibility(View.GONE);
        }
        // NOTE: mShowBugReportsButton visibility is also handled in #onCarDrivingStateChanged().
        mShowBugReportsButton.setVisibility(View.GONE);
        if (mDrivingStateManager != null) {
            try {
                // Call onCarDrivingStateChanged(), because it's not called when Car is connected.
                onCarDrivingStateChanged(mDrivingStateManager.getCurrentCarDrivingState());
            } catch (CarNotConnectedException e) {
                Log.e(TAG, "Failed to get current driving state.", e);
            }
        }
    }

    /**
     * Initializes MetaBugReport in a local DB and starts audio recording.
     *
     * <p>This method expected to be called when the activity is started and bound to the service.
     */
    private void startAudioMessageRecording() {
        mService.setBugReportProgressListener(this::onProgressChanged);

        if (mService.isCollectingBugReport()) {
            Log.i(TAG, "Bug report is already being collected.");
            showInProgressUi();
            return;
        }

        if (mAudioRecordingStarted) {
            Log.i(TAG, "Audio message recording is already started.");
            return;
        }

        mAudioRecordingStarted = true;
        showSubmitBugReportUi(/* isRecording= */ true);

        Date initiatedAt = new Date();
        String timestamp = BUG_REPORT_TIMESTAMP_DATE_FORMAT.format(initiatedAt);
        String username = getCurrentUserName();
        String title = BugReportTitleGenerator.generateBugReportTitle(initiatedAt, username);
        mMetaBugReport = BugStorageUtils.createBugReport(this, title, timestamp, username);

        if (!hasRecordPermissions()) {
            requestRecordPermissions();
        } else {
            startRecordingWithPermission();
        }
    }

    /**
     * Cancels bugreporting by stopping audio recording and deleting temp files.
     */
    private void cancelAudioMessageRecording() {
        if (!mAudioRecordingStarted) {
            return;
        }
        stopAudioRecording();
        File tempDir = FileUtils.getTempDir(this, mMetaBugReport.getTimestamp());
        new DeleteDirectoryAsyncTask().execute(tempDir);
        BugStorageUtils.setBugReportStatus(this, mMetaBugReport, Status.STATUS_USER_CANCELLED, "");
        Log.i(TAG, "Bug report is cancelled");
        mAudioRecordingStarted = false;
    }

    private void buttonCancelClick(View view) {
        finish();
    }

    private void buttonSubmitClick(View view) {
        startBugReportingInService();
        finish();
    }

    /**
     * Starts {@link BugReportInfoActivity} and finishes current activity, so it won't be running
     * in the background and closing {@link BugReportInfoActivity} will not open it again.
     */
    private void buttonShowBugReportsClick(View view) {
        cancelAudioMessageRecording();
        // Delete the bugreport from database, otherwise pressing "Show Bugreports" button will
        // create unnecessary cancelled bugreports.
        if (mMetaBugReport != null) {
            BugStorageUtils.deleteBugReport(this, mMetaBugReport.getId());
        }
        Intent intent = new Intent(this, BugReportInfoActivity.class);
        startActivity(intent);
        finish();
    }

    private void startBugReportingInService() {
        stopAudioRecording();
        Bundle bundle = new Bundle();
        bundle.putParcelable(EXTRA_META_BUG_REPORT, mMetaBugReport);
        Intent intent = new Intent(this, BugReportService.class);
        intent.putExtras(bundle);
        startService(intent);
        mBugReportServiceStarted = true;
    }

    private void requestRecordPermissions() {
        requestPermissions(
                new String[]{Manifest.permission.RECORD_AUDIO}, AUDIO_PERMISSIONS_REQUEST_ID);
    }

    private boolean hasRecordPermissions() {
        return checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode != AUDIO_PERMISSIONS_REQUEST_ID) {
            return;
        }
        for (int i = 0; i < grantResults.length; i++) {
            if (Manifest.permission.RECORD_AUDIO.equals(permissions[i])
                    && grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                startRecordingWithPermission();
                return;
            }
        }
        handleNoPermission(permissions);
    }

    private void handleNoPermission(String[] permissions) {
        String text = this.getText(R.string.toast_permissions_denied) + " : "
                + Arrays.toString(permissions);
        Log.w(TAG, text);
        Toast.makeText(this, text, Toast.LENGTH_LONG).show();
        BugStorageUtils.setBugReportStatus(this, mMetaBugReport,
                Status.STATUS_USER_CANCELLED, text);
        finish();
    }

    private void startRecordingWithPermission() {
        File recordingFile = FileUtils.getFileWithSuffix(this, mMetaBugReport.getTimestamp(),
                "-message.3gp");
        Log.i(TAG, "Started voice recording, and saving audio to " + recordingFile);

        mLastAudioFocusRequest = new AudioFocusRequest.Builder(
                        AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_EXCLUSIVE)
                .setOnAudioFocusChangeListener(focusChange ->
                        Log.d(TAG, "AudioManager focus change " + focusChange))
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                        .build())
                .setAcceptsDelayedFocusGain(true)
                .build();
        int focusGranted = mAudioManager.requestAudioFocus(mLastAudioFocusRequest);
        // NOTE: We will record even if the audio focus was not granted.
        Log.d(TAG,
                "AudioFocus granted " + (focusGranted == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));

        mRecorder = new MediaRecorder();
        mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mRecorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
        mRecorder.setOutputFile(recordingFile);

        try {
            mRecorder.prepare();
        } catch (IOException e) {
            Log.e(TAG, "Failed on MediaRecorder#prepare(), filename: " + recordingFile, e);
            finish();
            return;
        }

        mRecorder.start();
        mVoiceRecordingView.setRecorder(mRecorder);

        mHandler.postDelayed(() -> {
            Log.i(TAG, "Timed out while recording voice message, cancelling.");
            stopAudioRecording();
            showSubmitBugReportUi(/* isRecording= */ false);
        }, VOICE_MESSAGE_MAX_DURATION_MILLIS);
    }

    private void stopAudioRecording() {
        if (mRecorder != null) {
            Log.i(TAG, "Recording ended, stopping the MediaRecorder.");
            mRecorder.stop();
            mRecorder.release();
            mRecorder = null;
        }
        if (mLastAudioFocusRequest != null) {
            int focusAbandoned = mAudioManager.abandonAudioFocusRequest(mLastAudioFocusRequest);
            Log.d(TAG, "Audio focus abandoned "
                    + (focusAbandoned == AudioManager.AUDIOFOCUS_REQUEST_GRANTED));
            mLastAudioFocusRequest = null;
        }
        mVoiceRecordingView.setRecorder(null);
    }

    private String getCurrentUserName() {
        UserManager um = UserManager.get(this);
        return um.getUserName();
    }

    /** A helper class to generate bugreport title. */
    private static final class BugReportTitleGenerator {
        /** Contains easily readable characters. */
        private static final char[] CHARS_FOR_RANDOM_GENERATOR =
                new char[]{'A', 'B', 'C', 'D', 'E', 'F', 'G', 'H', 'J', 'K', 'L', 'M', 'N', 'P',
                        'R', 'S', 'T', 'U', 'W', 'X', 'Y', 'Z'};

        private static final int LOOKUP_STRING_LENGTH = 6;

        /**
         * Generates a bugreport title from given timestamp and username.
         *
         * <p>Example: "[A45E8] Feedback from user Driver at 2019-09-21_12:00:00"
         */
        static String generateBugReportTitle(Date initiatedAt, String username) {
            // Lookup string is used to search a bug in Buganizer (see b/130915969).
            String lookupString = generateRandomString(LOOKUP_STRING_LENGTH);
            String timestamp = BUG_REPORT_TIMESTAMP_DATE_FORMAT.format(initiatedAt);
            return "[" + lookupString + "] Feedback from user " + username + " at " + timestamp;
        }

        private static String generateRandomString(int length) {
            Random random = new Random();
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < length; i++) {
                int randomIndex = random.nextInt(CHARS_FOR_RANDOM_GENERATOR.length);
                builder.append(CHARS_FOR_RANDOM_GENERATOR[randomIndex]);
            }
            return builder.toString();
        }
    }

    /** AsyncTask that recursively deletes directories. */
    private static class DeleteDirectoryAsyncTask extends AsyncTask<File, Void, Void> {
        @Override
        protected Void doInBackground(File... files) {
            for (File file : files) {
                Log.i(TAG, "Deleting " + file.getAbsolutePath());
                FileUtils.deleteDirectory(file);
            }
            return null;
        }
    }
}
