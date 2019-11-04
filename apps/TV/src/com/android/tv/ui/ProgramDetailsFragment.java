/*
 * Copyright (C) 2018 The Android Open Source Project
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
 * limitations under the License
 */

package com.android.tv.ui;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v17.leanback.app.DetailsFragment;
import android.support.v17.leanback.widget.Action;
import android.support.v17.leanback.widget.ArrayObjectAdapter;
import android.support.v17.leanback.widget.ClassPresenterSelector;
import android.support.v17.leanback.widget.DetailsOverviewRow;
import android.support.v17.leanback.widget.DetailsOverviewRowPresenter;
import android.support.v17.leanback.widget.OnActionClickedListener;
import android.support.v17.leanback.widget.PresenterSelector;
import android.support.v17.leanback.widget.SparseArrayObjectAdapter;
import android.support.v17.leanback.widget.VerticalGridView;
import android.text.TextUtils;
import com.android.tv.R;
import com.android.tv.TvSingletons;
import com.android.tv.common.feature.CommonFeatures;
import com.android.tv.data.Program;
import com.android.tv.data.api.Channel;
import com.android.tv.dvr.DvrDataManager;
import com.android.tv.dvr.DvrManager;
import com.android.tv.dvr.DvrScheduleManager;
import com.android.tv.dvr.data.ScheduledRecording;
import com.android.tv.dvr.ui.DvrUiHelper;
import com.android.tv.dvr.ui.browse.ActionPresenterSelector;
import com.android.tv.dvr.ui.browse.DetailsContent;
import com.android.tv.dvr.ui.browse.DetailsContentPresenter;
import com.android.tv.dvr.ui.browse.DetailsViewBackgroundHelper;
import com.android.tv.util.images.ImageLoader;

/** A fragment shows the details of a Program */
public class ProgramDetailsFragment extends DetailsFragment
        implements DvrDataManager.ScheduledRecordingListener,
                DvrScheduleManager.OnConflictStateChangeListener {
    private static final int LOAD_LOGO_IMAGE = 1;
    private static final int LOAD_BACKGROUND_IMAGE = 2;

    private static final int ACTION_VIEW_SCHEDULE = 1;
    private static final int ACTION_CANCEL = 2;
    private static final int ACTION_SCHEDULE_RECORDING = 3;

    protected DetailsViewBackgroundHelper mBackgroundHelper;
    private ArrayObjectAdapter mRowsAdapter;
    private DetailsOverviewRow mDetailsOverview;
    private Program mProgram;
    private String mInputId;
    private ScheduledRecording mScheduledRecording;
    private DvrManager mDvrManager;
    private DvrDataManager mDvrDataManager;
    private DvrScheduleManager mDvrScheduleManager;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!onLoadDetails(getArguments())) {
            getActivity().finish();
        }
    }

    @Override
    public void onDestroy() {
        mDvrDataManager.removeScheduledRecordingListener(this);
        mDvrScheduleManager.removeOnConflictStateChangeListener(this);
        super.onDestroy();
    }

    @Override
    public void onStart() {
        super.onStart();
        VerticalGridView container =
                (VerticalGridView) getActivity().findViewById(R.id.container_list);
        // Need to manually modify offset. Please refer DetailsFragment.setVerticalGridViewLayout.
        container.setItemAlignmentOffset(0);
        container.setWindowAlignmentOffset(
                getResources().getDimensionPixelSize(R.dimen.lb_details_rows_align_top));
    }

    private void setupAdapter() {
        DetailsOverviewRowPresenter rowPresenter =
                new DetailsOverviewRowPresenter(new DetailsContentPresenter(getActivity()));
        rowPresenter.setBackgroundColor(
                getResources().getColor(R.color.common_tv_background, null));
        rowPresenter.setSharedElementEnterTransition(
                getActivity(), DetailsActivity.SHARED_ELEMENT_NAME);
        rowPresenter.setOnActionClickedListener(onCreateOnActionClickedListener());
        mRowsAdapter = new ArrayObjectAdapter(onCreatePresenterSelector(rowPresenter));
        setAdapter(mRowsAdapter);
    }

    /** Sets details overview. */
    protected void setDetailsOverviewRow(DetailsContent detailsContent) {
        mDetailsOverview = new DetailsOverviewRow(detailsContent);
        mDetailsOverview.setActionsAdapter(onCreateActionsAdapter());
        mRowsAdapter.add(mDetailsOverview);
        onLoadLogoAndBackgroundImages(detailsContent);
    }

    /** Creates and returns presenter selector will be used by rows adaptor. */
    protected PresenterSelector onCreatePresenterSelector(
            DetailsOverviewRowPresenter rowPresenter) {
        ClassPresenterSelector presenterSelector = new ClassPresenterSelector();
        presenterSelector.addClassPresenter(DetailsOverviewRow.class, rowPresenter);
        return presenterSelector;
    }

    /** Updates actions of details overview. */
    protected void updateActions() {
        mDetailsOverview.setActionsAdapter(onCreateActionsAdapter());
    }

    /**
     * Loads program details according to the arguments the fragment got.
     *
     * @return false if cannot find valid programs, else return true. If the return value is false,
     *     the detail activity and fragment will be ended.
     */
    private boolean onLoadDetails(Bundle args) {
        Program program = args.getParcelable(DetailsActivity.PROGRAM);
        long channelId = args.getLong(DetailsActivity.CHANNEL_ID);
        String inputId = args.getString(DetailsActivity.INPUT_ID);
        if (program != null && channelId != Channel.INVALID_ID && !TextUtils.isEmpty(inputId)) {
            mProgram = program;
            mInputId = inputId;
            TvSingletons singletons = TvSingletons.getSingletons(getContext());
            mDvrDataManager = singletons.getDvrDataManager();
            mDvrManager = singletons.getDvrManager();
            mDvrScheduleManager = singletons.getDvrScheduleManager();
            mScheduledRecording =
                    mDvrDataManager.getScheduledRecordingForProgramId(program.getId());
            mBackgroundHelper = new DetailsViewBackgroundHelper(getActivity());
            setupAdapter();
            setDetailsOverviewRow(DetailsContent.createFromProgram(getContext(), mProgram));
            mDvrDataManager.addScheduledRecordingListener(this);
            mDvrScheduleManager.addOnConflictStateChangeListener(this);
            return true;
        }
        return false;
    }

    private int getScheduleIconId() {
        if (mDvrManager.isConflicting(mScheduledRecording)) {
            return R.drawable.ic_warning_white_32dp;
        } else {
            return R.drawable.ic_schedule_32dp;
        }
    }

    /** Creates actions users can interact with and their adaptor for this fragment. */
    private SparseArrayObjectAdapter onCreateActionsAdapter() {
        SparseArrayObjectAdapter adapter =
                new SparseArrayObjectAdapter(new ActionPresenterSelector());
        Resources res = getResources();
        if (mScheduledRecording != null) {
            adapter.set(
                    ACTION_VIEW_SCHEDULE,
                    new Action(
                            ACTION_VIEW_SCHEDULE,
                            res.getString(R.string.dvr_detail_view_schedule),
                            null,
                            res.getDrawable(getScheduleIconId())));
            adapter.set(
                    ACTION_CANCEL,
                    new Action(
                            ACTION_CANCEL,
                            res.getString(R.string.dvr_detail_cancel_recording),
                            null,
                            res.getDrawable(R.drawable.ic_dvr_cancel_32dp)));
        } else if (CommonFeatures.DVR.isEnabled(getActivity())
                && mDvrManager.isProgramRecordable(mProgram)) {
            adapter.set(
                    ACTION_SCHEDULE_RECORDING,
                    new Action(
                            ACTION_SCHEDULE_RECORDING,
                            res.getString(R.string.dvr_detail_schedule_recording),
                            null,
                            res.getDrawable(R.drawable.ic_schedule_32dp)));
        }
        return adapter;
    }

    /**
     * Creates actions listeners to implement the behavior of the fragment after users click some
     * action buttons.
     */
    private OnActionClickedListener onCreateOnActionClickedListener() {
        return new OnActionClickedListener() {
            @Override
            public void onActionClicked(Action action) {
                long actionId = action.getId();
                if (actionId == ACTION_VIEW_SCHEDULE) {
                    DvrUiHelper.startSchedulesActivity(getContext(), mScheduledRecording);
                } else if (actionId == ACTION_CANCEL) {
                    mDvrManager.removeScheduledRecording(mScheduledRecording);
                } else if (actionId == ACTION_SCHEDULE_RECORDING) {
                    DvrUiHelper.checkStorageStatusAndShowErrorMessage(
                            getActivity(),
                            mInputId,
                            () ->
                                    DvrUiHelper.requestRecordingFutureProgram(
                                            getActivity(), mProgram, false));
                }
            }
        };
    }

    /** Loads logo and background images for detail fragments. */
    protected void onLoadLogoAndBackgroundImages(DetailsContent detailsContent) {
        Drawable logoDrawable = null;
        Drawable backgroundDrawable = null;
        if (TextUtils.isEmpty(detailsContent.getLogoImageUri())) {
            logoDrawable =
                    getContext().getResources().getDrawable(R.drawable.dvr_default_poster, null);
            mDetailsOverview.setImageDrawable(logoDrawable);
        }
        if (TextUtils.isEmpty(detailsContent.getBackgroundImageUri())) {
            backgroundDrawable =
                    getContext().getResources().getDrawable(R.drawable.dvr_default_poster, null);
            mBackgroundHelper.setBackground(backgroundDrawable);
        }
        if (logoDrawable != null && backgroundDrawable != null) {
            return;
        }
        if (logoDrawable == null
                && backgroundDrawable == null
                && detailsContent
                        .getLogoImageUri()
                        .equals(detailsContent.getBackgroundImageUri())) {
            ImageLoader.loadBitmap(
                    getContext(),
                    detailsContent.getLogoImageUri(),
                    new MyImageLoaderCallback(
                            this, LOAD_LOGO_IMAGE | LOAD_BACKGROUND_IMAGE, getContext()));
            return;
        }
        if (logoDrawable == null) {
            int imageWidth = getResources().getDimensionPixelSize(R.dimen.dvr_details_poster_width);
            int imageHeight =
                    getResources().getDimensionPixelSize(R.dimen.dvr_details_poster_height);
            ImageLoader.loadBitmap(
                    getContext(),
                    detailsContent.getLogoImageUri(),
                    imageWidth,
                    imageHeight,
                    new MyImageLoaderCallback(this, LOAD_LOGO_IMAGE, getContext()));
        }
        if (backgroundDrawable == null) {
            ImageLoader.loadBitmap(
                    getContext(),
                    detailsContent.getBackgroundImageUri(),
                    new MyImageLoaderCallback(this, LOAD_BACKGROUND_IMAGE, getContext()));
        }
    }

    @Override
    public void onScheduledRecordingAdded(ScheduledRecording... scheduledRecordings) {
        for (ScheduledRecording recording : scheduledRecordings) {
            if (recording.getProgramId() == mProgram.getId()) {
                mScheduledRecording = recording;
                updateActions();
                return;
            }
        }
    }

    @Override
    public void onScheduledRecordingRemoved(ScheduledRecording... scheduledRecordings) {
        if (mScheduledRecording == null) {
            return;
        }
        for (ScheduledRecording recording : scheduledRecordings) {
            if (recording.getId() == mScheduledRecording.getId()) {
                mScheduledRecording = null;
                updateActions();
                return;
            }
        }
    }

    @Override
    public void onScheduledRecordingStatusChanged(ScheduledRecording... scheduledRecordings) {
        if (mScheduledRecording == null) {
            return;
        }
        for (ScheduledRecording recording : scheduledRecordings) {
            if (recording.getId() == mScheduledRecording.getId()) {
                mScheduledRecording = recording;
                updateActions();
                return;
            }
        }
    }

    @Override
    public void onConflictStateChange(boolean conflict, ScheduledRecording... scheduledRecordings) {
        onScheduledRecordingStatusChanged(scheduledRecordings);
    }

    private static class MyImageLoaderCallback
            extends ImageLoader.ImageLoaderCallback<ProgramDetailsFragment> {
        private final Context mContext;
        private final int mLoadType;

        public MyImageLoaderCallback(
                ProgramDetailsFragment fragment, int loadType, Context context) {
            super(fragment);
            mLoadType = loadType;
            mContext = context;
        }

        @Override
        public void onBitmapLoaded(ProgramDetailsFragment fragment, @Nullable Bitmap bitmap) {
            Drawable drawable;
            int loadType = mLoadType;
            if (bitmap == null) {
                Resources res = mContext.getResources();
                drawable = res.getDrawable(R.drawable.dvr_default_poster, null);
                if ((loadType & LOAD_BACKGROUND_IMAGE) != 0 && !fragment.isDetached()) {
                    loadType &= ~LOAD_BACKGROUND_IMAGE;
                    fragment.mBackgroundHelper.setBackgroundColor(
                            res.getColor(R.color.dvr_detail_default_background));
                    fragment.mBackgroundHelper.setScrim(
                            res.getColor(R.color.dvr_detail_default_background_scrim));
                }
            } else {
                drawable = new BitmapDrawable(mContext.getResources(), bitmap);
            }
            if (!fragment.isDetached()) {
                if ((loadType & LOAD_LOGO_IMAGE) != 0) {
                    fragment.mDetailsOverview.setImageDrawable(drawable);
                }
                if ((loadType & LOAD_BACKGROUND_IMAGE) != 0) {
                    fragment.mBackgroundHelper.setBackground(drawable);
                }
            }
        }
    }
}
