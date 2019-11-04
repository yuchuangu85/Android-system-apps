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
package com.android.documentsui.services;

import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.RemoteViews;

/**
* This class receives a callback when Notification is posted or removed
* and monitors the Notification status.
* And, this sends the operation's result by Broadcast.
*/
public class TestNotificationService extends NotificationListenerService {
    private static final String TAG = "TestNotificationService";

    public static final String ACTION_CHANGE_CANCEL_MODE =
            "com.android.documentsui.services.TestNotificationService.ACTION_CHANGE_CANCEL_MODE";

    public static final String ACTION_CHANGE_EXECUTION_MODE =
            "com.android.documentsui.services.TestNotificationService.ACTION_CHANGE_EXECUTION_MODE";

    public static final String ACTION_OPERATION_RESULT =
            "com.android.documentsui.services.TestNotificationService.ACTION_OPERATION_RESULT";

    public static final String ANDROID_PACKAGENAME = "android";

    public static final String CANCEL_RES_NAME = "cancel";

    public static final String EXTRA_RESULT =
            "com.android.documentsui.services.TestNotificationService.EXTRA_RESULT";

    public static final String EXTRA_ERROR_REASON =
            "com.android.documentsui.services.TestNotificationService.EXTRA_ERROR_REASON";

    public enum MODE {
        CANCEL_MODE,
        EXECUTION_MODE;
    }

    private static String mTargetPackageName;

    private MODE mCurrentMode = MODE.CANCEL_MODE;

    private boolean mCancelled = false;

    private FrameLayout mFrameLayout = null;

    private ProgressBar mProgressBar = null;

    private final BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_CHANGE_CANCEL_MODE.equals(action)) {
                mCurrentMode = MODE.CANCEL_MODE;
            } else if (ACTION_CHANGE_EXECUTION_MODE.equals(action)) {
                mCurrentMode = MODE.EXECUTION_MODE;
            }
        }
    };

    @Override
    public void onCreate() {
        mTargetPackageName = getTargetPackageName();
        mFrameLayout = new FrameLayout(getBaseContext());
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_CHANGE_CANCEL_MODE);
        filter.addAction(ACTION_CHANGE_EXECUTION_MODE);
        registerReceiver(mReceiver, filter);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        unregisterReceiver(mReceiver);
        mProgressBar = null;
        mFrameLayout.removeAllViews();
        mFrameLayout = null;
    }

    @Override
    public void onNotificationPosted(StatusBarNotification sbn) {
        String pkgName = sbn.getPackageName();
        if (mTargetPackageName.equals(pkgName)) {
            if (MODE.CANCEL_MODE.equals(mCurrentMode)) {
                try {
                    mCancelled = doCancel(sbn.getNotification());
                } catch (Exception e) {
                    Log.d(TAG, "Error occurs when cancel notification.", e);
                }
            }
        }
    }

    @Override
    public void onNotificationRemoved(StatusBarNotification sbn) {
        String pkgName = sbn.getPackageName();
        if (!mTargetPackageName.equals(pkgName)) {
            return;
        }

        Intent intent = new Intent(ACTION_OPERATION_RESULT);
        if (MODE.CANCEL_MODE.equals(mCurrentMode)) {
            intent.putExtra(EXTRA_RESULT, mCancelled);
            if (!mCancelled) {
                intent.putExtra(EXTRA_ERROR_REASON, "Cannot executed cancel");
            }
        } else if (MODE.EXECUTION_MODE.equals(mCurrentMode)) {
            boolean isStartProgress = isStartProgress(sbn.getNotification());
            intent.putExtra(EXTRA_RESULT, isStartProgress);
            if (!isStartProgress) {
                intent.putExtra(EXTRA_ERROR_REASON, "Progress does not displayed correctly.");
            }
        }
        sendBroadcast(intent);
    }

    private boolean doCancel(Notification noti)
            throws NameNotFoundException, PendingIntent.CanceledException {
        if (!isStartProgress(noti)) {
            return false;
        }

        Notification.Action aList [] = noti.actions;
        if (aList == null) {
            return false;
        }

        boolean result = false;
        for (Notification.Action item : aList) {
            Context android_context = getBaseContext().createPackageContext(ANDROID_PACKAGENAME,
                    Context.CONTEXT_RESTRICTED);
            int res_id = android_context.getResources().getIdentifier(CANCEL_RES_NAME,
                    "string", ANDROID_PACKAGENAME);
            final String cancel_label = android_context.getResources().getString(res_id);

            if (cancel_label.equals(item.title)) {
                item.actionIntent.send();
                result = true;
            }
        }
        return result;
    }

    private boolean isStartProgress(Notification notifiction) {
        ProgressBar progressBar = getProgresssBar(getRemoteViews(notifiction));
        return (progressBar != null) ? progressBar.getProgress() > 0 : false;
    }

    private RemoteViews getRemoteViews(Notification notifiction) {
        Notification.Builder builder = Notification.Builder.recoverBuilder(
            getBaseContext(), notifiction);
        if (builder == null) {
            return null;
        }

        return builder.createContentView();
    }

    private ProgressBar getProgresssBar(RemoteViews remoteViews) {
        if (remoteViews == null) {
            return null;
        }

        View view = remoteViews.apply(getBaseContext(), mFrameLayout);
        return getProgressBarImpl(view);
    }

    private ProgressBar getProgressBarImpl(View view) {
        if (view == null || !(view instanceof ViewGroup)) {
            return null;
        }

        ViewGroup viewGroup = (ViewGroup)view;
        if (viewGroup.getChildCount() <= 0) {
            return null;
        }

        ProgressBar result = null;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            View v = viewGroup.getChildAt(i);
            if (v instanceof ProgressBar) {
                result = ((ProgressBar)v);
                break;
            } else if (v instanceof ViewGroup) {
                result = getProgressBarImpl(v);
                if (result != null) {
                    break;
                }
            }
        }
        return result;
    }

    private String getTargetPackageName() {
        final PackageManager pm = getPackageManager();

        final Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        final ResolveInfo ri = pm.resolveActivity(intent, 0);
        return ri.activityInfo.packageName;
    }
}
