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

import static androidx.lifecycle.Transformations.map;

import android.app.Application;
import android.content.Context;
import android.telephony.PhoneStateListener;
import android.telephony.TelephonyManager;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.android.car.telephony.common.Contact;
import com.android.car.telephony.common.InMemoryPhoneBook;
import com.android.car.telephony.common.TelecomUtils.PhoneNumberInfo;

/**
 * View model for {@link PhoneFragment}
 */
public final class PhoneFragmentViewModel extends AndroidViewModel {
    private MutableLiveData<Long> mConnectTime = new MutableLiveData<>();
    private MutableLiveData<Integer> mState = new MutableLiveData<>();
    private MutableLiveData<String> mNumber = new MutableLiveData<>();
    private LiveData<String> mBody;
    private LiveData<ContactInfo> mContactInfo;

    private PhoneStateCallback mCallback;
    private ClusterPhoneStateListener mPhoneStateListener = new ClusterPhoneStateListener();

    public PhoneFragmentViewModel(Application application) {
        super(application);

        TelephonyManager telephonyManager = (TelephonyManager) application.getSystemService(
                Context.TELEPHONY_SERVICE);

        // We have to keep a reference to the PhoneStateListener around to prevent it from being
        // garbage-collected.
        telephonyManager.listen(mPhoneStateListener, PhoneStateListener.LISTEN_CALL_STATE);

        LiveData<PhoneNumberInfo> numberInfo = new PhoneNumberInfoLiveData(
                getApplication(), mNumber);
        mBody = new SelfRefreshDescriptionLiveData(
                getApplication(), mState, numberInfo, mConnectTime);

        mContactInfo = map(numberInfo, ContactInfo::new);
    }

    public interface PhoneStateCallback {
        void onCall();

        void onDisconnect();
    }

    public LiveData<Integer> getState() {
        return mState;
    }

    public LiveData<String> getBody() {
        return mBody;
    }

    public LiveData<ContactInfo> getContactInfo() {
        return mContactInfo;
    }

    public void setPhoneStateCallback(PhoneStateCallback callback) {
        mCallback = callback;
    }

    /**
     * Listens to phone state changes
     */
    private class ClusterPhoneStateListener extends PhoneStateListener {
        ClusterPhoneStateListener() {
        }

        @Override
        public void onCallStateChanged(int state, String incomingNumber) {
            super.onCallStateChanged(state, incomingNumber);

            mState.setValue(state);
            mNumber.setValue(incomingNumber);

            if (state == TelephonyManager.CALL_STATE_IDLE) {
                if (mCallback != null) {
                    mCallback.onDisconnect();
                }
            } else if (state == TelephonyManager.CALL_STATE_RINGING) {
                if (mCallback != null) {
                    mCallback.onCall();
                }
            } else if (state == TelephonyManager.CALL_STATE_OFFHOOK) {
                mConnectTime.setValue(System.currentTimeMillis());
                if (mCallback != null) {
                    mCallback.onCall();
                }
            }
        }
    }

    public class ContactInfo {
        private String mNumber;
        private String mDisplayName;
        private Contact mContact;

        public ContactInfo(PhoneNumberInfo info) {
            mNumber = info.getPhoneNumber();
            mDisplayName = info.getDisplayName();
            mContact = InMemoryPhoneBook.get().lookupContactEntry(info.getPhoneNumber());
        }

        public String getNumber() {
            return mNumber;
        }

        public String getDisplayName() {
            return mDisplayName;
        }

        public Contact getContact() {
            return mContact;
        }
    }
}
