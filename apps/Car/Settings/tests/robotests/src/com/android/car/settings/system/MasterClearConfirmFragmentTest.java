/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.system;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.provider.Settings;
import android.service.oemlock.OemLockManager;
import android.service.persistentdata.PersistentDataBlockManager;
import android.widget.Button;

import androidx.preference.PreferenceManager;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;
import com.android.car.settings.testutils.FragmentController;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.shadows.ShadowApplication;

/** Unit test for {@link MasterClearConfirmFragment}. */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class MasterClearConfirmFragmentTest {

    @Mock
    private PersistentDataBlockManager mPersistentDataBlockManager;
    @Mock
    private OemLockManager mOemLockManager;

    private Context mContext;
    private MasterClearConfirmFragment mFragment;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = RuntimeEnvironment.application;
        // Note: this is not a general pattern to follow for other use cases! Most system
        // services can be mocked using Shadows!
        // Because these services are conditionally included in Android's SystemServiceRegistry
        // and are not created in ShadowServiceManager, there is no way to conventionally shadow
        // them in Robolectric.
        ShadowApplication.getInstance().setSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE,
                mPersistentDataBlockManager);
        ShadowApplication.getInstance().setSystemService(Context.OEM_LOCK_SERVICE, mOemLockManager);

        mFragment = FragmentController.of(new MasterClearConfirmFragment()).setup();

        // Default to not provisioned.
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                0);
    }

    @After
    public void tearDown() {
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                0);
    }

    @Test
    public void confirmClicked_sendsResetIntent() {
        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
        assertThat(resetIntent.getPackage()).isEqualTo("android");
        assertThat(resetIntent.getFlags() & Intent.FLAG_RECEIVER_FOREGROUND).isEqualTo(
                Intent.FLAG_RECEIVER_FOREGROUND);
        assertThat(resetIntent.getExtras().getString(Intent.EXTRA_REASON)).isEqualTo(
                "MasterClearConfirm");
    }

    @Test
    public void confirmClicked_resetEsimFalse_resetIntentReflectsChoice() {
        PreferenceManager.getDefaultSharedPreferences(mContext).edit().putBoolean(
                mContext.getString(R.string.pk_master_clear_reset_esim), false).commit();

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getExtras().getBoolean(Intent.EXTRA_WIPE_ESIMS)).isEqualTo(false);
    }

    @Test
    public void confirmClicked_pdbManagerNull_sendsResetIntent() {
        ShadowApplication.getInstance().removeSystemService(Context.PERSISTENT_DATA_BLOCK_SERVICE);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_oemUnlockAllowed_doesNotWipePdb() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(true);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        verify(mPersistentDataBlockManager, never()).wipe();
    }

    @Test
    public void confirmClicked_oemUnlockAllowed_sendsResetIntent() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(true);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_notProvisioned_doesNotWipePdb() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        verify(mPersistentDataBlockManager, never()).wipe();
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_notProvisioned_sendsResetIntent() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_provisioned_wipesPdb() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        verify(mPersistentDataBlockManager).wipe();
    }

    @Test
    public void confirmClicked_noOemUnlockAllowed_provisioned_sendsResetIntent() {
        when(mOemLockManager.isOemUnlockAllowed()).thenReturn(false);
        Settings.Global.putInt(mContext.getContentResolver(), Settings.Global.DEVICE_PROVISIONED,
                1);

        findMasterClearConfirmButton(mFragment.requireActivity()).performClick();

        Intent resetIntent = ShadowApplication.getInstance().getBroadcastIntents().get(0);
        assertThat(resetIntent.getAction()).isEqualTo(Intent.ACTION_FACTORY_RESET);
    }

    private Button findMasterClearConfirmButton(Activity activity) {
        return activity.findViewById(R.id.action_button1);
    }
}
