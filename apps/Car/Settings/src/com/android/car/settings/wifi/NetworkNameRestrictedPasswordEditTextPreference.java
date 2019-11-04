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

package com.android.car.settings.wifi;

import android.content.Context;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.widget.Toast;

import com.android.car.settings.R;
import com.android.car.settings.common.PasswordEditTextPreference;

/**
 * Custom {@link PasswordEditTextPreference} which doesn't open the password dialog unless the
 * network name is provided.
 */
public class NetworkNameRestrictedPasswordEditTextPreference extends PasswordEditTextPreference {

    private String mNetworkName;

    public NetworkNameRestrictedPasswordEditTextPreference(Context context, AttributeSet attrs,
            int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    public NetworkNameRestrictedPasswordEditTextPreference(Context context, AttributeSet attrs,
            int defStyle) {
        super(context, attrs, defStyle);
    }

    public NetworkNameRestrictedPasswordEditTextPreference(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public NetworkNameRestrictedPasswordEditTextPreference(Context context) {
        super(context);
    }

    /** Sets the network name. */
    public void setNetworkName(String name) {
        mNetworkName = name;
    }

    @Override
    protected void onClick() {
        if (TextUtils.isEmpty(mNetworkName)) {
            Toast.makeText(getContext(), R.string.wifi_no_network_name, Toast.LENGTH_SHORT).show();
            return;
        }

        super.onClick();
    }
}
