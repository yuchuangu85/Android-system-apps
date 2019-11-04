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

package com.android.car.notification.template;

import android.annotation.ColorInt;
import android.graphics.Color;

import com.android.internal.graphics.ColorUtils;

/**
 * Helper class to determine if a color is a light color and find a foreground color that has enough
 * contrast than the background color.
 */
public class NotificationColorUtil {
    private static final int MAX_FIND_COLOR_STEPS = 15;
    private static final double MIN_COLOR_CONTRAST = 0.00001;
    private static final double MIN_CONTRAST_RATIO = 4.5;
    private static final double MIN_LIGHTNESS = 0;
    private static final float MAX_LIGHTNESS = 1;
    private static final float LIGHT_COLOR_LUMINANCE_THRESHOLD = 0.5f;

    private NotificationColorUtil() {
    }

    /**
     * Resolves a Notification's color such that it has enough contrast to be used as the
     * color for the Notification's action and header text.
     *
     * @param backgroundColor the background color to ensure the contrast against.
     * @return a color of the same hue as {@code notificationColor} with enough contrast against
     * the backgrounds.
     */
    public static int resolveContrastColor(
            @ColorInt int notificationColor, @ColorInt int backgroundColor) {
        return getContrastedForegroundColor(notificationColor, backgroundColor, MIN_CONTRAST_RATIO);
    }

    /**
     * Returns true if a color is considered a light color.
     */
    public static boolean isColorLight(int backgroundColor) {
        return Color.luminance(backgroundColor) > LIGHT_COLOR_LUMINANCE_THRESHOLD;
    }

    /**
     * Finds a suitable color such that there's enough contrast.
     *
     * @param foregroundColor  the color to start searching from.
     * @param backgroundColor  the color to ensure contrast against. Assumed to be lighter than
     *                         {@param foregroundColor}
     * @param minContrastRatio the minimum contrast ratio required.
     * @return a color with the same hue as {@param foregroundColor}, potentially darkened to
     * meet the contrast ratio.
     */
    private static int findContrastColorAgainstLightBackground(
            @ColorInt int foregroundColor, @ColorInt int backgroundColor, double minContrastRatio) {
        if (ColorUtils.calculateContrast(foregroundColor, backgroundColor) >= minContrastRatio) {
            return foregroundColor;
        }

        double[] lab = new double[3];
        ColorUtils.colorToLAB(foregroundColor, lab);

        double low = MIN_LIGHTNESS;
        double high = lab[0];
        double a = lab[1];
        double b = lab[2];
        for (int i = 0; i < MAX_FIND_COLOR_STEPS && high - low > MIN_COLOR_CONTRAST; i++) {
            double l = (low + high) / 2;
            foregroundColor = ColorUtils.LABToColor(l, a, b);
            if (ColorUtils.calculateContrast(foregroundColor, backgroundColor) > minContrastRatio) {
                low = l;
            } else {
                high = l;
            }
        }
        return ColorUtils.LABToColor(low, a, b);
    }

    /**
     * Finds a suitable color such that there's enough contrast.
     *
     * @param foregroundColor  the foregroundColor to start searching from.
     * @param backgroundColor  the foregroundColor to ensure contrast against. Assumed to be
     *                         darker than {@param foregroundColor}
     * @param minContrastRatio the minimum contrast ratio required.
     * @return a foregroundColor with the same hue as {@param foregroundColor}, potentially
     * lightened to meet the contrast ratio.
     */
    private static int findContrastColorAgainstDarkBackground(
            @ColorInt int foregroundColor, @ColorInt int backgroundColor, double minContrastRatio) {
        if (ColorUtils.calculateContrast(foregroundColor, backgroundColor) >= minContrastRatio) {
            return foregroundColor;
        }

        float[] hsl = new float[3];
        ColorUtils.colorToHSL(foregroundColor, hsl);

        float low = hsl[2];
        float high = MAX_LIGHTNESS;
        for (int i = 0; i < MAX_FIND_COLOR_STEPS && high - low > MIN_COLOR_CONTRAST; i++) {
            float l = (low + high) / 2;
            hsl[2] = l;
            foregroundColor = ColorUtils.HSLToColor(hsl);
            if (ColorUtils.calculateContrast(foregroundColor, backgroundColor)
                    > minContrastRatio) {
                high = l;
            } else {
                low = l;
            }
        }
        return foregroundColor;
    }

    /**
     * Finds a foregroundColor with sufficient contrast over backgroundColor that has the same or
     * darker hue as the original foregroundColor.
     *
     * @param foregroundColor  the foregroundColor to start searching from
     * @param backgroundColor  the foregroundColor to ensure contrast against
     * @param minContrastRatio the minimum contrast ratio required
     */
    private static int getContrastedForegroundColor(
            @ColorInt int foregroundColor, @ColorInt int backgroundColor, double minContrastRatio) {
        boolean isBackgroundDarker =
                Color.luminance(foregroundColor) > Color.luminance(backgroundColor);
        return isBackgroundDarker
                ? findContrastColorAgainstDarkBackground(
                        foregroundColor, backgroundColor, minContrastRatio)
                : findContrastColorAgainstLightBackground(
                        foregroundColor, backgroundColor, minContrastRatio);
    }
}
