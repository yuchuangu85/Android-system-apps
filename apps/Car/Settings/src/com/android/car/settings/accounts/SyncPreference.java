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

package com.android.car.settings.accounts;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import androidx.preference.PreferenceViewHolder;
import androidx.preference.SwitchPreference;

import com.android.car.apps.common.util.Themes;
import com.android.car.settings.R;

/**
 * A preference that represents the state of a sync adapter.
 *
 * <p>Largely derived from {@link com.android.settings.accounts.SyncStateSwitchPreference}.
 */
public class SyncPreference extends SwitchPreference {
    private int mUid;
    private String mPackageName;
    private AccountSyncHelper.SyncState mSyncState = AccountSyncHelper.SyncState.NONE;
    private boolean mOneTimeSyncMode = false;

    public SyncPreference(Context context, String authority) {
        super(context);
        setKey(authority);
        setPersistent(false);
        updateIcon();
    }

    @Override
    public void onBindViewHolder(PreferenceViewHolder view) {
        super.onBindViewHolder(view);

        View switchView = view.findViewById(com.android.internal.R.id.switch_widget);
        if (mOneTimeSyncMode) {
            switchView.setVisibility(View.GONE);

            /*
             * Override the summary. Fill in the %1$s with the existing summary
             * (what ends up happening is the old summary is shown on the next
             * line).
             */
            TextView summary = (TextView) view.findViewById(android.R.id.summary);
            summary.setText(getContext().getString(R.string.sync_one_time_sync, getSummary()));
        } else {
            switchView.setVisibility(View.VISIBLE);
        }
    }

    /** Updates the preference icon based on the current syncing state. */
    private void updateIcon() {
        switch (mSyncState) {
            case ACTIVE:
                setIcon(R.drawable.ic_sync_anim);
                getIcon().setTintList(Themes.getAttrColorStateList(getContext(), R.attr.iconColor));
                break;
            case PENDING:
                setIcon(R.drawable.ic_sync);
                getIcon().setTintList(Themes.getAttrColorStateList(getContext(), R.attr.iconColor));
                break;
            case FAILED:
                setIcon(R.drawable.ic_sync_problem);
                getIcon().setTintList(Themes.getAttrColorStateList(getContext(), R.attr.iconColor));
                break;
            default:
                setIcon(null);
                setIconSpaceReserved(true);
                break;
        }
    }

    /** Sets the sync state for this preference. */
    public void setSyncState(AccountSyncHelper.SyncState state) {
        mSyncState = state;
        // Force a manual update of the icon since the sync state affects what is shown.
        updateIcon();
    }

    /**
     * Returns whether or not the sync adapter is in one-time sync mode.
     *
     * <p>One-time sync mode means that the sync adapter is not being automatically synced but
     * can be manually synced (i.e. a one time sync).
     */
    public boolean isOneTimeSyncMode() {
        return mOneTimeSyncMode;
    }

    /** Sets whether one-time sync mode is on for this preference. */
    public void setOneTimeSyncMode(boolean oneTimeSyncMode) {
        mOneTimeSyncMode = oneTimeSyncMode;
        // Force a refresh so that onBindViewHolder is called
        notifyChanged();
    }

    /**
     * Returns the uid corresponding to the sync adapter's package.
     *
     * <p>This can be used to create an intent to request account access via
     * {@link android.accounts.AccountManager#createRequestAccountAccessIntentSenderAsUser}.
     */
    public int getUid() {
        return mUid;
    }

    /** Sets the uid for this preference. */
    public void setUid(int uid) {
        mUid = uid;
    }

    public String getPackageName() {
        return mPackageName;
    }

    public void setPackageName(String packageName) {
        mPackageName = packageName;
    }
}
