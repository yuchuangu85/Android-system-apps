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
package com.android.wallpaper.module;

import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import androidx.annotation.Nullable;

/**
 * Checks whether an explore action can be taken for the given uri, i.e. whether any activity on
 * the device is capable of handling it.
 */
public class DefaultExploreIntentChecker implements ExploreIntentChecker {
    private Context mAppContext;

    private Map<Uri, Intent> mUriToActionViewIntentMap;

    public DefaultExploreIntentChecker(Context appContext) {
        mAppContext = appContext;
        mUriToActionViewIntentMap = new HashMap<>();
    }

    @Override
    public void fetchValidActionViewIntent(Uri uri, IntentReceiver receiver) {
        if (mUriToActionViewIntentMap.containsKey(uri)) {
            receiver.onIntentReceived(mUriToActionViewIntentMap.get(uri));
            return;
        }

        new FetchActionViewIntentTask(mAppContext, uri, receiver).execute();
    }

    private class FetchActionViewIntentTask extends AsyncTask<Void, Void, Intent> {
        private Context mAppContext;
        private Uri mUri;
        private IntentReceiver mReceiver;

        public FetchActionViewIntentTask(Context appContext, Uri uri, IntentReceiver receiver) {
            mAppContext = appContext;
            mUri = uri;
            mReceiver = receiver;
        }

        @Nullable
        @Override
        protected Intent doInBackground(Void... params) {
            Intent actionViewIntent = new Intent(Intent.ACTION_VIEW, mUri);

            PackageManager pm = mAppContext.getPackageManager();
            List<ResolveInfo> activities = pm.queryIntentActivities(actionViewIntent, /* flags */ 0);

            Intent result = activities.isEmpty() ? null : actionViewIntent;
            mUriToActionViewIntentMap.put(mUri, result);

            return result;
        }

        @Override
        protected void onPostExecute(Intent intent) {
            mReceiver.onIntentReceived(intent);
        }
    }
}

