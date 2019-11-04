package com.android.customization.model.theme;

import static com.android.customization.model.ResourceConstants.ANDROID_PACKAGE;
import static com.android.customization.model.ResourceConstants.ICONS_FOR_PREVIEW;
import static com.android.customization.model.ResourceConstants.SETTINGS_PACKAGE;
import static com.android.customization.model.ResourceConstants.SYSUI_PACKAGE;

import android.content.Context;
import android.content.om.OverlayInfo;
import android.content.om.OverlayManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.content.res.Resources.NotFoundException;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.Dimension;
import androidx.annotation.Nullable;

import com.android.customization.model.ResourceConstants;
import com.android.customization.model.theme.ThemeBundle.Builder;
import com.android.wallpaper.R;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

class OverlayThemeExtractor {

    private static final String TAG = "OverlayThemeExtractor";

    private final Context mContext;
    private final Map<String, OverlayInfo> mOverlayInfos = new HashMap<>();
    // List of packages
    private final String[] mShapePreviewIconPackages;

    OverlayThemeExtractor(Context context) {
        mContext = context;
        OverlayManager om = context.getSystemService(OverlayManager.class);
        if (om != null) {
            Consumer<OverlayInfo> addToMap = overlayInfo -> mOverlayInfos.put(
                    overlayInfo.getPackageName(), overlayInfo);

            UserHandle user = UserHandle.of(UserHandle.myUserId());
            om.getOverlayInfosForTarget(ANDROID_PACKAGE, user).forEach(addToMap);
            om.getOverlayInfosForTarget(SYSUI_PACKAGE, user).forEach(addToMap);
            om.getOverlayInfosForTarget(SETTINGS_PACKAGE, user).forEach(addToMap);
            om.getOverlayInfosForTarget(ResourceConstants.getLauncherPackage(context), user)
                    .forEach(addToMap);
            om.getOverlayInfosForTarget(context.getPackageName(), user).forEach(addToMap);
        }
        mShapePreviewIconPackages = context.getResources().getStringArray(
                R.array.icon_shape_preview_packages);
    }

    boolean isAvailable() {
        return !mOverlayInfos.isEmpty();
    }

    void addColorOverlay(Builder builder, String colorOverlayPackage)
            throws NameNotFoundException {
        if (!TextUtils.isEmpty(colorOverlayPackage)) {
            builder.addOverlayPackage(getOverlayCategory(colorOverlayPackage),
                    colorOverlayPackage)
                    .setColorAccentLight(loadColor(ResourceConstants.ACCENT_COLOR_LIGHT_NAME,
                            colorOverlayPackage))
                    .setColorAccentDark(loadColor(ResourceConstants.ACCENT_COLOR_DARK_NAME,
                            colorOverlayPackage));
        } else {
            addSystemDefaultColor(builder);
        }
    }

    void addShapeOverlay(Builder builder, String shapeOverlayPackage)
            throws NameNotFoundException {
        addShapeOverlay(builder, shapeOverlayPackage, true);
    }

    void addShapeOverlay(Builder builder, String shapeOverlayPackage, boolean addPreview)
            throws NameNotFoundException {
        if (!TextUtils.isEmpty(shapeOverlayPackage)) {
            builder.addOverlayPackage(getOverlayCategory(shapeOverlayPackage),
                    shapeOverlayPackage)
                    .setShapePath(
                            loadString(ResourceConstants.CONFIG_ICON_MASK, shapeOverlayPackage))
                    .setBottomSheetCornerRadius(
                            loadDimen(ResourceConstants.CONFIG_CORNERRADIUS, shapeOverlayPackage));
        } else {
            addSystemDefaultShape(builder);
        }
        if (addPreview) {
            addShapePreviewIcons(builder);
        }
    }

    private void addShapePreviewIcons(Builder builder) {
        for (String packageName : mShapePreviewIconPackages) {
            try {
                builder.addShapePreviewIcon(
                        mContext.getPackageManager().getApplicationIcon(
                                packageName));
            } catch (NameNotFoundException e) {
                Log.d(TAG, "Couldn't find app " + packageName
                        + ", won't use it for icon shape preview");
            }
        }
    }

    void addNoPreviewIconOverlay(Builder builder, String overlayPackage) {
        if (!TextUtils.isEmpty(overlayPackage)) {
            builder.addOverlayPackage(getOverlayCategory(overlayPackage),
                    overlayPackage);
        }
    }

    void addSysUiIconOverlay(Builder builder, String iconSysUiOverlayPackage)
            throws NameNotFoundException {
        if (!TextUtils.isEmpty(iconSysUiOverlayPackage)) {
            addIconOverlay(builder, iconSysUiOverlayPackage);
        }
    }

    void addAndroidIconOverlay(Builder builder, String iconAndroidOverlayPackage)
            throws NameNotFoundException {
        if (!TextUtils.isEmpty(iconAndroidOverlayPackage)) {
            addIconOverlay(builder, iconAndroidOverlayPackage, ICONS_FOR_PREVIEW);
        } else {
            addSystemDefaultIcons(builder, ANDROID_PACKAGE, ICONS_FOR_PREVIEW);
        }
    }

    void addIconOverlay(Builder builder, String packageName, String... previewIcons)
            throws NameNotFoundException {
        builder.addOverlayPackage(getOverlayCategory(packageName), packageName);
        for (String iconName : previewIcons) {
            builder.addIcon(loadIconPreviewDrawable(iconName, packageName, false));
        }
    }

    void addFontOverlay(Builder builder, String fontOverlayPackage)
            throws NameNotFoundException {
        if (!TextUtils.isEmpty(fontOverlayPackage)) {
            builder.addOverlayPackage(getOverlayCategory(fontOverlayPackage),
                    fontOverlayPackage)
                    .setBodyFontFamily(loadTypeface(
                            ResourceConstants.CONFIG_BODY_FONT_FAMILY,
                            fontOverlayPackage))
                    .setHeadlineFontFamily(loadTypeface(
                            ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY,
                            fontOverlayPackage));
        } else {
            addSystemDefaultFont(builder);
        }
    }

    void addSystemDefaultIcons(Builder builder, String packageName,
            String... previewIcons) {
        try {
            for (String iconName : previewIcons) {
                builder.addIcon(loadIconPreviewDrawable(iconName, packageName, true));
            }
        } catch (NameNotFoundException | NotFoundException e) {
            Log.w(TAG, "Didn't find android package icons, will skip preview", e);
        }
    }

    void addSystemDefaultShape(Builder builder) {
        Resources system = Resources.getSystem();
        String iconMaskPath = system.getString(
                system.getIdentifier(ResourceConstants.CONFIG_ICON_MASK,
                        "string", ResourceConstants.ANDROID_PACKAGE));
        builder.setShapePath(iconMaskPath)
                .setBottomSheetCornerRadius(
                        system.getDimensionPixelOffset(
                                system.getIdentifier(ResourceConstants.CONFIG_CORNERRADIUS,
                                        "dimen", ResourceConstants.ANDROID_PACKAGE)));
    }

    void addSystemDefaultColor(Builder builder) {
        Resources system = Resources.getSystem();
        int colorAccentLight = system.getColor(
                system.getIdentifier(ResourceConstants.ACCENT_COLOR_LIGHT_NAME, "color",
                        ResourceConstants.ANDROID_PACKAGE), null);
        builder.setColorAccentLight(colorAccentLight);

        int colorAccentDark = system.getColor(
                system.getIdentifier(ResourceConstants.ACCENT_COLOR_DARK_NAME, "color",
                        ResourceConstants.ANDROID_PACKAGE), null);
        builder.setColorAccentDark(colorAccentDark);
    }

    void addSystemDefaultFont(Builder builder) {
        Resources system = Resources.getSystem();
        String headlineFontFamily = system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_HEADLINE_FONT_FAMILY, "string",
                ResourceConstants.ANDROID_PACKAGE));
        String bodyFontFamily = system.getString(system.getIdentifier(
                ResourceConstants.CONFIG_BODY_FONT_FAMILY,
                "string", ResourceConstants.ANDROID_PACKAGE));
        builder.setHeadlineFontFamily(Typeface.create(headlineFontFamily, Typeface.NORMAL))
                .setBodyFontFamily(Typeface.create(bodyFontFamily, Typeface.NORMAL));
    }

    Typeface loadTypeface(String configName, String fontOverlayPackage)
            throws NameNotFoundException, NotFoundException {

        // TODO(santie): check for font being present in system

        Resources overlayRes = mContext.getPackageManager()
                .getResourcesForApplication(fontOverlayPackage);

        String fontFamily = overlayRes.getString(overlayRes.getIdentifier(configName,
                "string", fontOverlayPackage));
        return Typeface.create(fontFamily, Typeface.NORMAL);
    }

    int loadColor(String colorName, String colorPackage)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes = mContext.getPackageManager()
                .getResourcesForApplication(colorPackage);
        return overlayRes.getColor(overlayRes.getIdentifier(colorName, "color", colorPackage),
                null);
    }

    String loadString(String stringName, String packageName)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes =
                mContext.getPackageManager().getResourcesForApplication(
                        packageName);
        return overlayRes.getString(overlayRes.getIdentifier(stringName, "string", packageName));
    }

    @Dimension
    int loadDimen(String dimenName, String packageName)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes =
                mContext.getPackageManager().getResourcesForApplication(
                        packageName);
        return overlayRes.getDimensionPixelOffset(overlayRes.getIdentifier(
                dimenName, "dimen", packageName));
    }

    boolean loadBoolean(String booleanName, String packageName)
            throws NameNotFoundException, NotFoundException {

        Resources overlayRes =
                mContext.getPackageManager().getResourcesForApplication(
                        packageName);
        return overlayRes.getBoolean(overlayRes.getIdentifier(
                booleanName, "boolean", packageName));
    }

    Drawable loadIconPreviewDrawable(String drawableName, String packageName,
            boolean fromSystem) throws NameNotFoundException, NotFoundException {

        Resources packageRes =
                mContext.getPackageManager().getResourcesForApplication(
                        packageName);
        Resources res = fromSystem ? Resources.getSystem() : packageRes;
        return res.getDrawable(
                packageRes.getIdentifier(drawableName, "drawable", packageName), null);
    }

    @Nullable
    String getOverlayCategory(String packageName) {
        OverlayInfo info = mOverlayInfos.get(packageName);
        return info != null ? info.getCategory() : null;
    }

    String[] getShapePreviewIconPackages() {
        return mShapePreviewIconPackages;
    }
}