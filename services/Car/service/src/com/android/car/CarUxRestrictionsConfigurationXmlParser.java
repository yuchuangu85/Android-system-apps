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

import static android.car.drivingstate.CarUxRestrictionsManager.UX_RESTRICTION_MODE_BASELINE;

import android.annotation.Nullable;
import android.annotation.XmlRes;
import android.car.drivingstate.CarDrivingStateEvent;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsConfiguration;
import android.car.drivingstate.CarUxRestrictionsConfiguration.Builder;
import android.car.drivingstate.CarUxRestrictionsConfiguration.DrivingStateRestrictions;
import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.util.AttributeSet;
import android.util.Log;
import android.util.Xml;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @hide
 */
public final class CarUxRestrictionsConfigurationXmlParser {
    private static final String TAG = "UxRConfigParser";
    private static final int UX_RESTRICTIONS_UNKNOWN = -1;
    private static final float INVALID_SPEED = -1f;
    // XML tags to parse
    private static final String ROOT_ELEMENT = "UxRestrictions";
    private static final String RESTRICTION_MAPPING = "RestrictionMapping";
    private static final String RESTRICTION_PARAMETERS = "RestrictionParameters";
    private static final String DRIVING_STATE = "DrivingState";
    private static final String RESTRICTIONS = "Restrictions";
    private static final String STRING_RESTRICTIONS = "StringRestrictions";
    private static final String CONTENT_RESTRICTIONS = "ContentRestrictions";

    private final Context mContext;

    private int mMaxRestrictedStringLength = UX_RESTRICTIONS_UNKNOWN;
    private int mMaxCumulativeContentItems = UX_RESTRICTIONS_UNKNOWN;
    private int mMaxContentDepth = UX_RESTRICTIONS_UNKNOWN;
    private final List<CarUxRestrictionsConfiguration.Builder> mConfigBuilders = new ArrayList<>();

    private CarUxRestrictionsConfigurationXmlParser(Context context) {
        mContext = context;
    }

    /**
     * Loads the UX restrictions related information from the XML resource.
     *
     * @return parsed CarUxRestrictionsConfiguration; {@code null} if the XML is malformed.
     */
    @Nullable
    public static List<CarUxRestrictionsConfiguration> parse(
            Context context, @XmlRes int xmlResource)
            throws IOException, XmlPullParserException {
        return new CarUxRestrictionsConfigurationXmlParser(context).parse(xmlResource);
    }

    @Nullable
    private List<CarUxRestrictionsConfiguration> parse(@XmlRes int xmlResource)
            throws IOException, XmlPullParserException {

        XmlResourceParser parser = mContext.getResources().getXml(xmlResource);
        if (parser == null) {
            Log.e(TAG, "Invalid Xml resource");
            return null;
        }

        if (!traverseUntilStartTag(parser)) {
            Log.e(TAG, "XML root element invalid: " + parser.getName());
            return null;
        }

        if (!traverseUntilEndOfDocument(parser)) {
            Log.e(TAG, "Could not parse XML to end");
            return null;
        }

        List<CarUxRestrictionsConfiguration> configs = new ArrayList<>();
        for (CarUxRestrictionsConfiguration.Builder builder : mConfigBuilders) {
            builder.setMaxStringLength(mMaxRestrictedStringLength)
                    .setMaxCumulativeContentItems(mMaxCumulativeContentItems)
                    .setMaxContentDepth(mMaxContentDepth);
            configs.add(builder.build());
        }
        return configs;
    }

    private boolean traverseUntilStartTag(XmlResourceParser parser)
            throws IOException, XmlPullParserException {
        int type;
        // Traverse till we get to the first tag
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                && type != XmlResourceParser.START_TAG) {
            // Do nothing.
        }
        return ROOT_ELEMENT.equals(parser.getName());
    }

    private boolean traverseUntilEndOfDocument(XmlResourceParser parser)
            throws XmlPullParserException, IOException {
        AttributeSet attrs = Xml.asAttributeSet(parser);
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            // Every time we hit a start tag, check for the type of the tag
            // and load the corresponding information.
            if (parser.next() == XmlResourceParser.START_TAG) {
                switch (parser.getName()) {
                    case RESTRICTION_MAPPING:
                        // Each RestrictionMapping tag represents a new set of rules.
                        mConfigBuilders.add(new CarUxRestrictionsConfiguration.Builder());

                        if (!mapDrivingStateToRestrictions(parser, attrs)) {
                            Log.e(TAG, "Could not map driving state to restriction.");
                            return false;
                        }
                        break;
                    case RESTRICTION_PARAMETERS:
                        if (!parseRestrictionParameters(parser, attrs)) {
                            // Failure to parse is automatically handled by falling back to
                            // defaults. Just log the information here.
                            if (Log.isLoggable(TAG, Log.INFO)) {
                                Log.i(TAG, "Error reading restrictions parameters. "
                                        + "Falling back to platform defaults.");
                            }
                        }
                        break;
                    default:
                        Log.w(TAG, "Unknown class:" + parser.getName());
                }
            }
        }
        return true;
    }

    /**
     * Parses the information in the <restrictionMapping> tag to construct the mapping from
     * driving state to UX restrictions.
     */
    private boolean mapDrivingStateToRestrictions(XmlResourceParser parser, AttributeSet attrs)
            throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Log.e(TAG, "Invalid arguments");
            return false;
        }
        // The parser should be at the <RestrictionMapping> tag at this point.
        if (!RESTRICTION_MAPPING.equals(parser.getName())) {
            Log.e(TAG, "Parser not at RestrictionMapping element: " + parser.getName());
            return false;
        }
        {
            // Use a floating block to limit the scope of TypedArray and ensure it's recycled.
            TypedArray a = mContext.getResources().obtainAttributes(attrs,
                    R.styleable.UxRestrictions_RestrictionMapping);
            if (a.hasValue(R.styleable.UxRestrictions_RestrictionMapping_physicalPort)) {
                int portValue = a.getInt(
                        R.styleable.UxRestrictions_RestrictionMapping_physicalPort, 0);
                byte port = CarUxRestrictionsConfiguration.Builder.validatePort(portValue);
                getCurrentBuilder().setPhysicalPort(port);
            }
            a.recycle();
        }

        if (!traverseToTag(parser, DRIVING_STATE)) {
            Log.e(TAG, "No <" + DRIVING_STATE + "> tag in XML");
            return false;
        }
        // Handle all the <DrivingState> tags.
        while (DRIVING_STATE.equals(parser.getName())) {
            if (parser.getEventType() == XmlResourceParser.START_TAG) {
                // 1. Get the driving state attributes: driving state and speed range
                TypedArray a = mContext.getResources().obtainAttributes(attrs,
                        R.styleable.UxRestrictions_DrivingState);
                int drivingState = a.getInt(R.styleable.UxRestrictions_DrivingState_state,
                        CarDrivingStateEvent.DRIVING_STATE_UNKNOWN);
                float minSpeed = a.getFloat(R.styleable.UxRestrictions_DrivingState_minSpeed,
                        INVALID_SPEED);
                float maxSpeed = a.getFloat(R.styleable.UxRestrictions_DrivingState_maxSpeed,
                        Builder.SpeedRange.MAX_SPEED);
                a.recycle();

                // 2. Traverse to the <Restrictions> tag
                if (!traverseToTag(parser, RESTRICTIONS)) {
                    Log.e(TAG, "No <" + RESTRICTIONS + "> tag in XML");
                    return false;
                }

                // 3. Parse the restrictions for this driving state
                Builder.SpeedRange speedRange = parseSpeedRange(minSpeed, maxSpeed);
                if (!parseAllRestrictions(parser, attrs, drivingState, speedRange)) {
                    Log.e(TAG, "Could not parse restrictions for driving state:" + drivingState);
                    return false;
                }
            }
            parser.next();
        }
        return true;
    }

    /**
     * Parses all <restrictions> tags nested with <drivingState> tag.
     */
    private boolean parseAllRestrictions(XmlResourceParser parser, AttributeSet attrs,
            int drivingState, Builder.SpeedRange speedRange)
            throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Log.e(TAG, "Invalid arguments");
            return false;
        }
        // The parser should be at the <Restrictions> tag at this point.
        if (!RESTRICTIONS.equals(parser.getName())) {
            Log.e(TAG, "Parser not at Restrictions element: " + parser.getName());
            return false;
        }
        while (RESTRICTIONS.equals(parser.getName())) {
            if (parser.getEventType() == XmlResourceParser.START_TAG) {
                // Parse one restrictions tag.
                DrivingStateRestrictions restrictions = parseRestrictions(parser, attrs);
                if (restrictions == null) {
                    Log.e(TAG, "");
                    return false;
                }
                restrictions.setSpeedRange(speedRange);

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "Map " + drivingState + " : " + restrictions);
                }

                // Update the builder if the driving state and restrictions info are valid.
                if (drivingState != CarDrivingStateEvent.DRIVING_STATE_UNKNOWN
                        && restrictions != null) {
                    getCurrentBuilder().setUxRestrictions(drivingState, restrictions);
                }
            }
            parser.next();
        }
        return true;
    }

    /**
     * Parses the <restrictions> tag nested with the <drivingState>.  This provides the restrictions
     * for the enclosing driving state.
     */
    @Nullable
    private DrivingStateRestrictions parseRestrictions(XmlResourceParser parser, AttributeSet attrs)
            throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Log.e(TAG, "Invalid Arguments");
            return null;
        }

        int restrictions = UX_RESTRICTIONS_UNKNOWN;
        int restrictionMode = UX_RESTRICTION_MODE_BASELINE;
        boolean requiresOpt = true;
        while (RESTRICTIONS.equals(parser.getName())
                && parser.getEventType() == XmlResourceParser.START_TAG) {
            TypedArray a = mContext.getResources().obtainAttributes(attrs,
                    R.styleable.UxRestrictions_Restrictions);
            restrictions = a.getInt(
                    R.styleable.UxRestrictions_Restrictions_uxr,
                    CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED);
            requiresOpt = a.getBoolean(
                    R.styleable.UxRestrictions_Restrictions_requiresDistractionOptimization, true);
            restrictionMode = a.getInt(
                    R.styleable.UxRestrictions_Restrictions_mode, UX_RESTRICTION_MODE_BASELINE);

            a.recycle();
            parser.next();
        }
        return new DrivingStateRestrictions()
                .setDistractionOptimizationRequired(requiresOpt)
                .setRestrictions(restrictions)
                .setMode(restrictionMode);
    }

    @Nullable
    private Builder.SpeedRange parseSpeedRange(float minSpeed, float maxSpeed) {
        if (Float.compare(minSpeed, 0) < 0 || Float.compare(maxSpeed, 0) < 0) {
            return null;
        }
        return new CarUxRestrictionsConfiguration.Builder.SpeedRange(minSpeed, maxSpeed);
    }

    private boolean traverseToTag(XmlResourceParser parser, String tag)
            throws IOException, XmlPullParserException {
        if (tag == null || parser == null) {
            return false;
        }
        int type;
        while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT) {
            if (type == XmlResourceParser.START_TAG && parser.getName().equals(tag)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Parses the information in the <RestrictionParameters> tag to read the parameters for the
     * applicable UX restrictions
     */
    private boolean parseRestrictionParameters(XmlResourceParser parser, AttributeSet attrs)
            throws IOException, XmlPullParserException {
        if (parser == null || attrs == null) {
            Log.e(TAG, "Invalid arguments");
            return false;
        }
        // The parser should be at the <RestrictionParameters> tag at this point.
        if (!RESTRICTION_PARAMETERS.equals(parser.getName())) {
            Log.e(TAG, "Parser not at RestrictionParameters element: " + parser.getName());
            return false;
        }
        while (parser.getEventType() != XmlResourceParser.END_DOCUMENT) {
            int type = parser.next();
            // Break if we have parsed all <RestrictionParameters>
            if (type == XmlResourceParser.END_TAG && RESTRICTION_PARAMETERS.equals(
                    parser.getName())) {
                return true;
            }
            if (type == XmlResourceParser.START_TAG) {
                TypedArray a = null;
                switch (parser.getName()) {
                    case STRING_RESTRICTIONS:
                        a = mContext.getResources().obtainAttributes(attrs,
                                R.styleable.UxRestrictions_StringRestrictions);
                        mMaxRestrictedStringLength = a.getInt(
                                R.styleable.UxRestrictions_StringRestrictions_maxLength,
                                UX_RESTRICTIONS_UNKNOWN);

                        break;
                    case CONTENT_RESTRICTIONS:
                        a = mContext.getResources().obtainAttributes(attrs,
                                R.styleable.UxRestrictions_ContentRestrictions);
                        mMaxCumulativeContentItems = a.getInt(
                                R.styleable.UxRestrictions_ContentRestrictions_maxCumulativeItems,
                                UX_RESTRICTIONS_UNKNOWN);
                        mMaxContentDepth = a.getInt(
                                R.styleable.UxRestrictions_ContentRestrictions_maxDepth,
                                UX_RESTRICTIONS_UNKNOWN);
                        break;
                    default:
                        if (Log.isLoggable(TAG, Log.DEBUG)) {
                            Log.d(TAG, "Unsupported Restriction Parameters in XML: "
                                    + parser.getName());
                        }
                        break;
                }
                if (a != null) {
                    a.recycle();
                }
            }
        }
        return true;
    }

    private CarUxRestrictionsConfiguration.Builder getCurrentBuilder() {
        return mConfigBuilders.get(mConfigBuilders.size() - 1);
    }
}

