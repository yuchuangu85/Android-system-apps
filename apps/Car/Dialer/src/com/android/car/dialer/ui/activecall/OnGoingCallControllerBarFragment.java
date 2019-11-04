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
 * limitations under the License.
 */

package com.android.car.dialer.ui.activecall;

import android.app.AlertDialog;
import android.content.Context;
import android.os.Bundle;
import android.telecom.Call;
import android.telecom.CallAudioState;
import android.telecom.CallAudioState.CallAudioRoute;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProviders;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.dialer.R;
import com.android.car.dialer.log.L;
import com.android.car.dialer.telecom.UiCallManager;

import com.google.common.collect.ImmutableMap;

import java.util.List;

/** A Fragment of the bar which controls on going call. */
public class OnGoingCallControllerBarFragment extends Fragment {
    private static String TAG = "CDialer.OngoingCallCtlFrg";

    private static final ImmutableMap<Integer, AudioRouteInfo> AUDIO_ROUTES =
            ImmutableMap.<Integer, AudioRouteInfo>builder()
                    .put(CallAudioState.ROUTE_WIRED_HEADSET, new AudioRouteInfo(
                            R.string.audio_route_handset,
                            R.drawable.ic_smartphone,
                            R.drawable.ic_smartphone_activatable))
                    .put(CallAudioState.ROUTE_EARPIECE, new AudioRouteInfo(
                            R.string.audio_route_handset,
                            R.drawable.ic_smartphone,
                            R.drawable.ic_smartphone_activatable))
                    .put(CallAudioState.ROUTE_BLUETOOTH, new AudioRouteInfo(
                            R.string.audio_route_vehicle,
                            R.drawable.ic_bluetooth,
                            R.drawable.ic_bluetooth_activatable))
                    .put(CallAudioState.ROUTE_SPEAKER, new AudioRouteInfo(
                            R.string.audio_route_phone_speaker,
                            R.drawable.ic_speaker_phone,
                            R.drawable.ic_speaker_phone_activatable))
                    .build();

    private AlertDialog mAudioRouteSelectionDialog;
    private AudioRouteListAdapter mAudioRouteAdapter;
    private ImageView mMuteButton;
    private ImageView mAudioRouteButton;
    private ImageView mPauseButton;
    private LiveData<Call> mCallLiveData;
    private MutableLiveData<Boolean> mDialpadState;
    private int mCallState;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        View dialogView = LayoutInflater.from(getContext()).inflate(
                R.layout.audio_route_switch_dialog, null, false);
        RecyclerView list = dialogView.findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(getContext()));

        mAudioRouteSelectionDialog = new AlertDialog.Builder(getContext())
                .setView(dialogView)
                .create();

        List<Integer> availableRoutes = UiCallManager.get().getSupportedAudioRoute();
        int activeRoute = UiCallManager.get().getAudioRoute();
        mAudioRouteAdapter = new AudioRouteListAdapter(getContext(), availableRoutes, activeRoute);
        list.setAdapter(mAudioRouteAdapter);

        InCallViewModel inCallViewModel = ViewModelProviders.of(getActivity()).get(
                InCallViewModel.class);

        inCallViewModel.getPrimaryCallState().observe(this, this::setCallState);
        mCallLiveData = inCallViewModel.getPrimaryCall();
        inCallViewModel.getAudioRoute().observe(this, this::updateViewBasedOnAudioRoute);

        OngoingCallStateViewModel ongoingCallStateViewModel = ViewModelProviders.of(
                getActivity()).get(OngoingCallStateViewModel.class);
        mDialpadState = ongoingCallStateViewModel.getDialpadState();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View fragmentView = inflater.inflate(R.layout.on_going_call_controller_bar_fragment,
                container, false);

        mMuteButton = fragmentView.findViewById(R.id.mute_button);
        mMuteButton.setOnClickListener((v) -> {
            if (v.isActivated()) {
                v.setActivated(false);
                onUnmuteMic();
            } else {
                v.setActivated(true);
                onMuteMic();
            }
        });

        View dialPadButton = fragmentView.findViewById(R.id.toggle_dialpad_button);
        dialPadButton.setOnClickListener(v -> mDialpadState.setValue(!mDialpadState.getValue()));
        mDialpadState.observe(this, activated -> dialPadButton.setActivated(activated));

        View endCallButton = fragmentView.findViewById(R.id.end_call_button);
        endCallButton.setOnClickListener(v -> onEndCall());

        List<Integer> audioRoutes = UiCallManager.get().getSupportedAudioRoute();
        mAudioRouteButton = fragmentView.findViewById(R.id.voice_channel_button);
        if (audioRoutes.size() > 1) {
            mAudioRouteButton.setOnClickListener((v) -> {
                mAudioRouteButton.setActivated(true);
                mAudioRouteAdapter.setActiveAudioRoute(UiCallManager.get().getAudioRoute());
                mAudioRouteSelectionDialog.show();
            });
        }

        mAudioRouteSelectionDialog.setOnDismissListener(
                (dialog) -> mAudioRouteButton.setActivated(false));

        mPauseButton = fragmentView.findViewById(R.id.pause_button);
        mPauseButton.setOnClickListener((v) -> {
            if (mCallState == Call.STATE_ACTIVE) {
                onHoldCall();
            } else if (mCallState == Call.STATE_HOLDING) {
                onUnholdCall();
            } else {
                L.i(TAG, "Pause button is clicked while call in %s state", mCallState);
            }
        });
        updatePauseButtonEnabledState();

        return fragmentView;
    }

    @Override
    public void onPause() {
        super.onPause();
        L.i(TAG, "onPause");
        if (mAudioRouteSelectionDialog.isShowing()) {
            mAudioRouteSelectionDialog.dismiss();
        }
    }

    /** Set the call state and change the view for the pause button accordingly */
    private void setCallState(int callState) {
        L.d(TAG, "Call State: %s", callState);
        mCallState = callState;
        updatePauseButtonEnabledState();
    }

    private void updatePauseButtonEnabledState() {
        if (mCallState == Call.STATE_HOLDING) {
            mPauseButton.setEnabled(true);
            mPauseButton.setActivated(true);
        } else if (mCallState == Call.STATE_ACTIVE) {
            mPauseButton.setEnabled(true);
            mPauseButton.setActivated(false);
        } else {
            mPauseButton.setEnabled(false);
        }
    }

    private void onMuteMic() {
        UiCallManager.get().setMuted(true);
    }

    private void onUnmuteMic() {
        UiCallManager.get().setMuted(false);
    }

    private void onHoldCall() {
        if (mCallLiveData.getValue() != null) {
            mCallLiveData.getValue().hold();
        }
    }

    private void onUnholdCall() {
        if (mCallLiveData.getValue() != null) {
            mCallLiveData.getValue().unhold();
        }
    }

    private void onEndCall() {
        if (mCallLiveData.getValue() != null) {
            mCallLiveData.getValue().disconnect();
        }
    }

    private void onSetAudioRoute(@CallAudioRoute int audioRoute) {
        UiCallManager.get().setAudioRoute(audioRoute);
        mAudioRouteSelectionDialog.dismiss();
    }

    private void updateViewBasedOnAudioRoute(@Nullable Integer audioRoute) {
        if (audioRoute == null) {
            return;
        }

        L.i(TAG, "Audio Route State: " + audioRoute);
        mAudioRouteButton.setImageResource(getAudioRouteInfo(audioRoute).mIconActivatable);

        updateMuteButtonEnabledState(audioRoute);
    }

    private void updateMuteButtonEnabledState(Integer audioRoute) {
        if (audioRoute == CallAudioState.ROUTE_BLUETOOTH) {
            mMuteButton.setEnabled(true);
            mMuteButton.setActivated(UiCallManager.get().getMuted());
        } else {
            mMuteButton.setEnabled(false);
        }
    }

    private static AudioRouteInfo getAudioRouteInfo(int route) {
        AudioRouteInfo routeInfo = AUDIO_ROUTES.get(route);
        if (routeInfo != null) {
            return routeInfo;
        } else {
            L.e(TAG, "Unknown audio route: %s", route);
            throw new RuntimeException("Unknown audio route: " + route);
        }
    }

    private static final class AudioRouteInfo {
        private final int mLabel;
        private final int mIcon;
        private final int mIconActivatable;

        private AudioRouteInfo(@StringRes int label,
                @DrawableRes int icon,
                @DrawableRes int iconActivatable) {
            mLabel = label;
            mIcon = icon;
            mIconActivatable = iconActivatable;
        }
    }

    private class AudioRouteListAdapter extends
            RecyclerView.Adapter<AudioRouteItemViewHolder> {
        private List<Integer> mSupportedRoutes;
        private Context mContext;
        private int mActiveAudioRoute;

        AudioRouteListAdapter(Context context,
                List<Integer> supportedRoutes,
                int activeAudioRoute) {
            mSupportedRoutes = supportedRoutes;
            mActiveAudioRoute = activeAudioRoute;
            mContext = context;
            if (mSupportedRoutes.contains(CallAudioState.ROUTE_EARPIECE)
                    && mSupportedRoutes.contains(CallAudioState.ROUTE_WIRED_HEADSET)) {
                // Keep either ROUTE_EARPIECE or ROUTE_WIRED_HEADSET, but not both of them.
                mSupportedRoutes.remove(CallAudioState.ROUTE_WIRED_HEADSET);
            }
        }

        public void setActiveAudioRoute(int route) {
            if (mActiveAudioRoute != route) {
                mActiveAudioRoute = route;
                notifyDataSetChanged();
            }
        }

        @Override
        public AudioRouteItemViewHolder onCreateViewHolder(ViewGroup container, int position) {
            View listItemView = LayoutInflater.from(mContext).inflate(
                    R.layout.audio_route_list_item, container, false);
            return new AudioRouteItemViewHolder(listItemView);
        }

        @Override
        public void onBindViewHolder(AudioRouteItemViewHolder viewHolder, int position) {
            int audioRoute = mSupportedRoutes.get(position);
            AudioRouteInfo routeInfo = getAudioRouteInfo(audioRoute);
            viewHolder.mBody.setText(routeInfo.mLabel);
            viewHolder.mIcon.setImageResource(routeInfo.mIcon);
            viewHolder.itemView.setActivated(audioRoute == mActiveAudioRoute);
            viewHolder.itemView.setOnClickListener((v) -> onSetAudioRoute(audioRoute));
        }

        @Override
        public int getItemCount() {
            return mSupportedRoutes.size();
        }
    }

    private static class AudioRouteItemViewHolder extends RecyclerView.ViewHolder {
        public final ImageView mIcon;
        public final TextView mBody;

        public AudioRouteItemViewHolder(View itemView) {
            super(itemView);
            mIcon = itemView.findViewById(R.id.icon);
            mBody = itemView.findViewById(R.id.body);
        }
    }
}
