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

package com.android.car.settings.users;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.car.userlib.CarUserManagerHelper;
import android.content.pm.UserInfo;
import android.os.UserManager;
import android.widget.Button;
import android.widget.EditText;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.BaseTestActivity;
import com.android.car.settings.testutils.ShadowCarUserManagerHelper;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.Robolectric;
import org.robolectric.annotation.Config;

/**
 * Tests for EditUsernameFragment.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
@Config(shadows = {ShadowCarUserManagerHelper.class})
public class EditUsernameFragmentTest {
    private BaseTestActivity mTestActivity;

    @Mock
    private UserManager mUserManager;
    @Mock
    private CarUserManagerHelper mCarUserManagerHelper;

    @Before
    public void setUpTestActivity() {
        MockitoAnnotations.initMocks(this);
        ShadowCarUserManagerHelper.setMockInstance(mCarUserManagerHelper);
        mTestActivity = Robolectric.setupActivity(BaseTestActivity.class);
    }

    @After
    public void tearDown() {
        ShadowCarUserManagerHelper.reset();
    }

    /**
     * Tests that user name of the profile in question is displayed in the TextInputEditTest field.
     */
    @Test
    public void testUserNameDisplayedInDetails() {
        String testUserName = "test_user";
        UserInfo testUser = new UserInfo(/* id= */ 10, testUserName, /* flags= */ 0);
        createEditUsernameFragment(testUser);

        EditText userNameEditText = mTestActivity.findViewById(R.id.user_name_text_edit);
        assertThat(userNameEditText.getText().toString()).isEqualTo(testUserName);
    }

    /**
     * Tests that clicking OK saves the new name for the user.
     */
    @Test
    public void testClickingOkSavesNewUserName() {
        UserInfo testUser = new UserInfo(/* id= */ 10, "user_name", /* flags= */ 0);
        createEditUsernameFragment(testUser);
        EditText userNameEditText = mTestActivity.findViewById(R.id.user_name_text_edit);
        Button okButton = (Button) mTestActivity.findViewById(R.id.action_button2);

        String newUserName = "new_user_name";
        userNameEditText.requestFocus();
        userNameEditText.setText(newUserName);
        assertThat(okButton.isEnabled()).isTrue();
        okButton.callOnClick();

        verify(mCarUserManagerHelper).setUserName(testUser, newUserName);
    }

    /**
     * Tests that clicking Cancel brings us back to the previous fragment in activity.
     */
    @Test
    public void testClickingCancelInvokesGoingBack() {
        int userId = 10;
        UserInfo testUser = new UserInfo(userId, /* name= */ "test_user", /* flags= */ 0);
        createEditUsernameFragment(testUser);
        EditText userNameEditText = mTestActivity.findViewById(R.id.user_name_text_edit);
        Button cancelButton = (Button) mTestActivity.findViewById(R.id.action_button1);

        String newUserName = "new_user_name";
        userNameEditText.requestFocus();
        userNameEditText.setText(newUserName);

        mTestActivity.clearOnBackPressedFlag();
        cancelButton.callOnClick();

        // Back called.
        assertThat(mTestActivity.getOnBackPressedFlag()).isTrue();

        // New user name is not saved.
        verify(mUserManager, never()).setUserName(userId, newUserName);
    }

    @Test
    public void testEmptyUsernameCannotBeSaved() {
        UserInfo testUser = new UserInfo(/* id= */ 10, "user_name", /* flags= */ 0);
        createEditUsernameFragment(testUser);
        EditText userNameEditText = mTestActivity.findViewById(R.id.user_name_text_edit);
        Button okButton = (Button) mTestActivity.findViewById(R.id.action_button2);

        userNameEditText.requestFocus();
        userNameEditText.setText("");

        assertThat(okButton.isEnabled()).isFalse();
    }

    private void createEditUsernameFragment(UserInfo userInfo) {
        EditUsernameFragment fragment = EditUsernameFragment.newInstance(userInfo);
        mTestActivity.launchFragment(fragment);
    }
}
