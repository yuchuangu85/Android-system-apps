package com.android.customization.widget;

import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableWrapper;

import androidx.annotation.Nullable;

/**
 * {@link DrawableWrapper} that no-ops {@link #setTint(int)} and
 * {@link #setTintList(ColorStateList)}, leaving the original {@link Drawable} tint intact.
 */
public class NoTintDrawableWrapper extends DrawableWrapper {
    public NoTintDrawableWrapper(Drawable drawable) {
        super(drawable);
    }

    @Override
    public void setTint(int tintColor) {}

    @Override
    public void setTintList(@Nullable ColorStateList tint) {}
}
