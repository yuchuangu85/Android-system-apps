/*
 * Copyright (C) 2013 The Android Open Source Project
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

import static com.android.documentsui.base.State.ACTION_CREATE;
import static com.android.documentsui.base.State.ACTION_GET_CONTENT;
import static com.android.documentsui.base.State.ACTION_OPEN;
import static com.android.documentsui.base.State.ACTION_OPEN_TREE;
import static com.android.documentsui.base.State.ACTION_PICK_COPY_DESTINATION;

import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import androidx.annotation.CallSuper;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.ActionModeController;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.DocsSelectionHelper;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.MenuManager.DirectoryDetails;
import com.android.documentsui.Metrics;
import com.android.documentsui.ProviderExecutor;
import com.android.documentsui.R;
import com.android.documentsui.SharedInputHandler;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.MimeTypes;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.dirlist.AppsRowManager;
import com.android.documentsui.dirlist.DirectoryFragment;
import com.android.documentsui.prefs.ScopedPreferences;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.sidebar.RootsFragment;
import com.android.documentsui.ui.DialogController;
import com.android.documentsui.ui.MessageBuilder;

import java.util.Collection;
import java.util.List;

public class PickActivity extends BaseActivity implements ActionHandler.Addons {

    static final String PREFERENCES_SCOPE = "picker";

    private static final String TAG = "PickActivity";

    private Injector<ActionHandler<PickActivity>> mInjector;
    private SharedInputHandler mSharedInputHandler;

    public PickActivity() {
        super(R.layout.documents_activity, TAG);
    }

    // make these methods visible in this package to work around compiler bug http://b/62218600
    @Override protected boolean focusSidebar() { return super.focusSidebar(); }
    @Override protected boolean popDir() { return super.popDir(); }

    @Override
    public void onCreate(Bundle icicle) {

        Features features = Features.create(this);
        ScopedPreferences prefs = ScopedPreferences.create(this, PREFERENCES_SCOPE);

        mInjector = new Injector<>(
                features,
                new Config(),
                prefs,
                new MessageBuilder(this),
                DialogController.create(features, this, null),
                DocumentsApplication.getFileTypeLookup(this),
                (Collection<RootInfo> roots) -> {});

        super.onCreate(icicle);

        mInjector.selectionMgr = DocsSelectionHelper.create();

        mInjector.focusManager = new FocusManager(
                mInjector.features,
                mInjector.selectionMgr,
                mDrawer,
                this::focusSidebar,
                getColor(R.color.primary));

        mInjector.menuManager = new MenuManager(mSearchManager, mState, new DirectoryDetails(this));

        mInjector.actionModeController = new ActionModeController(
                this,
                mInjector.selectionMgr,
                mInjector.menuManager,
                mInjector.messages);

        mInjector.pickResult = getPickResult(icicle);
        mInjector.actions = new ActionHandler<>(
                this,
                mState,
                mProviders,
                mDocs,
                mSearchManager,
                ProviderExecutor::forAuthority,
                mInjector,
                LastAccessedStorage.create());

        mInjector.searchManager = mSearchManager;

        Intent intent = getIntent();

        mAppsRowManager = new AppsRowManager(mInjector.actions);
        mInjector.appsRowManager = mAppsRowManager;

        mSharedInputHandler =
                new SharedInputHandler(
                        mInjector.focusManager,
                        mInjector.selectionMgr,
                        mInjector.searchManager::cancelSearch,
                        this::popDir,
                        mInjector.features,
                        mDrawer);
        setupLayout(intent);
        mInjector.actions.initLocation(intent);
        Metrics.logPickerLaunchedFrom(Shared.getCallingPackageName(this));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        // log the case of user picking nothing.
        mInjector.actions.getUpdatePickResultTask().execute();
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putParcelable(Shared.EXTRA_PICK_RESULT, mInjector.pickResult);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mInjector.pickResult.setPickStartTime(SystemClock.uptimeMillis());
    }

    @Override
    protected void onPause() {
        mInjector.pickResult.increaseDuration(SystemClock.uptimeMillis());
        super.onPause();
    }

    private static PickResult getPickResult(Bundle icicle) {
        if (icicle != null) {
            PickResult result = icicle.getParcelable(Shared.EXTRA_PICK_RESULT);
            return result;
        }

        return new PickResult();
    }

    private void setupLayout(Intent intent) {
        if (mState.action == ACTION_CREATE) {
            final String mimeType = intent.getType();
            final String title = intent.getStringExtra(Intent.EXTRA_TITLE);
            SaveFragment.show(getSupportFragmentManager(), mimeType, title);
        } else if (mState.action == ACTION_OPEN_TREE ||
                   mState.action == ACTION_PICK_COPY_DESTINATION) {
            PickFragment.show(getSupportFragmentManager());
        } else {
            // If PickFragment or SaveFragment does not show,
            // Set save container background to transparent for edge to edge nav bar.
            View saveContainer = findViewById(R.id.container_save);
            saveContainer.setBackgroundColor(Color.TRANSPARENT);
        }

        if (mState.action == ACTION_GET_CONTENT) {
            final Intent moreApps = new Intent(intent);
            moreApps.setComponent(null);
            moreApps.setPackage(null);
            RootsFragment.show(getSupportFragmentManager(), moreApps);
        } else if (mState.action == ACTION_OPEN ||
                   mState.action == ACTION_CREATE ||
                   mState.action == ACTION_OPEN_TREE ||
                   mState.action == ACTION_PICK_COPY_DESTINATION) {
            RootsFragment.show(getSupportFragmentManager(), (Intent) null);
        }
    }

    @Override
    protected void includeState(State state) {
        final Intent intent = getIntent();

        String defaultMimeType = (intent.getType() == null) ? "*/*" : intent.getType();
        state.initAcceptMimes(intent, defaultMimeType);

        final String action = intent.getAction();
        if (Intent.ACTION_OPEN_DOCUMENT.equals(action)) {
            state.action = ACTION_OPEN;
        } else if (Intent.ACTION_CREATE_DOCUMENT.equals(action)) {
            state.action = ACTION_CREATE;
        } else if (Intent.ACTION_GET_CONTENT.equals(action)) {
            state.action = ACTION_GET_CONTENT;
        } else if (Intent.ACTION_OPEN_DOCUMENT_TREE.equals(action)) {
            state.action = ACTION_OPEN_TREE;
        } else if (Shared.ACTION_PICK_COPY_DESTINATION.equals(action)) {
            state.action = ACTION_PICK_COPY_DESTINATION;
        }

        if (state.action == ACTION_OPEN || state.action == ACTION_GET_CONTENT) {
            state.allowMultiple = intent.getBooleanExtra(
                    Intent.EXTRA_ALLOW_MULTIPLE, false);
        }

        if (state.action == ACTION_OPEN || state.action == ACTION_GET_CONTENT
                || state.action == ACTION_CREATE) {
            state.openableOnly = intent.hasCategory(Intent.CATEGORY_OPENABLE);
        }

        if (state.action == ACTION_PICK_COPY_DESTINATION) {
            // Indicates that a copy operation (or move) includes a directory.
            // Why? Directory creation isn't supported by some roots (like Downloads).
            // This allows us to restrict available roots to just those with support.
            state.directoryCopy = intent.getBooleanExtra(
                    Shared.EXTRA_DIRECTORY_COPY, false);
            state.copyOperationSubType = intent.getIntExtra(
                    FileOperationService.EXTRA_OPERATION_TYPE,
                    FileOperationService.OPERATION_COPY);
        }
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
        mDrawer.update();
        mNavigator.update();
    }

    @Override
    public String getDrawerTitle() {
        String title = getIntent().getStringExtra(DocumentsContract.EXTRA_PROMPT);
        if (title == null) {
            if (mState.action == ACTION_OPEN ||
                mState.action == ACTION_GET_CONTENT ||
                mState.action == ACTION_OPEN_TREE) {
                title = getResources().getString(R.string.title_open);
            } else if (mState.action == ACTION_CREATE ||
                       mState.action == ACTION_PICK_COPY_DESTINATION) {
                title = getResources().getString(R.string.title_save);
            } else {
                // If all else fails, just call it "Documents".
                title = getResources().getString(R.string.app_label);
            }
        }

        return title;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);
        mInjector.menuManager.updateOptionMenu(menu);

        final DocumentInfo cwd = getCurrentDirectory();

        if (mState.action == ACTION_CREATE) {
            final FragmentManager fm = getSupportFragmentManager();
            SaveFragment.get(fm).prepareForDirectory(cwd);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        mInjector.pickResult.increaseActionCount();
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void refreshDirectory(int anim) {
        final FragmentManager fm = getSupportFragmentManager();
        final RootInfo root = getCurrentRoot();
        final DocumentInfo cwd = getCurrentDirectory();

        if (mState.stack.isRecents()) {
            DirectoryFragment.showRecentsOpen(fm, anim);

            // In recents we pick layout mode based on the mimetype,
            // picking GRID for visual types. We intentionally don't
            // consult a user's saved preferences here since they are
            // set per root (not per root and per mimetype).
            boolean visualMimes = MimeTypes.mimeMatches(
                    MimeTypes.VISUAL_MIMES, mState.acceptMimes);
            mState.derivedMode = visualMimes ? State.MODE_GRID : State.MODE_LIST;
        } else {
                // Normal boring directory
                DirectoryFragment.showDirectory(fm, root, cwd, anim);
        }

        // Forget any replacement target
        if (mState.action == ACTION_CREATE) {
            final SaveFragment save = SaveFragment.get(fm);
            if (save != null) {
                save.setReplaceTarget(null);
            }
        }

        if (mState.action == ACTION_OPEN_TREE ||
            mState.action == ACTION_PICK_COPY_DESTINATION) {
            final PickFragment pick = PickFragment.get(fm);
            if (pick != null) {
                pick.setPickTarget(mState.action, mState.copyOperationSubType, cwd);
            }
        }
    }

    @Override
    protected void onDirectoryCreated(DocumentInfo doc) {
        assert(doc.isDirectory());
        mInjector.actions.openContainerDocument(doc);
    }

    @Override
    public void onDocumentPicked(DocumentInfo doc) {
        final FragmentManager fm = getSupportFragmentManager();
        // Do not inline-open archives, as otherwise it would be impossible to pick
        // archive files. Note, that picking files inside archives is not supported.
        if (doc.isDirectory()) {
            mInjector.actions.openContainerDocument(doc);
            mSearchManager.recordHistory();
        } else if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT) {
            // Explicit file picked, return
            mInjector.actions.finishPicking(doc.derivedUri);
            mSearchManager.recordHistory();
        } else if (mState.action == ACTION_CREATE) {
            // Replace selected file
            SaveFragment.get(fm).setReplaceTarget(doc);
        }
    }

    @Override
    public void onDocumentsPicked(List<DocumentInfo> docs) {
        if (mState.action == ACTION_OPEN || mState.action == ACTION_GET_CONTENT) {
            final int size = docs.size();
            final Uri[] uris = new Uri[size];
            for (int i = 0; i < size; i++) {
                uris[i] = docs.get(i).derivedUri;
            }
            mInjector.actions.finishPicking(uris);
            mSearchManager.recordHistory();
        }
    }

    @CallSuper
    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return mSharedInputHandler.onKeyDown(
                keyCode,
                event)
                || super.onKeyDown(keyCode, event);
    }

    @Override
    public void setResult(int resultCode, Intent intent, int notUsed) {
        setResult(resultCode, intent);
    }

    public static PickActivity get(Fragment fragment) {
        return (PickActivity) fragment.getActivity();
    }

    @Override
    public Injector<ActionHandler<PickActivity>> getInjector() {
        return mInjector;
    }
}
