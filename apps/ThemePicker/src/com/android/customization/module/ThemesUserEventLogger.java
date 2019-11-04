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
package com.android.customization.module;

import com.android.customization.model.clock.Clockface;
import com.android.customization.model.grid.GridOption;
import com.android.customization.model.theme.ThemeBundle;
import com.android.wallpaper.module.UserEventLogger;

/**
 * Extension of {@link UserEventLogger} that adds ThemePicker specific events.
 */
public interface ThemesUserEventLogger extends UserEventLogger {

    void logThemeSelected(ThemeBundle theme, boolean isCustomTheme);

    void logThemeApplied(ThemeBundle theme, boolean isCustomTheme);

    void logClockSelected(Clockface clock);

    void logClockApplied(Clockface clock);

    void logGridSelected(GridOption grid);

    void logGridApplied(GridOption grid);

}
