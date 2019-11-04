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

package com.android.bluetooth.a2dp;

import static org.mockito.Mockito.*;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothCodecConfig;
import android.bluetooth.BluetoothCodecStatus;
import android.bluetooth.BluetoothDevice;
import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class A2dpCodecConfigTest {
    private Context mTargetContext;
    private A2dpCodecConfig mA2dpCodecConfig;
    private BluetoothAdapter mAdapter;
    private BluetoothCodecConfig mCodecConfigSbc;
    private BluetoothCodecConfig mCodecConfigAac;
    private BluetoothCodecConfig mCodecConfigAptx;
    private BluetoothCodecConfig mCodecConfigAptxHd;
    private BluetoothCodecConfig mCodecConfigLdac;
    private BluetoothDevice mTestDevice;

    @Mock private A2dpNativeInterface mA2dpNativeInterface;

    static final int SBC_PRIORITY_DEFAULT = 1001;
    static final int AAC_PRIORITY_DEFAULT = 2001;
    static final int APTX_PRIORITY_DEFAULT = 3001;
    static final int APTX_HD_PRIORITY_DEFAULT = 4001;
    static final int LDAC_PRIORITY_DEFAULT = 5001;
    static final int PRIORITY_HIGH = 1000000;


    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        // Set up mocks and test assets
        MockitoAnnotations.initMocks(this);

        mA2dpCodecConfig = new A2dpCodecConfig(mTargetContext, mA2dpNativeInterface);
        mTestDevice = BluetoothAdapter.getDefaultAdapter().getRemoteDevice("00:01:02:03:04:05");

        doReturn(true).when(mA2dpNativeInterface).setCodecConfigPreference(
                any(BluetoothDevice.class),
                any(BluetoothCodecConfig[].class));

        // Create sample codec configs
        mCodecConfigSbc = new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
            SBC_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields
        mCodecConfigAac = new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC,
            AAC_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields
        mCodecConfigAptx = new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX,
            APTX_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields
        mCodecConfigAptxHd = new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD,
            APTX_HD_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields
        mCodecConfigLdac = new BluetoothCodecConfig(
            BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
            LDAC_PRIORITY_DEFAULT,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields

    }

    @After
    public void tearDown() throws Exception {
    }

    @Test
    public void testAssignCodecConfigPriorities() {
        BluetoothCodecConfig[] codecConfigs = mA2dpCodecConfig.codecConfigPriorities();
        for (BluetoothCodecConfig config : codecConfigs) {
            switch(config.getCodecType()) {
                case BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC:
                    Assert.assertEquals(config.getCodecPriority(), SBC_PRIORITY_DEFAULT);
                    break;
                case BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC:
                    Assert.assertEquals(config.getCodecPriority(), AAC_PRIORITY_DEFAULT);
                    break;
                case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX:
                    Assert.assertEquals(config.getCodecPriority(), APTX_PRIORITY_DEFAULT);
                    break;
                case BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD:
                    Assert.assertEquals(config.getCodecPriority(), APTX_HD_PRIORITY_DEFAULT);
                    break;
                case BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC:
                    Assert.assertEquals(config.getCodecPriority(), LDAC_PRIORITY_DEFAULT);
                    break;
            }
        }
    }

    @Test
    public void testSetCodecPreference_priorityHighToDefault() {
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, SBC_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC, AAC_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX, APTX_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD, APTX_HD_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, PRIORITY_HIGH,
                false);
    }

    @Test
    public void testSetCodecPreference_priorityDefaultToHigh() {
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, LDAC_PRIORITY_DEFAULT,
                false);
    }

    @Test
    public void testSetCodecPreference_priorityHighToHigh() {
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                false);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_AAC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_APTX_HD, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                true);
        testSetCodecPreference_codecPriorityChangeCase(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC, PRIORITY_HIGH,
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC, PRIORITY_HIGH,
                true);
    }

    @Test
    public void testSetCodecPreference_parametersChange() {
        int unSupportedParameter = 200;

        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_44100,
                BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                false);
        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_44100,
                BluetoothCodecConfig.BITS_PER_SAMPLE_24,
                true);
        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_44100,
                unSupportedParameter,
                false);

        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_48000,
                BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                true);
        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_48000,
                BluetoothCodecConfig.BITS_PER_SAMPLE_24,
                true);
        testSetCodecPreference_parametersChangeCase(
                BluetoothCodecConfig.SAMPLE_RATE_48000,
                unSupportedParameter,
                false);

        testSetCodecPreference_parametersChangeCase(
                unSupportedParameter,
                BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                false);
        testSetCodecPreference_parametersChangeCase(
                unSupportedParameter,
                BluetoothCodecConfig.BITS_PER_SAMPLE_24,
                false);
        testSetCodecPreference_parametersChangeCase(
                unSupportedParameter,
                unSupportedParameter,
                false);
    }

    @Test
    public void testDisableOptionalCodecs() {
        int verifyCount = 0;
        BluetoothCodecConfig[] codecConfigArray =
                new BluetoothCodecConfig[BluetoothCodecConfig.SOURCE_CODEC_TYPE_MAX];
        codecConfigArray[0] = new BluetoothCodecConfig(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                BluetoothCodecConfig.CODEC_PRIORITY_HIGHEST,
                BluetoothCodecConfig.SAMPLE_RATE_NONE,
                BluetoothCodecConfig.BITS_PER_SAMPLE_NONE,
                BluetoothCodecConfig.CHANNEL_MODE_NONE,
                0, 0, 0, 0);       // Codec-specific fields

        // Test don't invoke to native when current codec is SBC
        mA2dpCodecConfig.disableOptionalCodecs(mTestDevice, mCodecConfigSbc);
        verify(mA2dpNativeInterface, times(verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);

        // Test invoke to native when current codec is not SBC
        mA2dpCodecConfig.disableOptionalCodecs(mTestDevice, mCodecConfigAac);
        verify(mA2dpNativeInterface, times(++verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.disableOptionalCodecs(mTestDevice, mCodecConfigAptx);
        verify(mA2dpNativeInterface, times(++verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.disableOptionalCodecs(mTestDevice, mCodecConfigAptxHd);
        verify(mA2dpNativeInterface, times(++verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.disableOptionalCodecs(mTestDevice, mCodecConfigLdac);
        verify(mA2dpNativeInterface, times(++verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
    }

    @Test
    public void testEnableOptionalCodecs() {
        int verifyCount = 0;
        BluetoothCodecConfig[] codecConfigArray =
                new BluetoothCodecConfig[BluetoothCodecConfig.SOURCE_CODEC_TYPE_MAX];
        codecConfigArray[0] = new BluetoothCodecConfig(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_SBC,
                SBC_PRIORITY_DEFAULT,
                BluetoothCodecConfig.SAMPLE_RATE_NONE,
                BluetoothCodecConfig.BITS_PER_SAMPLE_NONE,
                BluetoothCodecConfig.CHANNEL_MODE_NONE,
                0, 0, 0, 0);       // Codec-specific fields

        // Test invoke to native when current codec is SBC
        mA2dpCodecConfig.enableOptionalCodecs(mTestDevice, mCodecConfigSbc);
        verify(mA2dpNativeInterface, times(++verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);

        // Test don't invoke to native when current codec is not SBC
        mA2dpCodecConfig.enableOptionalCodecs(mTestDevice, mCodecConfigAac);
        verify(mA2dpNativeInterface, times(verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.enableOptionalCodecs(mTestDevice, mCodecConfigAptx);
        verify(mA2dpNativeInterface, times(verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.enableOptionalCodecs(mTestDevice, mCodecConfigAptxHd);
        verify(mA2dpNativeInterface, times(verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
        mA2dpCodecConfig.enableOptionalCodecs(mTestDevice, mCodecConfigLdac);
        verify(mA2dpNativeInterface, times(verifyCount))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
    }

    private void testSetCodecPreference_parametersChangeCase(int sampleRate, int bitPerSample,
            boolean invokeNative) {
        BluetoothCodecConfig[] invalidSelectableCodecs = new BluetoothCodecConfig[1];
        invalidSelectableCodecs[0] = new BluetoothCodecConfig(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
                LDAC_PRIORITY_DEFAULT,
                (BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000),
                (BluetoothCodecConfig.BITS_PER_SAMPLE_16 | BluetoothCodecConfig.BITS_PER_SAMPLE_24),
                BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                0, 0, 0, 0);       // Codec-specific fields


        BluetoothCodecConfig[] selectableCodecs = new BluetoothCodecConfig[2];
        selectableCodecs[0] = mCodecConfigSbc;
        selectableCodecs[1] = new BluetoothCodecConfig(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
                LDAC_PRIORITY_DEFAULT,
                (BluetoothCodecConfig.SAMPLE_RATE_44100 | BluetoothCodecConfig.SAMPLE_RATE_48000),
                (BluetoothCodecConfig.BITS_PER_SAMPLE_16 | BluetoothCodecConfig.BITS_PER_SAMPLE_24),
                BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                0, 0, 0, 0);       // Codec-specific fields

        BluetoothCodecConfig[] codecConfigArray = new BluetoothCodecConfig[1];
        codecConfigArray[0] = new BluetoothCodecConfig(
                BluetoothCodecConfig.SOURCE_CODEC_TYPE_LDAC,
                LDAC_PRIORITY_DEFAULT,
                sampleRate,
                bitPerSample,
                BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                0, 0, 0, 0);       // Codec-specific fields

        BluetoothCodecStatus codecStatus = new BluetoothCodecStatus(mCodecConfigLdac,
                                                                    invalidSelectableCodecs,
                                                                    invalidSelectableCodecs);
        mA2dpCodecConfig.setCodecConfigPreference(mTestDevice, codecStatus, codecConfigArray[0]);

        codecStatus = new BluetoothCodecStatus(mCodecConfigLdac,
                                               selectableCodecs,
                                               selectableCodecs);

        mA2dpCodecConfig.setCodecConfigPreference(mTestDevice, codecStatus, codecConfigArray[0]);
        verify(mA2dpNativeInterface, times(invokeNative ? 1 : 0))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);

    }

    private void testSetCodecPreference_codecPriorityChangeCase(int newCodecType,
            int newCodecPriority, int oldCodecType, int oldCodecPriority, boolean invokeNative) {
        BluetoothCodecConfig[] selectableCodecs = new BluetoothCodecConfig[5];
        selectableCodecs[0] = mCodecConfigSbc;
        selectableCodecs[1] = mCodecConfigAac;
        selectableCodecs[2] = mCodecConfigAptx;
        selectableCodecs[3] = mCodecConfigAptxHd;
        selectableCodecs[4] = mCodecConfigLdac;

        BluetoothCodecConfig[] invalidSelectableCodecs = new BluetoothCodecConfig[4];
        invalidSelectableCodecs[0] = mCodecConfigAac;
        invalidSelectableCodecs[1] = mCodecConfigAptx;
        invalidSelectableCodecs[2] = mCodecConfigAptxHd;
        invalidSelectableCodecs[3] = mCodecConfigLdac;

        BluetoothCodecConfig oldCodecConfig =  new BluetoothCodecConfig(
                oldCodecType,
                oldCodecPriority,
                BluetoothCodecConfig.SAMPLE_RATE_44100,
                BluetoothCodecConfig.BITS_PER_SAMPLE_16,
                BluetoothCodecConfig.CHANNEL_MODE_STEREO,
                0, 0, 0, 0);       // Codec-specific fields

        BluetoothCodecConfig[] codecConfigArray = new BluetoothCodecConfig[1];
        codecConfigArray[0] = new BluetoothCodecConfig(
            newCodecType,
            newCodecPriority,
            BluetoothCodecConfig.SAMPLE_RATE_44100,
            BluetoothCodecConfig.BITS_PER_SAMPLE_16,
            BluetoothCodecConfig.CHANNEL_MODE_STEREO,
            0, 0, 0, 0);       // Codec-specific fields

        BluetoothCodecStatus codecStatus = new BluetoothCodecStatus(oldCodecConfig,
                                                                    invalidSelectableCodecs,
                                                                    invalidSelectableCodecs);
        mA2dpCodecConfig.setCodecConfigPreference(mTestDevice, codecStatus, codecConfigArray[0]);

        codecStatus = new BluetoothCodecStatus(oldCodecConfig,
                                               selectableCodecs,
                                               selectableCodecs);

        mA2dpCodecConfig.setCodecConfigPreference(mTestDevice, codecStatus, codecConfigArray[0]);
        verify(mA2dpNativeInterface, times(invokeNative ? 1 : 0))
                .setCodecConfigPreference(mTestDevice, codecConfigArray);
    }
}
