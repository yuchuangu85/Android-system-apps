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
package com.android.car.media.common;

import android.annotation.NonNull;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.media.common.source.MediaSource;
import com.android.car.media.common.source.MediaSourceViewModel;
import com.android.car.media.common.source.MediaSourcesLiveData;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Fragment} that implements the app selection UI. It is typically created by a
 * {@link MediaAppSelectorWidget} widget that shows the current media source (stored as
 * {@link #mSelectorWidget}. To ensure visual coherence, the UI of the fragment also shows a
 * {@link MediaAppSelectorWidget} widget, but this one is in "display only" mode as tapping it
 * closes the fragment rather than opening a new one.
 * Note: the fragment dismisses itself in {@link #onStop} as its less confusing to come back to the
 * media app than the fragment after using another facet.
 */
public class AppSelectionFragment extends DialogFragment {

    private static final String ORIGIN_SOURCE_PACKAGE_KEY = "origin_source_package_key";
    private static final String FULL_SCREEN_KEY = "full_screen_key";

    /** The widget that opened this fragment. */
    private final MediaAppSelectorWidget mSelectorWidget;

    private boolean mFullScreenDialog;

    /** The widget contained by this fragment UI to display the current source. */
    private MediaAppSelectorWidget mDisplayWidget;

    /**
     * Creates a new {@link AppSelectionFragment}.
     *
     * @param selectorWidget the widget that is opening this fragment
     */
    public static AppSelectionFragment create(MediaAppSelectorWidget selectorWidget,
            boolean fullScreenDialog) {
        AppSelectionFragment result = new AppSelectionFragment(selectorWidget);
        Bundle bundle = new Bundle(1);
        bundle.putBoolean(FULL_SCREEN_KEY, fullScreenDialog);
        result.setArguments(bundle);
        return result;
    }

    public AppSelectionFragment() {
        this(null);
    }

    private AppSelectionFragment(MediaAppSelectorWidget selectorWidget) {
        mSelectorWidget = selectorWidget;
    }


    private class AppGridAdapter extends RecyclerView.Adapter<AppItemViewHolder> {
        private List<MediaSource> mMediaSources;

        /**
         * Triggers a refresh of media sources
         */
        void updateSources(List<MediaSource> mediaSources) {
            mMediaSources = new ArrayList<>(mediaSources);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public AppItemViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.app_selection_item, parent, false);
            return new AppItemViewHolder(view);

        }

        @Override
        public void onBindViewHolder(@NonNull AppItemViewHolder vh, int position) {
            vh.bind(mMediaSources.get(position));
        }

        @Override
        public int getItemCount() {
            return mMediaSources.size();
        }
    }

    private class AppItemViewHolder extends RecyclerView.ViewHolder {
        View mAppItem;
        ImageView mAppIconView;
        TextView mAppNameView;

        AppItemViewHolder(View view) {
            super(view);
            mAppItem = view.findViewById(R.id.app_item);
            mAppIconView = mAppItem.findViewById(R.id.app_icon);
            mAppNameView = mAppItem.findViewById(R.id.app_name);
        }

        /**
         * Binds a media source to a view
         */
        void bind(@NonNull MediaSource mediaSrc) {
            mAppItem.setOnClickListener(
                    v -> {
                        MediaSourceViewModel model = MediaSourceViewModel.get(
                                requireActivity().getApplication());
                        model.setPrimaryMediaSource(mediaSrc);
                        dismiss();
                    });

            mAppIconView.setImageDrawable(mediaSrc.getIcon());
            mAppNameView.setText(mediaSrc.getDisplayName());
        }
    }

    /** Closes the selector (allowing state loss). */
    @Override
    public void dismiss() {
        if (mSelectorWidget != null) {
            mSelectorWidget.setIsOpen(false);
        }
        mDisplayWidget.setIsOpen(false);
        // TODO(b/122324380) try using a shared element transition instead of this trick.
        // The delay is needed to update the arrow in the MediaAppSelectorWidget of this fragment
        // before the fragment fades away to reveal the underlying MediaAppSelectorWidget with an
        // arrow pointing the other way. Otherwise we end up seeing the arrows pointing in opposite
        // directions... Note that in the home screen the widgets are not overlapped.
        mDisplayWidget.postDelayed(() -> dismissAllowingStateLoss(), 50);
    }

    @Override
    public void onStart() {
        super.onStart();

        if (getDialog() != null) {
            getDialog().getWindow().setWindowAnimations(R.style.media_app_selector_animation_fade);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        dismiss();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(DialogFragment.STYLE_NORMAL, R.style.MediaAppSelectorStyle); // Full screen style.

        Bundle args = getArguments();
        if (args != null) {
            mFullScreenDialog = args.getBoolean(FULL_SCREEN_KEY, true);
        }
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_app_selection, container, false);

        if (mFullScreenDialog) {
            ViewGroup contentView = view.findViewById(R.id.actual_content);
            ViewGroup.MarginLayoutParams p =
                    (ViewGroup.MarginLayoutParams) contentView.getLayoutParams();
            p.setMargins(0, 0, 0, 0);
        }

        int columnNumber = getResources().getInteger(R.integer.num_app_selector_columns);
        AppGridAdapter gridAdapter = new AppGridAdapter();
        gridAdapter.updateSources(MediaSourcesLiveData.getInstance(getContext()).getList());
        mDisplayWidget = view.findViewById(R.id.app_switch_container);
        mDisplayWidget.setFragmentOwner(this);
        mDisplayWidget.setFragmentActivity(getActivity());

        RecyclerView gridView = view.findViewById(R.id.apps_grid);
        GridLayoutManager gridLayoutManager = new GridLayoutManager(getContext(), columnNumber);
        gridView.setLayoutManager(gridLayoutManager);
        gridView.setAdapter(gridAdapter);
        return view;
    }
}
