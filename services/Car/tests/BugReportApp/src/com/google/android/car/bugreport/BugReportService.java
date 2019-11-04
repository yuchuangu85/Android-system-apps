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

import static com.google.android.car.bugreport.PackageUtils.getPackageVersion;

import android.annotation.FloatRange;
import android.annotation.StringRes;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.car.Car;
import android.car.CarBugreportManager;
import android.car.CarNotConnectedException;
import android.content.Intent;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import com.google.common.util.concurrent.AtomicDouble;

import libcore.io.IoUtils;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;

/**
 * Service that captures screenshot and bug report using dumpstate and bluetooth snoop logs.
 *
 * <p>After collecting all the logs it updates the {@link MetaBugReport} using {@link
 * BugStorageProvider}, which in turn schedules bug report to upload.
 */
public class BugReportService extends Service {
    private static final String TAG = BugReportService.class.getSimpleName();

    /**
     * Extra data from intent - current bug report.
     */
    static final String EXTRA_META_BUG_REPORT = "meta_bug_report";

    // Wait a short time before starting to capture the bugreport and the screen, so that
    // bugreport activity can detach from the view tree.
    // It is ugly to have a timeout, but it is ok here because such a delay should not really
    // cause bugreport to be tainted with so many other events. If in the future we want to change
    // this, the best option is probably to wait for onDetach events from view tree.
    private static final int ACTIVITY_FINISH_DELAY_MILLIS = 1000;

    private static final String BT_SNOOP_LOG_LOCATION = "/data/misc/bluetooth/logs/btsnoop_hci.log";
    private static final boolean DEBUG = Log.isLoggable(TAG, Log.DEBUG);

    /** Notifications on this channel will silently appear in notification bar. */
    private static final String PROGRESS_CHANNEL_ID = "BUGREPORT_PROGRESS_CHANNEL";

    /** Notifications on this channel will pop-up. */
    private static final String STATUS_CHANNEL_ID = "BUGREPORT_STATUS_CHANNEL";

    private static final int BUGREPORT_IN_PROGRESS_NOTIF_ID = 1;

    /** The notification is shown when bugreport is collected. */
    static final int BUGREPORT_FINISHED_NOTIF_ID = 2;

    private static final String OUTPUT_ZIP_FILE = "output_file.zip";
    private static final String EXTRA_OUTPUT_ZIP_FILE = "extra_output_file.zip";

    private static final String MESSAGE_FAILURE_DUMPSTATE = "Failed to grab dumpstate";
    private static final String MESSAGE_FAILURE_ZIP = "Failed to zip files";

    private static final int PROGRESS_HANDLER_EVENT_PROGRESS = 1;
    private static final String PROGRESS_HANDLER_DATA_PROGRESS = "progress";

    static final float MAX_PROGRESS_VALUE = 100f;

    /** Binder given to clients. */
    private final IBinder mBinder = new ServiceBinder();

    private final AtomicBoolean mIsCollectingBugReport = new AtomicBoolean(false);
    private final AtomicDouble mBugReportProgress = new AtomicDouble(0);

    private MetaBugReport mMetaBugReport;
    private NotificationManager mNotificationManager;
    private ScheduledExecutorService mSingleThreadExecutor;
    private BugReportProgressListener mBugReportProgressListener;
    private Car mCar;
    private CarBugreportManager mBugreportManager;
    private CarBugreportManager.CarBugreportManagerCallback mCallback;

    /** A handler on the main thread. */
    private Handler mHandler;

    /** A listener that's notified when bugreport progress changes. */
    interface BugReportProgressListener {
        /**
         * Called when bug report progress changes.
         *
         * @param progress - a bug report progress in [0.0, 100.0].
         */
        void onProgress(float progress);
    }

    /** Client binder. */
    public class ServiceBinder extends Binder {
        BugReportService getService() {
            // Return this instance of LocalService so clients can call public methods
            return BugReportService.this;
        }
    }

    /** A handler on a main thread. */
    private class BugReportHandler extends Handler {
        @Override
        public void handleMessage(Message message) {
            switch (message.what) {
                case PROGRESS_HANDLER_EVENT_PROGRESS:
                    if (mBugReportProgressListener != null) {
                        float progress = message.getData().getFloat(PROGRESS_HANDLER_DATA_PROGRESS);
                        mBugReportProgressListener.onProgress(progress);
                    }
                    showProgressNotification();
                    break;
                default:
                    Log.d(TAG, "Unknown event " + message.what + ", ignoring.");
            }
        }
    }

    @Override
    public void onCreate() {
        mNotificationManager = getSystemService(NotificationManager.class);
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                PROGRESS_CHANNEL_ID,
                getString(R.string.notification_bugreport_channel_name),
                NotificationManager.IMPORTANCE_DEFAULT));
        mNotificationManager.createNotificationChannel(new NotificationChannel(
                STATUS_CHANNEL_ID,
                getString(R.string.notification_bugreport_channel_name),
                NotificationManager.IMPORTANCE_HIGH));
        mSingleThreadExecutor = Executors.newSingleThreadScheduledExecutor();
        mHandler = new BugReportHandler();
        mCar = Car.createCar(this);
        try {
            mBugreportManager = (CarBugreportManager) mCar.getCarManager(Car.CAR_BUGREPORT_SERVICE);
        } catch (CarNotConnectedException | NoClassDefFoundError e) {
            Log.w(TAG, "Couldn't get CarBugreportManager", e);
        }
    }

    @Override
    public int onStartCommand(final Intent intent, int flags, int startId) {
        if (mIsCollectingBugReport.get()) {
            Log.w(TAG, "bug report is already being collected, ignoring");
            Toast.makeText(this, R.string.toast_bug_report_in_progress, Toast.LENGTH_SHORT).show();
            return START_NOT_STICKY;
        }
        Log.i(TAG, String.format("Will start collecting bug report, version=%s",
                getPackageVersion(this)));
        mIsCollectingBugReport.set(true);
        mBugReportProgress.set(0);

        startForeground(BUGREPORT_IN_PROGRESS_NOTIF_ID, buildProgressNotification());
        showProgressNotification();

        Bundle extras = intent.getExtras();
        mMetaBugReport = extras.getParcelable(EXTRA_META_BUG_REPORT);

        collectBugReport();

        // If the service process gets killed due to heavy memory pressure, do not restart.
        return START_NOT_STICKY;
    }

    /** Shows an updated progress notification. */
    private void showProgressNotification() {
        if (isCollectingBugReport()) {
            mNotificationManager.notify(
                    BUGREPORT_IN_PROGRESS_NOTIF_ID, buildProgressNotification());
        }
    }

    private Notification buildProgressNotification() {
        return new Notification.Builder(this, PROGRESS_CHANNEL_ID)
                .setContentTitle(getText(R.string.notification_bugreport_in_progress))
                .setSubText(String.format("%.1f%%", mBugReportProgress.get()))
                .setSmallIcon(R.drawable.download_animation)
                .setCategory(Notification.CATEGORY_STATUS)
                .setOngoing(true)
                .setProgress((int) MAX_PROGRESS_VALUE, (int) mBugReportProgress.get(), false)
                .build();
    }

    /** Returns true if bugreporting is in progress. */
    public boolean isCollectingBugReport() {
        return mIsCollectingBugReport.get();
    }

    /** Returns current bugreport progress. */
    public float getBugReportProgress() {
        return (float) mBugReportProgress.get();
    }

    /** Sets a bugreport progress listener. The listener is called on a main thread. */
    public void setBugReportProgressListener(BugReportProgressListener listener) {
        mBugReportProgressListener = listener;
    }

    /** Removes the bugreport progress listener. */
    public void removeBugReportProgressListener() {
        mBugReportProgressListener = null;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    private void showToast(@StringRes int resId) {
        // run on ui thread.
        mHandler.post(() -> Toast.makeText(this, getText(resId), Toast.LENGTH_LONG).show());
    }

    private void collectBugReport() {
        if (Build.IS_USERDEBUG || Build.IS_ENG) {
            mSingleThreadExecutor.schedule(
                    this::grabBtSnoopLog, ACTIVITY_FINISH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
        }
        mSingleThreadExecutor.schedule(
                this::saveBugReport, ACTIVITY_FINISH_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private void grabBtSnoopLog() {
        Log.i(TAG, "Grabbing bt snoop log");
        File result = FileUtils.getFileWithSuffix(this, mMetaBugReport.getTimestamp(),
                "-btsnoop.bin.log");
        try {
            copyBinaryStream(new FileInputStream(new File(BT_SNOOP_LOG_LOCATION)),
                    new FileOutputStream(result));
        } catch (IOException e) {
            // this regularly happens when snooplog is not enabled so do not log as an error
            Log.i(TAG, "Failed to grab bt snooplog, continuing to take bug report.", e);
        }
    }

    private void saveBugReport() {
        Log.i(TAG, "Dumpstate to file");
        File outputFile = FileUtils.getFile(this, mMetaBugReport.getTimestamp(), OUTPUT_ZIP_FILE);
        File extraOutputFile = FileUtils.getFile(this, mMetaBugReport.getTimestamp(),
                EXTRA_OUTPUT_ZIP_FILE);
        try (ParcelFileDescriptor outFd = ParcelFileDescriptor.open(outputFile,
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE);
             ParcelFileDescriptor extraOutFd = ParcelFileDescriptor.open(extraOutputFile,
                ParcelFileDescriptor.MODE_CREATE | ParcelFileDescriptor.MODE_READ_WRITE)) {
            requestBugReport(outFd, extraOutFd);
        } catch (IOException | RuntimeException e) {
            Log.e(TAG, "Failed to grab dump state", e);
            BugStorageUtils.setBugReportStatus(this, mMetaBugReport, Status.STATUS_WRITE_FAILED,
                    MESSAGE_FAILURE_DUMPSTATE);
            showToast(R.string.toast_status_dump_state_failed);
        }
    }

    private void sendProgressEventToHandler(float progress) {
        Message message = new Message();
        message.what = PROGRESS_HANDLER_EVENT_PROGRESS;
        message.getData().putFloat(PROGRESS_HANDLER_DATA_PROGRESS, progress);
        mHandler.sendMessage(message);
    }

    private void requestBugReport(ParcelFileDescriptor outFd, ParcelFileDescriptor extraOutFd) {
        if (DEBUG) {
            Log.d(TAG, "Requesting a bug report from CarBugReportManager.");
        }
        mCallback = new CarBugreportManager.CarBugreportManagerCallback() {
            @Override
            public void onError(int errorCode) {
                Log.e(TAG, "Bugreport failed " + errorCode);
                showToast(R.string.toast_status_failed);
                // TODO(b/133520419): show this error on Info page or add to zip file.
                scheduleZipTask();
                // We let the UI know that bug reporting is finished, because the next step is to
                // zip everything and upload.
                mBugReportProgress.set(MAX_PROGRESS_VALUE);
                sendProgressEventToHandler(MAX_PROGRESS_VALUE);
            }

            @Override
            public void onProgress(@FloatRange(from = 0f, to = MAX_PROGRESS_VALUE) float progress) {
                mBugReportProgress.set(progress);
                sendProgressEventToHandler(progress);
            }

            @Override
            public void onFinished() {
                Log.i(TAG, "Bugreport finished");
                scheduleZipTask();
                mBugReportProgress.set(MAX_PROGRESS_VALUE);
                sendProgressEventToHandler(MAX_PROGRESS_VALUE);
            }
        };
        mBugreportManager.requestBugreport(outFd, extraOutFd, mCallback);
    }

    private void scheduleZipTask() {
        mSingleThreadExecutor.submit(this::zipDirectoryAndScheduleForUpload);
    }

    /**
     * Shows a clickable bugreport finished notification. When clicked it opens
     * {@link BugReportInfoActivity}.
     */
    private void showBugReportFinishedNotification() {
        Intent intent = new Intent(getApplicationContext(), BugReportInfoActivity.class);
        PendingIntent startBugReportInfoActivity =
                PendingIntent.getActivity(getApplicationContext(), 0, intent, 0);
        Notification notification = new Notification
                .Builder(getApplicationContext(), STATUS_CHANNEL_ID)
                .setContentTitle(getText(R.string.notification_bugreport_finished_title))
                .setContentText(getText(JobSchedulingUtils.uploadByDefault()
                        ? R.string.notification_bugreport_auto_upload_finished_text
                        : R.string.notification_bugreport_manual_upload_finished_text))
                .setCategory(Notification.CATEGORY_STATUS)
                .setSmallIcon(R.drawable.ic_upload)
                .setContentIntent(startBugReportInfoActivity)
                .build();
        mNotificationManager.notify(BUGREPORT_FINISHED_NOTIF_ID, notification);
    }

    private void zipDirectoryAndScheduleForUpload() {
        try {
            // When OutputStream from openBugReportFile is closed, BugStorageProvider automatically
            // schedules an upload job.
            zipDirectoryToOutputStream(
                    FileUtils.createTempDir(this, mMetaBugReport.getTimestamp()),
                    BugStorageUtils.openBugReportFile(this, mMetaBugReport));
            showBugReportFinishedNotification();
        } catch (IOException e) {
            Log.e(TAG, "Failed to zip files", e);
            BugStorageUtils.setBugReportStatus(this, mMetaBugReport, Status.STATUS_WRITE_FAILED,
                    MESSAGE_FAILURE_ZIP);
            showToast(R.string.toast_status_failed);
        }
        mIsCollectingBugReport.set(false);
        showToast(R.string.toast_status_finished);
        mHandler.post(() -> stopForeground(true));
    }

    @Override
    public void onDestroy() {
        if (DEBUG) {
            Log.d(TAG, "Service destroyed");
        }
    }

    private static void copyBinaryStream(InputStream in, OutputStream out) throws IOException {
        OutputStream writer = null;
        InputStream reader = null;
        try {
            writer = new DataOutputStream(out);
            reader = new DataInputStream(in);
            rawCopyStream(writer, reader);
        } finally {
            IoUtils.closeQuietly(reader);
            IoUtils.closeQuietly(writer);
        }
    }

    // does not close the reader or writer.
    private static void rawCopyStream(OutputStream writer, InputStream reader) throws IOException {
        int read;
        byte[] buf = new byte[8192];
        while ((read = reader.read(buf, 0, buf.length)) > 0) {
            writer.write(buf, 0, read);
        }
    }

    /**
     * Compresses a directory into a zip file. The method is not recursive. Any sub-directory
     * contained in the main directory and any files contained in the sub-directories will be
     * skipped.
     *
     * @param dirToZip  The path of the directory to zip
     * @param outStream The output stream to write the zip file to
     * @throws IOException if the directory does not exist, its files cannot be read, or the output
     *                     zip file cannot be written.
     */
    private void zipDirectoryToOutputStream(File dirToZip, OutputStream outStream)
            throws IOException {
        if (!dirToZip.isDirectory()) {
            throw new IOException("zip directory does not exist");
        }
        Log.v(TAG, "zipping directory " + dirToZip.getAbsolutePath());

        File[] listFiles = dirToZip.listFiles();
        ZipOutputStream zipStream = new ZipOutputStream(new BufferedOutputStream(outStream));
        try {
            for (File file : listFiles) {
                if (file.isDirectory()) {
                    continue;
                }
                String filename = file.getName();

                // only for the zipped output file, we add invidiual entries to zip file
                if (filename.equals(OUTPUT_ZIP_FILE) || filename.equals(EXTRA_OUTPUT_ZIP_FILE)) {
                    extractZippedFileToOutputStream(file, zipStream);
                } else {
                    FileInputStream reader = new FileInputStream(file);
                    addFileToOutputStream(filename, reader, zipStream);
                }
            }
        } finally {
            zipStream.close();
            outStream.close();
        }
        // Zipping successful, now cleanup the temp dir.
        FileUtils.deleteDirectory(dirToZip);
    }

    private void extractZippedFileToOutputStream(File file, ZipOutputStream zipStream)
            throws IOException {
        ZipFile zipFile = new ZipFile(file);
        Enumeration<? extends ZipEntry> entries = zipFile.entries();
        while (entries.hasMoreElements()) {
            ZipEntry entry = entries.nextElement();
            InputStream stream = zipFile.getInputStream(entry);
            addFileToOutputStream(entry.getName(), stream, zipStream);
        }
    }

    private void addFileToOutputStream(String filename, InputStream reader,
            ZipOutputStream zipStream) throws IOException {
        ZipEntry entry = new ZipEntry(filename);
        zipStream.putNextEntry(entry);
        rawCopyStream(zipStream, reader);
        zipStream.closeEntry();
        reader.close();
    }
}
