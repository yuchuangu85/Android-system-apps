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
package com.android.emergency.preferences;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.os.UserManager;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import com.android.emergency.R;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Answers;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class EmergencyNamePreferenceTest {

    private final Bitmap mBitmap = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
    @Mock
    private UserManager mUserManager;
    @Mock
    private Dialog mDialog;
    private Context mContext;
    private EditText mNameView;
    private ImageView mIconView;
    private TestEmergencyNamePreference mTestEmergencyNamePreference;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        when(mContext.getSystemService(UserManager.class)).thenReturn(mUserManager);
        when(mUserManager.getUserIcon(0)).thenReturn(mBitmap);

        mTestEmergencyNamePreference = spy(new TestEmergencyNamePreference(mContext));
    }

    @Test
    public void emergencyNamePreference_setDefaultProperties_defaultPropertiesIsSet() {
        assertThat(mTestEmergencyNamePreference.isEnabled()).isTrue();
        assertThat(mTestEmergencyNamePreference.isSelectable()).isTrue();
    }


    @Test
    public void loadDataFromUserManager_setUserNameAndPhoto_preferenceSummaryAndIconIsSet() {
        assertThat(mTestEmergencyNamePreference.getSummary()).isNull();
        assertThat(mTestEmergencyNamePreference.getIcon()).isNull();

        when(mUserManager.getUserName()).thenReturn("Wesley");
        mTestEmergencyNamePreference.reloadFromUserManager();

        assertThat(mTestEmergencyNamePreference.getSummary()).isEqualTo(mUserManager.getUserName());
        assertThat(mTestEmergencyNamePreference.getIcon()).isNotNull();
    }

    @Test
    public void onBindDialogView_setUserName_textViewTitleIsSet() {
        View view = createLayout();
        when(mUserManager.getUserName()).thenReturn("Wesley");

        mTestEmergencyNamePreference.onBindDialogView(view);

        TextView textView = view.findViewById(R.id.user_name);
        assertThat(textView.getText().toString()).isEqualTo(mUserManager.getUserName());
    }

    @Test
    public void onBindDialogView_setUserPhoto_imageViewDrawableIsSet() {
        View view = createLayout();

        mTestEmergencyNamePreference.onBindDialogView(view);

        ImageView imageView = view.findViewById(R.id.user_photo);
        assertThat(imageView.getDrawable()).isNotNull();
    }

    @Test
    public void onBindDialogView_setEditUserPhotoController_editUserPhotoControllerIsSet() {
        View view = createLayout();

        EditUserPhotoController photoController = mTestEmergencyNamePreference.getPhotoController();
        assertThat(photoController).isNull();

        mTestEmergencyNamePreference.onBindDialogView(view);

        photoController = mTestEmergencyNamePreference.getPhotoController();
        assertThat(photoController).isNotNull();
    }

    @Test
    public void onActivityResult_whenWaitingForActivityResult_onActivityResultIsCalled() {
        View view = createLayout();

        mTestEmergencyNamePreference.onBindDialogView(view);
        doReturn(mDialog).when(mTestEmergencyNamePreference).getDialog();
        mTestEmergencyNamePreference.startingActivityForResult();
        Intent resultData = new Intent();
        mTestEmergencyNamePreference.onActivityResult(0, 0, resultData);
        EditUserPhotoController photoController = mTestEmergencyNamePreference.getPhotoController();
        verify(photoController).onActivityResult(eq(0), eq(0), same(resultData));
    }

    private ViewGroup createLayout() {
        ViewGroup root = new LinearLayout(mContext);

        mNameView = new EditText(mContext);
        mNameView.setId(R.id.user_name);
        root.addView(mNameView);

        mIconView = new ImageView(mContext);
        mIconView.setId(R.id.user_photo);
        root.addView(mIconView);

        return root;
    }

    public class TestEmergencyNamePreference extends EmergencyNamePreference {

        private EditUserPhotoController mPhotoController;

        public TestEmergencyNamePreference(Context context) {
            super(context, null);
            setSummary(mUserManager.getUserName());
            setIcon(null);
        }

        public EditUserPhotoController getPhotoController() {
            return mPhotoController;
        }

        @Override
        protected EditUserPhotoController createEditUserPhotoController(ImageView userPhotoView,
            Drawable drawable) {
            mPhotoController = mock(EditUserPhotoController.class, Answers.RETURNS_DEEP_STUBS);
            return mPhotoController;
        }
    }
}
