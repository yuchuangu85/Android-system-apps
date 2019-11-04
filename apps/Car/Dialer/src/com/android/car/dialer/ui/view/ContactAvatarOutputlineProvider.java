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

package com.android.car.dialer.ui.view;

import android.graphics.Outline;
import android.view.View;
import android.view.ViewOutlineProvider;

import com.android.car.dialer.R;

/** OutlineProvider that changes the shape of ImageViews used to display contact avatars */
public final class ContactAvatarOutputlineProvider extends ViewOutlineProvider {

    private ContactAvatarOutputlineProvider() {}

    private static final ContactAvatarOutputlineProvider INSTANCE =
            new ContactAvatarOutputlineProvider();

    /** Gets the singleton instance */
    public static ContactAvatarOutputlineProvider get() {
        return INSTANCE;
    }

    @Override
    public void getOutline(View view, Outline outline) {
        float radiusPercent = view.getContext().getResources()
                .getFloat(R.dimen.contact_avatar_corner_radius_percent);
        float radius = Math.min(view.getWidth(), view.getHeight()) * radiusPercent;
        outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), radius);
        view.setClipToOutline(true);
    }
}
