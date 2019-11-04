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

package com.android.phone;

import android.content.Context;
import android.content.res.XmlResourceParser;
import android.support.annotation.VisibleForTesting;
import android.telephony.TelephonyManager;
import android.text.TextUtils;
import android.util.Log;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * CarrierXmlParser is a xml parser. It parses the carrier's ussd format from carrier_ss_string.xml.
 * The carrier_ss_string.xml defines carrier's ussd structure and meaning in res/xml folder.
 * Some carrier has specific ussd structure ,developer can add new xml and xml is named
 * carrier_ss_string_carrierId.xml. The carrierId is a number and is defined in
 * <a href="https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/master/assets/carrier_list.textpb">here</a>
 * For example: carrier_ss_string_850.xml
 * <p>
 * How do use CarrierXmlParser?
 * For example:
 * @see CallForwardEditPreference
 *     TelephonyManager telephonyManager = new TelephonyManager(getContext(),phone.getSubId());
 *     CarrierXmlParser  = new CarrierXmlParser(getContext(), telephonyManager.getSimCarrierId());
 *
 *     //make a ussd command
 *     String newUssdCommand = mCarrierXmlParser.getFeature(
 *             CarrierXmlParser.FEATURE_CALL_FORWARDING).makeCommand(inputAction, inputCfInfo);
 *     //analyze ussd result
 *     HashMap<String, String> analysisResult = mCarrierXmlParser.getFeature(
 *             CarrierXmlParser.FEATURE_CALL_FORWARDING)
 *             .getResponseSet(mSsAction, response.toString());
 */
public class CarrierXmlParser {
    public static final String LOG_TAG = "CarrierXmlParser";
    private static final boolean DEBUG = true;

    private static final String STAR_SIGN = "*";
    private static final String POUND_SIGN = "#";

    private static final String TAG_SIGN = "tag_";

    // To define feature's item name in xml
    public static final String FEATURE_CALL_FORWARDING = "callforwarding";
    public static final String FEATURE_CALLER_ID = "callerid";

    // COMMAND_NAME is xml's command name.
    public static final String TAG_COMMAND_NAME_QUERY = "query";
    public static final String TAG_COMMAND_NAME_ACTIVATE = "activate";
    public static final String TAG_COMMAND_NAME_DEACTIVATE = "deactivate";

    // To define string level in xml.
    // level 1
    private static final String TAG_FEATURE = "feature";
    private static final String TAG_REGULAR_PARSER = "regular_parser";
    // level 2
    private static final String TAG_COMMAND = "command";
    // level 3
    private static final String TAG_SERVICE_CODE = "service_code";
    private static final String TAG_ACTION_CODE = "action_code";
    private static final String TAG_PARAMETER = "parameter";
    private static final String TAG_RESPONSE_FORMAT = "response_format";
    private static final String TAG_COMMAND_RESULT = "command_result";
    // level 4
    private static final String TAG_ENTRY = "entry";

    private static final String ATTR_NAME = "name";
    private static final String ATTR_PARAMETER_NUM = "number";
    private static final String ATTR_POSITION = "position";
    private static final String ATTR_RESULT_KEY = "key";
    private static final String ATTR_DEFINITION_KEY = "definition";

    HashMap<String, SsFeature> mFeatureMaps;

    @VisibleForTesting(otherwise = VisibleForTesting.PRIVATE)
    static String sParserFormat = "";

    // TAG_ENTRY_NUMBER and TAG_ENTRY_TIME is xml's entry value.
    // This is mapping user's input value. For example: number,time ...
    // When UI makes command ,it will map the value and insert this value at position location.
    // How to use it?
    //      The UI calls CarrierXmlParser's makeCommand function and inputs the hashmap which
    //      includes tag_name and user's input value.
    //      For example: User calls CarrierXmlParser's makeCommand in call forwarding , and inputs
    //      the hashmap {<TAG_ENTRY_NUMBER,0123456789>,<TAG_ENTRY_TIME,20>}
    // If developer wants to add new one, xml string should the same as hashmap's name.
    public static final String TAG_ENTRY_NUMBER = "tag_number";
    public static final String TAG_ENTRY_TIME = "tag_time";

    // "response_format" key
    // The key of "response_format" should define as below in xml.
    // The UI will use it to define value from the response command
    // and use the data show the screen.
    public static final String TAG_RESPONSE_STATUS = "status_code";
    public static final String TAG_RESPONSE_STATUS_ERROR = "RESPONSE_ERROR";
    public static final String TAG_RESPONSE_NUMBER = "number";
    public static final String TAG_RESPONSE_TIME = "time";

    // This is the definition for the entry's key in response_format.
    // Xml's COMMAND_RESULT_DEFINITION should same as below.
    public static final String TAG_COMMAND_RESULT_DEFINITION_ACTIVATE = "activate";
    public static final String TAG_COMMAND_RESULT_DEFINITION_DEACTIVATE = "deactivate";
    public static final String TAG_COMMAND_RESULT_DEFINITION_UNREGISTER = "unregister";
    public static final String TAG_COMMAND_RESULT_DEFINITION_OK = "ok";
    public static final String TAG_COMMAND_RESULT_DEFINITION_FAIL = "fail";

    /**
     * UssdParser is a string parser. It parses the USSD response message.
     */
    public static class UssdParser {
        private Vector<String> mParserStr = new Vector<String>();
        private Pattern mPatternSuppServiceResponse;

        public UssdParser(String inputParserFormat) {
            mPatternSuppServiceResponse = Pattern.compile(inputParserFormat);
        }

        /**
         * This function is a parser and analyzes the USSD responses message.
         *
         * @param responseString The USSD responses message.
         */
        public void newFromResponseString(String responseString) {
            Matcher m;
            m = mPatternSuppServiceResponse.matcher(responseString);
            if (m.matches()) {
                mParserStr.clear();
                int groupSize = m.groupCount();
                for (int i = 0; i <= groupSize; i++) {
                    if (!TextUtils.isEmpty(m.group(i))) {
                        mParserStr.add(m.group(i));
                    } else {
                        mParserStr.add("");
                    }
                }
            } else {
                Log.d(LOG_TAG, "no match");
            }
        }

        /**
         * To get the UssdParser result.
         */
        public Vector<String> getResult() {
            return mParserStr;
        }
    }

    /**
     * CarrierXmlParser parses command from xml and saves in SsEntry class.
     */
    public static class SsEntry {
        public enum SSAction {
            UNKNOWN,
            QUERY,
            UPDATE_ACTIVATE,
            UPDATE_DEACTIVATE
        }

        public String serviceCode;
        public SSAction ssAction = SSAction.UNKNOWN;
        public String actionCode;
        public HashMap<Integer, String> commandParameter = new HashMap<Integer, String>();
        public HashMap<Integer, String> responseFormat = new HashMap<Integer, String>();

        public SsEntry(String action) {
            if (action.equals(TAG_COMMAND_NAME_QUERY)) {
                ssAction = SSAction.QUERY;
            } else if (action.equals(TAG_COMMAND_NAME_ACTIVATE)) {
                ssAction = SSAction.UPDATE_ACTIVATE;
            } else if (action.equals(TAG_COMMAND_NAME_DEACTIVATE)) {
                ssAction = SSAction.UPDATE_DEACTIVATE;
            }
        }

        @Override
        public String toString() {
            return "SsEntry serviceCode:" + serviceCode
                    + ", ssAction:" + ssAction
                    + ", actionCode:" + actionCode
                    + ", commandParameter:" + commandParameter.toString()
                    + ", responseFormat:" + responseFormat.toString();
        }

        /**
         * To get the caller id command by xml's structure.
         */
        public String getCommandStructure() {
            String result = actionCode + serviceCode;
            int mapSize = commandParameter.size();
            int parameterIndex = 0;
            while (parameterIndex < mapSize) {
                parameterIndex++;
                if (commandParameter.containsKey(parameterIndex)) {
                    if (commandParameter.get(parameterIndex) != null) {
                        result = result + STAR_SIGN + commandParameter.get(parameterIndex);
                    }
                }
            }
            result = result + POUND_SIGN;
            Log.d(LOG_TAG, "getCommandStructure result:" + result);
            return result;
        }

        /**
         * To make ussd command by xml's structure.
         *
         * @param inputInformationSet This is a map which includes parameters from UI.
         *                            The name of map is mapping parameter's key of entry in xml.
         */
        public String makeCommand(Map<String, String> inputInformationSet) {
            String result = actionCode + serviceCode;
            int mapSize = commandParameter.size();
            int parameterIndex = 0;
            int counter = 1;
            Map<String, String> informationSet = inputInformationSet;
            while (parameterIndex < mapSize) {
                if (commandParameter.containsKey(counter)) {
                    String getInputValue = "";
                    // need to handle tag_XXXX
                    if (informationSet != null && informationSet.size() > 0
                            && informationSet.containsKey(commandParameter.get(counter))) {
                        getInputValue = informationSet.get(commandParameter.get(counter));
                    }
                    if (TextUtils.isEmpty(getInputValue)) {
                        result = result + STAR_SIGN + commandParameter.get(counter);
                    } else {
                        result = result + STAR_SIGN + informationSet.get(
                                commandParameter.get(counter));
                    }
                    parameterIndex++;
                } else {
                    result = result + STAR_SIGN;
                }
                counter++;
            }
            result = result + POUND_SIGN;
            return result;
        }

        /**
         * To parse the specific key and value from response message.
         *
         * @param inputResponse  This is a ussd response message from network.
         * @param responseDefine This is the definition for "command_result" in xml.
         */
        public HashMap<String, String> getResponseSet(String inputResponse,
                HashMap<String, ArrayList<SsResultEntry>> responseDefine) {
            HashMap<String, String> responseSet = new HashMap<String, String>();
            if (TextUtils.isEmpty(sParserFormat)) {
                return responseSet;
            }
            UssdParser parserResult = new UssdParser(sParserFormat);
            parserResult.newFromResponseString(inputResponse);
            if (parserResult == null) {
                return responseSet;
            }

            Vector<String> result = parserResult.getResult();

            if (result == null) {
                return responseSet;
            }
            for (int i = 0; i < result.size(); i++) {
                if (responseFormat.containsKey(i)) {
                    String defineString = "";
                    if (responseDefine.containsKey(responseFormat.get(i))) {
                        for (int x = 0; x < responseDefine.get(responseFormat.get(i)).size(); x++) {
                            defineString = ((SsResultEntry) responseDefine.get(
                                    responseFormat.get(i)).get(x)).getDefinitionByCompareValue(
                                    result.get(i));
                            if (!TextUtils.isEmpty(defineString)) {
                                break;
                            }
                        }
                        // if status_code do not match definition value, we will set command error.
                        if (TAG_RESPONSE_STATUS.equals(responseFormat.get(i))) {
                            if (TextUtils.isEmpty(defineString)) {
                                responseSet.put(TAG_RESPONSE_STATUS_ERROR,
                                        TAG_RESPONSE_STATUS_ERROR);
                            }
                        }
                    }
                    if (TextUtils.isEmpty(defineString)) {
                        responseSet.put(responseFormat.get(i), result.get(i));
                    } else {
                        responseSet.put(responseFormat.get(i), defineString);
                    }
                }
            }
            return responseSet;
        }
    }

    /**
     * CarrierXmlParser parses command_result from xml and saves in SsResultEntry class.
     */
    public static class SsResultEntry {
        String mDefinition;
        String mCompareValue;

        public SsResultEntry() {
        }

        @Override
        public String toString() {
            return "SsResultEntry mDefinition:" + mDefinition
                    + ", mCompareValue:" + mCompareValue;
        }

        /**
         * If mCompareValue item is the same as compare value,it will return the mDefinition.
         *
         * @param inputValue This is the entry of response command's value.
         * @return mDefinition or null.
         */
        public String getDefinitionByCompareValue(String inputValue) {
            if (mCompareValue.equals(inputValue)) {
                return mDefinition;
            }
            return null;
        }
    }

    /**
     * CarrierXmlParser parses feature from xml and saves in SsFeature class.
     */
    public class SsFeature {
        public HashMap<SsEntry.SSAction, SsEntry> ssEntryHashMap =
                new HashMap<SsEntry.SSAction, SsEntry>();
        public HashMap<String, ArrayList<SsResultEntry>> responseCode =
                new HashMap<String, ArrayList<SsResultEntry>>();

        public SsFeature() {
        }

        private String getResponseCodeString() {
            String result = "";
            for (Map.Entry<String, ArrayList<SsResultEntry>> entry : responseCode.entrySet()) {
                ArrayList<SsResultEntry> values = entry.getValue();
                for (int i = 0; i < values.size(); i++) {
                    result += "value of i is " + ((SsResultEntry) values.get(i)).toString();
                }
            }
            return result;
        }

        @Override
        public String toString() {
            return getResponseCodeString();
        }

        /**
         * To get the caller id command by xml's structure.
         *
         * @param inputAction This is action_code of command item from xml.
         */
        public String getCommandStructure(SsEntry.SSAction inputAction) {
            SsEntry entry = ssEntryHashMap.get(inputAction);
            return entry.getCommandStructure();
        }

        /**
         * To make the ussd command by xml structure
         *
         * @param inputAction         This is action_code of command item from xml.
         * @param inputInformationSet This is for parameter of command.
         * @return The ussd command string.
         */
        public String makeCommand(SsEntry.SSAction inputAction,
                Map<String, String> inputInformationSet) {
            SsEntry entry = ssEntryHashMap.get(inputAction);
            return entry.makeCommand(inputInformationSet);
        }

        /**
         * To parse the special key and value from response message.
         *
         * @param inputAction   This is action_code of command item from xml.
         * @param inputResponse This is response message from network.
         * @return The set includes specific key and value.
         */
        public HashMap<String, String> getResponseSet(SsEntry.SSAction inputAction,
                String inputResponse) {
            SsEntry entry = ssEntryHashMap.get(inputAction);
            return entry.getResponseSet(inputResponse, responseCode);
        }
    }

    /**
     * @param context context to get res's xml
     * @param carrierId carrier id of the current subscription. The carrier ID is an Android
     * platform-wide identifier for a carrier. AOSP maintains carrier ID assignments in
     * <a href="https://android.googlesource.com/platform/packages/providers/TelephonyProvider/+/master/assets/carrier_list.textpb">here</a>
     */
    public CarrierXmlParser(Context context, int carrierId) {
        try {
            int xmlResId = 0;
            if (carrierId != TelephonyManager.UNKNOWN_CARRIER_ID) {
                String xmlResIdName = "carrier_ss_string" + "_" + carrierId;
                xmlResId = context.getResources().getIdentifier(xmlResIdName, "xml",
                        context.getPackageName());
            }
            if (xmlResId == 0) {
                xmlResId = R.xml.carrier_ss_string;
            }
            Log.d(LOG_TAG, "carrierId: " + carrierId);

            XmlResourceParser parser = context.getResources().getXml(xmlResId);
            mFeatureMaps = parseXml(parser);
        } catch (Exception e) {
            Log.d(LOG_TAG, "Error parsing XML " + e.toString());
        }
    }

    private HashMap<String, SsFeature> parseXml(XmlResourceParser parser) throws IOException {
        HashMap<String, SsFeature> features = new HashMap<String, SsFeature>();
        try {
            int eventType = parser.getEventType();
            while (eventType != XmlPullParser.END_DOCUMENT) {
                if (eventType == XmlPullParser.START_TAG) {
                    if (TAG_REGULAR_PARSER.equals(parser.getName())) {
                        sParserFormat = readText(parser);
                        Log.d(LOG_TAG, "sParserFormat " + sParserFormat);
                    } else if (TAG_FEATURE.equals(parser.getName())) {
                        String featureName = getSpecificAttributeValue(parser, ATTR_NAME);
                        if (!TextUtils.isEmpty(featureName)) {
                            SsFeature feature = generateFeatureList(parser);
                            features.put(featureName, feature);
                            Log.d(LOG_TAG, "add " + featureName + " to map:" + feature.toString());
                        }
                    }
                }
                parser.next();
                eventType = parser.getEventType();
            }
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        }
        return features;
    }

    private SsFeature generateFeatureList(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        SsFeature ssfeature = new SsFeature();
        int outerDepth = parser.getDepth();

        Log.d(LOG_TAG, "generateFeatureList outerDepth" + outerDepth);

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            Log.d(LOG_TAG, "generateFeatureList parser.getDepth()" + parser.getDepth());

            int eventType = parser.getEventType();
            if (eventType == XmlPullParser.END_TAG
                    && outerDepth == parser.getDepth()) {
                break;
            }

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }
            String name = parser.getName();
            // Starts by looking for the command tag.
            if (TAG_COMMAND.equals(name)) {
                SsEntry entry = readCommandEntry(parser);
                ssfeature.ssEntryHashMap.put(entry.ssAction, entry);
            } else if (TAG_COMMAND_RESULT.equals(name)) {
                readCommandResultEntry(parser, ssfeature);
            }
        }
        return ssfeature;
    }

    private void readCommandResultEntry(XmlResourceParser parser, SsFeature ssFeature)
            throws XmlPullParserException, IOException {
        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int eventType = parser.getEventType();
            if (eventType == XmlPullParser.END_TAG
                    && TAG_COMMAND_RESULT.equals(parser.getName())) {
                break;
            }
            if (eventType == XmlPullParser.START_TAG
                    && TAG_ENTRY.equals(parser.getName())) {
                String key = getSpecificAttributeValue(parser, ATTR_RESULT_KEY);
                if (!TextUtils.isEmpty(key)) {
                    SsResultEntry entry = new SsResultEntry();
                    entry.mDefinition = getSpecificAttributeValue(parser, ATTR_DEFINITION_KEY);
                    entry.mCompareValue = readText(parser);
                    if (ssFeature.responseCode.containsKey(key)) {
                        ssFeature.responseCode.get(key).add(entry);
                    } else {
                        ArrayList<SsResultEntry> arrayList = new ArrayList<>();
                        arrayList.add(entry);
                        ssFeature.responseCode.put(key, arrayList);
                    }
                }
            }
        }
    }

    private SsEntry readCommandEntry(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        int outerDepth = parser.getDepth();
        String command_action = getSpecificAttributeValue(parser, ATTR_NAME);
        SsEntry entry = new SsEntry(command_action);

        while (parser.next() != XmlPullParser.END_DOCUMENT) {
            int eventType = parser.getEventType();
            if (eventType == XmlPullParser.END_TAG
                    && outerDepth == parser.getDepth()) {
                break;
            }

            if (eventType != XmlPullParser.START_TAG) {
                continue;
            }

            String name = parser.getName();
            if (TAG_SERVICE_CODE.equals(name)) {
                entry.serviceCode = readText(parser);
            } else if (TAG_ACTION_CODE.equals(name)) {
                entry.actionCode = readText(parser);
            } else if (TAG_PARAMETER.equals(name)) {
                String number = getSpecificAttributeValue(parser, ATTR_PARAMETER_NUM);
                if (!TextUtils.isEmpty(number)) {
                    readParameters(parser, entry, Integer.valueOf(number), TAG_PARAMETER);
                }
            } else if (TAG_RESPONSE_FORMAT.equals(name)) {
                String number = getSpecificAttributeValue(parser, ATTR_PARAMETER_NUM);
                if (!TextUtils.isEmpty(number)) {
                    readParameters(parser, entry, Integer.valueOf(number), TAG_RESPONSE_FORMAT);
                }
            }
        }
        Log.d(LOG_TAG, "ssEntry:" + entry.toString());
        return entry;
    }

    private void readParameters(XmlResourceParser parser, SsEntry entry, int num, String parentTag)
            throws IOException, XmlPullParserException {
        Log.d(LOG_TAG, "readParameters() nume:" + num);
        int i = 0;
        while (i < num) {
            if (parser.next() == XmlPullParser.START_TAG) {
                String name = parser.getName();
                if (TAG_ENTRY.equals(name)) {
                    String position = getSpecificAttributeValue(parser, ATTR_POSITION);
                    if (!TextUtils.isEmpty(position)) {
                        if (TAG_PARAMETER.equals(parentTag)) {
                            String value = readText(parser);
                            if (!TextUtils.isEmpty(value)) {
                                entry.commandParameter.put(Integer.valueOf(position), value);
                            }
                        } else if (TAG_RESPONSE_FORMAT.equals(parentTag)) {
                            String key = getSpecificAttributeValue(parser, ATTR_RESULT_KEY);
                            if (!TextUtils.isEmpty(key)) {
                                entry.responseFormat.put(Integer.valueOf(position), key);
                            }
                        }
                        i++;
                    }
                }
            }
        }
    }

    private String getSpecificAttributeValue(XmlResourceParser parser, String attrTag) {
        String value = "";
        for (int i = 0; i < parser.getAttributeCount(); i++) {
            if (attrTag.equals(parser.getAttributeName(i))) {
                value = parser.getAttributeValue(i);
            }
        }
        return value;
    }

    private String readText(XmlResourceParser parser) throws IOException, XmlPullParserException {
        String result = "";
        if (parser.next() == XmlPullParser.TEXT) {
            result = parser.getText();
            parser.nextTag();
        }
        return result;
    }

    /**
     * CarrierXmlParser parses the xml and saves in mFeatureMap.
     * To use this function get feature from the mFeatureMaps.
     *
     * @param inputFeatureName This is feature's name from xml.
     */
    public SsFeature getFeature(String inputFeatureName) {
        return mFeatureMaps.get(inputFeatureName);
    }

    /**
     * To check the command which is dialed by user is caller id command.
     * <p>
     * If it is a caller id command which sets to activate, return the {@code
     * SsEntry.SSAction.UPDATE_ACTIVATE}.
     * If it is a caller id command which sets to deactivate, return the {@code
     * SsEntry.SSAction.UPDATE_DEACTIVATE}.
     * If it is not a caller id command, return the {@code SsEntry.SSAction.UNKNOWN}.
     *
     * @param inputCommand This is caller id's ussd command which is dialed by user.
     * @return {@link SsEntry.SSAction}
     */
    public SsEntry.SSAction getCallerIdUssdCommandAction(String inputCommand) {
        if (isCallerIdActivate(inputCommand)) {
            return SsEntry.SSAction.UPDATE_ACTIVATE;
        }
        if (isCallerIdDeactivate(inputCommand)) {
            return SsEntry.SSAction.UPDATE_DEACTIVATE;
        }
        return SsEntry.SSAction.UNKNOWN;
    }

    private String getCallerIdActivateCommandFromXml() {
        return getFeature(FEATURE_CALLER_ID).getCommandStructure(SsEntry.SSAction.UPDATE_ACTIVATE);
    }

    private String getCallerIdDeactivateCommandFromXml() {
        return getFeature(FEATURE_CALLER_ID).getCommandStructure(
                SsEntry.SSAction.UPDATE_DEACTIVATE);
    }

    private boolean isCallerIdActivate(String inputStr) {
        String activateStr = getCallerIdActivateCommandFromXml();
        return compareCommand(activateStr, inputStr);
    }

    private boolean isCallerIdDeactivate(String inputStr) {
        String activateStr = getCallerIdDeactivateCommandFromXml();
        return compareCommand(activateStr, inputStr);
    }

    private boolean compareCommand(String activateStr, String inputStr) {
        String[] activateArray = activateStr.split("\\" + STAR_SIGN);
        String[] inputArray = inputStr.split("\\" + STAR_SIGN);

        if (activateArray.length == 0 || inputArray.length == 0) {
            return false;
        }
        for (int i = 0; i < activateArray.length; i++) {
            if (activateArray[i].startsWith(TAG_SIGN)) {
                continue;
            }
            if (!activateArray[i].equals(inputArray[i])) {
                Log.d(LOG_TAG, "compare fails:" + activateStr + "," + inputStr);
                return false;
            }
        }
        Log.d(LOG_TAG, "compare success");
        return true;
    }
}
