package com.android.car.media;

import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.car.apps.common.util.ViewUtils;
import com.android.car.media.common.browse.MediaBrowserViewModel;
import com.android.car.media.common.source.MediaSource;

/**
 * Empty fragment to show while we are loading content
 */
public class EmptyFragment extends Fragment {
    private static final String TAG = "EmptyFragment";

    private ImageView mErrorIcon;
    private TextView mMessage;

    private int mLoadingIndicatorDelay;
    private Handler mHandler = new Handler();
    private int mFadeDuration;
    private Runnable mProgressIndicatorRunnable = new Runnable() {
        @Override
        public void run() {
            mMessage.setText(requireContext().getString(R.string.browser_loading));
            ViewUtils.showViewAnimated(mMessage, mFadeDuration);
        }
    };

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, final ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_empty, container, false);
        mLoadingIndicatorDelay = requireContext().getResources()
                .getInteger(R.integer.progress_indicator_delay);
        mFadeDuration = requireContext().getResources().getInteger(
                R.integer.new_album_art_fade_in_duration);
        mErrorIcon = view.findViewById(R.id.error_icon);
        mMessage = view.findViewById(R.id.error_message);

        return view;
    }

    @Override
    public void onPause() {
        super.onPause();
        mHandler.removeCallbacks(mProgressIndicatorRunnable);
    }

    /**
     * Updates the state of this fragment
     *
     * @param state       browsing state to display
     * @param mediaSource media source currently being browsed
     */
    void setState(@NonNull MediaBrowserViewModel.BrowseState state,
            @Nullable MediaSource mediaSource) {
        mHandler.removeCallbacks(mProgressIndicatorRunnable);
        if (this.getView() != null) {
            update(state, mediaSource);
        }
    }

    private void update(@NonNull MediaBrowserViewModel.BrowseState state,
            @Nullable MediaSource mediaSource) {
        switch (state) {
            case LOADING:
                // Display the indicator after a certain time, to avoid flashing the indicator
                // constantly, even when performance is acceptable.
                mHandler.postDelayed(mProgressIndicatorRunnable, mLoadingIndicatorDelay);
                mErrorIcon.setVisibility(View.GONE);
                mMessage.setVisibility(View.GONE);
                break;
            case ERROR:
                mErrorIcon.setVisibility(View.VISIBLE);
                mMessage.setVisibility(View.VISIBLE);
                mMessage.setText(requireContext().getString(
                        R.string.cannot_connect_to_app,
                        mediaSource != null
                                ? mediaSource.getDisplayName()
                                : requireContext().getString(
                                        R.string.unknown_media_provider_name)));
                break;
            case EMPTY:
                mErrorIcon.setVisibility(View.GONE);
                mMessage.setVisibility(View.VISIBLE);
                mMessage.setText(requireContext().getString(R.string.nothing_to_play));
                break;
            case LOADED:
                Log.d(TAG, "Updated with LOADED state, ignoring.");
                // Do nothing, this fragment is about to be removed
                break;
            default:
                // Fail fast on any other state.
                throw new IllegalStateException("Invalid state for this fragment: " + state);
        }
    }
}
