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

package com.example.android.locationattribution;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import androidx.core.app.ActivityCompat;
import androidx.appcompat.app.AppCompatActivity;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.text.TextPaint;
import android.text.method.LinkMovementMethod;
import android.text.style.ClickableSpan;
import android.text.style.URLSpan;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/**
 * Non-framework Location Attribution sample application.
 *
 * <p>This location attribution sample application demonstrates how to give user visibility
 * and control of non-user-emergency location access by non-framework entities accessing GNSS
 * chipset API directly bypassing the standard Android framework location permission settings.
 *
 * <p>Displays text to the user about the benefits of giving location permission to this app so
 * that the non-framework entity or entities this app represents can access device location from
 * GNSS chipset directly.
 *
 * <p>Provides a button to allow the user to modify the location permission settings for this app.
 */
public class MainActivity extends AppCompatActivity {
    private static final String APPLICATION_ID = "com.example.android.locationattribution";
    private static final String TAG = "LocationAttribution";
    private static final String PREFS_FILE_NAME = "LocationAttributionPrefs";
    private static final int NON_FRAMEWORK_LOCATION_PERMISSION = 100;

    private static final String URL_PREFIX = "location_attribution_app://";
    private static final String LINK_LEARN_MORE = URL_PREFIX + "learn_more";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Set non-framework location access use case description to display.
        setTextViewAppInfoContent();

        // Show button for the user to modify location settings for this app.
        Button button = (Button)findViewById(R.id.buttonModifyLocationSettings);
        button.setOnClickListener(createModifyLocationSettingsButtonClickListener());
    }

    private void setTextViewAppInfoContent() {
        // This text is seen by the user when this app is opened through the App info screen in
        // Android Settings or when an intent is sent by carrier's own app.
        TextView textViewAppInfo = findViewById(R.id.textViewAppInfo);
        SpannableStringBuilder textViewAppInfoText = new SpannableStringBuilder();
        for (CharSequence paragraph : getResources().getTextArray(
                R.array.textViewAppInfo_Paragraphs)) {
            textViewAppInfoText.append(paragraph);
        }

        replaceUrlSpansWithClickableSpans(textViewAppInfoText);
        textViewAppInfo.setText(textViewAppInfoText);
        textViewAppInfo.setMovementMethod(LinkMovementMethod.getInstance());
    }

    private View.OnClickListener createModifyLocationSettingsButtonClickListener() {
        return new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (isLocationPermissionGranted(Manifest.permission.ACCESS_FINE_LOCATION)) {
                    if (!isLocationPermissionGranted(
                            Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        // Request 'Allow all the time' permission if the user didn't select
                        // 'Don't ask again' option earlier.
                        if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            showRequestBackgroundLocationPermissionDialog();
                            return;
                        }
                    }

                    // We can't show tri-state dialog when permission is already granted.
                    // So, go to the location permission settings screen directly.
                    showLocationPermissionSettingsDashboard();
                    return;
                }

                if (isFirstTimeAskingLocationPermission()) {
                    // Show tri-state dialog to change permission.
                    setFirstTimeAskingLocationPermission(false);
                    showRequestLocationPermissionDialog();
                    return;
                }

                if (ActivityCompat.shouldShowRequestPermissionRationale(MainActivity.this,
                        Manifest.permission.ACCESS_FINE_LOCATION)) {
                    // The user has previously denied the request. Show the tri-state dialog again.
                    showRequestLocationPermissionDialog();
                } else {
                    // User has denied permission and selected 'Don't ask again' option.
                    showLocationPermissionSettingsDashboard();
                }
            }
        };
    }

    private boolean isLocationPermissionGranted(String locationPermissionType) {
        return ActivityCompat.checkSelfPermission(this, locationPermissionType)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void showRequestLocationPermissionDialog() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                NON_FRAMEWORK_LOCATION_PERMISSION);
    }

    private void showRequestBackgroundLocationPermissionDialog() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                NON_FRAMEWORK_LOCATION_PERMISSION);
    }

    private void showLocationPermissionSettingsDashboard() {
        startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.parse("package:" + APPLICATION_ID)));
    }

    private void setFirstTimeAskingLocationPermission(boolean isFirstTime) {
        SharedPreferences sharedPreference = getApplicationContext().getSharedPreferences(
                PREFS_FILE_NAME, MODE_PRIVATE);
        SharedPreferences.Editor editor = sharedPreference.edit();
        editor.putBoolean(Manifest.permission.ACCESS_FINE_LOCATION, isFirstTime).apply();
        editor.commit();
    }

    private boolean isFirstTimeAskingLocationPermission() {
        return getApplicationContext().getSharedPreferences(PREFS_FILE_NAME,
                MODE_PRIVATE).getBoolean(Manifest.permission.ACCESS_FINE_LOCATION, true);
    }

    /**
     * A clickable text listener.
     *
     * <p>Used to listen to click events for clickable text in the description displayed by this
     * activity's main screen and navigate to the appropriate screen based on the text link
     * clicked.
     */
    private class AppInfoTextLinkClickableSpan extends ClickableSpan {
        private final String mUrl;

        private AppInfoTextLinkClickableSpan(String url) {
            mUrl = url;
        }

        @Override
        public void onClick(View textView) {
            switch (mUrl) {
                case LINK_LEARN_MORE:
                    startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(getString(R.string.urlLearnMore))));
                    break;
                default:
                    Log.e(TAG, "@string/textViewAppInfo contains invalid URL: " + mUrl);
            }
        }

        @Override
        public void updateDrawState(TextPaint drawState) {
            super.updateDrawState(drawState);
            drawState.setUnderlineText(false);
        }
    }

    /*
     * The description text in {@code textAppInfo} shown in the activity screen has URL links.
     * Replace those links with clickable links so that we get notified when those links are
     * clicked. We can then navigate to different screens based on the links clicked.
     */
    private void replaceUrlSpansWithClickableSpans(Spannable textAppInfo) {
        for(URLSpan span: textAppInfo.getSpans(0, textAppInfo.length(), URLSpan.class)) {
            int start = textAppInfo.getSpanStart(span);
            int end = textAppInfo.getSpanEnd(span);
            textAppInfo.removeSpan(span);
            textAppInfo.setSpan(new AppInfoTextLinkClickableSpan(span.getURL()), start, end, 0);
        }
    }
}
