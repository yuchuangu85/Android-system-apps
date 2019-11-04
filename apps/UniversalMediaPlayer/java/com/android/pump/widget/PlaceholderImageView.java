package com.android.pump.widget;

import android.content.Context;
import android.graphics.drawable.Drawable;
import android.util.AttributeSet;

import androidx.annotation.AttrRes;
import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.UiThread;
import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.content.ContextCompat;

import com.android.pump.R;

@UiThread
public class PlaceholderImageView extends AppCompatImageView {
    private static final @DrawableRes int PLACEHOLDER_DRAWABLE = R.drawable.ic_placeholder;

    public PlaceholderImageView(@NonNull Context context) {
        super(context);
        initialize();
    }

    public PlaceholderImageView(@NonNull Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public PlaceholderImageView(@NonNull Context context, @Nullable AttributeSet attrs,
            @AttrRes int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    @Override
    public void setImageDrawable(@Nullable Drawable drawable) {
        if (drawable == null) {
            drawable = ContextCompat.getDrawable(getContext(), PLACEHOLDER_DRAWABLE);
        }
        super.setImageDrawable(drawable);
    }

    private void initialize() {
        if (getDrawable() == null) {
            setImageDrawable(null);
        }
    }
}
