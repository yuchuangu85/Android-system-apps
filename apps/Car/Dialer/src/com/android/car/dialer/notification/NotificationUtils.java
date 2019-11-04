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

package com.android.car.dialer.notification;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.drawable.Icon;
import android.net.Uri;

import androidx.annotation.Nullable;
import androidx.core.graphics.drawable.RoundedBitmapDrawable;
import androidx.core.graphics.drawable.RoundedBitmapDrawableFactory;
import androidx.core.util.Pair;

import com.android.car.apps.common.LetterTileDrawable;
import com.android.car.dialer.R;
import com.android.car.telephony.common.TelecomUtils;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.concurrent.CompletableFuture;

/** Util class that shares common functionality for notifications. */
final class NotificationUtils {
    private NotificationUtils() {
    }

    static CompletableFuture<Pair<String, Icon>> getDisplayNameAndRoundedAvatar(Context context,
            String number) {
        return TelecomUtils.getPhoneNumberInfo(context, number)
                .thenApplyAsync((info) -> {
                    int size = context.getResources()
                            .getDimensionPixelSize(R.dimen.avatar_icon_size);
                    Icon largeIcon = loadContactAvatar(context, info.getAvatarUri(), size);
                    if (largeIcon == null) {
                        largeIcon = createLetterTile(context, info.getDisplayName(), size);
                    }

                    return new Pair<>(info.getDisplayName(), largeIcon);
                });
    }

    static Icon loadContactAvatar(Context context, @Nullable Uri avatarUri, int avatarSize) {
        if (avatarUri == null) {
            return null;
        }

        try {
            InputStream input = context.getContentResolver().openInputStream(avatarUri);
            if (input == null) {
                return null;
            }
            RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(
                    context.getResources(), input);
            return createFromRoundedBitmapDrawable(context, roundedBitmapDrawable, avatarSize);
        } catch (FileNotFoundException e) {
            // No-op
        }
        return null;
    }

    private static Icon createLetterTile(Context context, String displayName, int avatarSize) {
        LetterTileDrawable letterTileDrawable = TelecomUtils.createLetterTile(context, displayName);
        RoundedBitmapDrawable roundedBitmapDrawable = RoundedBitmapDrawableFactory.create(
                context.getResources(), letterTileDrawable.toBitmap(avatarSize));
        return createFromRoundedBitmapDrawable(context, roundedBitmapDrawable, avatarSize);
    }

    private static Icon createFromRoundedBitmapDrawable(Context context,
            RoundedBitmapDrawable roundedBitmapDrawable, int avatarSize) {
        float radiusPercent = context.getResources()
                .getFloat(R.dimen.contact_avatar_corner_radius_percent);
        float radius = avatarSize * radiusPercent;
        roundedBitmapDrawable.setCornerRadius(radius);

        final Bitmap result = Bitmap.createBitmap(avatarSize, avatarSize,
                Bitmap.Config.ARGB_8888);
        final Canvas canvas = new Canvas(result);
        roundedBitmapDrawable.setBounds(0, 0, canvas.getWidth(), canvas.getHeight());
        roundedBitmapDrawable.draw(canvas);
        return Icon.createWithBitmap(result);
    }
}
