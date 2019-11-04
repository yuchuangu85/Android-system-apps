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
package com.android.car;

import static com.android.car.CarUxRestrictionsManagerService.CONFIG_FILENAME_PRODUCTION;
import static com.android.car.CarUxRestrictionsManagerService.CONFIG_FILENAME_STAGED;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.Builder;
import android.car.hardware.CarPropertyValue;
import android.content.Context;
import android.content.res.Resources;
import android.hardware.automotive.vehicle.V2_0.VehicleProperty;
import android.os.SystemClock;
import android.util.JsonReader;
import android.util.JsonWriter;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.car.systeminterface.SystemInterface;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.xmlpull.v1.XmlPullParserException;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;

@RunWith(AndroidJUnit4.class)
@MediumTest
public class CarUxRestrictionsManagerServiceTest {
    private CarUxRestrictionsManagerService mService;

    @Mock
    private CarDrivingStateService mMockDrivingStateService;
    @Mock
    private CarPropertyService mMockCarPropertyService;
    @Mock
    private SystemInterface mMockSystemInterface;

    private Context mSpyContext;

    private File mTempSystemCarDir;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        // Spy context because service needs to access xml resource during init.
        mSpyContext = spy(InstrumentationRegistry.getTargetContext());

        CarLocalServices.removeServiceForTest(SystemInterface.class);
        CarLocalServices.addService(SystemInterface.class, mMockSystemInterface);

        mTempSystemCarDir = Files.createTempDirectory("uxr_test").toFile();
        when(mMockSystemInterface.getSystemCarDir()).thenReturn(mTempSystemCarDir);

        setUpMockParkedState();

        mService = new CarUxRestrictionsManagerService(mSpyContext,
                mMockDrivingStateService, mMockCarPropertyService);
    }

    @After
    public void tearDown() throws Exception {
        mService = null;
        CarLocalServices.removeAllServices();
    }

    @Test
    public void testSaveConfig_WriteStagedFile() throws Exception {
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, null);
        CarUxRestrictionsConfiguration config = createEmptyConfig();

        assertTrue(mService.saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(config)));
        assertTrue(readFile(staged).equals(config));
        // Verify prod config file was not created.
        assertFalse(new File(mTempSystemCarDir, CONFIG_FILENAME_PRODUCTION).exists());
    }

    @Test
    public void testSaveConfig_ReturnFalseOnException() throws Exception {
        CarUxRestrictionsConfiguration spyConfig = spy(createEmptyConfig());
        doThrow(new IOException()).when(spyConfig).writeJson(any(JsonWriter.class));

        assertFalse(mService.saveUxRestrictionsConfigurationForNextBoot(Arrays.asList(spyConfig)));
    }

    @Test
    public void testLoadConfig_UseDefaultConfigWhenNoSavedConfigFileNoXml() {
        // Prevent R.xml.car_ux_restrictions_map being returned.
        Resources spyResources = spy(mSpyContext.getResources());
        doReturn(spyResources).when(mSpyContext).getResources();
        doReturn(null).when(spyResources).getXml(anyInt());

        for (CarUxRestrictionsConfiguration config : mService.loadConfig()) {
            assertTrue(config.equals(mService.createDefaultConfig(config.getPhysicalPort())));
        }
    }

    @Test
    public void testLoadConfig_UseXml() throws IOException, XmlPullParserException {
        List<CarUxRestrictionsConfiguration> expected =
                CarUxRestrictionsConfigurationXmlParser.parse(
                        mSpyContext, R.xml.car_ux_restrictions_map);

        List<CarUxRestrictionsConfiguration> actual = mService.loadConfig();

        assertTrue(actual.equals(expected));
    }

    @Test
    public void testLoadConfig_UseProdConfig() throws IOException {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        setupMockFile(CONFIG_FILENAME_PRODUCTION, expected);

        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        assertTrue(actual.equals(expected));
    }

    @Test
    public void testLoadConfig_PromoteStagedFileWhenParked() throws Exception {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        // Staged file contains actual config. Ignore prod since it should be overwritten by staged.
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, expected);
        // Set up temp file for prod to avoid polluting other tests.
        setupMockFile(CONFIG_FILENAME_PRODUCTION, null);

        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        // Staged file should be moved as production.
        assertFalse(staged.exists());
        assertTrue(actual.equals(expected));
    }

    @Test
    public void testLoadConfig_NoPromoteStagedFileWhenMoving() throws Exception {
        CarUxRestrictionsConfiguration expected = createEmptyConfig();
        File staged = setupMockFile(CONFIG_FILENAME_STAGED, null);
        // Prod file contains actual config. Ignore staged since it should not be promoted.
        setupMockFile(CONFIG_FILENAME_PRODUCTION, expected);

        setUpMockDrivingState();
        CarUxRestrictionsConfiguration actual = mService.loadConfig().get(0);

        // Staged file should be untouched.
        assertTrue(staged.exists());
        assertTrue(actual.equals(expected));
    }

    @Test
    public void testValidateConfigs_SingleConfigNotSetPort() throws Exception {
        CarUxRestrictionsConfiguration config = createEmptyConfig();
        mService.validateConfigs(Arrays.asList(config));
    }

    @Test
    public void testValidateConfigs_SingleConfigWithPort() throws Exception {
        CarUxRestrictionsConfiguration config = createEmptyConfig((byte) 0);
        mService.validateConfigs(Arrays.asList(config));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateConfigs_MultipleConfigsMustHavePort() throws Exception {
        mService.validateConfigs(Arrays.asList(createEmptyConfig(null), createEmptyConfig(null)));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidateConfigs_MultipleConfigsMustHaveUniquePort() throws Exception {
        mService.validateConfigs(Arrays.asList(
                createEmptyConfig((byte) 0), createEmptyConfig((byte) 0)));
    }

    @Test
    public void testGetCurrentUxRestrictions_UnknownDisplayId_ReturnsFullRestrictions()
            throws Exception {
        mService.init();
        CarUxRestrictions restrictions = mService.getCurrentUxRestrictions(/* displayId= */ 10);
        CarUxRestrictions expected = new CarUxRestrictions.Builder(
                /*reqOpt= */ true,
                CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED,
                SystemClock.elapsedRealtimeNanos()).build();
        assertTrue(restrictions.toString(), expected.isSameRestrictions(restrictions));
    }

    private CarUxRestrictionsConfiguration createEmptyConfig() {
        return createEmptyConfig(null);
    }

    private CarUxRestrictionsConfiguration createEmptyConfig(Byte port) {
        Builder builder = new Builder();
        if (port != null) {
            builder.setPhysicalPort(port);
        }
        return builder.build();
    }

    private void setUpMockParkedState() {
        when(mMockDrivingStateService.getCurrentDrivingState()).thenReturn(
                new CarDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_PARKED, 0));

        CarPropertyValue<Float> speed = new CarPropertyValue<>(VehicleProperty.PERF_VEHICLE_SPEED,
                0, 0f);
        when(mMockCarPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED, 0))
                .thenReturn(speed);
    }

    private void setUpMockDrivingState() {
        when(mMockDrivingStateService.getCurrentDrivingState()).thenReturn(
                new CarDrivingStateEvent(CarDrivingStateEvent.DRIVING_STATE_MOVING, 0));

        CarPropertyValue<Float> speed = new CarPropertyValue<>(VehicleProperty.PERF_VEHICLE_SPEED,
                0, 30f);
        when(mMockCarPropertyService.getProperty(VehicleProperty.PERF_VEHICLE_SPEED, 0))
                .thenReturn(speed);
    }

    private File setupMockFile(String filename, CarUxRestrictionsConfiguration config)
            throws IOException {
        File f = new File(mTempSystemCarDir, filename);
        assertTrue(f.createNewFile());

        if (config != null) {
            try (JsonWriter writer = new JsonWriter(
                    new OutputStreamWriter(new FileOutputStream(f), "UTF-8"))) {
                writer.beginArray();
                config.writeJson(writer);
                writer.endArray();
            }
        }
        return f;
    }

    private CarUxRestrictionsConfiguration readFile(File file) throws Exception {
        try (JsonReader reader = new JsonReader(
                new InputStreamReader(new FileInputStream(file), "UTF-8"))) {
            CarUxRestrictionsConfiguration config = null;
            reader.beginArray();
            config = CarUxRestrictionsConfiguration.readJson(reader);
            reader.endArray();
            return config;
        }
    }
}
