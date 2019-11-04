/*
 * Copyright (C) 2014 The Android Open Source Project
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

package com.android.cellbroadcastreceiver;

import static android.provider.SearchIndexablesContract.COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_CLASS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEY;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_KEYWORDS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_SCREEN_TITLE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_RAW_TITLE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_CLASS_NAME;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_ICON_RESID;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_ACTION;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RANK;
import static android.provider.SearchIndexablesContract.COLUMN_INDEX_XML_RES_RESID;
import static android.provider.SearchIndexablesContract.INDEXABLES_RAW_COLUMNS;
import static android.provider.SearchIndexablesContract.INDEXABLES_XML_RES_COLUMNS;
import static android.provider.SearchIndexablesContract.NON_INDEXABLES_KEYS_COLUMNS;

import android.content.Intent;
import android.content.res.Resources;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.provider.SearchIndexableResource;
import android.provider.SearchIndexablesProvider;
import android.provider.Settings;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.List;

public class CellBroadcastSearchIndexableProvider extends SearchIndexablesProvider {

    // Additional keywords for settings search
    private static final int[] INDEXABLE_KEYWORDS_RESOURCES = {
            R.string.etws_earthquake_warning,
            R.string.etws_tsunami_warning,
            R.string.cmas_presidential_level_alert,
            R.string.cmas_required_monthly_test,
            R.string.emergency_alerts_title
    };

    private static final SearchIndexableResource[] INDEXABLE_RES = new SearchIndexableResource[] {
            new SearchIndexableResource(1, R.xml.preferences,
                    CellBroadcastSettings.class.getName(),
                    R.mipmap.ic_launcher_cell_broadcast),
    };
    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor queryXmlResources(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_XML_RES_COLUMNS);
        final int count = INDEXABLE_RES.length;
        for (int n = 0; n < count; n++) {
            Object[] ref = new Object[7];
            ref[COLUMN_INDEX_XML_RES_RANK] = INDEXABLE_RES[n].rank;
            ref[COLUMN_INDEX_XML_RES_RESID] = INDEXABLE_RES[n].xmlResId;
            ref[COLUMN_INDEX_XML_RES_CLASS_NAME] = null;
            ref[COLUMN_INDEX_XML_RES_ICON_RESID] = INDEXABLE_RES[n].iconResId;
            ref[COLUMN_INDEX_XML_RES_INTENT_ACTION] = Intent.ACTION_MAIN;
            ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_PACKAGE] = getContext().getPackageName();
            ref[COLUMN_INDEX_XML_RES_INTENT_TARGET_CLASS] = INDEXABLE_RES[n].className;
            cursor.addRow(ref);
        }
        return cursor;
    }

    @Override
    public Cursor queryRawData(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(INDEXABLES_RAW_COLUMNS);
        final Resources res =
                CellBroadcastSettings.getResourcesForDefaultSmsSubscriptionId(getContext());

        Object[] raw = new Object[INDEXABLES_RAW_COLUMNS.length];
        raw[COLUMN_INDEX_RAW_TITLE] = res.getString(R.string.sms_cb_settings);
        List<String> keywordList = new ArrayList<>();
        for (int keywordRes : INDEXABLE_KEYWORDS_RESOURCES) {
            keywordList.add(res.getString(keywordRes));
        }

        if (!CellBroadcastChannelManager.getCellBroadcastChannelRanges(
                this.getContext(),
                R.array.public_safety_messages_channels_range_strings).isEmpty()) {
            keywordList.add(res.getString(R.string.public_safety_message));
        }

        if (!CellBroadcastChannelManager.getCellBroadcastChannelRanges(
                this.getContext(),
                R.array.state_local_test_alert_range_strings).isEmpty()) {
            keywordList.add(res.getString(R.string.state_local_test_alert));
        }

        raw[COLUMN_INDEX_RAW_KEYWORDS] = TextUtils.join(",", keywordList);

        raw[COLUMN_INDEX_RAW_SCREEN_TITLE] = res.getString(R.string.sms_cb_settings);
        raw[COLUMN_INDEX_RAW_KEY] = CellBroadcastSettings.class.getSimpleName();
        raw[COLUMN_INDEX_RAW_INTENT_ACTION] = Intent.ACTION_MAIN;
        raw[COLUMN_INDEX_RAW_INTENT_TARGET_PACKAGE] = getContext().getPackageName();
        raw[COLUMN_INDEX_RAW_INTENT_TARGET_CLASS] = CellBroadcastSettings.class.getName();

        cursor.addRow(raw);
        return cursor;
    }

    @Override
    public Cursor queryNonIndexableKeys(String[] projection) {
        MatrixCursor cursor = new MatrixCursor(NON_INDEXABLES_KEYS_COLUMNS);

        // Show extra settings when developer options is enabled in settings.
        boolean enableDevSettings = Settings.Global.getInt(getContext().getContentResolver(),
                Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) != 0;

        Resources res = CellBroadcastSettings.getResourcesForDefaultSmsSubscriptionId(getContext());
        Object[] ref;

        ref = new Object[1];
        ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                CellBroadcastSettings.KEY_CATEGORY_DEV_SETTINGS;
        cursor.addRow(ref);

        // Show alert settings and ETWS categories for ETWS builds and developer mode.
        if (!enableDevSettings) {
            // Remove general emergency alert preference items (not shown for CMAS builds).
            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_ALERTS_MASTER_TOGGLE;
            cursor.addRow(ref);

            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_ALERT_SPEECH;
            cursor.addRow(ref);
        }

        if (!res.getBoolean(R.bool.show_cmas_settings)) {
            // Remove CMAS preference items in emergency alert category.
            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_CMAS_EXTREME_THREAT_ALERTS;
            cursor.addRow(ref);

            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_CMAS_SEVERE_THREAT_ALERTS;
            cursor.addRow(ref);

            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_CMAS_AMBER_ALERTS;
            cursor.addRow(ref);
        }

        if (!Resources.getSystem().getBoolean(
                com.android.internal.R.bool.config_showAreaUpdateInfoSettings)) {
            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_ENABLE_AREA_UPDATE_INFO_ALERTS;
            cursor.addRow(ref);
        }

        if (!enableDevSettings) {
            ref = new Object[1];
            ref[COLUMN_INDEX_NON_INDEXABLE_KEYS_KEY_VALUE] =
                    CellBroadcastSettings.KEY_CATEGORY_DEV_SETTINGS;
            cursor.addRow(ref);
        }

        return cursor;
    }
}
