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

package com.android.server.telecom;

import android.Manifest;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.telecom.CallScreeningService;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.text.TextUtils;

import com.android.internal.telecom.ICallScreeningAdapter;
import com.android.internal.telecom.ICallScreeningService;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * Helper class for performing operations with {@link CallScreeningService}s.
 */
public class CallScreeningServiceHelper {
    private static final String TAG = CallScreeningServiceHelper.class.getSimpleName();

    /**
     * Abstracts away dependency on the {@link PackageManager} required to fetch the label for an
     * app.
     */
    public interface AppLabelProxy {
        CharSequence getAppLabel(String packageName);
    }

    /**
     * Implementation of {@link CallScreeningService} adapter AIDL; provides a means for responses
     * from the call screening service to be handled.
     */
    private class CallScreeningAdapter extends ICallScreeningAdapter.Stub {
        @Override
        public void allowCall(String s) throws RemoteException {
            // no-op; we don't allow this on outgoing calls.
        }

        @Override
        public void silenceCall(String s) throws RemoteException {
            // no-op; we don't allow this on outgoing calls.
        }

        @Override
        public void disallowCall(String s, boolean b, boolean b1, boolean b2,
                ComponentName componentName) throws RemoteException {
            // no-op; we don't allow this on outgoing calls.
        }
    }

    private final ParcelableCallUtils.Converter mParcelableCallUtilsConverter;
    private final TelecomSystem.SyncRoot mTelecomLock;
    private final Call mCall;
    private final UserHandle mUserHandle;
    private final Context mContext;
    private final AppLabelProxy mAppLabelProxy;
    private final Session mLoggingSession;
    private CompletableFuture mFuture;
    private String mPackageName;

    public CallScreeningServiceHelper(Context context, TelecomSystem.SyncRoot telecomLock,
            String packageName, ParcelableCallUtils.Converter converter,
            UserHandle userHandle, Call call, AppLabelProxy appLabelProxy) {
        mContext = context;
        mTelecomLock = telecomLock;
        mParcelableCallUtilsConverter = converter;
        mCall = call;
        mUserHandle = userHandle;
        mPackageName = packageName;
        mAppLabelProxy = appLabelProxy;
        mLoggingSession = Log.createSubsession();
    }

    /**
     * Builds a {@link CompletableFuture} which performs a bind to a {@link CallScreeningService}
     * @return
     */
    public CompletableFuture process() {
        Log.d(this, "process");
        return bindAndGetCallIdentification();
    }

    public CompletableFuture bindAndGetCallIdentification() {
        Log.d(this, "bindAndGetCallIdentification");
        if (mPackageName == null) {
            return CompletableFuture.completedFuture(null);
        }

        mFuture = new CompletableFuture();

        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                ICallScreeningService screeningService =
                        ICallScreeningService.Stub.asInterface(service);
                Log.continueSession(mLoggingSession, "CSSH.oSC");
                try {
                    try {
                        // Note: for outgoing calls, never include the restricted extras.
                        screeningService.screenCall(new CallScreeningAdapter(),
                                mParcelableCallUtilsConverter.toParcelableCallForScreening(mCall,
                                        false /* areRestrictedExtrasIncluded */));
                    } catch (RemoteException e) {
                        Log.w(CallScreeningServiceHelper.this,
                                "Cancelling call id due to remote exception");
                        mFuture.complete(null);
                    }
                } finally {
                    Log.endSession();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // No locking needed -- CompletableFuture only lets one thread call complete.
                Log.continueSession(mLoggingSession, "CSSH.oSD");
                try {
                    if (!mFuture.isDone()) {
                        Log.w(CallScreeningServiceHelper.this,
                                "Cancelling outgoing call screen due to service disconnect.");
                    }
                    mFuture.complete(null);
                } finally {
                    Log.endSession();
                }
            }
        };

        if (!bindCallScreeningService(mContext, mUserHandle, mPackageName, serviceConnection)) {
            Log.i(this, "bindAndGetCallIdentification - bind failed");
            Log.addEvent(mCall, LogUtils.Events.BIND_SCREENING, mPackageName);
            mFuture.complete(null);
        }

        // Set up a timeout so that we're not waiting forever for the caller ID information.
        Handler handler = new Handler();
        handler.postDelayed(() -> {
                    // No locking needed -- CompletableFuture only lets one thread call complete.
                    Log.continueSession(mLoggingSession, "CSSH.timeout");
                    try {
                        if (!mFuture.isDone()) {
                            Log.w(TAG, "Cancelling call id process due to timeout");
                        }
                        mFuture.complete(null);
                    } finally {
                        Log.endSession();
                    }
                },
                Timeouts.getCallScreeningTimeoutMillis(mContext.getContentResolver()));
        return mFuture;
    }

    /**
     * Binds to a {@link CallScreeningService}.
     * @param context The current context.
     * @param userHandle User to bind as.
     * @param packageName Package name of the {@link CallScreeningService}.
     * @param serviceConnection The {@link ServiceConnection} to be notified of binding.
     * @return {@code true} if binding succeeds, {@code false} otherwise.
     */
    public static boolean bindCallScreeningService(Context context, UserHandle userHandle,
            String packageName, ServiceConnection serviceConnection) {
        if (TextUtils.isEmpty(packageName)) {
            Log.i(TAG, "PackageName is empty. Not performing call screening.");
            return false;
        }

        Intent intent = new Intent(CallScreeningService.SERVICE_INTERFACE)
                .setPackage(packageName);
        List<ResolveInfo> entries = context.getPackageManager().queryIntentServicesAsUser(
                intent, 0, userHandle.getIdentifier());
        if (entries.isEmpty()) {
            Log.i(TAG, packageName + " has no call screening service defined.");
            return false;
        }

        ResolveInfo entry = entries.get(0);
        if (entry.serviceInfo == null) {
            Log.w(TAG, packageName + " call screening service has invalid service info");
            return false;
        }

        if (entry.serviceInfo.permission == null || !entry.serviceInfo.permission.equals(
                Manifest.permission.BIND_SCREENING_SERVICE)) {
            Log.w(TAG, "CallScreeningService must require BIND_SCREENING_SERVICE permission: " +
                    entry.serviceInfo.packageName);
            return false;
        }

        ComponentName componentName =
                new ComponentName(entry.serviceInfo.packageName, entry.serviceInfo.name);
        intent.setComponent(componentName);
        if (context.bindServiceAsUser(
                intent,
                serviceConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_FOREGROUND_SERVICE,
                UserHandle.CURRENT)) {
            Log.d(TAG, "bindService, found service, waiting for it to connect");
            return true;
        }

        return false;
    }
}
