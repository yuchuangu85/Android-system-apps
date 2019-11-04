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

package com.android.car.settings.common;

import static com.google.common.truth.Truth.assertThat;

import android.os.Bundle;

import com.android.car.settings.CarSettingsRobolectricTestRunner;
import com.android.car.settings.R;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RuntimeEnvironment;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.List;

/**
 * Unit test for {@link PreferenceXmlParser}.
 */
@RunWith(CarSettingsRobolectricTestRunner.class)
public class PreferenceXmlParserTest {

    @Test
    public void extractMetadata_keyAndControllerName() throws IOException, XmlPullParserException {
        List<Bundle> metadata = PreferenceXmlParser.extractMetadata(
                RuntimeEnvironment.application, R.xml.preference_parser,
                PreferenceXmlParser.MetadataFlag.FLAG_NEED_KEY
                        | PreferenceXmlParser.MetadataFlag.FLAG_NEED_PREF_CONTROLLER);

        assertThat(metadata).hasSize(4);
        for (Bundle bundle : metadata) {
            assertThat(bundle.getString(PreferenceXmlParser.METADATA_KEY)).isNotNull();
            assertThat(bundle.getString(PreferenceXmlParser.METADATA_CONTROLLER)).isNotNull();
        }
    }
}
