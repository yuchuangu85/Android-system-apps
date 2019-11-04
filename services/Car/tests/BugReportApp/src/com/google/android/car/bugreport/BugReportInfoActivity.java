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

import android.app.Activity;
import android.app.NotificationManager;
import android.content.ContentResolver;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;

/**
 * Provides an activity that provides information on the bugreports that are filed.
 */
public class BugReportInfoActivity extends Activity {
    public static final String TAG = BugReportInfoActivity.class.getSimpleName();

    private static final int SELECT_DIRECTORY_REQUEST_CODE = 1;

    private RecyclerView mRecyclerView;
    private RecyclerView.Adapter mAdapter;
    private RecyclerView.LayoutManager mLayoutManager;
    private NotificationManager mNotificationManager;
    private MetaBugReport mLastSelectedBugReport;

    private static final class AsyncMoveFilesTask extends AsyncTask<Void, Void, Boolean> {
        private final BugReportInfoActivity mActivity;
        private final MetaBugReport mBugReport;
        private final Uri mDestinationDirUri;

        AsyncMoveFilesTask(BugReportInfoActivity activity, MetaBugReport bugReport,
                Uri destinationDir) {
            mActivity = activity;
            mBugReport = bugReport;
            mDestinationDirUri = destinationDir;
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            Uri sourceUri = BugStorageProvider.buildUriWithBugId(mBugReport.getId());
            ContentResolver resolver = mActivity.getContentResolver();
            String documentId = DocumentsContract.getTreeDocumentId(mDestinationDirUri);
            Uri parentDocumentUri =
                    DocumentsContract.buildDocumentUriUsingTree(mDestinationDirUri, documentId);
            String mimeType = resolver.getType(sourceUri);
            try {
                Uri newFileUri = DocumentsContract.createDocument(resolver, parentDocumentUri,
                        mimeType,
                        new File(mBugReport.getFilePath()).toPath().getFileName().toString());
                if (newFileUri == null) {
                    Log.e(TAG, "Unable to create a new file.");
                    return false;
                }
                try (InputStream input = resolver.openInputStream(sourceUri);
                     OutputStream output = resolver.openOutputStream(newFileUri)) {
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = input.read(buffer)) > 0) {
                        output.write(buffer, 0, len);
                    }
                }
                BugStorageUtils.setBugReportStatus(
                        mActivity, mBugReport,
                        com.google.android.car.bugreport.Status.STATUS_MOVE_SUCCESSFUL, "");
            } catch (IOException e) {
                Log.e(TAG, "Failed to create the bug report in the location.", e);
                return false;
            }
            return true;
        }

        @Override
        protected void onPostExecute(Boolean moveSuccessful) {
            if (!moveSuccessful) {
                BugStorageUtils.setBugReportStatus(
                        mActivity, mBugReport,
                        com.google.android.car.bugreport.Status.STATUS_MOVE_FAILED, "");
            }
            // Refresh the UI to reflect the new status.
            new BugReportInfoTask(mActivity).execute();
        }
    }

    private static final class BugReportInfoTask extends
            AsyncTask<Void, Void, List<MetaBugReport>> {
        private final WeakReference<BugReportInfoActivity> mBugReportInfoActivityWeakReference;

        BugReportInfoTask(BugReportInfoActivity activity) {
            mBugReportInfoActivityWeakReference = new WeakReference<>(activity);
        }

        @Override
        protected List<MetaBugReport> doInBackground(Void... voids) {
            BugReportInfoActivity activity = mBugReportInfoActivityWeakReference.get();
            if (activity == null) {
                Log.w(TAG, "Activity is gone, cancelling BugReportInfoTask.");
                return new ArrayList<>();
            }
            return BugStorageUtils.getAllBugReportsDescending(activity);
        }

        @Override
        protected void onPostExecute(List<MetaBugReport> result) {
            BugReportInfoActivity activity = mBugReportInfoActivityWeakReference.get();
            if (activity == null) {
                Log.w(TAG, "Activity is gone, cancelling onPostExecute.");
                return;
            }
            activity.mAdapter = new BugInfoAdapter(result, activity::onBugReportItemClicked);
            activity.mRecyclerView.setAdapter(activity.mAdapter);
            activity.mRecyclerView.getAdapter().notifyDataSetChanged();
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.bug_report_info_activity);

        mNotificationManager = getSystemService(NotificationManager.class);

        mRecyclerView = findViewById(R.id.rv_bug_report_info);
        mRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        mLayoutManager = new LinearLayoutManager(this);
        mRecyclerView.setLayoutManager(mLayoutManager);
        mRecyclerView.addItemDecoration(new DividerItemDecoration(mRecyclerView.getContext(),
                DividerItemDecoration.VERTICAL));

        // specify an adapter (see also next example)
        mAdapter = new BugInfoAdapter(new ArrayList<>(), this::onBugReportItemClicked);
        mRecyclerView.setAdapter(mAdapter);

        findViewById(R.id.quit_button).setOnClickListener(this::onQuitButtonClick);
        findViewById(R.id.start_bug_report_button).setOnClickListener(
                this::onStartBugReportButtonClick);
        ((TextView) findViewById(R.id.version_text_view)).setText(
                String.format("v%s", getPackageVersion(this)));

        cancelBugReportFinishedNotification();
    }

    @Override
    protected void onStart() {
        super.onStart();
        new BugReportInfoTask(this).execute();
    }

    /**
     * Dismisses {@link BugReportService#BUGREPORT_FINISHED_NOTIF_ID}, otherwise the notification
     * will stay there forever if this activity opened through the App Launcher.
     */
    private void cancelBugReportFinishedNotification() {
        mNotificationManager.cancel(BugReportService.BUGREPORT_FINISHED_NOTIF_ID);
    }

    private void onBugReportItemClicked(int buttonType, MetaBugReport bugReport) {
        if (buttonType == BugInfoAdapter.BUTTON_TYPE_UPLOAD) {
            Log.i(TAG, "Uploading " + bugReport.getFilePath());
            BugStorageUtils.setBugReportStatus(this, bugReport, Status.STATUS_UPLOAD_PENDING, "");
            // Refresh the UI to reflect the new status.
            new BugReportInfoTask(this).execute();
        } else if (buttonType == BugInfoAdapter.BUTTON_TYPE_MOVE) {
            Log.i(TAG, "Moving " + bugReport.getFilePath());
            mLastSelectedBugReport = bugReport;
            startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE),
                    SELECT_DIRECTORY_REQUEST_CODE);
        } else {
            throw new IllegalStateException("unreachable");
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == SELECT_DIRECTORY_REQUEST_CODE && resultCode == RESULT_OK) {
            int takeFlags =
                    data.getFlags() & (Intent.FLAG_GRANT_READ_URI_PERMISSION
                            | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
            Uri destDirUri = data.getData();
            getContentResolver().takePersistableUriPermission(destDirUri, takeFlags);
            if (mLastSelectedBugReport == null) {
                Log.w(TAG, "No bug report is selected.");
                return;
            }
            new AsyncMoveFilesTask(this, mLastSelectedBugReport, destDirUri).execute();
        }
    }

    private void onQuitButtonClick(View view) {
        finish();
    }

    private void onStartBugReportButtonClick(View view) {
        Intent intent = new Intent(this, BugReportActivity.class);
        // Clear top is needed, otherwise multiple BugReportActivity-ies get opened and
        // MediaRecorder crashes.
        intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
    }
}
