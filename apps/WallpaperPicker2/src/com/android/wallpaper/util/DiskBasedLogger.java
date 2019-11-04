/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Process;
import android.util.Log;

import com.android.wallpaper.compat.BuildCompat;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

/**
 * Logs messages to logcat and for debuggable build types ("eng" or "userdebug") also mirrors logs
 * to a disk-based log buffer.
 */
public class DiskBasedLogger {

    static final String LOGS_FILE_PATH = "logs.txt";
    static final SimpleDateFormat DATE_FORMAT =
            new SimpleDateFormat("EEE MMM dd HH:mm:ss.SSS z yyyy", Locale.US);

    private static final String TEMP_LOGS_FILE_PATH = "temp_logs.txt";
    private static final String TAG = "DiskBasedLogger";

    /**
     * POJO used to lock thread creation and file read/write operations.
     */
    private static final Object S_LOCK = new Object();

    private static final long THREAD_TIMEOUT_MILLIS =
            TimeUnit.MILLISECONDS.convert(2, TimeUnit.MINUTES);
    private static Handler sHandler;
    private static HandlerThread sLoggerThread;
    private static final Runnable THREAD_CLEANUP_RUNNABLE = new Runnable() {
        @Override
        public void run() {
            if (sLoggerThread != null && sLoggerThread.isAlive()) {

                // HandlerThread#quitSafely was added in JB-MR2, so prefer to use that instead of #quit.
                boolean isQuitSuccessful = BuildCompat.isAtLeastJBMR2()
                        ? sLoggerThread.quitSafely()
                        : sLoggerThread.quit();

                if (!isQuitSuccessful) {
                    Log.e(TAG, "Unable to quit disk-based logger HandlerThread");
                }

                sLoggerThread = null;
                sHandler = null;
            }
        }
    };

    /**
     * Initializes and returns a new dedicated HandlerThread for reading and writing to the disk-based
     * logs file.
     */
    private static void initializeLoggerThread() {
        sLoggerThread = new HandlerThread("DiskBasedLoggerThread", Process.THREAD_PRIORITY_BACKGROUND);
        sLoggerThread.start();
    }

    /**
     * Returns a Handler that can post messages to the dedicated HandlerThread for reading and writing
     * to the logs file on disk. Lazy-loads the HandlerThread if it doesn't already exist and delays
     * its death by a timeout if the thread already exists.
     */
    private static Handler getLoggerThreadHandler() {
        synchronized (S_LOCK) {
            if (sLoggerThread == null) {
                initializeLoggerThread();

                // Create a new Handler tied to the new HandlerThread's Looper for processing disk I/O off
                // the main thread. Starts with a default timeout to quit and remove references to the
                // thread after a period of inactivity.
                sHandler = new Handler(sLoggerThread.getLooper());
            } else {
                sHandler.removeCallbacks(THREAD_CLEANUP_RUNNABLE);
            }

            // Delay the logger thread's eventual death.
            sHandler.postDelayed(THREAD_CLEANUP_RUNNABLE, THREAD_TIMEOUT_MILLIS);

            return sHandler;
        }
    }

    /**
     * Logs an "error" level log to logcat based on the provided tag and message and also duplicates
     * the log to a file-based log buffer if running on a "userdebug" or "eng" build.
     */
    public static void e(String tag, String msg, Context context) {
        // Pass log tag and message through to logcat regardless of build type.
        Log.e(tag, msg);

        // Only mirror logs to disk-based log buffer if the build is debuggable.
        if (!Build.TYPE.equals("eng") && !Build.TYPE.equals("userdebug")) {
            return;
        }

        Handler handler = getLoggerThreadHandler();
        if (handler == null) {
            Log.e(TAG, "Something went wrong creating the logger thread handler, quitting this logging "
                    + "operation");
            return;
        }

        handler.post(() -> {
            File logs = new File(context.getFilesDir(), LOGS_FILE_PATH);

            // Construct a log message that we can parse later in order to clean up old logs.
            String datetime = DATE_FORMAT.format(Calendar.getInstance().getTime());
            String log = datetime + "/E " + tag + ": " + msg + "\n";

            synchronized (S_LOCK) {
                FileOutputStream outputStream;

                try {
                    outputStream = context.openFileOutput(logs.getName(), Context.MODE_APPEND);
                    outputStream.write(log.getBytes(UTF_8));
                    outputStream.close();
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close output stream for disk-based log buffer", e);
                }
            }
        });
    }

    /**
     * Deletes logs in the disk-based log buffer older than 7 days.
     */
    public static void clearOldLogs(Context context) {
        if (!Build.TYPE.equals("eng") && !Build.TYPE.equals("userdebug")) {
            return;
        }

        Handler handler = getLoggerThreadHandler();
        if (handler == null) {
            Log.e(TAG, "Something went wrong creating the logger thread handler, quitting this logging "
                    + "operation");
            return;
        }

        handler.post(() -> {
            // Check if the logs file exists first before trying to read from it.
            File logsFile = new File(context.getFilesDir(), LOGS_FILE_PATH);
            if (!logsFile.exists()) {
                Log.w(TAG, "Disk-based log buffer doesn't exist, so there's nothing to clean up.");
                return;
            }

            synchronized (S_LOCK) {
                FileInputStream inputStream;
                BufferedReader bufferedReader;

                try {
                    inputStream = context.openFileInput(LOGS_FILE_PATH);
                    bufferedReader = new BufferedReader(new InputStreamReader(inputStream, UTF_8));
                } catch (IOException e) {
                    Log.e(TAG, "IO exception opening a buffered reader for the existing logs file", e);
                    return;
                }

                Date sevenDaysAgo = getSevenDaysAgo();

                File tempLogsFile = new File(context.getFilesDir(), TEMP_LOGS_FILE_PATH);
                FileOutputStream outputStream;

                try {
                    outputStream = context.openFileOutput(TEMP_LOGS_FILE_PATH, Context.MODE_APPEND);
                } catch (IOException e) {
                    Log.e(TAG, "Unable to close output stream for disk-based log buffer", e);
                    return;
                }

                copyLogsNewerThanDate(bufferedReader, outputStream, sevenDaysAgo);

                // Close streams to prevent resource leaks.
                closeStream(inputStream, "couldn't close input stream for log file");
                closeStream(outputStream, "couldn't close output stream for temp log file");

                // Rename temp log file (if it exists--which is only when the logs file has logs newer than
                // 7 days to begin with) to the final logs file.
                if (tempLogsFile.exists() && !tempLogsFile.renameTo(logsFile)) {
                    Log.e(TAG, "couldn't rename temp logs file to final logs file");
                }
            }
        });
    }

    @Nullable
    @VisibleForTesting
  /* package */ static Handler getHandler() {
        return sHandler;
    }

    /**
     * Constructs and returns a {@link Date} object representing the time 7 days ago.
     */
    private static Date getSevenDaysAgo() {
        Calendar sevenDaysAgoCalendar = Calendar.getInstance();
        sevenDaysAgoCalendar.add(Calendar.DAY_OF_MONTH, -7);
        return sevenDaysAgoCalendar.getTime();
    }

    /**
     * Tries to close the provided Closeable stream and logs the error message if the stream couldn't
     * be closed.
     */
    private static void closeStream(Closeable stream, String errorMessage) {
        try {
            stream.close();
        } catch (IOException e) {
            Log.e(TAG, errorMessage);
        }
    }

    /**
     * Copies all log lines newer than the supplied date from the provided {@link BufferedReader} to
     * the provided {@OutputStream}.
     * <p>
     * The caller of this method is responsible for closing the output stream and input stream
     * underlying the BufferedReader when all operations have finished.
     */
    private static void copyLogsNewerThanDate(BufferedReader reader, OutputStream outputStream,
                                              Date date) {
        try {
            String line = reader.readLine();
            while (line != null) {
                // Get the date from the line string.
                String datetime = line.split("/")[0];
                Date logDate;
                try {
                    logDate = DATE_FORMAT.parse(datetime);
                } catch (ParseException e) {
                    Log.e(TAG, "Error parsing date from previous logs", e);
                    return;
                }

                // Copy logs newer than the provided date into a temp log file.
                if (logDate.after(date)) {
                    outputStream.write(line.getBytes(UTF_8));
                    outputStream.write("\n".getBytes(UTF_8));
                }

                line = reader.readLine();
            }
        } catch (IOException e) {
            Log.e(TAG, "IO exception while reading line from buffered reader", e);
        }
    }
}
