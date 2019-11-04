/*
 * Copyright (C) 2016 The Android Open Source Project
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

import static android.content.ContentResolver.wrap;

import static com.android.documentsui.DocumentsApplication.acquireUnstableProviderOrThrow;

import android.content.ContentProviderClient;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.RemoteException;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Path;
import android.provider.DocumentsProvider;
import android.util.Log;

import androidx.annotation.Nullable;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Providers;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.State;
import com.android.documentsui.files.LauncherActivity;
import com.android.documentsui.picker.PickResult;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;

import java.io.FileNotFoundException;
import java.util.List;

/**
 * Methods for logging metrics.
 */
public final class Metrics {
    private static final String TAG = "Metrics";

    /**
     * Logs when DocumentsUI is started, and how. Call this when DocumentsUI first starts up.
     *
     * @param state
     * @param intent
     */
    public static void logActivityLaunch(State state, Intent intent) {
        Uri uri = intent.getData();
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_LAUNCH_REPORTED,
                toMetricsAction(state.action), false,
                sanitizeMime(intent.getType()), sanitizeRoot(uri));
    }

    /**
     * Logs when DocumentsUI are launched with {@link DocumentsContract#EXTRA_INITIAL_URI}.
     *
     * @param state used to resolve action
     * @param rootUri the resolved rootUri, or {@code null} if the provider doesn't
     *                support {@link DocumentsProvider#findDocumentPath(String, String)}
     */
    public static void logLaunchAtLocation(State state, @Nullable Uri rootUri) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_LAUNCH_REPORTED,
                toMetricsAction(state.action), true,
                MetricConsts.MIME_UNKNOWN, sanitizeRoot(rootUri));
    }

    /**
     * Logs a root visited event in file managers. Call this when the user
     * taps on a root in {@link com.android.documentsui.sidebar.RootsFragment}.
     * @param scope
     * @param info
     */
    public static void logRootVisited(@MetricConsts.ContextScope int scope, RootInfo info) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_ROOT_VISITED, scope, sanitizeRoot(info));
    }

    /**
     * Logs an app visited event in file pickers. Call this when the user visits
     * on an app in the RootsFragment.
     *
     * @param info
     */
    public static void logAppVisited(ResolveInfo info) {
        DocumentsStatsLog.write(
                DocumentsStatsLog.DOCS_UI_ROOT_VISITED,
                MetricConsts.PICKER_SCOPE, sanitizeRoot(info));
    }

    /**
     * Logs file operation stats. Call this when a file operation has completed. The given
     * DocumentInfo is only used to distinguish broad categories of actions (e.g. copying from one
     * provider to another vs copying within a given provider).  No PII is logged.
     *
     * @param operationType
     * @param srcs
     * @param dst
     */
    public static void logFileOperation(
            @OpType int operationType,
            List<DocumentInfo> srcs,
            @Nullable DocumentInfo dst) {
        ProviderCounts counts = new ProviderCounts();
        countProviders(counts, srcs, dst);
        if (counts.intraProvider > 0) {
            logIntraProviderFileOps(dst.authority, operationType);
        }
        if (counts.systemProvider > 0) {
            // Log file operations on system providers.
            logInterProviderFileOps(MetricConsts.PROVIDER_SYSTEM, dst, operationType);
        }
        if (counts.externalProvider > 0) {
            // Log file operations on external providers.
            logInterProviderFileOps(MetricConsts.PROVIDER_EXTERNAL, dst, operationType);
        }
    }

    public static void logFileOperated(
            @OpType int operationType, @MetricConsts.FileOpMode int approach) {
        switch (operationType) {
            case FileOperationService.OPERATION_COPY:
                DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_FILE_OP_COPY_MOVE_MODE_REPORTED,
                        MetricConsts.FILEOP_COPY, approach);
                break;
            case FileOperationService.OPERATION_MOVE:
                DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_FILE_OP_COPY_MOVE_MODE_REPORTED,
                        MetricConsts.FILEOP_MOVE, approach);
                break;
        }
    }

    /**
     * Logs create directory operation. It is a part of file operation stats. We do not
     * differentiate between internal and external locations, all create directory operations are
     * logged under COUNT_FILEOP_SYSTEM. Call this when a create directory operation has completed.
     */
    public static void logCreateDirOperation() {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                MetricConsts.PROVIDER_SYSTEM, MetricConsts.FILEOP_CREATE_DIR);
    }

    /**
     * Logs rename file operation. It is a part of file operation stats. We do not differentiate
     * between internal and external locations, all rename operations are logged under
     * COUNT_FILEOP_SYSTEM. Call this when a rename file operation has completed.
     */
    public static void logRenameFileOperation() {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                MetricConsts.PROVIDER_SYSTEM, MetricConsts.FILEOP_RENAME);
    }

    /**
     * Logs some kind of file operation error. Call this when a file operation (e.g. copy, delete)
     * fails.
     *
     * @param operationType
     * @param failedFiles
     */
    public static void logFileOperationErrors(@OpType int operationType,
            List<DocumentInfo> failedFiles, List<Uri> failedUris) {
        ProviderCounts counts = new ProviderCounts();
        countProviders(counts, failedFiles, null);
        // TODO: Report URI errors separate from file operation errors.
        countProviders(counts, failedUris);
        @MetricConsts.FileOp int opCode = MetricConsts.FILEOP_OTHER_ERROR;
        switch (operationType) {
            case FileOperationService.OPERATION_COPY:
                opCode = MetricConsts.FILEOP_COPY_ERROR;
                break;
            case FileOperationService.OPERATION_COMPRESS:
                opCode = MetricConsts.FILEOP_COMPRESS_ERROR;
                break;
            case FileOperationService.OPERATION_EXTRACT:
                opCode = MetricConsts.FILEOP_EXTRACT_ERROR;
                break;
            case FileOperationService.OPERATION_DELETE:
                opCode = MetricConsts.FILEOP_DELETE_ERROR;
                break;
            case FileOperationService.OPERATION_MOVE:
                opCode = MetricConsts.FILEOP_MOVE_ERROR;
                break;
        }
        if (counts.systemProvider > 0) {
            DocumentsStatsLog.write(
                    DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                    MetricConsts.PROVIDER_SYSTEM, opCode);
        }
        if (counts.externalProvider > 0) {
            DocumentsStatsLog.write(
                    DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                    MetricConsts.PROVIDER_EXTERNAL, opCode);
        }
    }

    public static void logFileOperationFailure(
            Context context, @MetricConsts.SubFileOp int subFileOp, Uri docUri) {
        final String authority = docUri.getAuthority();
        switch (authority) {
            case Providers.AUTHORITY_MEDIA:
                DocumentsStatsLog.write(
                        DocumentsStatsLog.DOCS_UI_FILE_OP_FAILURE,
                        MetricConsts.AUTH_MEDIA, subFileOp);
                break;
            case Providers.AUTHORITY_STORAGE:
                logStorageFileOperationFailure(context, subFileOp, docUri);
                break;
            case Providers.AUTHORITY_DOWNLOADS:
                DocumentsStatsLog.write(
                        DocumentsStatsLog.DOCS_UI_FILE_OP_FAILURE,
                        MetricConsts.AUTH_DOWNLOADS, subFileOp);
                break;
            case Providers.AUTHORITY_MTP:
                DocumentsStatsLog.write(
                        DocumentsStatsLog.DOCS_UI_FILE_OP_FAILURE,
                        MetricConsts.AUTH_MTP, subFileOp);
                break;
            default:
                DocumentsStatsLog.write(
                        DocumentsStatsLog.DOCS_UI_FILE_OP_FAILURE,
                        MetricConsts.AUTH_OTHER, subFileOp);
                break;
        }
    }

    /**
     * Logs create directory operation error. We do not differentiate between internal and external
     * locations, all create directory errors are logged under COUNT_FILEOP_SYSTEM. Call this when a
     * create directory operation fails.
     */
    public static void logCreateDirError() {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                MetricConsts.PROVIDER_SYSTEM, MetricConsts.FILEOP_CREATE_DIR_ERROR);
    }

    /**
     * Logs rename file operation error. We do not differentiate between internal and external
     * locations, all rename errors are logged under COUNT_FILEOP_SYSTEM. Call this
     * when a rename file operation fails.
     */
    public static void logRenameFileError() {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                MetricConsts.PROVIDER_SYSTEM, MetricConsts.FILEOP_RENAME_ERROR);
    }

    /**
     * Logs the cancellation of a file operation.  Call this when a Job is canceled.
     *
     * @param operationType
     */
    public static void logFileOperationCancelled(@OpType int operationType) {
        DocumentsStatsLog.write(
                DocumentsStatsLog.DOCS_UI_FILE_OP_CANCELED, toMetricsOpType(operationType));
    }

    /**
     * Logs startup time in milliseconds.
     *
     * @param startupMs Startup time in milliseconds.
     */
    public static void logStartupMs(int startupMs) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_STARTUP_MS, startupMs);
    }

    private static void logInterProviderFileOps(
            @MetricConsts.Provider int providerType,
            DocumentInfo dst,
            @OpType int operationType) {
        if (operationType == FileOperationService.OPERATION_DELETE) {
            DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                    providerType, MetricConsts.FILEOP_DELETE);
        } else {
            assert(dst != null);
            @MetricConsts.Provider int opProviderType = isSystemProvider(dst.authority)
                    ? MetricConsts.PROVIDER_SYSTEM : MetricConsts.PROVIDER_EXTERNAL;
            DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                    providerType, getOpCode(operationType, opProviderType));
        }
    }

    private static void logIntraProviderFileOps(String authority, @OpType int operationType) {
        @MetricConsts.Provider int providerType = isSystemProvider(authority)
                ? MetricConsts.PROVIDER_SYSTEM : MetricConsts.PROVIDER_EXTERNAL;
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_PROVIDER_FILE_OP,
                providerType, getOpCode(operationType, MetricConsts.PROVIDER_INTRA));
    }

    /**
     * Logs the action that was started by user.
     *
     * @param userAction
     */
    public static void logUserAction(@MetricConsts.UserAction int userAction) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_USER_ACTION_REPORTED, userAction);
    }

    public static void logPickerLaunchedFrom(String packgeName) {
        DocumentsStatsLog.write(
                DocumentsStatsLog.DOCS_UI_PICKER_LAUNCHED_FROM_REPORTED, packgeName);
    }

    public static void logSearchType(int searchType) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_SEARCH_TYPE_REPORTED, searchType);
    }

    public static void logSearchMode(boolean isKeywordSearch, boolean isChipsSearch) {
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_SEARCH_MODE_REPORTED,
                getSearchMode(isKeywordSearch, isChipsSearch));
    }

    public static void logPickResult(PickResult result) {
        DocumentsStatsLog.write(
                DocumentsStatsLog.DOCS_UI_PICK_RESULT_REPORTED,
                result.getActionCount(),
                result.getDuration(),
                result.getFileCount(),
                result.isSearching(),
                result.getRoot(),
                result.getMimeType(),
                result.getRepeatedPickTimes());
    }

    private static void logStorageFileOperationFailure(
            Context context, @MetricConsts.SubFileOp int subFileOp, Uri docUri) {
        assert(Providers.AUTHORITY_STORAGE.equals(docUri.getAuthority()));
        boolean isInternal;
        try (ContentProviderClient client = acquireUnstableProviderOrThrow(
                context.getContentResolver(), Providers.AUTHORITY_STORAGE)) {
            final Path path = DocumentsContract.findDocumentPath(wrap(client), docUri);
            final ProvidersAccess providers = DocumentsApplication.getProvidersCache(context);
            final RootInfo root = providers.getRootOneshot(
                    Providers.AUTHORITY_STORAGE, path.getRootId());
            isInternal = !root.supportsEject();
        } catch (FileNotFoundException | RemoteException | RuntimeException e) {
            Log.e(TAG, "Failed to obtain its root info. Log the metrics as internal.", e);
            // It's not very likely to have an external storage so log it as internal.
            isInternal = true;
        }
        @MetricConsts.MetricsAuth final int authority = isInternal
                ? MetricConsts.AUTH_STORAGE_INTERNAL : MetricConsts.AUTH_STORAGE_EXTERNAL;
        DocumentsStatsLog.write(DocumentsStatsLog.DOCS_UI_FILE_OP_FAILURE, authority, subFileOp);
    }

    /**
     * Generates an integer identifying the given root. For privacy, this function only recognizes a
     * small set of hard-coded roots (ones provided by the system). Other roots are all grouped into
     * a single ROOT_OTHER bucket.
     */
    private static @MetricConsts.Root int sanitizeRoot(Uri uri) {
        if (uri == null || uri.getAuthority() == null || LauncherActivity.isLaunchUri(uri)) {
            return MetricConsts.ROOT_NONE;
        }
        switch (uri.getAuthority()) {
            case Providers.AUTHORITY_MEDIA:
                String rootId = getRootIdSafely(uri);
                if (rootId == null) {
                    return MetricConsts.ROOT_NONE;
                }
                switch (rootId) {
                    case Providers.ROOT_ID_AUDIO:
                        return MetricConsts.ROOT_AUDIO;
                    case Providers.ROOT_ID_IMAGES:
                        return MetricConsts.ROOT_IMAGES;
                    case Providers.ROOT_ID_VIDEOS:
                        return MetricConsts.ROOT_VIDEOS;
                    default:
                        return MetricConsts.ROOT_OTHER_DOCS_PROVIDER;
                }
            case Providers.AUTHORITY_STORAGE:
                rootId = getRootIdSafely(uri);
                if (rootId == null) {
                    return MetricConsts.ROOT_NONE;
                }
                if (Providers.ROOT_ID_HOME.equals(rootId)) {
                    return MetricConsts.ROOT_HOME;
                } else {
                    return MetricConsts.ROOT_DEVICE_STORAGE;
                }
            case Providers.AUTHORITY_DOWNLOADS:
                return MetricConsts.ROOT_DOWNLOADS;
            case Providers.AUTHORITY_MTP:
                return MetricConsts.ROOT_MTP;
            default:
                return MetricConsts.ROOT_OTHER_DOCS_PROVIDER;
        }
    }

    /** @see #sanitizeRoot(Uri) */
    public static @MetricConsts.Root int sanitizeRoot(RootInfo root) {
        if (root.isRecents()) {
            // Recents root is special and only identifiable via this method call. Other roots are
            // identified by URI.
            return MetricConsts.ROOT_RECENTS;
        } else {
            return sanitizeRoot(root.getUri());
        }
    }

    /** @see #sanitizeRoot(Uri) */
    public static @MetricConsts.Root int sanitizeRoot(ResolveInfo info) {
        // Log all apps under a single bucket in the roots histogram.
        return MetricConsts.ROOT_THIRD_PARTY_APP;
    }

    /**
     * Generates an int identifying a mime type. For privacy, this function only recognizes a small
     * set of hard-coded types. For any other type, this function returns "other".
     *
     * @param mimeType
     * @return
     */
    public static @MetricConsts.Mime int sanitizeMime(String mimeType) {
        if (mimeType == null) {
            return MetricConsts.MIME_NONE;
        } else if ("*/*".equals(mimeType)) {
            return MetricConsts.MIME_ANY;
        } else {
            String type = mimeType.substring(0, mimeType.indexOf('/'));
            switch (type) {
                case "application":
                    return MetricConsts.MIME_APPLICATION;
                case "audio":
                    return MetricConsts.MIME_AUDIO;
                case "image":
                    return MetricConsts.MIME_IMAGE;
                case "message":
                    return MetricConsts.MIME_MESSAGE;
                case "multipart":
                    return MetricConsts.MIME_MULTIPART;
                case "text":
                    return MetricConsts.MIME_TEXT;
                case "video":
                    return MetricConsts.MIME_VIDEO;
            }
        }
        // Bucket all other types into one bucket.
        return MetricConsts.MIME_OTHER;
    }

    private static boolean isSystemProvider(String authority) {
        switch (authority) {
            case Providers.AUTHORITY_MEDIA:
            case Providers.AUTHORITY_STORAGE:
            case Providers.AUTHORITY_DOWNLOADS:
                return true;
            default:
                return false;
        }
    }

    /**
     * @param operation
     * @param providerType
     * @return An opcode, suitable for use as histogram bucket, for the given operation/provider
     *         combination.
     */
    private static @MetricConsts.FileOp int getOpCode(
            @OpType int operation, @MetricConsts.Provider int providerType) {
        switch (operation) {
            case FileOperationService.OPERATION_COPY:
                switch (providerType) {
                    case MetricConsts.PROVIDER_INTRA:
                        return MetricConsts.FILEOP_COPY_INTRA_PROVIDER;
                    case MetricConsts.PROVIDER_SYSTEM:
                        return MetricConsts.FILEOP_COPY_SYSTEM_PROVIDER;
                    case MetricConsts.PROVIDER_EXTERNAL:
                        return MetricConsts.FILEOP_COPY_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_COMPRESS:
                switch (providerType) {
                    case MetricConsts.PROVIDER_INTRA:
                        return MetricConsts.FILEOP_COMPRESS_INTRA_PROVIDER;
                    case MetricConsts.PROVIDER_SYSTEM:
                        return MetricConsts.FILEOP_COMPRESS_SYSTEM_PROVIDER;
                    case MetricConsts.PROVIDER_EXTERNAL:
                        return MetricConsts.FILEOP_COMPRESS_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_EXTRACT:
                switch (providerType) {
                    case MetricConsts.PROVIDER_INTRA:
                        return MetricConsts.FILEOP_EXTRACT_INTRA_PROVIDER;
                    case MetricConsts.PROVIDER_SYSTEM:
                        return MetricConsts.FILEOP_EXTRACT_SYSTEM_PROVIDER;
                    case MetricConsts.PROVIDER_EXTERNAL:
                        return MetricConsts.FILEOP_EXTRACT_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_MOVE:
                switch (providerType) {
                    case MetricConsts.PROVIDER_INTRA:
                        return MetricConsts.FILEOP_MOVE_INTRA_PROVIDER;
                    case MetricConsts.PROVIDER_SYSTEM:
                        return MetricConsts.FILEOP_MOVE_SYSTEM_PROVIDER;
                    case MetricConsts.PROVIDER_EXTERNAL:
                        return MetricConsts.FILEOP_MOVE_EXTERNAL_PROVIDER;
                }
            case FileOperationService.OPERATION_DELETE:
                return MetricConsts.FILEOP_DELETE;
            default:
                Log.w(TAG, "Unrecognized operation type when logging a file operation");
                return MetricConsts.FILEOP_OTHER;
        }
    }

    /**
     * Maps FileOperationService OpType values, to MetricsOpType values.
     */
    private static @MetricConsts.FileOp int toMetricsOpType(@OpType int operation) {
        switch (operation) {
            case FileOperationService.OPERATION_COPY:
                return MetricConsts.FILEOP_COPY;
            case FileOperationService.OPERATION_MOVE:
                return MetricConsts.FILEOP_MOVE;
            case FileOperationService.OPERATION_DELETE:
                return MetricConsts.FILEOP_DELETE;
            case FileOperationService.OPERATION_UNKNOWN:
            default:
                return MetricConsts.FILEOP_UNKNOWN;
        }
    }

    private static @MetricConsts.MetricsAction int toMetricsAction(int action) {
        switch(action) {
            case State.ACTION_OPEN:
                return MetricConsts.ACTION_OPEN;
            case State.ACTION_CREATE:
                return MetricConsts.ACTION_CREATE;
            case State.ACTION_GET_CONTENT:
                return MetricConsts.ACTION_GET_CONTENT;
            case State.ACTION_OPEN_TREE:
                return MetricConsts.ACTION_OPEN_TREE;
            case State.ACTION_BROWSE:
                return MetricConsts.ACTION_BROWSE;
            case State.ACTION_PICK_COPY_DESTINATION:
                return MetricConsts.ACTION_PICK_COPY_DESTINATION;
            default:
                return MetricConsts.ACTION_OTHER;
        }
    }

    private static int getSearchMode(boolean isKeyword, boolean isChip) {
        if (isKeyword && isChip) {
            return MetricConsts.SEARCH_KEYWORD_N_CHIPS;
        } else if (isKeyword) {
            return MetricConsts.SEARCH_KEYWORD;
        } else if (isChip) {
            return MetricConsts.SEARCH_CHIPS;
        } else {
            return MetricConsts.SEARCH_UNKNOWN;
        }
    }

    /**
     * Count the given src documents and provide a tally of how many come from the same provider as
     * the dst document (if a dst is provided), how many come from system providers, and how many
     * come from external 3rd-party providers.
     */
    private static void countProviders(
            ProviderCounts counts, List<DocumentInfo> srcs, @Nullable DocumentInfo dst) {
        for (DocumentInfo doc: srcs) {
            countForAuthority(counts, doc.authority, dst);
        }
    }

    /**
     * Count the given uris and provide a tally of how many come from the same provider as
     * the dst document (if a dst is provided), how many come from system providers, and how many
     * come from external 3rd-party providers.
     */
    private static void countProviders(ProviderCounts counts, List<Uri> uris) {
        for (Uri uri: uris) {
            countForAuthority(counts, uri.getAuthority(), null);
        }
    }

    private static void countForAuthority(
            ProviderCounts counts, String authority, @Nullable DocumentInfo dst) {
        if (dst != null && authority.equals(dst.authority)) {
            counts.intraProvider++;
        } else if (isSystemProvider(authority)){
            counts.systemProvider++;
        } else {
            counts.externalProvider++;
        }
    }

    private static class ProviderCounts {
        int intraProvider;
        int systemProvider;
        int externalProvider;
    }

    private static String getRootIdSafely(Uri uri) {
        try {
            return DocumentsContract.getRootId(uri);
        } catch (IllegalArgumentException iae) {
            Log.w(TAG, "Invalid root Uri " + uri.toSafeString());
        }
        return null;
    }
}
