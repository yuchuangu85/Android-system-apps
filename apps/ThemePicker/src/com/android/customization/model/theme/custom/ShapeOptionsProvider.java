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
package com.android.customization.model.theme.custom;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.CONFIG_CORNERRADIUS;
import static com.android.customization.model.ResourceConstants.CONFIG_ICON_MASK;
import static com.android.customization.model.ResourceConstants.OVERLAY_CATEGORY_SHAPE;
import static com.android.customization.model.ResourceConstants.PATH_SIZE;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Path;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.PathShape;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Dimension;
import androidx.core.graphics.PathParser;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.OverlayManagerCompat;
import com.android.customization.model.theme.custom.ThemeComponentOption.ShapeOption;
import com.android.customization.widget.DynamicAdaptiveIconDrawable;
import com.android.wallpaper.R;

import java.util.ArrayList;
import java.util.List;

/**
 * Implementation of {@link ThemeComponentOptionProvider} that reads {@link ShapeOption}s from
 * icon overlays.
 */
public class ShapeOptionsProvider extends ThemeComponentOptionProvider<ShapeOption> {

    private static final String TAG = "ShapeOptionsProvider";
    private final String[] mShapePreviewIconPackages;
    private int mThumbSize;

    public ShapeOptionsProvider(Context context, OverlayManagerCompat manager) {
        super(context, manager, OVERLAY_CATEGORY_SHAPE);
        mShapePreviewIconPackages = context.getResources().getStringArray(
                R.array.icon_shape_preview_packages);
        mThumbSize = mContext.getResources().getDimensionPixelSize(
                R.dimen.component_shape_thumb_size);
    }

    @Override
    protected void loadOptions() {
        addDefault();
        for (String overlayPackage : mOverlayPackages) {
            try {
                Path path = loadPath(mContext.getPackageManager()
                        .getResourcesForApplication(overlayPackage), overlayPackage);
                ShapeDrawable shapeDrawable = createShapeDrawable(path);
                PackageManager pm = mContext.getPackageManager();
                String label = pm.getApplicationInfo(overlayPackage, 0).loadLabel(pm).toString();
                mOptions.add(new ShapeOption(overlayPackage, label, path,
                        loadCornerRadius(overlayPackage), shapeDrawable, getShapedIcons(path)));
            } catch (NameNotFoundException | NotFoundException e) {
                Log.w(TAG, String.format("Couldn't load shape overlay %s, will skip it",
                        overlayPackage), e);
            }
        }
    }

    private void addDefault() {
        Resources system = Resources.getSystem();
        Path path = loadPath(system, ANDROID_PACKAGE);
        ShapeDrawable shapeDrawable = createShapeDrawable(path);
        mOptions.add(new ShapeOption(null, mContext.getString(R.string.default_theme_title), path,
                system.getDimensionPixelOffset(
                    system.getIdentifier(ResourceConstants.CONFIG_CORNERRADIUS,
                        "dimen", ResourceConstants.ANDROID_PACKAGE)),
                shapeDrawable, getShapedIcons(path)));
    }

    private ShapeDrawable createShapeDrawable(Path path) {
        PathShape shape = new PathShape(path, PATH_SIZE, PATH_SIZE);
        ShapeDrawable shapeDrawable = new ShapeDrawable(shape);
        shapeDrawable.setIntrinsicHeight(mThumbSize);
        shapeDrawable.setIntrinsicWidth(mThumbSize);
        return shapeDrawable;
    }

    private List<Drawable> getShapedIcons(Path path) {
        List<Drawable> icons = new ArrayList<>();
        for (String packageName : mShapePreviewIconPackages) {
            try {
                Drawable appIcon = mContext.getPackageManager().getApplicationIcon(packageName);
                if (appIcon instanceof AdaptiveIconDrawable) {
                    AdaptiveIconDrawable adaptiveIcon = (AdaptiveIconDrawable) appIcon;
                    icons.add(new DynamicAdaptiveIconDrawable(adaptiveIcon.getBackground(),
                            adaptiveIcon.getForeground(), path));
                }
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Couldn't find app " + packageName
                        + ", won't use it for icon shape preview");
            }
        }
        return icons;
    }

    private Path loadPath(Resources overlayRes, String packageName) {
        String shape = overlayRes.getString(overlayRes.getIdentifier(CONFIG_ICON_MASK, "string",
                packageName));

        if (!TextUtils.isEmpty(shape)) {
            return PathParser.createPathFromPathData(shape);
        }
        return null;
    }

    @Dimension
    private int loadCornerRadius(String packageName)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes =
                mContext.getPackageManager().getResourcesForApplication(
                        packageName);
        return overlayRes.getDimensionPixelOffset(overlayRes.getIdentifier(
                CONFIG_CORNERRADIUS, "dimen", packageName));
    }
}
