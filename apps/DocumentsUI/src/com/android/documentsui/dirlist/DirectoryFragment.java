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

package com.android.documentsui.dirlist;

import static com.android.documentsui.base.DocumentInfo.getCursorString;
import static com.android.documentsui.base.SharedMinimal.DEBUG;
import static com.android.documentsui.base.SharedMinimal.VERBOSE;
import static com.android.documentsui.base.State.MODE_GRID;
import static com.android.documentsui.base.State.MODE_LIST;

import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcelable;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.util.Log;
import android.util.SparseArray;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.ImageView;

import androidx.annotation.DimenRes;
import androidx.annotation.FractionRes;
import androidx.annotation.IntDef;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;
import androidx.recyclerview.selection.ItemDetailsLookup.ItemDetails;
import androidx.recyclerview.selection.MutableSelection;
import androidx.recyclerview.selection.Selection;
import androidx.recyclerview.selection.SelectionTracker;
import androidx.recyclerview.selection.SelectionTracker.SelectionPredicate;
import androidx.recyclerview.selection.StorageStrategy;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.GridLayoutManager.SpanSizeLookup;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.RecyclerListener;
import androidx.recyclerview.widget.RecyclerView.ViewHolder;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

import com.android.documentsui.ActionHandler;
import com.android.documentsui.ActionModeController;
import com.android.documentsui.BaseActivity;
import com.android.documentsui.ContentLock;
import com.android.documentsui.DocsSelectionHelper.DocDetailsLookup;
import com.android.documentsui.DocumentsApplication;
import com.android.documentsui.DragHoverListener;
import com.android.documentsui.FocusManager;
import com.android.documentsui.Injector;
import com.android.documentsui.Injector.ContentScoped;
import com.android.documentsui.Injector.Injected;
import com.android.documentsui.MetricConsts;
import com.android.documentsui.Metrics;
import com.android.documentsui.Model;
import com.android.documentsui.R;
import com.android.documentsui.ThumbnailCache;
import com.android.documentsui.base.DocumentFilters;
import com.android.documentsui.base.DocumentInfo;
import com.android.documentsui.base.DocumentStack;
import com.android.documentsui.base.EventListener;
import com.android.documentsui.base.Features;
import com.android.documentsui.base.RootInfo;
import com.android.documentsui.base.Shared;
import com.android.documentsui.base.State;
import com.android.documentsui.base.State.ViewMode;
import com.android.documentsui.clipping.ClipStore;
import com.android.documentsui.clipping.DocumentClipper;
import com.android.documentsui.clipping.UrisSupplier;
import com.android.documentsui.dirlist.AnimationView.AnimationType;
import com.android.documentsui.picker.PickActivity;
import com.android.documentsui.services.FileOperation;
import com.android.documentsui.services.FileOperationService;
import com.android.documentsui.services.FileOperationService.OpType;
import com.android.documentsui.services.FileOperations;
import com.android.documentsui.sorting.SortDimension;
import com.android.documentsui.sorting.SortModel;

import java.io.IOException;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Iterator;
import java.util.List;

/**
 * Display the documents inside a single directory.
 */
public class DirectoryFragment extends Fragment implements SwipeRefreshLayout.OnRefreshListener {

    static final int TYPE_NORMAL = 1;
    static final int TYPE_RECENT_OPEN = 2;

    @IntDef(flag = true, value = {
            REQUEST_COPY_DESTINATION
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface RequestCode {}
    public static final int REQUEST_COPY_DESTINATION = 1;

    static final String TAG = "DirectoryFragment";

    private static final int CACHE_EVICT_LIMIT = 100;
    private static final int REFRESH_SPINNER_TIMEOUT = 500;

    private BaseActivity mActivity;

    private State mState;
    private Model mModel;
    private final EventListener<Model.Update> mModelUpdateListener = new ModelUpdateListener();
    private final DocumentsAdapter.Environment mAdapterEnv = new AdapterEnvironment();

    @Injected
    @ContentScoped
    private Injector<?> mInjector;

    @Injected
    @ContentScoped
    private SelectionTracker<String> mSelectionMgr;

    @Injected
    @ContentScoped
    private FocusManager mFocusManager;

    @Injected
    @ContentScoped
    private ActionHandler mActions;

    @Injected
    @ContentScoped
    private ActionModeController mActionModeController;

    private DocDetailsLookup mDetailsLookup;
    private SelectionMetadata mSelectionMetadata;
    private KeyInputHandler mKeyListener;
    private @Nullable DragHoverListener mDragHoverListener;
    private IconHelper mIconHelper;
    private SwipeRefreshLayout mRefreshLayout;
    private RecyclerView mRecView;
    private DocumentsAdapter mAdapter;
    private DocumentClipper mClipper;
    private GridLayoutManager mLayout;
    private int mColumnCount = 1;  // This will get updated when layout changes.
    private int mColumnUnit = 1;

    private float mLiveScale = 1.0f;
    private @ViewMode int mMode;
    private int mAppBarHeight;

    private View mProgressBar;

    private DirectoryState mLocalState;

    // Note, we use !null to indicate that selection was restored (from rotation).
    // So don't fiddle with this field unless you've got the bigger picture in mind.
    private @Nullable Bundle mRestoredState;

    // Blocks loading/reloading of content while user is actively making selection.
    private ContentLock mContentLock = new ContentLock();

    private SortModel.UpdateListener mSortListener = (model, updateType) -> {
        // Only when sort order has changed do we need to trigger another loading.
        if ((updateType & SortModel.UPDATE_TYPE_SORTING) != 0) {
            mActions.loadDocumentsForCurrentStack();
        }
    };

    private final Runnable mOnDisplayStateChanged = this::onDisplayStateChanged;

    private final ViewTreeObserver.OnPreDrawListener mToolbarPreDrawListener = () -> {
        setPreDrawListener(false);
        if (mAppBarHeight != getAppBarLayoutHeight()) {
            updateLayout(mState.derivedMode);
        }
        return true;
    };

    @Override
    public View onCreateView(
            LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

        mActivity = (BaseActivity) getActivity();
        final View view = inflater.inflate(R.layout.fragment_directory, container, false);

        mProgressBar = view.findViewById(R.id.progressbar);
        assert mProgressBar != null;

        mRecView = (RecyclerView) view.findViewById(R.id.dir_list);
        mRecView.setRecyclerListener(
                new RecyclerListener() {
                    @Override
                    public void onViewRecycled(ViewHolder holder) {
                        cancelThumbnailTask(holder.itemView);
                    }
                });

        mRefreshLayout = (SwipeRefreshLayout) view.findViewById(R.id.refresh_layout);
        mRefreshLayout.setOnRefreshListener(this);
        mRecView.setItemAnimator(new DirectoryItemAnimator());

        mInjector = mActivity.getInjector();
        // Initially, this selection tracker (delegator) uses a dummy implementation, so it must be
        // updated (reset) when necessary things are ready.
        mSelectionMgr = mInjector.selectionMgr;
        mModel = mInjector.getModel();
        mModel.reset();

        mInjector.actions.registerDisplayStateChangedListener(mOnDisplayStateChanged);

        mClipper = DocumentsApplication.getDocumentClipper(getContext());
        if (mInjector.config.dragAndDropEnabled()) {
            DirectoryDragListener listener = new DirectoryDragListener(
                    new DragHost<>(
                            mActivity,
                            DocumentsApplication.getDragAndDropManager(mActivity),
                            mSelectionMgr,
                            mInjector.actions,
                            mActivity.getDisplayState(),
                            mInjector.dialogs,
                            (View v) -> {
                                return getModelId(v) != null;
                            },
                            this::getDocumentHolder,
                            this::getDestination
                    ));
            mDragHoverListener = DragHoverListener.create(listener, mRecView);
        }
        // Make the recycler and the empty views responsive to drop events when allowed.
        mRecView.setOnDragListener(mDragHoverListener);

        return view;
    }

    @Override
    public void onDestroyView() {
        mInjector.actions.unregisterDisplayStateChangedListener(mOnDisplayStateChanged);

        // Cancel any outstanding thumbnail requests
        final int count = mRecView.getChildCount();
        for (int i = 0; i < count; i++) {
            final View view = mRecView.getChildAt(i);
            cancelThumbnailTask(view);
        }

        mModel.removeUpdateListener(mModelUpdateListener);
        mModel.removeUpdateListener(mAdapter.getModelUpdateListener());
        setPreDrawListener(false);

        super.onDestroyView();
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mState = mActivity.getDisplayState();

        // Read arguments when object created for the first time.
        // Restore state if fragment recreated.
        Bundle args = savedInstanceState == null ? getArguments() : savedInstanceState;
        mRestoredState = args;

        mLocalState = new DirectoryState();
        mLocalState.restore(args);
        if (mLocalState.mSelectionId == null) {
            mLocalState.mSelectionId = Integer.toHexString(System.identityHashCode(mRecView));
        }

        mIconHelper = new IconHelper(mActivity, MODE_GRID);

        mAdapter = new DirectoryAddonsAdapter(
                mAdapterEnv,
                new ModelBackedDocumentsAdapter(mAdapterEnv, mIconHelper, mInjector.fileTypeLookup)
        );

        mRecView.setAdapter(mAdapter);

        mLayout = new GridLayoutManager(getContext(), mColumnCount) {
            @Override
            public void onLayoutCompleted(RecyclerView.State state) {
                super.onLayoutCompleted(state);
                mFocusManager.onLayoutCompleted();
            }
        };

        SpanSizeLookup lookup = mAdapter.createSpanSizeLookup();
        if (lookup != null) {
            mLayout.setSpanSizeLookup(lookup);
        }
        mRecView.setLayoutManager(mLayout);

        mModel.addUpdateListener(mAdapter.getModelUpdateListener());
        mModel.addUpdateListener(mModelUpdateListener);

        SelectionPredicate<String> selectionPredicate =
                new DocsSelectionPredicate(mInjector.config, mState, mModel, mRecView);

        mFocusManager = mInjector.getFocusManager(mRecView, mModel);
        mActions = mInjector.getActionHandler(mContentLock);

        mRecView.setAccessibilityDelegateCompat(
                new AccessibilityEventRouter(mRecView,
                        (View child) -> onAccessibilityClick(child),
                        (View child) -> onAccessibilityLongClick(child)));
        mSelectionMetadata = new SelectionMetadata(mModel::getItem);
        mDetailsLookup = new DocsItemDetailsLookup(mRecView);

        DragStartListener dragStartListener = mInjector.config.dragAndDropEnabled()
                ? DragStartListener.create(
                        mIconHelper,
                        mModel,
                        mSelectionMgr,
                        mSelectionMetadata,
                        mState,
                        this::getModelId,
                        mRecView::findChildViewUnder,
                        DocumentsApplication.getDragAndDropManager(mActivity))
                : DragStartListener.DUMMY;

        {
            // Limiting the scope of the localTracker so nobody uses it.
            // This block initializes/updates the global SelectionTracker held in mSelectionMgr.
            SelectionTracker<String> localTracker = new SelectionTracker.Builder<>(
                    mLocalState.mSelectionId,
                    mRecView,
                    new DocsStableIdProvider(mAdapter),
                    mDetailsLookup,
                    StorageStrategy.createStringStorage())
                            .withBandOverlay(R.drawable.band_select_overlay)
                            .withFocusDelegate(mFocusManager)
                            .withOnDragInitiatedListener(dragStartListener::onDragEvent)
                            .withOnContextClickListener(this::onContextMenuClick)
                            .withOnItemActivatedListener(this::onItemActivated)
                            .withOperationMonitor(mContentLock.getMonitor())
                            .withSelectionPredicate(selectionPredicate)
                            .build();
            mInjector.updateSharedSelectionTracker(localTracker);
        }

        mSelectionMgr.addObserver(mSelectionMetadata);

        // Construction of the input handlers is non trivial, so to keep logic clear,
        // and code flexible, and DirectoryFragment small, the construction has been
        // moved off into a separate class.
        InputHandlers handlers = new InputHandlers(
                mActions,
                mSelectionMgr,
                selectionPredicate,
                mFocusManager,
                mRecView);

        // This little guy gets added to each Holder, so that we can be notified of key events
        // on RecyclerView items.
        mKeyListener = handlers.createKeyHandler();

        if (DEBUG) {
            new ScaleHelper(this.getContext(), mInjector.features, this::scaleLayout)
                    .attach(mRecView);
        }

        new RefreshHelper(mRefreshLayout::setEnabled)
                .attach(mRecView);

        mActionModeController = mInjector.getActionModeController(
                mSelectionMetadata,
                this::handleMenuItemClick);

        mSelectionMgr.addObserver(mActionModeController);

        final ActivityManager am = (ActivityManager) mActivity.getSystemService(
                Context.ACTIVITY_SERVICE);
        boolean svelte = am.isLowRamDevice() && (mState.stack.isRecents());
        mIconHelper.setThumbnailsEnabled(!svelte);

        // If mDocument is null, we sort it by last modified by default because it's in Recents.
        final boolean prefersLastModified =
                (mLocalState.mDocument == null)
                || mLocalState.mDocument.prefersSortByLastModified();
        // Call this before adding the listener to avoid restarting the loader one more time
        mState.sortModel.setDefaultDimension(
                prefersLastModified
                        ? SortModel.SORT_DIMENSION_ID_DATE
                        : SortModel.SORT_DIMENSION_ID_TITLE);

        // Kick off loader at least once
        mActions.loadDocumentsForCurrentStack();
    }

    @Override
    public void onStart() {
        super.onStart();

        // Add listener to update contents on sort model change
        mState.sortModel.addListener(mSortListener);
    }

    @Override
    public void onStop() {
        super.onStop();

        mState.sortModel.removeListener(mSortListener);

        // Remember last scroll location
        final SparseArray<Parcelable> container = new SparseArray<>();
        getView().saveHierarchyState(container);
        mState.dirConfigs.put(mLocalState.getConfigKey(), container);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);

        mLocalState.save(outState);
        mSelectionMgr.onSaveInstanceState(outState);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu,
            View v,
            ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        final MenuInflater inflater = getActivity().getMenuInflater();

        final String modelId = getModelId(v);
        if (modelId == null) {
            // TODO: inject DirectoryDetails into MenuManager constructor
            // Since both classes are supplied by Activity and created
            // at the same time.
            mInjector.menuManager.inflateContextMenuForContainer(menu, inflater);
        } else {
            mInjector.menuManager.inflateContextMenuForDocs(
                    menu, inflater, mSelectionMetadata);
        }
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        return handleMenuItemClick(item);
    }

    private void onCopyDestinationPicked(int resultCode, Intent data) {

        FileOperation operation = mLocalState.claimPendingOperation();

        if (resultCode == FragmentActivity.RESULT_CANCELED || data == null) {
            // User pressed the back button or otherwise cancelled the destination pick. Don't
            // proceed with the copy.
            operation.dispose();
            return;
        }

        operation.setDestination(data.getParcelableExtra(Shared.EXTRA_STACK));
        final String jobId = FileOperations.createJobId();
        mInjector.dialogs.showProgressDialog(jobId, operation);
        FileOperations.start(
                mActivity,
                operation,
                mInjector.dialogs::showFileOperationStatus,
                jobId);
    }

    // TODO: Move to UserInputHander.
    protected boolean onContextMenuClick(MotionEvent e) {

        if (mDetailsLookup.overItemWithSelectionKey(e)) {
            View childView = mRecView.findChildViewUnder(e.getX(), e.getY());
            ViewHolder holder = mRecView.getChildViewHolder(childView);

            View view = holder.itemView;
            float x = e.getX() - view.getLeft();
            float y = e.getY() - view.getTop();
            mInjector.menuManager.showContextMenu(this, view, x, y);
            return true;
        }

        mInjector.menuManager.showContextMenu(this, mRecView, e.getX(), e.getY());
        return true;
    }

    private boolean onItemActivated(ItemDetails<String> item, MotionEvent e) {
        if (((DocumentItemDetails) item).inPreviewIconHotspot(e)) {
            return mActions.previewItem(item);
        }

        return mActions.openItem(
                item,
                ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
    }

    public void onViewModeChanged() {
        // Mode change is just visual change; no need to kick loader.
        onDisplayStateChanged();
    }

    private void onDisplayStateChanged() {
        updateLayout(mState.derivedMode);
        mRecView.setAdapter(mAdapter);
    }

    /**
     * Updates the layout after the view mode switches.
     * @param mode The new view mode.
     */
    private void updateLayout(@ViewMode int mode) {
        mMode = mode;
        mColumnCount = calculateColumnCount(mode);
        if (mLayout != null) {
            mLayout.setSpanCount(mColumnCount);
        }

        int pad = getDirectoryPadding(mode);
        mAppBarHeight = getAppBarLayoutHeight();
        mRecView.setPadding(pad, mAppBarHeight, pad, getSaveLayoutHeight());
        mRecView.requestLayout();
        mIconHelper.setViewMode(mode);

        int range = getResources().getDimensionPixelOffset(R.dimen.refresh_icon_range);
        mRefreshLayout.setProgressViewOffset(true, mAppBarHeight, mAppBarHeight + range);
    }

    private int getAppBarLayoutHeight() {
        View appBarLayout = getActivity().findViewById(R.id.app_bar);
        View collapsingBar = getActivity().findViewById(R.id.collapsing_toolbar);
        return collapsingBar == null ? 0 : appBarLayout.getHeight();
    }

    private int getSaveLayoutHeight() {
        View containerSave = getActivity().findViewById(R.id.container_save);
        return containerSave == null ? 0 : containerSave.getHeight();
    }

    /**
     * Updates the layout after the view mode switches.
     * @param mode The new view mode.
     */
    private void scaleLayout(float scale) {
        assert DEBUG;

        if (VERBOSE) Log.v(
                TAG, "Handling scale event: " + scale + ", existing scale: " + mLiveScale);

        if (mMode == MODE_GRID) {
            float minScale = getFraction(R.fraction.grid_scale_min);
            float maxScale = getFraction(R.fraction.grid_scale_max);
            float nextScale = mLiveScale * scale;

            if (VERBOSE) Log.v(TAG,
                    "Next scale " + nextScale + ", Min/max scale " + minScale + "/" + maxScale);

            if (nextScale > minScale && nextScale < maxScale) {
                if (DEBUG) {
                    Log.d(TAG, "Updating grid scale: " + scale);
                }
                mLiveScale = nextScale;
                updateLayout(mMode);
            }

        } else {
            if (DEBUG) {
                Log.d(TAG, "List mode, ignoring scale: " + scale);
            }
            mLiveScale = 1.0f;
        }
    }

    private int calculateColumnCount(@ViewMode int mode) {
        if (mode == MODE_LIST) {
            // List mode is a "grid" with 1 column.
            return 1;
        }

        int cellWidth = getScaledSize(R.dimen.grid_width);
        int cellMargin = 2 * getScaledSize(R.dimen.grid_item_margin);
        int viewPadding =
                (int) ((mRecView.getPaddingLeft() + mRecView.getPaddingRight()) * mLiveScale);

        // RecyclerView sometimes gets a width of 0 (see b/27150284).
        // Clamp so that we always lay out the grid with at least 2 columns by default.
        // If on photo picking state, the UI should show 3 images a row or 2 folders a row,
        // so use 6 columns by default and set folder size to 3 and document size is to 2.
        mColumnUnit = mState.isPhotoPicking() ? 3 : 1;
        int columnCount = mColumnUnit * Math.max(2,
                (mRecView.getWidth() - viewPadding) / (cellWidth + cellMargin));

        // Finally with our grid count logic firmly in place, we apply any live scaling
        // captured by the scale gesture detector.
        return Math.max(1, Math.round(columnCount / mLiveScale));
    }


    /**
     * Moderately abuse the "fraction" resource type for our purposes.
     */
    private float getFraction(@FractionRes int id) {
        return getResources().getFraction(id, 1, 0);
    }

    private int getScaledSize(@DimenRes int id) {
        return (int) (getResources().getDimensionPixelSize(id) * mLiveScale);
    }

    private int getDirectoryPadding(@ViewMode int mode) {
        switch (mode) {
            case MODE_GRID:
                return getResources().getDimensionPixelSize(R.dimen.grid_container_padding);
            case MODE_LIST:
                return getResources().getDimensionPixelSize(R.dimen.list_container_padding);
            default:
                throw new IllegalArgumentException("Unsupported layout mode: " + mode);
        }
    }

    private boolean handleMenuItemClick(MenuItem item) {
        if (mInjector.pickResult != null) {
            mInjector.pickResult.increaseActionCount();
        }
        MutableSelection<String> selection = new MutableSelection<>();
        mSelectionMgr.copySelection(selection);

        switch (item.getItemId()) {
            case R.id.action_menu_select:
            case R.id.dir_menu_open:
                openDocuments(selection);
                mActionModeController.finishActionMode();
                return true;

            case R.id.action_menu_open_with:
            case R.id.dir_menu_open_with:
                showChooserForDoc(selection);
                return true;

            case R.id.dir_menu_open_in_new_window:
                mActions.openSelectedInNewWindow();
                return true;

            case R.id.action_menu_share:
            case R.id.dir_menu_share:
                mActions.shareSelectedDocuments();
                return true;

            case R.id.action_menu_delete:
            case R.id.dir_menu_delete:
                // deleteDocuments will end action mode if the documents are deleted.
                // It won't end action mode if user cancels the delete.
                mActions.deleteSelectedDocuments();
                return true;

            case R.id.action_menu_copy_to:
                transferDocuments(selection, null, FileOperationService.OPERATION_COPY);
                // TODO: Only finish selection mode if copy-to is not canceled.
                // Need to plum down into handling the way we do with deleteDocuments.
                mActionModeController.finishActionMode();
                return true;

            case R.id.action_menu_compress:
                transferDocuments(selection, mState.stack,
                        FileOperationService.OPERATION_COMPRESS);
                // TODO: Only finish selection mode if compress is not canceled.
                // Need to plum down into handling the way we do with deleteDocuments.
                mActionModeController.finishActionMode();
                return true;

            // TODO: Implement extract (to the current directory).
            case R.id.action_menu_extract_to:
                transferDocuments(selection, null, FileOperationService.OPERATION_EXTRACT);
                // TODO: Only finish selection mode if compress-to is not canceled.
                // Need to plum down into handling the way we do with deleteDocuments.
                mActionModeController.finishActionMode();
                return true;

            case R.id.action_menu_move_to:
                if (mModel.hasDocuments(selection, DocumentFilters.NOT_MOVABLE)) {
                    mInjector.dialogs.showOperationUnsupported();
                    return true;
                }
                // Exit selection mode first, so we avoid deselecting deleted documents.
                mActionModeController.finishActionMode();
                transferDocuments(selection, null, FileOperationService.OPERATION_MOVE);
                return true;

            case R.id.action_menu_inspect:
            case R.id.dir_menu_inspect:
                mActionModeController.finishActionMode();
                assert selection.size() <= 1;
                DocumentInfo doc = selection.isEmpty()
                        ? mActivity.getCurrentDirectory()
                        : mModel.getDocuments(selection).get(0);

                        mActions.showInspector(doc);
                return true;

            case R.id.dir_menu_cut_to_clipboard:
                mActions.cutToClipboard();
                return true;

            case R.id.dir_menu_copy_to_clipboard:
                mActions.copyToClipboard();
                return true;

            case R.id.dir_menu_paste_from_clipboard:
                pasteFromClipboard();
                return true;

            case R.id.dir_menu_paste_into_folder:
                pasteIntoFolder();
                return true;

            case R.id.action_menu_select_all:
            case R.id.dir_menu_select_all:
                mActions.selectAllFiles();
                return true;

            case R.id.action_menu_rename:
            case R.id.dir_menu_rename:
                // Exit selection mode first, so we avoid deselecting deleted
                // (renamed) documents.
                mActionModeController.finishActionMode();
                renameDocuments(selection);
                return true;

            case R.id.dir_menu_create_dir:
                mActions.showCreateDirectoryDialog();
                return true;

            case R.id.dir_menu_view_in_owner:
                mActions.viewInOwner();
                return true;

            case R.id.action_menu_sort:
                mActions.showSortDialog();
                return true;

            default:
                if (DEBUG) {
                    Log.d(TAG, "Unhandled menu item selected: " + item);
                }
                return false;
        }
    }

    private boolean onAccessibilityClick(View child) {
        if (mSelectionMgr.hasSelection()) {
            selectItem(child);
        } else {
            DocumentHolder holder = getDocumentHolder(child);
            mActions.openItem(holder.getItemDetails(), ActionHandler.VIEW_TYPE_PREVIEW,
                ActionHandler.VIEW_TYPE_REGULAR);
        }
        return true;
    }

    private boolean onAccessibilityLongClick(View child) {
        selectItem(child);
        return true;
    }

    private void selectItem(View child) {
        final String id = getModelId(child);
        if (mSelectionMgr.isSelected(id)) {
            mSelectionMgr.deselect(id);
        } else {
            mSelectionMgr.select(id);
        }
    }

    private void cancelThumbnailTask(View view) {
        final ImageView iconThumb = (ImageView) view.findViewById(R.id.icon_thumb);
        if (iconThumb != null) {
            mIconHelper.stopLoading(iconThumb);
        }
    }

    // Support for opening multiple documents is currently exclusive to DocumentsActivity.
    private void openDocuments(final Selection selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_OPEN);

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        if (docs.size() > 1) {
            mActivity.onDocumentsPicked(docs);
        } else {
            mActivity.onDocumentPicked(docs.get(0));
        }
    }

    private void showChooserForDoc(final Selection<String> selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_OPEN);

        assert selected.size() == 1;
        DocumentInfo doc =
                DocumentInfo.fromDirectoryCursor(mModel.getItem(selected.iterator().next()));
        mActions.showChooserForDoc(doc);
    }

    private void transferDocuments(
            final Selection<String> selected, @Nullable DocumentStack destination,
            final @OpType int mode) {
        switch (mode) {
            case FileOperationService.OPERATION_COPY:
                Metrics.logUserAction(MetricConsts.USER_ACTION_COPY_TO);
                break;
            case FileOperationService.OPERATION_COMPRESS:
                Metrics.logUserAction(MetricConsts.USER_ACTION_COMPRESS);
                break;
            case FileOperationService.OPERATION_EXTRACT:
                Metrics.logUserAction(MetricConsts.USER_ACTION_EXTRACT_TO);
                break;
            case FileOperationService.OPERATION_MOVE:
                Metrics.logUserAction(MetricConsts.USER_ACTION_MOVE_TO);
                break;
        }

        UrisSupplier srcs;
        try {
            ClipStore clipStorage = DocumentsApplication.getClipStore(getContext());
            srcs = UrisSupplier.create(selected, mModel::getItemUri, clipStorage);
        } catch (IOException e) {
            throw new RuntimeException("Failed to create uri supplier.", e);
        }

        final DocumentInfo parent = mActivity.getCurrentDirectory();
        final FileOperation operation = new FileOperation.Builder()
                .withOpType(mode)
                .withSrcParent(parent == null ? null : parent.derivedUri)
                .withSrcs(srcs)
                .build();

        if (destination != null) {
            operation.setDestination(destination);
            final String jobId = FileOperations.createJobId();
            mInjector.dialogs.showProgressDialog(jobId, operation);
            FileOperations.start(
                    mActivity,
                    operation,
                    mInjector.dialogs::showFileOperationStatus,
                    jobId);
            return;
        }

        // Pop up a dialog to pick a destination.  This is inadequate but works for now.
        // TODO: Implement a picker that is to spec.
        mLocalState.mPendingOperation = operation;
        final Intent intent = new Intent(
                Shared.ACTION_PICK_COPY_DESTINATION,
                Uri.EMPTY,
                getActivity(),
                PickActivity.class);

        // Set an appropriate title on the drawer when it is shown in the picker.
        // Coupled with the fact that we auto-open the drawer for copy/move operations
        // it should basically be the thing people see first.
        int drawerTitleId;
        switch (mode) {
            case FileOperationService.OPERATION_COPY:
                drawerTitleId = R.string.menu_copy;
                break;
            case FileOperationService.OPERATION_COMPRESS:
                drawerTitleId = R.string.menu_compress;
                break;
            case FileOperationService.OPERATION_EXTRACT:
                drawerTitleId = R.string.menu_extract;
                break;
            case FileOperationService.OPERATION_MOVE:
                drawerTitleId = R.string.menu_move;
                break;
            default:
                throw new UnsupportedOperationException("Unknown mode: " + mode);
        }

        intent.putExtra(DocumentsContract.EXTRA_PROMPT, getResources().getString(drawerTitleId));

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);

        // Determine if there is a directory in the set of documents
        // to be copied? Why? Directory creation isn't supported by some roots
        // (like Downloads). This informs DocumentsActivity (the "picker")
        // to restrict available roots to just those with support.
        intent.putExtra(Shared.EXTRA_DIRECTORY_COPY, hasDirectory(docs));
        intent.putExtra(FileOperationService.EXTRA_OPERATION_TYPE, mode);

        // This just identifies the type of request...we'll check it
        // when we reveive a response.
        startActivityForResult(intent, REQUEST_COPY_DESTINATION);
    }

    @Override
    public void onActivityResult(@RequestCode int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_COPY_DESTINATION:
                onCopyDestinationPicked(resultCode, data);
                break;
            default:
                throw new UnsupportedOperationException("Unknown request code: " + requestCode);
        }
    }

    private static boolean hasDirectory(List<DocumentInfo> docs) {
        for (DocumentInfo info : docs) {
            if (Document.MIME_TYPE_DIR.equals(info.mimeType)) {
                return true;
            }
        }
        return false;
    }

    private void renameDocuments(Selection selected) {
        Metrics.logUserAction(MetricConsts.USER_ACTION_RENAME);

        // Batch renaming not supported
        // Rename option is only available in menu when 1 document selected
        assert selected.size() == 1;

        // Model must be accessed in UI thread, since underlying cursor is not threadsafe.
        List<DocumentInfo> docs = mModel.getDocuments(selected);
        RenameDocumentFragment.show(getChildFragmentManager(), docs.get(0));
    }

    Model getModel(){
        return mModel;
    }

    /**
     * Paste selection files from the primary clip into the current window.
     */
    public void pasteFromClipboard() {
        Metrics.logUserAction(MetricConsts.USER_ACTION_PASTE_CLIPBOARD);
        // Since we are pasting into the current window, we already have the destination in the
        // stack. No need for a destination DocumentInfo.
        mClipper.copyFromClipboard(
                mState.stack,
                mInjector.dialogs::showFileOperationStatus);
        getActivity().invalidateOptionsMenu();
    }

    public void pasteIntoFolder() {
        assert (mSelectionMgr.getSelection().size() == 1);

        String modelId = mSelectionMgr.getSelection().iterator().next();
        Cursor dstCursor = mModel.getItem(modelId);
        if (dstCursor == null) {
            Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + modelId);
            return;
        }
        DocumentInfo destination = DocumentInfo.fromDirectoryCursor(dstCursor);
        mClipper.copyFromClipboard(
                destination,
                mState.stack,
                mInjector.dialogs::showFileOperationStatus);
        getActivity().invalidateOptionsMenu();
    }

    private void setupDragAndDropOnDocumentView(View view, Cursor cursor) {
        final String docMimeType = getCursorString(cursor, Document.COLUMN_MIME_TYPE);
        if (Document.MIME_TYPE_DIR.equals(docMimeType)) {
            // Make a directory item a drop target. Drop on non-directories and empty space
            // is handled at the list/grid view level.
            view.setOnDragListener(mDragHoverListener);
        }
    }

    private DocumentInfo getDestination(View v) {
        String id = getModelId(v);
        if (id != null) {
            Cursor dstCursor = mModel.getItem(id);
            if (dstCursor == null) {
                Log.w(TAG, "Invalid destination. Can't obtain cursor for modelId: " + id);
                return null;
            }
            return DocumentInfo.fromDirectoryCursor(dstCursor);
        }

        if (v == mRecView) {
            return mActivity.getCurrentDirectory();
        }

        return null;
    }

    /**
     * Gets the model ID for a given RecyclerView item.
     * @param view A View that is a document item view, or a child of a document item view.
     * @return The Model ID for the given document, or null if the given view is not associated with
     *     a document item view.
     */
    private @Nullable String getModelId(View view) {
        View itemView = mRecView.findContainingItemView(view);
        if (itemView != null) {
            RecyclerView.ViewHolder vh = mRecView.getChildViewHolder(itemView);
            if (vh instanceof DocumentHolder) {
                return ((DocumentHolder) vh).getModelId();
            }
        }
        return null;
    }

    private @Nullable DocumentHolder getDocumentHolder(View v) {
        RecyclerView.ViewHolder vh = mRecView.getChildViewHolder(v);
        if (vh instanceof DocumentHolder) {
            return (DocumentHolder) vh;
        }
        return null;
    }

    private void setPreDrawListener(boolean enable) {
        if (mActivity == null) {
            return;
        }

        final View bar = mActivity.findViewById(R.id.collapsing_toolbar);
        if (bar != null) {
            if (enable) {
                bar.getViewTreeObserver().addOnPreDrawListener(mToolbarPreDrawListener);
            } else {
                bar.getViewTreeObserver().removeOnPreDrawListener(mToolbarPreDrawListener);
            }
        }
    }

    public static void showDirectory(
            FragmentManager fm, RootInfo root, DocumentInfo doc, int anim) {
        if (DEBUG) {
            Log.d(TAG, "Showing directory: " + DocumentInfo.debugString(doc));
        }
        create(fm, root, doc, anim);
    }

    public static void showRecentsOpen(FragmentManager fm, int anim) {
        create(fm, null, null, anim);
    }

    public static void create(
            FragmentManager fm,
            RootInfo root,
            @Nullable DocumentInfo doc,
            @AnimationType int anim) {

        if (DEBUG) {
            if (doc == null) {
                Log.d(TAG, "Creating new fragment null directory");
            } else {
                Log.d(TAG, "Creating new fragment for directory: " + DocumentInfo.debugString(doc));
            }
        }

        final Bundle args = new Bundle();
        args.putParcelable(Shared.EXTRA_ROOT, root);
        args.putParcelable(Shared.EXTRA_DOC, doc);

        final FragmentTransaction ft = fm.beginTransaction();
        AnimationView.setupAnimations(ft, anim, args);

        final DirectoryFragment fragment = new DirectoryFragment();
        fragment.setArguments(args);

        ft.replace(getFragmentId(), fragment);
        ft.commitAllowingStateLoss();
    }

    public static @Nullable DirectoryFragment get(FragmentManager fm) {
        // TODO: deal with multiple directories shown at once
        Fragment fragment = fm.findFragmentById(getFragmentId());
        return fragment instanceof DirectoryFragment
                ? (DirectoryFragment) fragment
                : null;
    }

    private static int getFragmentId() {
        return R.id.container_directory;
    }

    /**
     *  Scroll to top of recyclerView in fragment
     */
    public void scrollToTop() {
        if (mRecView != null) {
            mRecView.scrollToPosition(0);
        }
    }

    /**
     *  Stop the scroll of recyclerView in fragment
     */
    public void stopScroll() {
        if (mRecView != null) {
            mRecView.stopScroll();
        }
    }

    @Override
    public void onRefresh() {
        // Remove thumbnail cache. We do this not because we're worried about stale thumbnails as it
        // should be covered by last modified value we store in thumbnail cache, but rather to give
        // the user a greater sense that contents are being reloaded.
        ThumbnailCache cache = DocumentsApplication.getThumbnailCache(getContext());
        String[] ids = mModel.getModelIds();
        int numOfEvicts = Math.min(ids.length, CACHE_EVICT_LIMIT);
        for (int i = 0; i < numOfEvicts; ++i) {
            cache.removeUri(mModel.getItemUri(ids[i]));
        }

        final DocumentInfo doc = mActivity.getCurrentDirectory();
        mActions.refreshDocument(doc, (boolean refreshSupported) -> {
            if (refreshSupported) {
                mRefreshLayout.setRefreshing(false);
            } else {
                // If Refresh API isn't available, we will explicitly reload the loader
                mActions.loadDocumentsForCurrentStack();
            }
        });
    }

    private final class ModelUpdateListener implements EventListener<Model.Update> {

        @Override
        public void accept(Model.Update update) {
            if (DEBUG) {
                Log.d(TAG, "Received model update. Loading=" + mModel.isLoading());
            }

            mProgressBar.setVisibility(mModel.isLoading() ? View.VISIBLE : View.GONE);

            updateLayout(mState.derivedMode);

            // Update the selection to remove any disappeared IDs.
            Iterator<String> selectionIter = mSelectionMgr.getSelection().iterator();
            while (selectionIter.hasNext()) {
                if (!mAdapter.getStableIds().contains(selectionIter.next())) {
                    selectionIter.remove();
                }
            }

            mAdapter.notifyDataSetChanged();

            if (mRestoredState != null) {
                mSelectionMgr.onRestoreInstanceState(mRestoredState);
                mRestoredState = null;
            }

            // Restore any previous instance state
            final SparseArray<Parcelable> container =
                    mState.dirConfigs.remove(mLocalState.getConfigKey());
            final int curSortedDimensionId = mState.sortModel.getSortedDimensionId();

            final SortDimension curSortedDimension =
                    mState.sortModel.getDimensionById(curSortedDimensionId);

            // Default not restore to avoid app bar layout expand to confuse users.
            if (container != null
                    && !getArguments().getBoolean(Shared.EXTRA_IGNORE_STATE, true)) {
                getView().restoreHierarchyState(container);
            } else if (mLocalState.mLastSortDimensionId != curSortedDimension.getId()
                    || mLocalState.mLastSortDimensionId == SortModel.SORT_DIMENSION_ID_UNKNOWN
                    || mLocalState.mLastSortDirection != curSortedDimension.getSortDirection()) {
                // Scroll to the top if the sort order actually changed.
                mRecView.smoothScrollToPosition(0);
            }

            mLocalState.mLastSortDimensionId = curSortedDimension.getId();
            mLocalState.mLastSortDirection = curSortedDimension.getSortDirection();

            if (mRefreshLayout.isRefreshing()) {
                new Handler().postDelayed(
                        () -> mRefreshLayout.setRefreshing(false),
                        REFRESH_SPINNER_TIMEOUT);
            }

            if (!mModel.isLoading()) {
                mActivity.notifyDirectoryLoaded(
                        mModel.doc != null ? mModel.doc.derivedUri : null);
                // For orientation changed case, sometimes the docs loading comes after the menu
                // update. We need to update the menu here to ensure the status is correct.
                mInjector.menuManager.updateModel(mModel);
                mInjector.menuManager.updateOptionMenu();

                mActivity.updateHeaderTitle();

                setPreDrawListener(true);
            }
        }
    }

    private final class AdapterEnvironment implements DocumentsAdapter.Environment {

        @Override
        public Features getFeatures() {
            return mInjector.features;
        }

        @Override
        public Context getContext() {
            return mActivity;
        }

        @Override
        public State getDisplayState() {
            return mState;
        }

        @Override
        public boolean isInSearchMode() {
            return mInjector.searchManager.isSearching();
        }

        @Override
        public Model getModel() {
            return mModel;
        }

        @Override
        public int getColumnCount() {
            return mColumnCount;
        }

        @Override
        public boolean isSelected(String id) {
            return mSelectionMgr.isSelected(id);
        }

        @Override
        public boolean isDocumentEnabled(String mimeType, int flags) {
            return mInjector.config.isDocumentEnabled(mimeType, flags, mState);
        }

        @Override
        public void initDocumentHolder(DocumentHolder holder) {
            holder.addKeyEventListener(mKeyListener);
            holder.itemView.setOnFocusChangeListener(mFocusManager);
        }

        @Override
        public void onBindDocumentHolder(DocumentHolder holder, Cursor cursor) {
            setupDragAndDropOnDocumentView(holder.itemView, cursor);
        }

        @Override
        public ActionHandler getActionHandler() {
            return mActions;
        }
    }
}
