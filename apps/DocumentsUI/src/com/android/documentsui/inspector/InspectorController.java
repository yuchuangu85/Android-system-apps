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
package com.android.documentsui.inspector;

import static androidx.core.util.Preconditions.checkArgument;

import androidx.annotation.StringRes;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.DocumentsContract;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;
import android.view.View;
import android.view.View.OnClickListener;

import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.inspector.actions.Action;
import com.android.documentsui.inspector.actions.ClearDefaultAppAction;
import com.android.documentsui.inspector.actions.ShowInProviderAction;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.ui.Snackbars;

import java.util.function.Consumer;
/**
 * A controller that coordinates retrieving document information and sending it to the view.
 */
public final class InspectorController {

    private final DataSupplier mLoader;
    private final HeaderDisplay mHeader;
    private final DetailsDisplay mDetails;
    private final MediaDisplay mMedia;
    private final ActionDisplay mShowProvider;
    private final ActionDisplay mAppDefaults;
    private final DebugDisplay mDebugView;
    private final Context mContext;
    private final PackageManager mPackageManager;
    private final ProvidersAccess mProviders;
    private final Runnable mErrorSnackbar;
    private final String mTitle;
    private final boolean mShowDebug;

    /**
     * InspectorControllerTest relies on this controller.
     */
    @VisibleForTesting
    public InspectorController(
            Context context,
            DataSupplier loader,
            PackageManager pm,
            ProvidersAccess providers,
            HeaderDisplay header,
            DetailsDisplay details,
            MediaDisplay media,
            ActionDisplay showProvider,
            ActionDisplay appDefaults,
            DebugDisplay debugView,
            String title,
            boolean showDebug,
            Runnable errorRunnable) {

        checkArgument(context != null);
        checkArgument(loader != null);
        checkArgument(pm != null);
        checkArgument(providers != null);
        checkArgument(header != null);
        checkArgument(details != null);
        checkArgument(media != null);
        checkArgument(showProvider != null);
        checkArgument(appDefaults != null);
        checkArgument(debugView != null);
        checkArgument(errorRunnable != null);

        mContext = context;
        mLoader = loader;
        mPackageManager = pm;
        mProviders = providers;
        mHeader = header;
        mDetails = details;
        mMedia = media;
        mShowProvider = showProvider;
        mAppDefaults = appDefaults;
        mTitle = title;
        mShowDebug = showDebug;
        mDebugView = debugView;

        mErrorSnackbar = errorRunnable;
    }

    /**
     * @param activity
     * @param loader
     * @param layout
     * @param args Bundle of arguments passed to our host {@link InspectorActivity}. These
     *     can include extras that enable debug mode ({@link Shared#EXTRA_SHOW_DEBUG}
     *     and override the file title (@link {@link Intent#EXTRA_TITLE}).
     */
    public InspectorController(Activity activity, DataSupplier loader, View layout,
            String title, boolean showDebug) {
        this(activity,
            loader,
            activity.getPackageManager(),
            DocumentsApplication.getProvidersCache (activity),
            (HeaderView) layout.findViewById(R.id.inspector_header_view),
            (DetailsView) layout.findViewById(R.id.inspector_details_view),
            (MediaView) layout.findViewById(R.id.inspector_media_view),
            (ActionDisplay) layout.findViewById(R.id.inspector_show_in_provider_view),
            (ActionDisplay) layout.findViewById(R.id.inspector_app_defaults_view),
            (DebugView) layout.findViewById(R.id.inspector_debug_view),
            title,
            showDebug,
            () -> {
                // using a runnable to support unit testing this feature.
                Snackbars.showInspectorError(activity);
            }
        );

        if (showDebug) {
            DebugView view = (DebugView) layout.findViewById(R.id.inspector_debug_view);
            view.init(ProviderExecutor::forAuthority);
        }
    }

    public void reset() {
        mLoader.reset();
    }

    public void loadInfo(Uri uri) {
        mLoader.loadDocInfo(uri, this::updateView);
    }

    /**
     * Updates the view with documentInfo.
     */
    private void updateView(@Nullable DocumentInfo docInfo) {
        if (docInfo == null) {
            mErrorSnackbar.run();
        } else {
            mHeader.accept(docInfo);
            mDetails.accept(docInfo, mTitle != null ? mTitle : docInfo.displayName);

            if (docInfo.isDirectory()) {
                mLoader.loadDirCount(docInfo, this::displayChildCount);
            } else {

                mShowProvider.setVisible(docInfo.isSettingsSupported());
                if (docInfo.isSettingsSupported()) {
                    Action showProviderAction =
                        new ShowInProviderAction(mContext, mPackageManager, docInfo, mProviders);
                    mShowProvider.init(
                        showProviderAction,
                        (view) -> {
                            showInProvider(docInfo.derivedUri);
                        });
                }

                Action defaultAction =
                    new ClearDefaultAppAction(mContext, mPackageManager, docInfo);

                mAppDefaults.setVisible(defaultAction.canPerformAction());
            }

            if (docInfo.isMetadataSupported()) {
                mLoader.getDocumentMetadata(
                        docInfo.derivedUri,
                        (Bundle bundle) -> {
                            onDocumentMetadataLoaded(docInfo, bundle);
                        });
            }
            mMedia.setVisible(!mMedia.isEmpty());

            if (mShowDebug) {
                mDebugView.accept(docInfo);
            }
            mDebugView.setVisible(mShowDebug && !mDebugView.isEmpty());
        }
    }

    private void onDocumentMetadataLoaded(DocumentInfo doc, @Nullable Bundle metadata) {
        if (metadata == null) {
            return;
        }

        Runnable geoClickListener = null;
        if (MetadataUtils.hasGeoCoordinates(metadata)) {
            float[] coords = MetadataUtils.getGeoCoordinates(metadata);
            final Intent intent = createGeoIntent(coords[0], coords[1], doc.displayName);
            if (hasHandler(intent)) {
                geoClickListener = () -> {
                    startActivity(intent);
                };
            }
        }

        mMedia.accept(doc, metadata, geoClickListener);

        if (mShowDebug) {
            mDebugView.accept(metadata);
        }
    }

    /**
     * Displays a directory's information to the view.
     *
     * @param count - number of items in the directory.
     */
    private void displayChildCount(Integer count) {
        mDetails.setChildrenCount(count);
    }

    private void startActivity(Intent intent) {
        assert hasHandler(intent);
        mContext.startActivity(intent);
    }

    /**
     * checks that we can handle a geo-intent.
     */
    private boolean hasHandler(Intent intent) {
        return mPackageManager.resolveActivity(intent, 0) != null;
    }

    /**
     * Creates a geo-intent for opening a location in maps.
     *
     * @see https://developer.android.com/guide/components/intents-common.html#Maps
     */
    private static Intent createGeoIntent(
            float latitude, float longitude, @Nullable String label) {
        label = Uri.encode(label == null ? "" : label);
        String data = "geo:0,0?q=" + latitude + " " + longitude + "(" + label + ")";
        Uri uri = Uri.parse(data);
        return new Intent(Intent.ACTION_VIEW, uri);
    }

    /**
     * Shows the selected document in it's content provider.
     *
     * @param DocumentInfo whose flag FLAG_SUPPORTS_SETTINGS is set.
     */
    public void showInProvider(Uri uri) {

        Intent intent = new Intent(DocumentsContract.ACTION_DOCUMENT_SETTINGS);
        intent.setPackage(mProviders.getPackageName(uri.getAuthority()));
        intent.addCategory(Intent.CATEGORY_DEFAULT);
        intent.setData(uri);
        mContext.startActivity(intent);
    }

    /**
     * Interface for loading all the various forms of document data. This primarily
     * allows us to easily supply test data in tests.
     */
    public interface DataSupplier {

        /**
         * Starts the Asynchronous process of loading file data.
         *
         * @param uri - A content uri to query metadata from.
         * @param callback - Function to be called when the loader has finished loading metadata. A
         * DocumentInfo will be sent to this method. DocumentInfo may be null.
         */
        void loadDocInfo(Uri uri, Consumer<DocumentInfo> callback);

        /**
         * Loads a folders item count.
         * @param directory - a documentInfo thats a directory.
         * @param callback - Function to be called when the loader has finished loading the number
         * of children.
         */
        void loadDirCount(DocumentInfo directory, Consumer<Integer> callback);

        /**
         * Deletes all loader id's when android lifecycle ends.
         */
        void reset();

        /**
         * @param uri
         * @param callback
         */
        void getDocumentMetadata(Uri uri, Consumer<Bundle> callback);
    }

    /**
     * This interface is for unit testing.
     */
    public interface Display {
        /**
         * Makes the action visible.
         */
        void setVisible(boolean visible);
    }

    /**
     * This interface is for unit testing.
     */
    public interface ActionDisplay extends Display {

        /**
         * Initializes the view based on the action.
         * @param action - ClearDefaultAppAction or ShowInProviderAction
         * @param listener - listener for when the action is pressed.
         */
        void init(Action action, OnClickListener listener);

        void setActionHeader(String header);

        void setAppIcon(Drawable icon);

        void setAppName(String name);

        void showAction(boolean visible);
    }

    /**
     * Provides details about a file.
     */
    public interface HeaderDisplay {
        void accept(DocumentInfo info);
    }

    /**
     * Provides basic details about a file.
     */
    public interface DetailsDisplay {

        void accept(DocumentInfo info, String displayName);

        void setChildrenCount(int count);
    }

    /**
     * Provides details about a media file.
     */
    public interface MediaDisplay extends Display {
        void accept(DocumentInfo info, Bundle metadata, @Nullable Runnable geoClickListener);

        /**
         * Returns true if there are now rows in the display. Does not consider the title.
         */
        boolean isEmpty();
    }

    /**
     * Provides details about a media file.
     */
    public interface DebugDisplay extends Display {
        void accept(DocumentInfo info);
        void accept(Bundle metadata);

        /**
         * Returns true if there are now rows in the display. Does not consider the title.
         */
        boolean isEmpty();
    }

    /**
     * Displays a table of image metadata.
     */
    public interface TableDisplay extends Display {

        /**
         * Adds a row in the table.
         */
        void put(@StringRes int keyId, CharSequence value);

        /**
         * Adds a row in the table and makes it clickable.
         */
        void put(@StringRes int keyId, CharSequence value, OnClickListener callback);

        /**
         * Returns true if there are now rows in the display. Does not consider the title.
         */
        boolean isEmpty();
    }
}