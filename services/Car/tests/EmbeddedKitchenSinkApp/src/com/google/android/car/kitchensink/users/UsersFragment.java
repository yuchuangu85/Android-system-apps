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
package com.google.android.car.kitchensink.users;

import android.annotation.Nullable;
import android.content.Context;
import android.os.Bundle;
import android.os.UserManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ListView;
import android.widget.Toast;

import androidx.fragment.app.Fragment;

import com.google.android.car.kitchensink.R;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Manipulate users in various ways
 */
public class UsersFragment extends Fragment {

    private static final List<String> CONFIGURABLE_USER_RESTRICTIONS =
            Arrays.asList(
                    UserManager.DISALLOW_ADD_USER,
                    UserManager.DISALLOW_BLUETOOTH,
                    UserManager.DISALLOW_FACTORY_RESET,
                    UserManager.DISALLOW_INSTALL_APPS,
                    UserManager.DISALLOW_MODIFY_ACCOUNTS,
                    UserManager.DISALLOW_OUTGOING_CALLS,
                    UserManager.DISALLOW_REMOVE_USER,
                    UserManager.DISALLOW_SMS,
                    UserManager.DISALLOW_UNINSTALL_APPS,
                    UserManager.DISALLOW_USER_SWITCH
            );

    @Nullable
    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.users, container, false);
    }

    public void onViewCreated(View view, Bundle savedInstanceState) {
        ListView userRestrictionsList = view.findViewById(R.id.user_restrictions_list);
        userRestrictionsList.setAdapter(
                new UserRestrictionAdapter(getContext(), createUserRestrictionItems()));

        Button applyButton = view.findViewById(R.id.apply_button);
        applyButton.setOnClickListener(v -> {
            UserRestrictionAdapter adapter =
                    (UserRestrictionAdapter) userRestrictionsList.getAdapter();
            int count = adapter.getCount();
            UserManager userManager =
                    (UserManager) getContext().getSystemService(Context.USER_SERVICE);

            // Iterate through all of the user restrictions and set their values
            for (int i = 0; i < count; i++) {
                UserRestrictionListItem item = (UserRestrictionListItem) adapter.getItem(i);
                userManager.setUserRestriction(item.getKey(), item.getIsChecked());
            }

            Toast.makeText(
                    getContext(), "User restrictions have been set!", Toast.LENGTH_SHORT)
                    .show();
        });
    }

    private List<UserRestrictionListItem> createUserRestrictionItems() {
        UserManager userManager = (UserManager) getContext().getSystemService(Context.USER_SERVICE);
        ArrayList<UserRestrictionListItem> list = new ArrayList<>();
        for (String key : CONFIGURABLE_USER_RESTRICTIONS) {
            list.add(new UserRestrictionListItem(key, userManager.hasUserRestriction(key)));
        }
        return list;
    }
}
