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

package com.android.car.settings.sound;

import android.content.Context;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.media.AudioAttributes;
import android.util.AttributeSet;
import android.util.SparseArray;
import android.util.Xml;

import androidx.annotation.DrawableRes;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;

import com.android.car.settings.R;
import com.android.car.settings.common.Logger;

import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;

/**
 * Parses the xml file which specifies which Audio usages should be considered by sound settings.
 */
public class VolumeItemParser {
    private static final Logger LOG = new Logger(VolumeItemParser.class);

    private static final String XML_TAG_VOLUME_ITEMS = "carVolumeItems";
    private static final String XML_TAG_VOLUME_ITEM = "item";

    /**
     * Parses the volume items listed in the xml resource provided. This is returned as a sparse
     * array which is keyed by the rank (the order in which the volume item appears in the xml
     * resrouce).
     */
    public static SparseArray<VolumeItem> loadAudioUsageItems(Context context,
            @XmlRes int volumeItemsXml) {
        SparseArray<VolumeItem> volumeItems = new SparseArray<>();
        try (XmlResourceParser parser = context.getResources().getXml(volumeItemsXml)) {
            AttributeSet attrs = Xml.asAttributeSet(parser);
            int type;
            // Traverse to the first start tag.
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && type != XmlResourceParser.START_TAG) {
                continue;
            }

            if (!XML_TAG_VOLUME_ITEMS.equals(parser.getName())) {
                throw new RuntimeException("Meta-data does not start with carVolumeItems tag");
            }
            int outerDepth = parser.getDepth();
            int rank = 0;
            while ((type = parser.next()) != XmlResourceParser.END_DOCUMENT
                    && (type != XmlResourceParser.END_TAG || parser.getDepth() > outerDepth)) {
                if (type == XmlResourceParser.END_TAG) {
                    continue;
                }
                if (XML_TAG_VOLUME_ITEM.equals(parser.getName())) {
                    TypedArray item = context.getResources().obtainAttributes(
                            attrs, R.styleable.carVolumeItems_item);
                    int usage = item.getInt(R.styleable.carVolumeItems_item_usage, -1);
                    if (usage >= 0) {
                        volumeItems.put(usage, new VolumeItemParser.VolumeItem(
                                usage, rank,
                                item.getResourceId(R.styleable.carVolumeItems_item_titleText, 0),
                                item.getResourceId(R.styleable.carVolumeItems_item_icon, 0)));
                        rank++;
                    }
                    item.recycle();
                }
            }
        } catch (XmlPullParserException | IOException e) {
            LOG.e("Error parsing volume groups configuration", e);
        }
        return volumeItems;
    }

    /**
     * Wrapper class which contains information to render volume item on UI.
     */
    public static class VolumeItem {
        @AudioAttributes.AttributeUsage
        private final int mUsage;
        private final int mRank;
        @StringRes
        private final int mTitle;
        @DrawableRes
        private final int mIcon;

        /** Constructs the VolumeItem container with the given values. */
        public VolumeItem(@AudioAttributes.AttributeUsage int usage, int rank,
                @StringRes int title, @DrawableRes int icon) {
            mUsage = usage;
            mRank = rank;
            mTitle = title;
            mIcon = icon;
        }

        /**
         * Usage is used to represent what purpose the sound is used for. The values should be
         * defined within AudioAttributes.USAGE_*.
         */
        public int getUsage() {
            return mUsage;
        }

        /**
         * Rank represents the order in which the usage appears in
         * {@link R.xml#car_volume_items}. This order is used to determine which title and icon
         * should be used for each audio group. The lowest rank has the highest precedence.
         */
        public int getRank() {
            return mRank;
        }

        /** Title which should be used for the seek bar preference. */
        public int getTitle() {
            return mTitle;
        }

        /** Icon which should be used for the seek bar preference. */
        public int getIcon() {
            return mIcon;
        }
    }
}
