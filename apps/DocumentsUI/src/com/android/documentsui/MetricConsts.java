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

package com.android.documentsui;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * All constants are based on the enums in
 * frameworks/base/core/proto/android/stats/docsui/docsui_enums.proto.
 */
public class MetricConsts {

    // Codes representing different root types.
    public static final int ROOT_UNKNOWN = 0;
    public static final int ROOT_NONE = 1;
    public static final int ROOT_OTHER_DOCS_PROVIDER = 2;
    public static final int ROOT_AUDIO = 3;
    public static final int ROOT_DEVICE_STORAGE = 4;
    public static final int ROOT_DOWNLOADS = 5;
    public static final int ROOT_HOME = 6;
    public static final int ROOT_IMAGES = 7;
    public static final int ROOT_RECENTS = 8;
    public static final int ROOT_VIDEOS = 9;
    public static final int ROOT_MTP = 10;
    public static final int ROOT_THIRD_PARTY_APP = 11;

    @IntDef(flag = true, value = {
            ROOT_UNKNOWN,
            ROOT_NONE,
            ROOT_OTHER_DOCS_PROVIDER,
            ROOT_AUDIO,
            ROOT_DEVICE_STORAGE,
            ROOT_DOWNLOADS,
            ROOT_HOME,
            ROOT_IMAGES,
            ROOT_RECENTS,
            ROOT_VIDEOS,
            ROOT_MTP,
            ROOT_THIRD_PARTY_APP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Root {
    }

    // Codes representing different mime types.
    static final int MIME_UNKNOWN = 0;
    static final int MIME_NONE = 1; // null mime
    static final int MIME_ANY = 2; // */*
    static final int MIME_APPLICATION = 3; // application/*
    static final int MIME_AUDIO = 4; // audio/*
    static final int MIME_IMAGE = 5; // image/*
    static final int MIME_MESSAGE = 6; // message/*
    static final int MIME_MULTIPART = 7; // multipart/*
    static final int MIME_TEXT = 8; // text/*
    static final int MIME_VIDEO = 9; // video/*
    static final int MIME_OTHER = 10; // anything not enumerated below

    @IntDef(flag = true, value = {
            MIME_UNKNOWN,
            MIME_NONE,
            MIME_ANY,
            MIME_APPLICATION,
            MIME_AUDIO,
            MIME_IMAGE,
            MIME_MESSAGE,
            MIME_MULTIPART,
            MIME_TEXT,
            MIME_VIDEO,
            MIME_OTHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Mime {
    }

    public static final int UNKNOWN_SCOPE = 0;
    public static final int FILES_SCOPE = 1;
    public static final int PICKER_SCOPE = 2;

    // Codes representing different scopes(FILE/PICKER mode).
    @IntDef({UNKNOWN_SCOPE, FILES_SCOPE, PICKER_SCOPE})
    @Retention(RetentionPolicy.SOURCE)
    public @interface ContextScope {
    }

    // Codes representing different kinds of file operations.
    static final int FILEOP_UNKNOWN = 0;
    static final int FILEOP_OTHER = 1; // any file operation not listed below
    static final int FILEOP_COPY = 2;
    static final int FILEOP_COPY_INTRA_PROVIDER = 3; // Copy within a provider
    static final int FILEOP_COPY_SYSTEM_PROVIDER = 4; // Copy to a system provider.
    static final int FILEOP_COPY_EXTERNAL_PROVIDER = 5; // Copy to a 3rd-party provider.
    static final int FILEOP_MOVE = 6;
    static final int FILEOP_MOVE_INTRA_PROVIDER = 7; // Move within a provider.
    static final int FILEOP_MOVE_SYSTEM_PROVIDER = 8; // Move to a system provider.
    static final int FILEOP_MOVE_EXTERNAL_PROVIDER = 9; // Move to a 3rd-party provider.
    static final int FILEOP_DELETE = 10;
    static final int FILEOP_RENAME = 11;
    static final int FILEOP_CREATE_DIR = 12;
    static final int FILEOP_OTHER_ERROR = 13;
    static final int FILEOP_DELETE_ERROR = 14;
    static final int FILEOP_MOVE_ERROR = 15;
    static final int FILEOP_COPY_ERROR = 16;
    static final int FILEOP_RENAME_ERROR = 17;
    static final int FILEOP_CREATE_DIR_ERROR = 18;
    static final int FILEOP_COMPRESS_INTRA_PROVIDER = 19; // Compres within a provider
    static final int FILEOP_COMPRESS_SYSTEM_PROVIDER = 20; // Compress to a system provider.
    static final int FILEOP_COMPRESS_EXTERNAL_PROVIDER = 21; // Compress to a 3rd-party provider.
    static final int FILEOP_EXTRACT_INTRA_PROVIDER = 22; // Extract within a provider
    static final int FILEOP_EXTRACT_SYSTEM_PROVIDER = 23; // Extract to a system provider.
    static final int FILEOP_EXTRACT_EXTERNAL_PROVIDER = 24; // Extract to a 3rd-party provider.
    static final int FILEOP_COMPRESS_ERROR = 25;
    static final int FILEOP_EXTRACT_ERROR = 26;

    @IntDef(flag = true, value = {
            FILEOP_UNKNOWN,
            FILEOP_OTHER,
            FILEOP_COPY,
            FILEOP_COPY_INTRA_PROVIDER,
            FILEOP_COPY_SYSTEM_PROVIDER,
            FILEOP_COPY_EXTERNAL_PROVIDER,
            FILEOP_MOVE,
            FILEOP_MOVE_INTRA_PROVIDER,
            FILEOP_MOVE_SYSTEM_PROVIDER,
            FILEOP_MOVE_EXTERNAL_PROVIDER,
            FILEOP_DELETE,
            FILEOP_RENAME,
            FILEOP_CREATE_DIR,
            FILEOP_OTHER_ERROR,
            FILEOP_DELETE_ERROR,
            FILEOP_MOVE_ERROR,
            FILEOP_COPY_ERROR,
            FILEOP_RENAME_ERROR,
            FILEOP_CREATE_DIR_ERROR,
            FILEOP_COMPRESS_INTRA_PROVIDER,
            FILEOP_COMPRESS_SYSTEM_PROVIDER,
            FILEOP_COMPRESS_EXTERNAL_PROVIDER,
            FILEOP_EXTRACT_INTRA_PROVIDER,
            FILEOP_EXTRACT_SYSTEM_PROVIDER,
            FILEOP_EXTRACT_EXTERNAL_PROVIDER,
            FILEOP_COMPRESS_ERROR,
            FILEOP_EXTRACT_ERROR
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileOp {
    }

    // Codes representing different provider types.  Used for sorting file operations when logging.
    static final int PROVIDER_INTRA = 0;
    static final int PROVIDER_SYSTEM = 1;
    static final int PROVIDER_EXTERNAL = 2;

    @IntDef(flag = false, value = {
            PROVIDER_INTRA,
            PROVIDER_SYSTEM,
            PROVIDER_EXTERNAL
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface Provider {
    }

    // Codes representing different types of sub-fileops.
    public static final int SUBFILEOP_UNKNOWN = 0;
    public static final int SUBFILEOP_QUERY_DOCUMENT = 1;
    public static final int SUBFILEOP_QUERY_CHILDREN = 2;
    public static final int SUBFILEOP_OPEN_FILE = 3;
    public static final int SUBFILEOP_READ_FILE = 4;
    public static final int SUBFILEOP_CREATE_DOCUMENT = 5;
    public static final int SUBFILEOP_WRITE_FILE = 6;
    public static final int SUBFILEOP_DELETE_DOCUMENT = 7;
    public static final int SUBFILEOP_OBTAIN_STREAM_TYPE = 8;
    public static final int SUBFILEOP_QUICK_MOVE = 9;
    public static final int SUBFILEOP_QUICK_COPY = 10;

    @IntDef(flag = false, value = {
            SUBFILEOP_UNKNOWN,
            SUBFILEOP_QUERY_DOCUMENT,
            SUBFILEOP_QUERY_CHILDREN,
            SUBFILEOP_OPEN_FILE,
            SUBFILEOP_READ_FILE,
            SUBFILEOP_CREATE_DOCUMENT,
            SUBFILEOP_WRITE_FILE,
            SUBFILEOP_DELETE_DOCUMENT,
            SUBFILEOP_OBTAIN_STREAM_TYPE,
            SUBFILEOP_QUICK_MOVE,
            SUBFILEOP_QUICK_COPY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SubFileOp {
    }

    // Codes representing different user actions
    public static final int USER_ACTION_UNKNOWN = 0;
    public static final int USER_ACTION_OTHER = 1;
    public static final int USER_ACTION_GRID = 2;
    public static final int USER_ACTION_LIST = 3;
    public static final int USER_ACTION_SORT_NAME = 4;
    public static final int USER_ACTION_SORT_DATE = 5;
    public static final int USER_ACTION_SORT_SIZE = 6;
    public static final int USER_ACTION_SORT_TYPE = 7;
    public static final int USER_ACTION_SEARCH = 8;
    public static final int USER_ACTION_SHOW_SIZE = 9;
    public static final int USER_ACTION_HIDE_SIZE = 10;
    public static final int USER_ACTION_SETTINGS = 11;
    public static final int USER_ACTION_COPY_TO = 12;
    public static final int USER_ACTION_MOVE_TO = 13;
    public static final int USER_ACTION_DELETE = 14;
    public static final int USER_ACTION_RENAME = 15;
    public static final int USER_ACTION_CREATE_DIR = 16;
    public static final int USER_ACTION_SELECT_ALL = 17;
    public static final int USER_ACTION_SHARE = 18;
    public static final int USER_ACTION_OPEN = 19;
    public static final int USER_ACTION_SHOW_ADVANCED = 20;
    public static final int USER_ACTION_HIDE_ADVANCED = 21;
    public static final int USER_ACTION_NEW_WINDOW = 22;
    public static final int USER_ACTION_PASTE_CLIPBOARD = 23;
    public static final int USER_ACTION_COPY_CLIPBOARD = 24;
    public static final int USER_ACTION_DRAG_N_DROP = 25;
    public static final int USER_ACTION_DRAG_N_DROP_MULTI_WINDOW = 26;
    public static final int USER_ACTION_CUT_CLIPBOARD = 27;
    public static final int USER_ACTION_COMPRESS = 28;
    public static final int USER_ACTION_EXTRACT_TO = 29;
    public static final int USER_ACTION_VIEW_IN_APPLICATION = 30;
    public static final int USER_ACTION_INSPECTOR = 31;
    public static final int USER_ACTION_SEARCH_CHIP = 32;
    public static final int USER_ACTION_SEARCH_HISTORY = 33;

    @IntDef(flag = false, value = {
            USER_ACTION_UNKNOWN,
            USER_ACTION_OTHER,
            USER_ACTION_GRID,
            USER_ACTION_LIST,
            USER_ACTION_SORT_NAME,
            USER_ACTION_SORT_DATE,
            USER_ACTION_SORT_SIZE,
            USER_ACTION_SORT_TYPE,
            USER_ACTION_SEARCH,
            USER_ACTION_SHOW_SIZE,
            USER_ACTION_HIDE_SIZE,
            USER_ACTION_SETTINGS,
            USER_ACTION_COPY_TO,
            USER_ACTION_MOVE_TO,
            USER_ACTION_DELETE,
            USER_ACTION_RENAME,
            USER_ACTION_CREATE_DIR,
            USER_ACTION_SELECT_ALL,
            USER_ACTION_SHARE,
            USER_ACTION_OPEN,
            USER_ACTION_SHOW_ADVANCED,
            USER_ACTION_HIDE_ADVANCED,
            USER_ACTION_NEW_WINDOW,
            USER_ACTION_PASTE_CLIPBOARD,
            USER_ACTION_COPY_CLIPBOARD,
            USER_ACTION_DRAG_N_DROP,
            USER_ACTION_DRAG_N_DROP_MULTI_WINDOW,
            USER_ACTION_CUT_CLIPBOARD,
            USER_ACTION_COMPRESS,
            USER_ACTION_EXTRACT_TO,
            USER_ACTION_VIEW_IN_APPLICATION,
            USER_ACTION_INSPECTOR,
            USER_ACTION_SEARCH_CHIP,
            USER_ACTION_SEARCH_HISTORY
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface UserAction {
    }

    // Codes representing different approaches to copy/move a document. OPMODE_PROVIDER indicates
    // it's an optimized operation provided by providers; OPMODE_CONVERTED means it's converted from
    // a virtual file; and OPMODE_CONVENTIONAL means it's byte copied.
    public static final int OPMODE_UNKNOWN = 0;
    public static final int OPMODE_PROVIDER = 1;
    public static final int OPMODE_CONVERTED = 2;
    public static final int OPMODE_CONVENTIONAL = 3;

    @IntDef({OPMODE_UNKNOWN, OPMODE_PROVIDER, OPMODE_CONVERTED, OPMODE_CONVENTIONAL})
    @Retention(RetentionPolicy.SOURCE)
    public @interface FileOpMode {
    }

    // Codes representing different menu actions.
    static final int ACTION_UNKNOWN = 0;
    static final int ACTION_OPEN = 1;
    static final int ACTION_CREATE = 2;
    static final int ACTION_GET_CONTENT = 3;
    static final int ACTION_OPEN_TREE = 4;
    static final int ACTION_PICK_COPY_DESTINATION = 5;
    static final int ACTION_BROWSE = 6;
    static final int ACTION_OTHER = 7;

    @IntDef(flag = true, value = {
            ACTION_UNKNOWN,
            ACTION_OPEN,
            ACTION_CREATE,
            ACTION_GET_CONTENT,
            ACTION_OPEN_TREE,
            ACTION_PICK_COPY_DESTINATION,
            ACTION_BROWSE,
            ACTION_OTHER
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MetricsAction {
    }

    public static final int AUTH_UNKNOWN = 0;
    public static final int AUTH_OTHER = 1;
    public static final int AUTH_MEDIA = 2;
    public static final int AUTH_STORAGE_INTERNAL = 3;
    public static final int AUTH_STORAGE_EXTERNAL = 4;
    public static final int AUTH_DOWNLOADS = 5;
    public static final int AUTH_MTP = 6;

    @IntDef(flag = true, value = {
            AUTH_UNKNOWN,
            AUTH_OTHER,
            AUTH_MEDIA,
            AUTH_STORAGE_INTERNAL,
            AUTH_STORAGE_EXTERNAL,
            AUTH_DOWNLOADS,
            AUTH_MTP
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface MetricsAuth {
    }

    // Types for logInvalidScopedAccessRequest
    public static final int SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS = 1;
    public static final int SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY = 2;
    public static final int SCOPED_DIRECTORY_ACCESS_ERROR = 3;
    public static final int SCOPED_DIRECTORY_ACCESS_DEPRECATED = 4;

    @IntDef(value = {
            SCOPED_DIRECTORY_ACCESS_INVALID_ARGUMENTS,
            SCOPED_DIRECTORY_ACCESS_INVALID_DIRECTORY,
            SCOPED_DIRECTORY_ACCESS_ERROR,
            SCOPED_DIRECTORY_ACCESS_DEPRECATED
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface InvalidScopedAccess {
    }

    // Codes representing different search types
    public static final int TYPE_UNKNOWN = 0;
    public static final int TYPE_CHIP_IMAGES = 1;
    public static final int TYPE_CHIP_AUDIOS = 2;
    public static final int TYPE_CHIP_VIDEOS = 3;
    public static final int TYPE_CHIP_DOCS = 4;
    public static final int TYPE_SEARCH_HISTORY = 5;
    public static final int TYPE_SEARCH_STRING = 6;

    @IntDef(flag = true, value = {
            TYPE_UNKNOWN,
            TYPE_CHIP_IMAGES,
            TYPE_CHIP_AUDIOS,
            TYPE_CHIP_VIDEOS,
            TYPE_CHIP_DOCS,
            TYPE_SEARCH_HISTORY,
            TYPE_SEARCH_STRING
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchType {}

    // Codes representing different search types
    public static final int SEARCH_UNKNOWN = 0;
    public static final int SEARCH_KEYWORD = 1;
    public static final int SEARCH_CHIPS = 2;
    public static final int SEARCH_KEYWORD_N_CHIPS = 3;

    @IntDef(flag = true, value = {
            SEARCH_UNKNOWN,
            SEARCH_KEYWORD,
            SEARCH_CHIPS,
            SEARCH_KEYWORD_N_CHIPS
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface SearchMode {}
}