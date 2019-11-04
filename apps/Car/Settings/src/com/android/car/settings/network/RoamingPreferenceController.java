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
import android.os.PersistableBundle;
import android.provider.Settings;
import android.telephony.CarrierConfigManager;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;

import androidx.preference.TwoStatePreference;

import com.android.car.settings.R;
import com.android.car.settings.common.ConfirmationDialogFragment;
import com.android.car.settings.common.FragmentController;
import com.android.car.settings.common.PreferenceController;

/** Business logic for toggling the roaming state. */
// TODO: This preference should be available but unsearchable if subscription id is invalid.
public class RoamingPreferenceController extends PreferenceController<TwoStatePreference> implements
        MobileNetworkUpdateManager.MobileNetworkUpdateListener {

    private final CarrierConfigManager mCarrierConfigManager;
    private final RoamingStateChangeObserver mRoamingStateChangeObserver;
    private TelephonyManager mTelephonyManager;
    private int mSubId;

    private final ConfirmationDialogFragment.ConfirmListener mConfirmListener =
            arguments -> setRoamingEnabled(true);

    public RoamingPreferenceController(Context context, String preferenceKey,
            FragmentController fragmentController, CarUxRestrictions uxRestrictions) {
        super(context, preferenceKey, fragmentController, uxRestrictions);
        mCarrierConfigManager = context.getSystemService(CarrierConfigManager.class);
        mRoamingStateChangeObserver = new RoamingStateChangeObserver(
                new Handler(Looper.getMainLooper()), this::refreshUi);
        mSubId = SubscriptionManager.INVALID_SUBSCRIPTION_ID;
    }

    @Override
    protected Class<TwoStatePreference> getPreferenceType() {
        return TwoStatePreference.class;
    }

    /** Set the subscription id for which the roaming toggle should take effect. */
    public void setSubId(int subId) {
        mSubId = subId;
        mTelephonyManager = TelephonyManager.from(getContext()).createForSubscriptionId(mSubId);
    }

    @Override
    protected void onStartInternal() {
        mRoamingStateChangeObserver.register(getContext(), mSubId);

        ConfirmationDialogFragment.resetListeners(
                (ConfirmationDialogFragment) getFragmentController().findDialogByTag(
                        ConfirmationDialogFragment.TAG),
                mConfirmListener,
                /* rejectListener= */ null);
    }

    @Override
    protected void onStopInternal() {
        mRoamingStateChangeObserver.unregister(getContext());
    }

    @Override
    protected void updateState(TwoStatePreference preference) {
        preference.setEnabled(mSubId != SubscriptionManager.INVALID_SUBSCRIPTION_ID);
        preference.setChecked(
                mTelephonyManager != null ? mTelephonyManager.isDataRoamingEnabled() : false);
    }

    @Override
    protected boolean handlePreferenceChanged(TwoStatePreference preference, Object newValue) {
        boolean isEnabled = (boolean) newValue;
        if (isEnabled && isDialogNeeded()) {
            getFragmentController().showDialog(getRoamingAlertDialog(),
                    ConfirmationDialogFragment.TAG);
            return false;
        }

        setRoamingEnabled(isEnabled);
        return true;
    }

    @Override
    public void onMobileNetworkUpdated(int subId) {
        setSubId(subId);
        refreshUi();
    }

    private void setRoamingEnabled(boolean enabled) {
        mTelephonyManager.setDataRoamingEnabled(enabled);
        refreshUi();
    }

    private boolean isDialogNeeded() {
        boolean isRoamingEnabled = mTelephonyManager.isDataRoamingEnabled();
        PersistableBundle carrierConfig = mCarrierConfigManager.getConfigForSubId(mSubId);

        // Need dialog if we need to turn on roaming and the roaming charge indication is allowed.
        if (!isRoamingEnabled && (carrierConfig == null || !carrierConfig.getBoolean(
                CarrierConfigManager.KEY_DISABLE_CHARGE_INDICATION_BOOL))) {
            return true;
        }
        return false;
    }

    private ConfirmationDialogFragment getRoamingAlertDialog() {
        return new ConfirmationDialogFragment.Builder(getContext())
                .setTitle(R.string.roaming_alert_title)
                .setMessage(R.string.roaming_warning)
                .setPositiveButton(android.R.string.yes, mConfirmListener)
                .setNegativeButton(android.R.string.no, /* rejectListener= */ null)
                .build();
    }

    /** Observer that listens to data roaming change. */
    private static class RoamingStateChangeObserver extends ContentObserver {

        private Runnable mChangeListener;

        RoamingStateChangeObserver(Handler handler, Runnable changeListener) {
            super(handler);
            mChangeListener = changeListener;
        }

        @Override
        public void onChange(boolean selfChange) {
            super.onChange(selfChange);
            mChangeListener.run();
        }

        /** Register this observer to listen for updates to {@link Settings.Global#DATA_ROAMING}. */
        public void register(Context context, int subId) {
            Uri uri = Settings.Global.getUriFor(Settings.Global.DATA_ROAMING);
            if (TelephonyManager.from(context).getSimCount() != 1) {
                uri = Settings.Global.getUriFor(Settings.Global.DATA_ROAMING + subId);
            }
            context.getContentResolver().registerContentObserver(uri,
                    /* notifyForDescendants= */ false, /* observer= */ this);
        }

        /** Unregister this observer. */
        public void unregister(Context context) {
            context.getContentResolver().unregisterContentObserver(/* observer= */ this);
        }
    }
}
