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
package com.android.tv.dvr;

import android.content.ContentProviderOperation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.OperationApplicationException;
import android.database.Cursor;
import android.media.tv.TvInputInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.support.annotation.Nullable;
import android.util.Log;
import androidx.tvprovider.media.tv.TvContractCompat;
import com.android.tv.TvSingletons;
import com.android.tv.common.recording.RecordingStorageStatusManager;
import com.android.tv.common.util.CommonUtils;
import com.android.tv.util.TvInputManagerHelper;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** A class for extending TV app-specific function to {@link RecordingStorageStatusManager}. */
public class DvrStorageStatusManager extends RecordingStorageStatusManager {
    private static final String TAG = "DvrStorageStatusManager";

    private final Context mContext;
    private CleanUpDbTask mCleanUpDbTask;

    private static final String[] PROJECTION = {
        TvContractCompat.RecordedPrograms._ID,
        TvContractCompat.RecordedPrograms.COLUMN_PACKAGE_NAME,
        TvContractCompat.RecordedPrograms.COLUMN_RECORDING_DATA_URI
    };
    private static final int BATCH_OPERATION_COUNT = 100;

    public DvrStorageStatusManager(Context context) {
        super(context);
        mContext = context;
    }

    @Override
    protected void cleanUpDbIfNeeded() {
        if (mCleanUpDbTask != null) {
            mCleanUpDbTask.cancel(true);
        }
        mCleanUpDbTask = new CleanUpDbTask();
        mCleanUpDbTask.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private class CleanUpDbTask extends AsyncTask<Void, Void, Boolean> {
        private final ContentResolver mContentResolver;

        private CleanUpDbTask() {
            mContentResolver = mContext.getContentResolver();
        }

        @Override
        protected Boolean doInBackground(Void... params) {
            @StorageStatus int storageStatus = getDvrStorageStatus();
            if (storageStatus == STORAGE_STATUS_MISSING) {
                return null;
            }
            if (storageStatus == STORAGE_STATUS_TOTAL_CAPACITY_TOO_SMALL) {
                return true;
            }
            List<ContentProviderOperation> ops = getDeleteOps();
            if (ops == null || ops.isEmpty()) {
                return null;
            }
            Log.i(
                    TAG,
                    "New device storage mounted. # of recordings to be forgotten : " + ops.size());
            for (int i = 0; i < ops.size() && !isCancelled(); i += BATCH_OPERATION_COUNT) {
                int toIndex =
                        (i + BATCH_OPERATION_COUNT) > ops.size()
                                ? ops.size()
                                : (i + BATCH_OPERATION_COUNT);
                ArrayList<ContentProviderOperation> batchOps =
                        new ArrayList<>(ops.subList(i, toIndex));
                try {
                    mContext.getContentResolver().applyBatch(TvContractCompat.AUTHORITY, batchOps);
                } catch (RemoteException | OperationApplicationException e) {
                    Log.e(TAG, "Failed to clean up  RecordedPrograms.", e);
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Boolean forgetStorage) {
            if (forgetStorage != null && forgetStorage == true) {
                DvrManager dvrManager = TvSingletons.getSingletons(mContext).getDvrManager();
                TvInputManagerHelper tvInputManagerHelper =
                        TvSingletons.getSingletons(mContext).getTvInputManagerHelper();
                List<TvInputInfo> tvInputInfoList =
                        tvInputManagerHelper.getTvInputInfos(true, false);
                if (tvInputInfoList == null || tvInputInfoList.isEmpty()) {
                    return;
                }
                for (TvInputInfo info : tvInputInfoList) {
                    if (CommonUtils.isBundledInput(info.getId())) {
                        dvrManager.forgetStorage(info.getId());
                    }
                }
            }
            if (mCleanUpDbTask == this) {
                mCleanUpDbTask = null;
            }
        }


        @Nullable
        private List<ContentProviderOperation> getDeleteOps() {
            List<ContentProviderOperation> ops = new ArrayList<>();

            try (Cursor c =
                    mContentResolver.query(
                            TvContractCompat.RecordedPrograms.CONTENT_URI,
                            PROJECTION,
                            null,
                            null,
                            null)) {
                if (c == null) {
                    return null;
                }
                while (c.moveToNext()) {
                    @StorageStatus int storageStatus = getDvrStorageStatus();
                    if (isCancelled() || storageStatus == STORAGE_STATUS_MISSING) {
                        ops.clear();
                        break;
                    }
                    String id = c.getString(0);
                    String packageName = c.getString(1);
                    String dataUriString = c.getString(2);
                    if (dataUriString == null) {
                        continue;
                    }
                    Uri dataUri = Uri.parse(dataUriString);
                    if (!CommonUtils.isInBundledPackageSet(packageName)
                            || dataUri == null
                            || dataUri.getPath() == null
                            || !ContentResolver.SCHEME_FILE.equals(dataUri.getScheme())) {
                        continue;
                    }
                    File recordedProgramDir = new File(dataUri.getPath());
                    if (!recordedProgramDir.exists()) {
                        ops.add(
                                ContentProviderOperation.newDelete(
                                                TvContractCompat.buildRecordedProgramUri(
                                                        Long.parseLong(id)))
                                        .build());
                    }
                }
                return ops;
            } catch (Exception e) {
                Log.w(TAG, "Error when getting delete ops at CleanUpDbTask", e);
                return null;
            }
        }
    }
}
