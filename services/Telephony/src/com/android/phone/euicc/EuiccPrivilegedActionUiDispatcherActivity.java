/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.phone.euicc;

import android.annotation.Nullable;
import android.content.Intent;
import android.service.euicc.EuiccService;
import android.telephony.euicc.EuiccManager;
import android.util.Log;

/**
 * Trampoline activity to forward privileged eUICC intents from the system to the active UI
 * implementation.
 *
 * <p>Unlike {@link EuiccUiDispatcherActivity}, this activity requires a locked-down permission to
 * start.
 */
public class EuiccPrivilegedActionUiDispatcherActivity extends EuiccUiDispatcherActivity {
    private static final String TAG = "EuiccPrivUiDispatcher";

    @Override
    @Nullable
    protected Intent getEuiccUiIntent() {
        String action = getIntent().getAction();

        Intent intent = new Intent();
        // Propagate the extras from the original Intent.
        intent.putExtras(getIntent());
        switch (action) {
            case EuiccManager.ACTION_TOGGLE_SUBSCRIPTION_PRIVILEGED:
                intent.setAction(EuiccService.ACTION_TOGGLE_SUBSCRIPTION_PRIVILEGED);
                break;
            case EuiccManager.ACTION_DELETE_SUBSCRIPTION_PRIVILEGED:
                intent.setAction(EuiccService.ACTION_DELETE_SUBSCRIPTION_PRIVILEGED);
                break;
            case EuiccManager.ACTION_RENAME_SUBSCRIPTION_PRIVILEGED:
                intent.setAction(EuiccService.ACTION_RENAME_SUBSCRIPTION_PRIVILEGED);
                break;
            default:
                Log.w(TAG, "Unsupported action: " + action);
                return null;
        }

        return intent;
    }
}
