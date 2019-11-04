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


import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;
import android.util.Log;

public class StopTraceService extends TraceService {
    private static final String TAG = "Traceur";

    public StopTraceService() {
        super("StopTraceService");
        setIntentRedelivery(true);
    }

    /* If we stop a trace using this entrypoint, we must also reset the preference and the
     * Quick Settings UI, since this may be the only indication that the user wants to stop the
     * trace.
    */
    @Override
    public void onHandleIntent(Intent intent) {
        Context context = getApplicationContext();
        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(context);
        boolean prefsTracingOn =
            prefs.getBoolean(context.getString(R.string.pref_key_tracing_on), false);

        // If the user thinks tracing is off and the trace processor agrees, we have no work to do.
        // We must still start a foreground service, but let's log as an FYI.
        if (!prefsTracingOn && !TraceUtils.isTracingOn()) {
            Log.i(TAG, "StopTraceService does not see a trace to stop.");
        }

        PreferenceManager.getDefaultSharedPreferences(context)
                .edit().putBoolean(context.getString(R.string.pref_key_tracing_on),
                        false).commit();
        context.sendBroadcast(new Intent(MainFragment.ACTION_REFRESH_TAGS));
        QsService.updateTile();

        intent.setAction(INTENT_ACTION_FORCE_STOP_TRACING);
        super.onHandleIntent(intent);
    }
}
