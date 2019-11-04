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

package com.android.phone;

import static org.junit.Assert.assertEquals;

import android.content.Context;

import androidx.test.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Vector;

@RunWith(AndroidJUnit4.class)
public class CarrierXmlParserTest {
    private CarrierXmlParser mCarrierXmlParser;
    private Context mContext;

    @Before
    public void setUp() throws Exception {
        mContext = InstrumentationRegistry.getTargetContext();
    }

    @Test
    public void verifyParserFormat_shouldSameAsXml() {
        String expected =
                "((\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*"
                        + "([^*#]*)(\\*([^*#]*))?)?)?)?)?)?)?#)";

        mCarrierXmlParser = new CarrierXmlParser(mContext, -1);

        assertEquals(expected, mCarrierXmlParser.sParserFormat);
    }

    @Test
    public void verifyUssdParser_shouldMatchText() {
        String parserFormat =
                "((\\d{2,3})(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*([^*#]*)(\\*"
                        + "([^*#]*)(\\*([^*#]*))?)?)?)?)?)?)?#)";
        String text = "120*1*7*123456789*20*1*0*0#";
        String[] expectedContent = {"120*1*7*123456789*20*1*0*0#",
                "120*1*7*123456789*20*1*0*0#",
                "120",
                "*1*7*123456789*20*1*0*0",
                "1",
                "*7*123456789*20*1*0*0",
                "7",
                "*123456789*20*1*0*0",
                "123456789",
                "*20*1*0*0",
                "20",
                "*1*0*0",
                "1",
                "*0*0",
                "0",
                "*0",
                "0"};
        Vector<String> expected = new Vector<>();
        Collections.addAll(expected, expectedContent);

        CarrierXmlParser.UssdParser ussdParser = new CarrierXmlParser.UssdParser(parserFormat);
        ussdParser.newFromResponseString(text);

        assertEquals(expected, ussdParser.getResult());
    }
}
