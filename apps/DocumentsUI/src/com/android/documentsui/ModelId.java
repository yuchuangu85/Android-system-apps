package com.android.documentsui;

import static com.android.documentsui.base.DocumentInfo.getCursorString;

import android.database.Cursor;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.Log;

import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.roots.RootCursorWrapper;

import java.util.ArrayList;
import java.util.List;

public class ModelId {
    private final static String TAG = "ModelId";

    public static final String build(Uri uri) {
        String documentId;
        try {
            documentId = DocumentsContract.getDocumentId(uri);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Failed to get document id.", e);
            return null;
        }
        String authority;
        authority = uri.getAuthority();
        return ModelId.build(authority, documentId);
    }

    public static final String build(DocumentInfo docInfo) {
        if (docInfo == null) {
            return null;
        }
        return ModelId.build(docInfo.authority, docInfo.documentId);
    }

    public static final String build(Cursor cursor) {
        if (cursor == null) {
            return null;
        }
        return ModelId.build(getCursorString(cursor, RootCursorWrapper.COLUMN_AUTHORITY),
                getCursorString(cursor, DocumentsContract.Document.COLUMN_DOCUMENT_ID));
    }

    public static final ArrayList<String> build(ArrayList<Uri> uris) {
        if (uris == null || uris.isEmpty()) {
            return null;
        }
        ArrayList<String> ids = new ArrayList<>();
        String id;
        for (Uri uri : uris) {
            id = ModelId.build(uri);
            if (id != null) {
                ids.add(id);
            }
        }
        return ids;
    }

    public static final String build(String authority, String docId) {
        if (authority == null || authority.isEmpty() || docId == null || docId.isEmpty()) {
            return null;
        }
        return authority + "|" + docId;
    }
}
