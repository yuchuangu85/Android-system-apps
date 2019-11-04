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

package com.android.bluetooth.btservice.storage;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.migration.Migration;
import androidx.sqlite.db.SupportSQLiteDatabase;

import java.util.List;

/**
 * MetadataDatabase is a Room database stores Bluetooth persistence data
 */
@Database(entities = {Metadata.class}, exportSchema = false, version = 102)
public abstract class MetadataDatabase extends RoomDatabase {
    /**
     * The database file name
     */
    public static final String DATABASE_NAME = "bluetooth_db";

    protected abstract MetadataDao mMetadataDao();

    /**
     * Create a {@link MetadataDatabase} database with migrations
     *
     * @param context the Context to create database
     * @return the created {@link MetadataDatabase}
     */
    public static MetadataDatabase createDatabase(Context context) {
        return Room.databaseBuilder(context,
                MetadataDatabase.class, DATABASE_NAME)
                .addMigrations(MIGRATION_100_101)
                .addMigrations(MIGRATION_101_102)
                .build();
    }

    /**
     * Create a {@link MetadataDatabase} database without migration, database
     * would be reset if any load failure happens
     *
     * @param context the Context to create database
     * @return the created {@link MetadataDatabase}
     */
    public static MetadataDatabase createDatabaseWithoutMigration(Context context) {
        return Room.databaseBuilder(context,
                MetadataDatabase.class, DATABASE_NAME)
                .fallbackToDestructiveMigration()
                .build();
    }

    /**
     * Insert a {@link Metadata} to database
     *
     * @param metadata the data wish to put into storage
     */
    public void insert(Metadata... metadata) {
        mMetadataDao().insert(metadata);
    }

    /**
     * Load all data from database as a {@link List} of {@link Metadata}
     *
     * @return a {@link List} of {@link Metadata}
     */
    public List<Metadata> load() {
        return mMetadataDao().load();
    }

    /**
     * Delete one of the {@link Metadata} contains in database
     *
     * @param address the address of Metadata to delete
     */
    public void delete(String address) {
        mMetadataDao().delete(address);
    }

    /**
     * Clear database.
     */
    public void deleteAll() {
        mMetadataDao().deleteAll();
    }

    private static final Migration MIGRATION_100_101 = new Migration(100, 101) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE metadata ADD COLUMN `pbap_client_priority` INTEGER");
        }
    };

    private static final Migration MIGRATION_101_102 = new Migration(101, 102) {
        @Override
        public void migrate(SupportSQLiteDatabase database) {
            database.execSQL("CREATE TABLE IF NOT EXISTS `metadata_tmp` ("
                    + "`address` TEXT NOT NULL, `migrated` INTEGER NOT NULL, "
                    + "`a2dpSupportsOptionalCodecs` INTEGER NOT NULL, "
                    + "`a2dpOptionalCodecsEnabled` INTEGER NOT NULL, "
                    + "`a2dp_priority` INTEGER, `a2dp_sink_priority` INTEGER, "
                    + "`hfp_priority` INTEGER, `hfp_client_priority` INTEGER, "
                    + "`hid_host_priority` INTEGER, `pan_priority` INTEGER, "
                    + "`pbap_priority` INTEGER, `pbap_client_priority` INTEGER, "
                    + "`map_priority` INTEGER, `sap_priority` INTEGER, "
                    + "`hearing_aid_priority` INTEGER, `map_client_priority` INTEGER, "
                    + "`manufacturer_name` BLOB, `model_name` BLOB, `software_version` BLOB, "
                    + "`hardware_version` BLOB, `companion_app` BLOB, `main_icon` BLOB, "
                    + "`is_untethered_headset` BLOB, `untethered_left_icon` BLOB, "
                    + "`untethered_right_icon` BLOB, `untethered_case_icon` BLOB, "
                    + "`untethered_left_battery` BLOB, `untethered_right_battery` BLOB, "
                    + "`untethered_case_battery` BLOB, `untethered_left_charging` BLOB, "
                    + "`untethered_right_charging` BLOB, `untethered_case_charging` BLOB, "
                    + "`enhanced_settings_ui_uri` BLOB, PRIMARY KEY(`address`))");

            database.execSQL("INSERT INTO metadata_tmp ("
                    + "address, migrated, a2dpSupportsOptionalCodecs, a2dpOptionalCodecsEnabled, "
                    + "a2dp_priority, a2dp_sink_priority, hfp_priority, hfp_client_priority, "
                    + "hid_host_priority, pan_priority, pbap_priority, pbap_client_priority, "
                    + "map_priority, sap_priority, hearing_aid_priority, map_client_priority, "
                    + "manufacturer_name, model_name, software_version, hardware_version, "
                    + "companion_app, main_icon, is_untethered_headset, untethered_left_icon, "
                    + "untethered_right_icon, untethered_case_icon, untethered_left_battery, "
                    + "untethered_right_battery, untethered_case_battery, "
                    + "untethered_left_charging, untethered_right_charging, "
                    + "untethered_case_charging, enhanced_settings_ui_uri) "
                    + "SELECT "
                    + "address, migrated, a2dpSupportsOptionalCodecs, a2dpOptionalCodecsEnabled, "
                    + "a2dp_priority, a2dp_sink_priority, hfp_priority, hfp_client_priority, "
                    + "hid_host_priority, pan_priority, pbap_priority, pbap_client_priority, "
                    + "map_priority, sap_priority, hearing_aid_priority, map_client_priority, "
                    + "CAST (manufacturer_name AS BLOB), "
                    + "CAST (model_name AS BLOB), "
                    + "CAST (software_version AS BLOB), "
                    + "CAST (hardware_version AS BLOB), "
                    + "CAST (companion_app AS BLOB), "
                    + "CAST (main_icon AS BLOB), "
                    + "CAST (is_unthethered_headset AS BLOB), "
                    + "CAST (unthethered_left_icon AS BLOB), "
                    + "CAST (unthethered_right_icon AS BLOB), "
                    + "CAST (unthethered_case_icon AS BLOB), "
                    + "CAST (unthethered_left_battery AS BLOB), "
                    + "CAST (unthethered_right_battery AS BLOB), "
                    + "CAST (unthethered_case_battery AS BLOB), "
                    + "CAST (unthethered_left_charging AS BLOB), "
                    + "CAST (unthethered_right_charging AS BLOB), "
                    + "CAST (unthethered_case_charging AS BLOB), "
                    + "CAST (enhanced_settings_ui_uri AS BLOB)"
                    + "FROM metadata");

            database.execSQL("DROP TABLE `metadata`");
            database.execSQL("ALTER TABLE `metadata_tmp` RENAME TO `metadata`");
        }
    };
}
