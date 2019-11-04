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

import android.annotation.NonNull;
import android.content.Context;
import android.os.AsyncTask;
import android.text.TextUtils;
import android.util.Log;

import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.services.storage.Storage;
import com.google.api.services.storage.model.StorageObject;
import com.google.common.collect.ImmutableMap;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Uploads a file to GCS using a simple (no-multipart / no-resume) upload policy.
 *
 * <p>Please see {@code res/values/configs.xml} and {@code res/raw/gcs_credentials.json} for the
 * configuration.
 */
class SimpleUploaderAsyncTask extends AsyncTask<Void, Void, Boolean> {
    private static final String TAG = SimpleUploaderAsyncTask.class.getSimpleName();

    private static final String ACCESS_SCOPE =
            "https://www.googleapis.com/auth/devstorage.read_write";

    private static final String STORAGE_METADATA_TITLE = "title";

    private final Context mContext;
    private final Result mResult;

    /**
     * The uploader uploads only one bugreport each time it is called. This interface is
     * used to reschedule upload job, if there are more bugreports waiting.
     *
     * Pass true to reschedule upload job, false not to reschedule.
     */
    interface Result {
        void reschedule(boolean s);
    }

    /** Constructs SimpleUploaderAsyncTask. */
    SimpleUploaderAsyncTask(@NonNull Context context, @NonNull Result result) {
        mContext = context;
        mResult = result;
    }

    private StorageObject uploadSimple(
            Storage storage, MetaBugReport bugReport, String fileName, InputStream data)
            throws IOException {
        InputStreamContent mediaContent = new InputStreamContent("application/zip", data);

        String bucket = mContext.getString(R.string.config_gcs_bucket);
        if (TextUtils.isEmpty(bucket)) {
            throw new RuntimeException("config_gcs_bucket is empty.");
        }

        // Create GCS MetaData.
        Map<String, String> metadata = ImmutableMap.of(
                STORAGE_METADATA_TITLE, bugReport.getTitle()
        );
        StorageObject object = new StorageObject()
                .setBucket(bucket)
                .setName(fileName)
                .setMetadata(metadata)
                .setContentDisposition("attachment");
        Storage.Objects.Insert insertObject = storage.objects().insert(bucket, object,
                mediaContent);

        // The media uploader gzips content by default, and alters the Content-Encoding accordingly.
        // GCS dutifully stores content as-uploaded. This line disables the media uploader behavior,
        // so the service stores exactly what is in the InputStream, without transformation.
        insertObject.getMediaHttpUploader().setDisableGZipContent(true);
        Log.v(TAG, "started uploading object " + fileName + " to bucket " + bucket);
        return insertObject.execute();
    }

    private void upload(MetaBugReport bugReport) throws IOException {
        GoogleCredential credential = GoogleCredential
                .fromStream(mContext.getResources().openRawResource(R.raw.gcs_credentials))
                .createScoped(Collections.singleton(ACCESS_SCOPE));
        Log.v(TAG, "Created credential");
        HttpTransport httpTransport = AndroidHttp.newCompatibleTransport();
        JsonFactory jsonFactory = JacksonFactory.getDefaultInstance();

        Storage storage = new Storage.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("Bugreportupload/1.0").build();

        File bugReportFile = new File(bugReport.getFilePath());
        String fileName = bugReportFile.getName();
        try (FileInputStream inputStream = new FileInputStream(bugReportFile)) {
            StorageObject object = uploadSimple(storage, bugReport, fileName, inputStream);
            Log.v(TAG, "finished uploading object " + object.getName() + " file " + fileName);
        }
        Log.v(TAG, "Deleting file " + fileName);
        bugReportFile.delete();
    }

    @Override
    protected void onPostExecute(Boolean success) {
        mResult.reschedule(success);
    }

    /** Returns true is there are more files to upload. */
    @Override
    protected Boolean doInBackground(Void... voids) {
        List<MetaBugReport> bugReports = BugStorageUtils.getPendingBugReports(mContext);

        for (MetaBugReport bugReport : bugReports) {
            try {
                if (isCancelled()) {
                    BugStorageUtils.setUploadRetry(mContext, bugReport, "Upload Job Cancelled");
                    return true;
                }
                upload(bugReport);
                BugStorageUtils.setUploadSuccess(mContext, bugReport);
            } catch (Exception e) {
                Log.e(TAG, String.format("Failed uploading %s - likely a transient error",
                        bugReport.getTimestamp()), e);
                BugStorageUtils.setUploadRetry(mContext, bugReport, e);
            }
        }
        return false;
    }

    @Override
    protected void onCancelled(Boolean success) {
        mResult.reschedule(true);
    }
}
