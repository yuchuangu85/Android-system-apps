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

package com.android.documentsui.picker;

import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.content.ClipData;
import android.content.ComponentName;
import android.content.Intent;
import android.content.QuickViewConstants;
import android.content.pm.ResolveInfo;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.Settings;
import android.util.Log;

import androidx.annotation.VisibleForTesting;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;

import com.android.documentsui.AbstractActionHandler;
import com.android.documentsui.ActivityConfig;
import com.android.documentsui.DocumentsAccess;
import com.android.documentsui.Injector;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.Model;
import com.android.documentsui.base.BooleanConsumer;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.Lookup;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AnimationView;
import com.android.documentsui.files.QuickViewIntentBuilder;
import com.android.documentsui.picker.ActionHandler.Addons;
import com.android.documentsui.queries.SearchViewManager;
import com.android.documentsui.roots.ProvidersAccess;
import com.android.documentsui.services.FileOperationService;

import java.util.Arrays;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

/**
 * Provides {@link PickActivity} action specializations to fragments.
 */
class ActionHandler<T extends FragmentActivity & Addons> extends AbstractActionHandler<T> {

    private static final String TAG = "PickerActionHandler";
    private static final String[] PREVIEW_FEATURES = {
            QuickViewConstants.FEATURE_VIEW
    };

    private final Features mFeatures;
    private final ActivityConfig mConfig;
    private final Model mModel;
    private final LastAccessedStorage mLastAccessed;

    private UpdatePickResultTask mUpdatePickResultTask;

    ActionHandler(
        T activity,
        State state,
        ProvidersAccess providers,
        DocumentsAccess docs,
        SearchViewManager searchMgr,
        Lookup<String, Executor> executors,
        Injector injector,
        LastAccessedStorage lastAccessed) {
        super(activity, state, providers, docs, searchMgr, executors, injector);

        mConfig = injector.config;
        mFeatures = injector.features;
        mModel = injector.getModel();
        mLastAccessed = lastAccessed;
        mUpdatePickResultTask = new UpdatePickResultTask(
            activity.getApplicationContext(), mInjector.pickResult);
    }

    @Override
    public void initLocation(Intent intent) {
        assert(intent != null);

        // stack is initialized if it's restored from bundle, which means we're restoring a
        // previously stored state.
        if (mState.stack.isInitialized()) {
            if (DEBUG) {
                Log.d(TAG, "Stack already resolved for uri: " + intent.getData());
            }
            restoreRootAndDirectory();
            return;
        }

        // We set the activity title in AsyncTask.onPostExecute().
        // To prevent talkback from reading aloud the default title, we clear it here.
        mActivity.setTitle("");

        if (launchHomeForCopyDestination(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launching directly into Home directory for copy destination.");
            }
            return;
        }

        if (mFeatures.isLaunchToDocumentEnabled() && launchToInitialUri(intent)) {
            if (DEBUG) {
                Log.d(TAG, "Launched to initial uri.");
            }
            return;
        }

        if (DEBUG) {
            Log.d(TAG, "Load last accessed stack.");
        }
        loadLastAccessedStack();
    }

    @Override
    protected void launchToDefaultLocation() {
        loadLastAccessedStack();
    }

    private boolean launchHomeForCopyDestination(Intent intent) {
        // As a matter of policy we don't load the last used stack for the copy
        // destination picker (user is already in Files app).
        // Consensus was that the experice was too confusing.
        // In all other cases, where the user is visiting us from another app
        // we restore the stack as last used from that app.
        if (Shared.ACTION_PICK_COPY_DESTINATION.equals(intent.getAction())) {
            loadHomeDir();
            return true;
        }

        return false;
    }

    private boolean launchToInitialUri(Intent intent) {
        Uri uri = intent.getParcelableExtra(DocumentsContract.EXTRA_INITIAL_URI);
        if (uri != null) {
            if (DocumentsContract.isRootUri(mActivity, uri)) {
                loadRoot(uri);
                return true;
            } else if (DocumentsContract.isDocumentUri(mActivity, uri)) {
                return launchToDocument(uri);
            }
        }

        return false;
    }

    private void loadLastAccessedStack() {
        if (DEBUG) {
            Log.d(TAG, "Attempting to load last used stack for calling package.");
        }
        new LoadLastAccessedStackTask<>(
                mActivity, mLastAccessed, mState, mProviders, this::onLastAccessedStackLoaded)
                .execute();
    }

    private void onLastAccessedStackLoaded(@Nullable DocumentStack stack) {
        if (stack == null) {
            loadDefaultLocation();
        } else {
            mState.stack.reset(stack);
            mActivity.refreshCurrentRootAndDirectory(AnimationView.ANIM_NONE);
        }
    }

    public UpdatePickResultTask getUpdatePickResultTask() {
        return mUpdatePickResultTask;
    }

    private void updatePickResult(Intent intent, boolean isSearching, int root) {
        ClipData cdata = intent.getClipData();
        int fileCount = 0;
        Uri uri = null;

        // There are 2 cases that would be single-select:
        // 1. getData() isn't null and getClipData() is null.
        // 2. getClipData() isn't null and the item count of it is 1.
        if (intent.getData() != null && cdata == null) {
            fileCount = 1;
            uri = intent.getData();
        } else if (cdata != null) {
            fileCount = cdata.getItemCount();
            if (fileCount == 1) {
                uri = cdata.getItemAt(0).getUri();
            }
        }

        mInjector.pickResult.setFileCount(fileCount);
        mInjector.pickResult.setIsSearching(isSearching);
        mInjector.pickResult.setRoot(root);
        mInjector.pickResult.setFileUri(uri);
        getUpdatePickResultTask().execute();
    }

    private void loadDefaultLocation() {
        switch (mState.action) {
            case ACTION_CREATE:
            case ACTION_OPEN_TREE:
                loadHomeDir();
                break;
            case ACTION_GET_CONTENT:
            case ACTION_OPEN:
                loadRecent();
                break;
            default:
                throw new UnsupportedOperationException("Unexpected action type: " + mState.action);
        }
    }

    @Override
    public void showAppDetails(ResolveInfo info) {
        mInjector.pickResult.increaseActionCount();
        final Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        intent.setData(Uri.fromParts("package", info.activityInfo.packageName, null));
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        mActivity.startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (DEBUG) {
            Log.d(TAG, "onActivityResult() code=" + resultCode);
        }

        // Only relay back results when not canceled; otherwise stick around to
        // let the user pick another app/backend.
        switch (requestCode) {
            case CODE_FORWARD:
                onExternalAppResult(resultCode, data);
                break;
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    private void onExternalAppResult(int resultCode, Intent data) {
        if (resultCode != FragmentActivity.RESULT_CANCELED) {
            // Remember that we last picked via external app
            mLastAccessed.setLastAccessedToExternalApp(mActivity);

            updatePickResult(data, false, MetricConsts.ROOT_THIRD_PARTY_APP);

            // Pass back result to original caller
            mActivity.setResult(resultCode, data, 0);
            mActivity.finish();
        }
    }

    @Override
    public void openInNewWindow(DocumentStack path) {
        // Open new window support only depends on vanilla Activity, so it is
        // implemented in our parent class. But we don't support that in
        // picking. So as a matter of defensiveness, we override that here.
        throw new UnsupportedOperationException("Can't open in new window");
    }

    @Override
    public void openRoot(RootInfo root) {
        Metrics.logRootVisited(MetricConsts.PICKER_SCOPE, root);
        mInjector.pickResult.increaseActionCount();
        mActivity.onRootPicked(root);
    }

    @Override
    public void openRoot(ResolveInfo info) {
        Metrics.logAppVisited(info);
        mInjector.pickResult.increaseActionCount();
        final Intent intent = new Intent(mActivity.getIntent());
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_FORWARD_RESULT);
        intent.setComponent(new ComponentName(
                info.activityInfo.applicationInfo.packageName, info.activityInfo.name));
        mActivity.startActivityForResult(intent, CODE_FORWARD);
    }

    @Override
    public void springOpenDirectory(DocumentInfo doc) {
    }

    @Override
    public boolean openItem(ItemDetails<String> details, @ViewType int type,
            @ViewType int fallback) {
        mInjector.pickResult.increaseActionCount();
        DocumentInfo doc = mModel.getDocument(details.getSelectionKey());
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: "
                    + details.getSelectionKey());
            return false;
        }

        if (mConfig.isDocumentEnabled(doc.mimeType, doc.flags, mState)) {
            mActivity.onDocumentPicked(doc);
            mSelectionMgr.clearSelection();
            return !doc.isDirectory();
        }
        return false;
    }

    @Override
    public boolean previewItem(ItemDetails<String> details) {
        mInjector.pickResult.increaseActionCount();
        final DocumentInfo doc = mModel.getDocument(details.getSelectionKey());
        if (doc == null) {
            Log.w(TAG, "Can't view item. No Document available for modeId: "
                    + details.getSelectionKey());
            return false;
        }
        return priviewDocument(doc);

    }

    @VisibleForTesting
    boolean priviewDocument(DocumentInfo doc) {
        Intent intent = new QuickViewIntentBuilder(
                mActivity.getPackageManager(),
                mActivity.getResources(),
                doc,
                mModel,
                true /* fromPicker */).build();

        if (intent != null) {
            try {
                mActivity.startActivity(intent);
                return true;
            } catch (SecurityException e) {
                Log.e(TAG, "Caught security error: " + e.getLocalizedMessage());
            }
        } else {
            Log.e(TAG, "Quick view intetn is null");
        }

        mInjector.dialogs.showNoApplicationFound();
        return false;
    }

    void pickDocument(FragmentManager fm, DocumentInfo pickTarget) {
        assert(pickTarget != null);
        mInjector.pickResult.increaseActionCount();
        Uri result;
        switch (mState.action) {
            case ACTION_OPEN_TREE:
                mInjector.dialogs.confirmAction(fm, pickTarget, ConfirmFragment.TYPE_OEPN_TREE);
                break;
            case ACTION_PICK_COPY_DESTINATION:
                result = pickTarget.derivedUri;
                finishPicking(result);
                break;
            default:
                // Should not be reached
                throw new IllegalStateException("Invalid mState.action");
        }
    }

    void saveDocument(
            String mimeType, String displayName, BooleanConsumer inProgressStateListener) {
        assert(mState.action == ACTION_CREATE);
        mInjector.pickResult.increaseActionCount();
        new CreatePickedDocumentTask(
                mActivity,
                mDocs,
                mLastAccessed,
                mState.stack,
                mimeType,
                displayName,
                inProgressStateListener,
                this::onPickFinished)
                .executeOnExecutor(getExecutorForCurrentDirectory());
    }

    // User requested to overwrite a target. If confirmed by user #finishPicking() will be
    // called.
    void saveDocument(FragmentManager fm, DocumentInfo replaceTarget) {
        assert(mState.action == ACTION_CREATE);
        mInjector.pickResult.increaseActionCount();
        assert(replaceTarget != null);

        // Adding a confirmation dialog breaks an inherited CTS test (testCreateExisting), so we
        // need to add a feature flag to bypass this feature in ARC++ environment.
        if (mFeatures.isOverwriteConfirmationEnabled()) {
            mInjector.dialogs.confirmAction(fm, replaceTarget, ConfirmFragment.TYPE_OVERWRITE);
        } else {
            finishPicking(replaceTarget.derivedUri);
        }
    }

    void finishPicking(Uri... docs) {
        new SetLastAccessedStackTask(
                mActivity,
                mLastAccessed,
                mState.stack,
                () -> {
                    onPickFinished(docs);
                }
        ) .executeOnExecutor(getExecutorForCurrentDirectory());
    }

    private void onPickFinished(Uri... uris) {
        if (DEBUG) {
            Log.d(TAG, "onFinished() " + Arrays.toString(uris));
        }

        final Intent intent = new Intent();
        if (uris.length == 1) {
            intent.setData(uris[0]);
        } else if (uris.length > 1) {
            final ClipData clipData = new ClipData(
                    null, mState.acceptMimes, new ClipData.Item(uris[0]));
            for (int i = 1; i < uris.length; i++) {
                clipData.addItem(new ClipData.Item(uris[i]));
            }
            intent.setClipData(clipData);
        }

        updatePickResult(
            intent, mSearchMgr.isSearching(), Metrics.sanitizeRoot(mState.stack.getRoot()));

        // TODO: Separate this piece of logic per action.
        // We don't instantiate different objects for different actions at the first place, so it's
        // not a easy task to separate this logic cleanly.
        // Maybe we can add an ActionPolicy class for IoC and provide various behaviors through its
        // inheritance structure.
        if (mState.action == ACTION_GET_CONTENT) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } else if (mState.action == ACTION_OPEN_TREE) {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
        } else if (mState.action == ACTION_PICK_COPY_DESTINATION) {
            // Picking a copy destination is only used internally by us, so we
            // don't need to extend permissions to the caller.
            intent.putExtra(Shared.EXTRA_STACK, (Parcelable) mState.stack);
            intent.putExtra(FileOperationService.EXTRA_OPERATION_TYPE, mState.copyOperationSubType);
        } else {
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                    | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        }

        mActivity.setResult(FragmentActivity.RESULT_OK, intent, 0);
        mActivity.finish();
    }

    private Executor getExecutorForCurrentDirectory() {
        final DocumentInfo cwd = mState.stack.peek();
        if (cwd != null && cwd.authority != null) {
            return mExecutors.lookup(cwd.authority);
        } else {
            return AsyncTask.THREAD_POOL_EXECUTOR;
        }
    }

    public interface Addons extends CommonAddons {
        @Override
        void onDocumentPicked(DocumentInfo doc);

        /**
         * Overload final method {@link FragmentActivity#setResult(int, Intent)} so that we can
         * intercept this method call in test environment.
         */
        @VisibleForTesting
        void setResult(int resultCode, Intent result, int notUsed);
    }
}
