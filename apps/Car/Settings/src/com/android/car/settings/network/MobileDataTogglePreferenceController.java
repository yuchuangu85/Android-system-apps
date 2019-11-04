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

package com.android.car.settings.network;

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.database.ContentObserver;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.telephony.SubscriptionInfo;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.annotation.VisibleForTesting;
import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/**
 * Business logic to control the toggle that enables/disables usage of mobile data. Does not have
 * support for multi-sim.
 */
public class MobileDataTogglePreferenceController extends
        PreferenceController<TwoStatePreference> implements
        MobileNetworkUpdateManager.MobileNetworkUpdateListener {

    @VisibleForTesting
    static final String DISABLE_DIALOG_TAG = ConfirmationDialogFragment.TAG + "_DisableData";
    @VisibleForTesting
    static final String ENABLE_MULTISIM_DIALOG_TAG =
            ConfirmationDialogFragment.TAG + "_EnableMultisim";

    private final SubscriptionManager mSubscriptionManager;
    private TelephonyManager mTelephonyManager;
    private int mSubId;

    private final ContentObserver mMobileDataChangeObserver = new ContentObserver(
            new Handler(Looper.getMainLooper())) {
        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            refreshUi();
        }
    };

    private final ConfirmationDialogFragment.ConfirmListener mConfirmDisableListener =
            arguments -> setMobileDataEnabled(
                    /* enabled= */ false, /* disableOtherSubscriptions= */ false);
    private final ConfirmationDialogFragment.ConfirmListener mConfirmMultiSimListener =
            arguments -> setMobileDataEnabled(
                    /* enabled= */ true, /* disableOtherSubscriptions= */ true);
    private final ConfirmationDialogFragment.RejectListener mRejectRefreshListener =
            arguments -> refreshUi();

    public MobileDataTogglePreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mSubscriptionManager = getContext().getSystemService(SubscriptionManager.class);
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    /** Sets the subscription id to be controlled by this controller. */
    public void setSubId(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(getContext()).createForSubscriptionId(mSubId);
    }

    @Override
    protected int getAvailabilityStatus() {
        return mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID ? AVAILABLE
                : CONDITIONALLY_UNAVAILABLE;
    }

    @Override
    protected void onCreateInternal() {
        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        DISABLE_DIALOG_TAG), mConfirmDisableListener, mRejectRefreshListener);

        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ENABLE_MULTISIM_DIALOG_TAG), mConfirmMultiSimListener,
                mRejectRefreshListener);
    }

    @Override
    protected void onStartInternal() {
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            getContext().getContentResolver().registerContentObserver(getObservableUri(mSubId),
                    /* notifyForDescendants= */ false, mMobileDataChangeObserver);
        }
    }

    @Override
    protected void onStopInternal() {
        if (mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID) {
            getContext().getContentResolver().unregisterContentObserver(mMobileDataChangeObserver);
        }
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setEnabled(!isOpportunistic());
        preference.setChecked(
                mTelephonyManager != null ? mTelephonyManager.isDataEnabled() : false);
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean newToggleValue = (boolean) newValue;
        boolean isMultiSim = (mTelephonyManager.getSimCount() > 1);
        int defaultSubId = mSubscriptionManager.getDefaultDataSubscriptionId();
        boolean needToDisableOthers = mSubscriptionManager.isActiveSubscriptionId(defaultSubId)
                && mSubId != defaultSubId;

        if (!newToggleValue && !isMultiSim) {
            getFragmentController().showDialog(getConfirmDataDisableDialog(), DISABLE_DIALOG_TAG);
        } else if (newToggleValue && isMultiSim && needToDisableOthers) {
            getFragmentController().showDialog(getConfirmMultisimEnableDialog(),
                    ENABLE_MULTISIM_DIALOG_TAG);
        } else {
            setMobileDataEnabled(newToggleValue, /* disableOtherSubscriptions= */ false);
        }
        return false;
    }

    @Override
    public void onMobileNetworkUpdated(int subId) {
        setSubId(subId);
        refreshUi();
    }

    private void setMobileDataEnabled(boolean enabled, boolean disableOtherSubscriptions) {
        NetworkUtils.setMobileDataEnabled(getContext(), mSubId, enabled, disableOtherSubscriptions);
        refreshUi();
    }

    private boolean isOpportunistic() {
        SubscriptionInfo info = mSubscriptionManager.getActiveSubscriptionInfo(mSubId);
        return info != null && info.isOpportunistic();
    }

    private Uri getObservableUri(int subId) {
        Uri uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA);
        if (TelephonyManager.from(getContext()).getSimCount() != 1) {
            uri = Settings.Global.getUriFor(Settings.Global.MOBILE_DATA + subId);
        }
        return uri;
    }

    private ConfirmationDialogFragment getConfirmDataDisableDialog() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setMessage(R.string.confirm_mobile_data_disable)
                .setPositiveButton(android.R.string.ok, mConfirmDisableListener)
                .setNegativeButton(android.R.string.cancel, mRejectRefreshListener)
                .build();
    }

    private ConfirmationDialogFragment getConfirmMultisimEnableDialog() {
        SubscriptionInfo previousSubInfo =
                mSubscriptionManager.getDefaultDataSubscriptionInfo();
        SubscriptionInfo newSubInfo =
                mSubscriptionManager.getActiveSubscriptionInfo(mSubId);

        String previousName = (previousSubInfo == null)
                ? getContext().getResources().getString(R.string.sim_selection_required_pref)
                : previousSubInfo.getDisplayName().toString();

        String newName = (newSubInfo == null)
                ? getContext().getResources().getString(R.string.sim_selection_required_pref)
                : newSubInfo.getDisplayName().toString();

        return new ConfirmationDialogFragment.Builder(getContext())
                .setTitle(getContext().getString(R.string.sim_change_data_title, newName))
                .setMessage(getContext().getString(R.string.sim_change_data_message,
                        newName, previousName))
                .setPositiveButton(getContext().getString(R.string.sim_change_data_ok, newName),
                        mConfirmMultiSimListener)
                .setNegativeButton(android.R.string.cancel, mRejectRefreshListener)
                .build();
    }
}
