/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.wallpaper.module;

/**
 * Key constants used to index into implementations of {@link WallpaperPreferences}.
 */
public class WallpaperPreferenceKeys {
    public static final String KEY_WALLPAPER_PRESENTATION_MODE = "wallpaper_presentation_mode";

    public static final String KEY_HOME_WALLPAPER_ATTRIB_1 = "home_wallpaper_attribution_line_1";
    public static final String KEY_HOME_WALLPAPER_ATTRIB_2 = "home_wallpaper_attribution_line_2";
    public static final String KEY_HOME_WALLPAPER_ATTRIB_3 = "home_wallpaper_attribution_line_3";
    public static final String KEY_HOME_WALLPAPER_ACTION_URL = "home_wallpaper_action_url";
    public static final String KEY_HOME_WALLPAPER_ACTION_LABEL_RES = "home_wallpaper_action_label";
    public static final String KEY_HOME_WALLPAPER_ACTION_ICON_RES = "home_wallpaper_action_icon";
    public static final String KEY_HOME_WALLPAPER_COLLECTION_ID = "home_wallpaper_collection_id";
    public static final String KEY_HOME_WALLPAPER_BASE_IMAGE_URL = "home_wallpaper_base_image_url";
    public static final String KEY_HOME_WALLPAPER_HASH_CODE = "home_wallpaper_hash_code";
    public static final String KEY_HOME_WALLPAPER_MANAGER_ID = "home_wallpaper_id";
    public static final String KEY_HOME_WALLPAPER_PACKAGE_NAME = "home_wallpaper_package_name";
    public static final String KEY_HOME_WALLPAPER_REMOTE_ID = "home_wallpaper_remote_id";
    public static final String KEY_HOME_WALLPAPER_BACKING_FILE = "home_wallpaper_backing_file";

    public static final String KEY_LOCK_WALLPAPER_ATTRIB_1 = "lock_wallpaper_attribution_line_1";
    public static final String KEY_LOCK_WALLPAPER_ATTRIB_2 = "lock_wallpaper_attribution_line_2";
    public static final String KEY_LOCK_WALLPAPER_ATTRIB_3 = "lock_wallpaper_attribution_line_3";
    public static final String KEY_LOCK_WALLPAPER_ACTION_URL = "lock_wallpaper_action_url";
    public static final String KEY_LOCK_WALLPAPER_ACTION_LABEL_RES = "lock_wallpaper_action_label";
    public static final String KEY_LOCK_WALLPAPER_ACTION_ICON_RES = "lock_wallpaper_action_icon";
    public static final String KEY_LOCK_WALLPAPER_HASH_CODE = "lock_wallpaper_hash_code";
    public static final String KEY_LOCK_WALLPAPER_COLLECTION_ID = "lock_wallpaper_collection_id";
    public static final String KEY_LOCK_WALLPAPER_MANAGER_ID = "lock_wallpaper_id";
    public static final String KEY_LOCK_WALLPAPER_BACKING_FILE = "lock_wallpaper_backing_file";

    public static final String KEY_DAILY_ROTATION_TIMESTAMPS = "daily_rotation_timestamps";
    public static final String KEY_DAILY_WALLPAPER_ENABLED_TIMESTAMP =
            "daily_wallpaper_enabled_timestamp";

    public static final String KEY_LAST_DAILY_LOG_TIMESTAMP = "last_daily_log_timestamp";
    public static final String KEY_LAST_APP_ACTIVE_TIMESTAMP = "last_app_active_timestamp";
    public static final String KEY_LAST_ROTATION_STATUS = "last_rotation_status";
    public static final String KEY_LAST_ROTATION_STATUS_TIMESTAMP = "last_rotation_status_timestamp";
    public static final String KEY_LAST_SYNC_TIMESTAMP = "last_sync_timestamp";

    public static final String KEY_PENDING_WALLPAPER_SET_STATUS = "pending_wallpaper_set_status";
    public static final String KEY_PENDING_DAILY_WALLPAPER_UPDATE_STATUS =
            "pending_daily_wallpaper_update_status";

    public static final String KEY_NUM_DAYS_DAILY_ROTATION_FAILED = "num_days_daily_rotation_failed";
    public static final String KEY_NUM_DAYS_DAILY_ROTATION_NOT_ATTEMPTED =
            "num_days_daily_rotation_not_attempted";
}
