/*
 * Copyright 2019, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.android.managedprovisioning.common;

import android.accounts.Account;
import android.annotation.Nullable;
import android.content.Context;
import android.os.AsyncTask;

import java.lang.ref.WeakReference;

import static com.android.internal.util.Preconditions.checkNotNull;

/**
 * Asynchronous task to remove the specified account.
 */
class RemoveAccountAsyncTask extends AsyncTask<Void, Void, Void> {

    private final WeakReference<Context> mContextRef;
    private final Account mAccountToRemove;
    private final Utils mUtils;
    private RemoveAccountListener mCallback;

    RemoveAccountAsyncTask(
            Context context, Account accountToRemove, Utils utils,
            @Nullable RemoveAccountListener callback) {
        this.mContextRef = new WeakReference<>(checkNotNull(context));
        this.mAccountToRemove = checkNotNull(accountToRemove);
        this.mUtils = checkNotNull(utils);
        this.mCallback = callback;
    }

    @Override
    protected Void doInBackground(Void... params) {
        final Context context = mContextRef.get();
        if (context != null) {
            mUtils.removeAccount(context, mAccountToRemove);
        }
        return null;
    }

    @Override
    protected void onPostExecute(Void result) {
        if (mCallback != null) {
            mCallback.onAccountRemoved();
        }
    }
}
