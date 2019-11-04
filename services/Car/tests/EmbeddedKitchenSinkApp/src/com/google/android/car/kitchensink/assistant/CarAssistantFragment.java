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
package com.google.android.car.kitchensink.assistant;

import static android.service.voice.VoiceInteractionSession.SHOW_SOURCE_PUSH_TO_TALK;

import android.app.ActivityManager;
import android.content.Context;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.android.internal.app.AssistUtils;
import com.android.internal.app.IVoiceInteractionSessionShowCallback;

import com.google.android.car.kitchensink.R;

public class CarAssistantFragment extends Fragment {

    private static final String EXTRA_CAR_PUSH_TO_TALK =
            "com.android.car.input.EXTRA_CAR_PUSH_TO_TALK";

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.car_assistant, container, false);
    }

    @Override
    public void onViewCreated(View view, Bundle savedInstanceState) {
        ImageButton micButton = (ImageButton) view.findViewById(R.id.voice_button_service);
        Context context = getContext();

        IVoiceInteractionSessionShowCallback showCallback =
                new IVoiceInteractionSessionShowCallback.Stub() {
                    @Override
                    public void onFailed() {
                        Toast.makeText(context, "Failed to show VoiceInteractionSession",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override
                    public void onShown() {}
                };

        micButton.setOnClickListener(v1 -> {
            v1.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);

            AssistUtils assistUtils = new AssistUtils(context);

            if (assistUtils.getAssistComponentForUser(ActivityManager.getCurrentUser()) == null) {
                Toast.makeText(context, "Unable to retrieve assist component for current user",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Bundle args = new Bundle();
            args.putBoolean(EXTRA_CAR_PUSH_TO_TALK, true);

            boolean success = assistUtils.showSessionForActiveService(args,
                    SHOW_SOURCE_PUSH_TO_TALK, showCallback, /*activityToken=*/ null);
            if (!success) {
                Toast.makeText(context,
                        "Assistant app is not available.", Toast.LENGTH_SHORT).show();
            }
        });
    }
}
