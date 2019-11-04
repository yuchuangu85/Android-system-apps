/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.pump.activity;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;

import com.android.pump.util.Clog;

@UiThread
// This class needs to inherit from Activity in order for Theme.Translucent to be applied correctly.
public class ActivityStarterActivity extends /* NOT AppCompatActivity !!! */ Activity {
    private static final String TAG = Clog.tag(ActivityStarterActivity.class);

    private static final int REQUEST_CODE = 42;
    private static final String EXTRA_INTENT =
            "com.android.pump.activity.ActivityStarterActivity.EXTRA_INTENT";
    private static final String EXTRA_OPTIONS =
            "com.android.pump.activity.ActivityStarterActivity.EXTRA_OPTIONS";

    private boolean mApplicationCrashed;

    public static @NonNull Intent createStartIntent(@NonNull Context context,
            @NonNull Intent intent, @Nullable Bundle options) {
        Intent wrapperIntent = new Intent(context, ActivityStarterActivity.class);
        wrapperIntent.putExtra(EXTRA_INTENT, intent);
        wrapperIntent.putExtra(EXTRA_OPTIONS, options);
        return wrapperIntent;
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (savedInstanceState != null) {
            // We're already waiting for the activity to finish, so don't start another instance.
            mApplicationCrashed = true;
            return;
        }

        Intent intent = getIntent();
        Intent startIntent = getExtraIntent(intent);
        if (startIntent != null) {
            try {
                Bundle options = intent.getParcelableExtra(EXTRA_OPTIONS);
                startActivityForResult(startIntent, REQUEST_CODE, options);
                mApplicationCrashed = true;
            } catch (ActivityNotFoundException e) {
                Clog.w(TAG, "Failed to find activity for intent " + startIntent, e);

                // TODO(b/123037263) I18n -- Move to resource
                cancel("Failed to find application");
            } catch (SecurityException e) {
                Clog.w(TAG, "No permission to launch intent " + startIntent, e);

                // TODO(b/123037263) I18n -- Move to resource
                cancel("No permission to launch application");
            }
        } else {
            throw new IllegalArgumentException(
                    "Couldn't find EXTRA_INTENT for activity starter activity");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (!isFinishing()) {
            // The system is temporarily killing us, so ignore for now.
            return;
        }

        if (mApplicationCrashed) {
            Clog.w(TAG, "Activity crashed for intent " + getExtraIntent(getIntent()));

            // TODO(b/123037263) I18n -- Move to resource
            Toast.makeText(this, "Tried to start an external application but it crashed.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_CODE) {
            mApplicationCrashed = false;
            setResult(resultCode, data);
            finish();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private @Nullable Intent getExtraIntent(@Nullable Intent intent) {
        return intent == null ? null : intent.getParcelableExtra(EXTRA_INTENT);
    }

    private void cancel(@NonNull CharSequence message) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        setResult(Activity.RESULT_CANCELED);
        finish();
    }
}
