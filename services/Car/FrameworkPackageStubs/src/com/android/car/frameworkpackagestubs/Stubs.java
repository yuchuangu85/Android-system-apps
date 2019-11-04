/*
 * Copyright (C) 2019 The Android Open Source Project.
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

package com.android.car.frameworkpackagestubs;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Toast;

/**
 * Contains different stub classes.
 *
 * <p>These are broken out so that the intent filters are easier to track and so that
 * individual ones can create more specific messages if desired.
 */
public final class Stubs {

    /**
     * Base class for stubs.
     *
     * <p>Shows a toast and finishes.
     *
     * <p>Subclasses can override {@link #getMessage()} to customize the message.
     */
    private static class BaseActivity extends Activity {

        private Toast mToast;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            showToast();
            finish();
        }

        protected CharSequence getMessage() {
            return getResources().getString(R.string.message_not_supported);
        }

        private void showToast() {
            cancelToast();
            mToast = Toast.makeText(this, getMessage(), Toast.LENGTH_LONG);
            mToast.show();
        }

        private void cancelToast() {
            if (mToast != null) {
                mToast.cancel();
            }
        }
    }

    /**
     * Stub activity for Browser events.
     */
    public static class BrowserStub extends BaseActivity { }

    /**
     * Stub activity for Calendar events.
     */
    public static class CalendarStub extends BaseActivity { }

    /**
     * Stub activity for Desk Clock events.
     */
    public static class DeskClockStub extends BaseActivity { }

    /**
     * Stub activity for Dialer events.
     */
    public static class DialerStub extends BaseActivity { }

    /**
     * Stub activity for media events.
     */
    public static class MediaStub extends BaseActivity { }

    /**
     * Stub activity for setting events.
     */
    public static class SettingsStub extends BaseActivity { }

    /**
     * Stub activity for ignore background data restriction setting.
     */
    public static class IgnoreBackgroundDataRestrictionsSettingsStub extends BaseActivity { }

    /**
     * Stub activity for ignore battery optimization setting.
     */
    public static class IgnoreBatteryOptimizationSettingsStub extends BaseActivity { }

    /**
     * Stub activity for request battery optimization.
     */
    public static class RequestIgnoreBatteryOptimizationsStub extends BaseActivity { }

    /**
     * Stub activity for webview setting.
     */
    public static class WebViewSettingsStub extends BaseActivity { }
}
