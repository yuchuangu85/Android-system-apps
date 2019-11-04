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
package android.car.cluster;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.text.format.DateUtils;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;

import com.android.car.telephony.common.TelecomUtils.PhoneNumberInfo;

/**
 * Emits the description for the body in {@link PhoneFragmentViewModel}.
 *
 * This description may be the current duration of the call, call state, call type,
 * or a combination of them.
 *
 * Possible strings:
 * "Ringing"
 * "1:05"
 * "Mobile · Dialing"
 * "Mobile · 1:05"
 */
public class SelfRefreshDescriptionLiveData extends MediatorLiveData<String> {
    private final LiveData<Long> mConnectTimeLiveData;
    private final LiveData<PhoneNumberInfo> mNumberLiveData;
    private final LiveData<Integer> mStateLiveData;
    private final Context mContext;

    /**
     * @param stateLiveData       LiveData holding the {@link TelephonyManager} call state
     * @param numberLiveData      LiveData holding the call number
     * @param connectTimeLiveData LiveData holding the starting timestamp of the call
     */
    public SelfRefreshDescriptionLiveData(Context context,
            LiveData<Integer> stateLiveData,
            LiveData<PhoneNumberInfo> numberLiveData,
            LiveData<Long> connectTimeLiveData) {
        mContext = context;
        mNumberLiveData = numberLiveData;
        mStateLiveData = stateLiveData;
        mConnectTimeLiveData = connectTimeLiveData;

        HeartBeatLiveData heartBeatLiveData = new HeartBeatLiveData(DateUtils.SECOND_IN_MILLIS);

        addSource(stateLiveData, (trigger) -> updateDescription());
        addSource(heartBeatLiveData, (trigger) -> updateDescription());
        addSource(mNumberLiveData, (trigger) -> updateDescription());
        addSource(mConnectTimeLiveData, (trigger) -> updateDescription());
    }

    private void updateDescription() {
        PhoneNumberInfo number = mNumberLiveData.getValue();
        Integer callState = mStateLiveData.getValue();
        Long connectTime = mConnectTimeLiveData.getValue();
        if (callState != null) {
            String newDescription = getCallInfoText(mContext, callState, number,
                    connectTime != null ? connectTime : 0);

            String oldDescription = getValue();
            if (!newDescription.equals(oldDescription)) {
                setValue(newDescription);
            }
        } else {
            setValue("");
        }
    }

    /**
     * @return A formatted string that has information about the phone call
     * Possible strings:
     * "Mobile · Dialing"
     * "Mobile · 1:05"
     */
    private String getCallInfoText(Context context, Integer callState, PhoneNumberInfo number,
            Long connectTime) {
        String label = number != null ? number.getTypeLabel() : null;
        String text = "";
        if (callState == TelephonyManager.CALL_STATE_OFFHOOK) {
            long duration = connectTime > 0 ? System.currentTimeMillis()
                    - connectTime : 0;
            String durationString = DateUtils.formatElapsedTime(duration / 1000);
            if (!TextUtils.isEmpty(durationString) && !TextUtils.isEmpty(label)) {
                text = context.getString(R.string.phone_label_with_info, label,
                        durationString);
            } else if (!TextUtils.isEmpty(durationString)) {
                text = durationString;
            } else if (!TextUtils.isEmpty(label)) {
                text = label;
            }
        } else {
            String state = callStateToUiString(context, callState);
            if (!TextUtils.isEmpty(label)) {
                text = context.getString(R.string.phone_label_with_info, label, state);
            } else {
                text = state;
            }
        }

        return text;
    }

    /**
     * @return A string representation of the call state that can be presented to a user.
     */
    private String callStateToUiString(Context context, int state) {
        switch (state) {
            case TelephonyManager.CALL_STATE_IDLE:
                return context.getString(R.string.call_state_call_ended);
            case TelephonyManager.CALL_STATE_RINGING:
                return context.getString(R.string.call_state_call_ringing);
            case TelephonyManager.CALL_STATE_OFFHOOK:
                return context.getString(R.string.call_state_call_active);
            default:
                return "";
        }
    }
}
