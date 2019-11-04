/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.providers.tv;

import android.content.BroadcastReceiver;
import android.content.ContentProviderOperation;
import android.content.ContentProviderResult;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.OperationApplicationException;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.media.tv.TvContract;
import android.os.RemoteException;
import android.util.Log;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * This will be launched when PACKAGE_CHANGED intent is broadcast.
 */
public class PackageChangedReceiver extends BroadcastReceiver {
    private static final boolean DEBUG = true;
    private static final String TAG = "PackageChangedReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Intent.ACTION_PACKAGE_CHANGED.equals(intent.getAction()) && intent.getData() != null) {
            String packageName = intent.getData().getSchemeSpecificPart();
            PackageManager pm = context.getPackageManager();
            try {
                ApplicationInfo appInfo = pm.getApplicationInfo(packageName, 0);
                if (appInfo.enabled) {
                    return;
                }
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "Error: package " + packageName + " not found", e);
                return;
            }
            ArrayList<ContentProviderOperation> operations = new ArrayList<>();
            int uid = intent.getIntExtra(Intent.EXTRA_UID, 0);

            String ProgramsSelection = TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME + "=?";
            String[] ProgramsSelectionArgs = {packageName};
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.WatchNextPrograms.CONTENT_URI)
                    .withSelection(ProgramsSelection, ProgramsSelectionArgs).build());
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.PreviewPrograms.CONTENT_URI)
                    .withSelection(ProgramsSelection, ProgramsSelectionArgs).build());


            String ChannelsSelection = TvContract.BaseTvColumns.COLUMN_PACKAGE_NAME + "=? AND "
                    + TvContract.Channels.COLUMN_TYPE + "=?";
            String[] ChannelsSelectionArgs = {packageName, TvContract.Channels.TYPE_PREVIEW};
            operations.add(ContentProviderOperation
                    .newDelete(TvContract.Channels.CONTENT_URI)
                    .withSelection(ChannelsSelection, ChannelsSelectionArgs).build());

            ContentProviderResult[] results = null;
            try {
                ContentResolver cr = context.getContentResolver();
                results = cr.applyBatch(TvContract.AUTHORITY, operations);
            } catch (RemoteException | OperationApplicationException e) {
                Log.e(TAG, "error in applyBatch", e);
            }

            if (DEBUG) {
                Log.d(TAG, "onPackageFullyChanged(packageName=" + packageName + ", uid=" + uid
                        + ")");
                Log.d(TAG, "results=" + Arrays.toString(results));
            }
        }
    }
}
