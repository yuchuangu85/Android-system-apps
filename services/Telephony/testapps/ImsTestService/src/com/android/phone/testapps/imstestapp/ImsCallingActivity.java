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

package com.android.phone.testapps.imstestapp;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.telephony.SubscriptionManager;
import android.telephony.ims.ImsException;
import android.telephony.ims.ImsMmTelManager;
import android.telephony.ims.feature.MmTelFeature;
import android.telephony.ims.stub.ImsRegistrationImplBase;
import android.util.Log;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Objects;

public class ImsCallingActivity extends Activity {

    private static final String PREFIX_ITEM = "Capability Event: ";
    private static final String PREFIX_VALUE = "Value: ";

    private static class CapItem {
        public String key;
        public String value;

        CapItem(String key, String value) {
            this.key = key;
            this.value = value;
        }

        CapItem(String key, int value) {
            this.key = key;
            this.value = String.valueOf(value);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CapItem regItem = (CapItem) o;
            return Objects.equals(key, regItem.key)
                    && Objects.equals(value, regItem.value);
        }

        @Override
        public int hashCode() {

            return Objects.hash(key, value);
        }
    }

    private static class CapItemAdapter extends ArrayAdapter<CapItem> {
        CapItemAdapter(Context context, ArrayList<CapItem> regItems) {
            super(context, 0, regItems);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            CapItem capItem = getItem(position);

            if (convertView == null) {
                convertView = LayoutInflater.from(getContext()).inflate(R.layout.config_item,
                        parent, false);
            }

            TextView textItem = (TextView) convertView.findViewById(R.id.configItem);
            TextView textValue = (TextView) convertView.findViewById(R.id.configValue);

            textItem.setText(PREFIX_ITEM + capItem.key);
            textValue.setText(PREFIX_VALUE + capItem.value);

            return convertView;
        }
    }

    private final ImsMmTelManager.CapabilityCallback mCapabilityCallback =
            new ImsMmTelManager.CapabilityCallback() {

        @Override
        public void onCapabilitiesStatusChanged(
                MmTelFeature.MmTelCapabilities capabilities) {
            Log.i("ImsCallingActivity" , "onCapabilitiesStatusChanged:" + capabilities);
            mCapabilityEvents.add(new CapItem("cap changed: ", capabilities.toString()));
            notifyDataChanged();

        }

        private void notifyDataChanged() {
            mCapabiltyEventAdapter.notifyDataSetChanged();
        }
    };

    //Capabilities available by service
    private CheckBox mCapVoiceAvailBox;
    private CheckBox mCapVideoAvailBox;
    private CheckBox mCapUtAvailBox;
    private CheckBox mCapSmsAvailBox;

    private TextView mCapEnabledText;

    private ArrayList<CapItem> mCapabilityEvents = new ArrayList<>();
    private CapItemAdapter mCapabiltyEventAdapter;
    private ListView mListView;

    private ImsMmTelManager mImsManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_calling);

        TestMmTelFeatureImpl.getInstance().initialize(this, 0);

        mCapVoiceAvailBox = findViewById(R.id.call_cap_voice);
        mCapVideoAvailBox = findViewById(R.id.call_cap_video);
        mCapUtAvailBox = findViewById(R.id.call_cap_ut);
        mCapSmsAvailBox = findViewById(R.id.call_cap_sms);
        mCapEnabledText = findViewById(R.id.call_cap_enabled_text);
        Button capChangedButton = findViewById(R.id.call_cap_change);
        capChangedButton.setOnClickListener((v) -> onCapabilitiesChangedClicked());

        TestMmTelFeatureImpl.getInstance().addUpdateCallback(
                new TestMmTelFeatureImpl.MmTelUpdateCallback() {
                    @Override
                    void onEnabledCapabilityChanged() {
                        mmTelCapabilityChanged();
                    }
                });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mmTelCapabilityChanged();

        mCapabiltyEventAdapter = new CapItemAdapter(this, mCapabilityEvents);
        mListView = (ListView) findViewById(R.id.cap_cb_list);
        mListView.setAdapter(mCapabiltyEventAdapter);
        try {
            mImsManager = ImsMmTelManager.createForSubscriptionId(
                    SubscriptionManager.getDefaultVoiceSubscriptionId());
            Log.i("ImsCallingActivity", "onResume");
            mImsManager.registerMmTelCapabilityCallback(getMainExecutor(), mCapabilityCallback);
        } catch (IllegalArgumentException | ImsException e) {
            Log.w("ImsCallingActivity", "Exception: " + e.getMessage());
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        mImsManager.unregisterMmTelCapabilityCallback(mCapabilityCallback);
        mImsManager = null;
    }

    private void mmTelCapabilityChanged() {
        SparseArray<MmTelFeature.MmTelCapabilities> caps =
                TestMmTelFeatureImpl.getInstance().getEnabledCapabilities();
        StringBuilder sb = new StringBuilder("LTE: ");
        sb.append("{");
        sb.append(caps.get(ImsRegistrationImplBase.REGISTRATION_TECH_LTE));
        sb.append("}, \nIWLAN: ");
        sb.append("{");
        sb.append(caps.get(ImsRegistrationImplBase.REGISTRATION_TECH_IWLAN));
        sb.append("}");
        mCapEnabledText.setText(sb.toString());
    }

    private void onCapabilitiesChangedClicked() {
        if (!isFrameworkConnected()) {
            return;
        }
        boolean isVoiceAvail = mCapVoiceAvailBox.isChecked();
        boolean isVideoAvail = mCapVideoAvailBox.isChecked();
        boolean isUtAvail = mCapUtAvailBox.isChecked();
        boolean isSmsAvail = mCapSmsAvailBox.isChecked();

        MmTelFeature.MmTelCapabilities capabilities = new MmTelFeature.MmTelCapabilities();
        if (isVoiceAvail) {
            capabilities.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VOICE);
        }
        if (isVideoAvail) {
            capabilities.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_VIDEO);
        }
        if (isUtAvail) {
            capabilities.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_UT);
        }
        if (isSmsAvail) {
            capabilities.addCapabilities(MmTelFeature.MmTelCapabilities.CAPABILITY_TYPE_SMS);
        }
        TestMmTelFeatureImpl.getInstance().sendCapabilitiesUpdate(capabilities);
    }

    private boolean isFrameworkConnected() {
        if (!TestMmTelFeatureImpl.getInstance().isReady()) {
            Toast.makeText(this, "Connection to Framework Unavailable",
                    Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }
}
