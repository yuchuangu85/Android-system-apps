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
 * limitations under the License.
 */

package com.android.server.telecom;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.net.Uri;
import android.os.Handler;
import android.os.IBinder;
import android.os.RemoteException;
import android.telecom.Log;
import android.telecom.Logging.Session;
import android.telecom.PhoneAccountHandle;
import android.telecom.PhoneAccountSuggestion;
import android.telecom.PhoneAccountSuggestionService;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;

import com.android.internal.telecom.IPhoneAccountSuggestionCallback;
import com.android.internal.telecom.IPhoneAccountSuggestionService;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PhoneAccountSuggestionHelper {
    private static final String TAG = PhoneAccountSuggestionHelper.class.getSimpleName();
    private static ComponentName sOverrideComponent;

    /**
     * @return A future (possible already complete) that contains a list of suggestions.
     */
    public static CompletableFuture<List<PhoneAccountSuggestion>>
    bindAndGetSuggestions(Context context, Uri handle,
            List<PhoneAccountHandle> availablePhoneAccounts) {
        // Use the default list if there's no handle
        if (handle == null) {
            return CompletableFuture.completedFuture(getDefaultSuggestions(availablePhoneAccounts));
        }
        String number = PhoneNumberUtils.extractNetworkPortion(handle.getSchemeSpecificPart());

        // Use the default list if there's no service on the device.
        ServiceInfo suggestionServiceInfo = getSuggestionServiceInfo(context);
        if (suggestionServiceInfo == null) {
            return CompletableFuture.completedFuture(getDefaultSuggestions(availablePhoneAccounts));
        }

        Intent bindIntent = new Intent();
        bindIntent.setComponent(new ComponentName(suggestionServiceInfo.packageName,
                suggestionServiceInfo.name));

        final CompletableFuture<List<PhoneAccountSuggestion>> future = new CompletableFuture<>();

        final Session logSession = Log.createSubsession();
        ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder _service) {
                Log.continueSession(logSession, "PASH.oSC");
                try {
                    IPhoneAccountSuggestionService service =
                            IPhoneAccountSuggestionService.Stub.asInterface(_service);
                    // Set up the callback to complete the future once the remote side comes
                    // back with suggestions
                    IPhoneAccountSuggestionCallback callback =
                            new IPhoneAccountSuggestionCallback.Stub() {
                                @Override
                                public void suggestPhoneAccounts(String suggestResultNumber,
                                        List<PhoneAccountSuggestion> suggestions) {
                                    if (TextUtils.equals(number, suggestResultNumber)) {
                                        if (suggestions == null) {
                                            future.complete(
                                                    getDefaultSuggestions(availablePhoneAccounts));
                                        } else {
                                            future.complete(
                                                    addDefaultsToProvidedSuggestions(
                                                            suggestions, availablePhoneAccounts));
                                        }
                                    }
                                }
                            };
                    try {
                        service.onAccountSuggestionRequest(callback, number);
                    } catch (RemoteException e) {
                        Log.w(TAG, "Cancelling suggestion process due to remote exception");
                        future.complete(getDefaultSuggestions(availablePhoneAccounts));
                    }
                } finally {
                    Log.endSession();
                }
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                // No locking needed -- CompletableFuture only lets one thread call complete.
                Log.continueSession(logSession, "PASH.oSD");
                try {
                    if (!future.isDone()) {
                        Log.w(TAG, "Cancelling suggestion process due to service disconnect");
                    }
                    future.complete(getDefaultSuggestions(availablePhoneAccounts));
                } finally {
                    Log.endSession();
                }
            }
        };

        if (!context.bindService(bindIntent, serviceConnection, Context.BIND_AUTO_CREATE)) {
            Log.i(TAG, "Cancelling suggestion process due to bind failure.");
            future.complete(getDefaultSuggestions(availablePhoneAccounts));
        }

        // Set up a timeout so that we're not waiting forever for the suggestion service.
        Handler handler = new Handler();
        handler.postDelayed(() -> {
                    // No locking needed -- CompletableFuture only lets one thread call complete.
                    Log.continueSession(logSession, "PASH.timeout");
                    try {
                        if (!future.isDone()) {
                            Log.w(TAG, "Cancelling suggestion process due to timeout");
                        }
                        future.complete(getDefaultSuggestions(availablePhoneAccounts));
                    } finally {
                        Log.endSession();
                    }
                },
                Timeouts.getPhoneAccountSuggestionServiceTimeout(context.getContentResolver()));
        return future;
    }

    private static List<PhoneAccountSuggestion> addDefaultsToProvidedSuggestions(
            List<PhoneAccountSuggestion> providedSuggestions,
            List<PhoneAccountHandle> availableAccountHandles) {
        List<PhoneAccountHandle> handlesInSuggestions = providedSuggestions.stream()
                .map(PhoneAccountSuggestion::getPhoneAccountHandle)
                .collect(Collectors.toList());
        List<PhoneAccountHandle> handlesToFillIn = availableAccountHandles.stream()
                .filter(handle -> !handlesInSuggestions.contains(handle))
                .collect(Collectors.toList());
        List<PhoneAccountSuggestion> suggestionsToAppend = getDefaultSuggestions(handlesToFillIn);
        return Stream.concat(suggestionsToAppend.stream(), providedSuggestions.stream())
                .collect( Collectors.toList());
    }

    private static ServiceInfo getSuggestionServiceInfo(Context context) {
        PackageManager packageManager = context.getPackageManager();
        Intent queryIntent = new Intent();
        queryIntent.setAction(PhoneAccountSuggestionService.SERVICE_INTERFACE);

        List<ResolveInfo> services;
        if (sOverrideComponent == null) {
            services = packageManager.queryIntentServices(queryIntent,
                    PackageManager.MATCH_SYSTEM_ONLY);
        } else {
            Log.i(TAG, "Using override component %s", sOverrideComponent);
            queryIntent.setComponent(sOverrideComponent);
            services = packageManager.queryIntentServices(queryIntent,
                    PackageManager.MATCH_ALL);
        }

        if (services == null || services.size() == 0) {
            Log.i(TAG, "No acct suggestion services found. Using defaults.");
            return null;
        }

        if (services.size() > 1) {
            Log.w(TAG, "More than acct suggestion service found, cannot get unique service");
            return null;
        }
        return services.get(0).serviceInfo;
    }

    static void setOverrideServiceName(String flattenedComponentName) {
        try {
            sOverrideComponent = TextUtils.isEmpty(flattenedComponentName)
                    ? null : ComponentName.unflattenFromString(flattenedComponentName);
        } catch (Exception e) {
            sOverrideComponent = null;
            throw e;
        }
    }

    private static List<PhoneAccountSuggestion> getDefaultSuggestions(
            List<PhoneAccountHandle> phoneAccountHandles) {
        return phoneAccountHandles.stream().map(phoneAccountHandle ->
                new PhoneAccountSuggestion(phoneAccountHandle,
                        PhoneAccountSuggestion.REASON_NONE, false)
        ).collect(Collectors.toList());
    }
}