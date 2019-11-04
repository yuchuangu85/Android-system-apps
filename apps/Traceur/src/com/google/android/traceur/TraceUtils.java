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
 * limitations under the License
 */

package com.android.traceur;

import android.os.Build;
import android.util.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import java.util.Collection;
import java.util.TreeMap;

/**
 * Utility functions for tracing.
 * Will call atrace or perfetto depending on the setting.
 */
public class TraceUtils {

    static final String TAG = "Traceur";

    public static final String TRACE_DIRECTORY = "/data/local/traces/";

    // To change Traceur to use atrace to collect traces,
    // change mTraceEngine to point to AtraceUtils().
    private static TraceEngine mTraceEngine = new PerfettoUtils();

    private static final Runtime RUNTIME = Runtime.getRuntime();

    public interface TraceEngine {
        public String getName();
        public String getOutputExtension();
        public boolean traceStart(Collection<String> tags, int bufferSizeKb, boolean apps,
            boolean longTrace, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes);
        public void traceStop();
        public boolean traceDump(File outFile);
        public boolean isTracingOn();
    }

    public static String currentTraceEngine() {
        return mTraceEngine.getName();
    }

    public static boolean traceStart(Collection<String> tags, int bufferSizeKb, boolean apps,
            boolean longTrace, int maxLongTraceSizeMb, int maxLongTraceDurationMinutes) {
        return mTraceEngine.traceStart(tags, bufferSizeKb, apps,
            longTrace, maxLongTraceSizeMb, maxLongTraceDurationMinutes);
    }

    public static void traceStop() {
        mTraceEngine.traceStop();
    }

    public static boolean traceDump(File outFile) {
        return mTraceEngine.traceDump(outFile);
    }

    public static boolean isTracingOn() {
        return mTraceEngine.isTracingOn();
    }

    public static TreeMap<String, String> listCategories() {
        return AtraceUtils.atraceListCategories();
    }

    public static void clearSavedTraces() {
        String cmd = "rm -f " + TRACE_DIRECTORY + "trace-*.*trace";

        Log.v(TAG, "Clearing trace directory: " + cmd);
        try {
            Process rm = exec(cmd);

            if (rm.waitFor() != 0) {
                Log.e(TAG, "clearSavedTraces failed with: " + rm.exitValue());
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static Process exec(String cmd) throws IOException {
        return exec(cmd, null);
    }

    public static Process exec(String cmd, String tmpdir) throws IOException {
        String[] cmdarray = {"sh", "-c", cmd};
        String[] envp = {"TMPDIR=" + tmpdir};
        envp = tmpdir == null ? null : envp;

        Log.v(TAG, "exec: " + Arrays.toString(envp) + " " + Arrays.toString(cmdarray));

        return RUNTIME.exec(cmdarray, envp);
    }

    public static String getOutputFilename() {
        String format = "yyyy-MM-dd-HH-mm-ss";
        String now = new SimpleDateFormat(format, Locale.US).format(new Date());
        return String.format("trace-%s-%s-%s.%s", Build.BOARD, Build.ID, now,
            mTraceEngine.getOutputExtension());
    }

    public static File getOutputFile(String filename) {
        return new File(TraceUtils.TRACE_DIRECTORY, filename);
    }

}
