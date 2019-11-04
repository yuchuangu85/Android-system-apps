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

import android.car.userlib.CarUserManagerHelper;
import android.content.Context;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.SettingsFragment;

/**
 * Lists the user's accounts and any related options.
 */
public class AccountSettingsFragment extends SettingsFragment {
    @Override
    @XmlRes
    protected int getPreferenceScreenResId() {
        return R.xml.account_settings_fragment;
    }

    @Override
    @LayoutRes
    protected int getActionBarLayoutId() {
        return R.layout.action_bar_with_button;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        // Enable the add account button if the user is allowed to modify accounts
        Button addAccountButton = requireActivity().findViewById(R.id.action_button1);
        if (new CarUserManagerHelper(getContext()).canCurrentProcessModifyAccounts()) {
            addAccountButton.setText(R.string.user_add_account_menu);
            addAccountButton.setOnClickListener(v -> onAddAccountClicked());
        } else {
            addAccountButton.setVisibility(View.GONE);
        }
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);

        String[] authorities = getActivity().getIntent().getStringArrayExtra(
                Settings.EXTRA_AUTHORITIES);
        if (authorities != null) {
            use(AccountListPreferenceController.class, R.string.pk_account_list)
                    .setAuthorities(authorities);
        }
    }

    private void onAddAccountClicked() {
        launchFragment(new ChooseAccountFragment());
    }
}
