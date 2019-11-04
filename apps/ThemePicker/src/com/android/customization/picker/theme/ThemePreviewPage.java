package com.android.customization.picker.theme;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Resources;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.icu.text.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLayoutChangeListener;
import android.view.ViewGroup;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.ColorInt;
import androidx.annotation.DrawableRes;
import androidx.annotation.LayoutRes;
import androidx.annotation.StringRes;
import androidx.constraintlayout.widget.Guideline;

import com.android.customization.picker.BasePreviewAdapter.PreviewPage;
import com.android.wallpaper.R;

import java.text.FieldPosition;
import java.util.Calendar;
import java.util.List;
import java.util.TimeZone;

abstract class ThemePreviewPage extends PreviewPage {

    public interface TimeContainer {
        void updateTime();
    }

    @StringRes
    final int nameResId;
    final Drawable icon;
    @LayoutRes
    final int contentLayoutRes;
    @ColorInt
    final int accentColor;
    protected final LayoutInflater inflater;

    public ThemePreviewPage(Context context, @StringRes int titleResId,
            @DrawableRes int iconSrc, @LayoutRes int contentLayoutRes,
            @ColorInt int accentColor) {
        super(null);
        this.nameResId = titleResId;
        if (iconSrc != Resources.ID_NULL) {
            this.icon = context.getResources().getDrawable(iconSrc, context.getTheme());
            int size = context.getResources().getDimensionPixelSize(R.dimen.card_header_icon_size);
            icon.setBounds(0, 0, size, size);
        } else {
            this.icon = null;
        }
        this.contentLayoutRes = contentLayoutRes;
        this.accentColor = accentColor;
        this.inflater = LayoutInflater.from(context);
    }

    @Override
    public void bindPreviewContent() {
        TextView header = card.findViewById(R.id.theme_preview_card_header);
        header.setText(nameResId);
        header.setCompoundDrawables(null, icon, null, null);
        header.setCompoundDrawableTintList(ColorStateList.valueOf(accentColor));
        card.findViewById(R.id.theme_preview_top_bar).setVisibility(View.GONE);
        card.findViewById(R.id.edit_label).setVisibility(View.GONE);

        ViewGroup body = card.findViewById(R.id.theme_preview_card_body_container);
        inflater.inflate(contentLayoutRes, body, true);
        bindBody(false);
    }

    protected boolean containsWallpaper() {
        return false;
    }

    protected abstract void bindBody(boolean forceRebind);

    static class ThemeCoverPage extends ThemePreviewPage implements TimeContainer {

        public static final int COVER_PAGE_WALLPAPER_ALPHA = 0x66;
        /**
         * Maps which icon from ResourceConstants#ICONS_FOR_PREVIEW to use for each icon in the
         * top bar (fake "status bar") of the cover page.
         */
        private static final int [] sTopBarIconToPreviewIcon = new int [] { 0, 6, 7 };

        private final Typeface mHeadlineFont;
        private final List<Drawable> mIcons;
        private final List<Drawable> mShapeAppIcons;
        private Drawable mShapeDrawable;
        private final int[] mColorButtonIds;
        private final int[] mColorTileIds;
        private final int[][] mColorTileIconIds;
        private final int[] mShapeIconIds;
        private final Resources mRes;
        private String mTitle;
        private OnClickListener mEditClickListener;
        private final OnLayoutChangeListener[] mListeners;
        private final int mCornerRadius;
        private final ColorStateList mTintList;

        public ThemeCoverPage(Context context, String title, int accentColor, List<Drawable> icons,
                Typeface headlineFont, int cornerRadius,
                Drawable shapeDrawable,
                List<Drawable> shapeAppIcons,
                OnClickListener editClickListener,
                int[] colorButtonIds, int[] colorTileIds, int[][] colorTileIconIds,
                int[] shapeIconIds, OnLayoutChangeListener... wallpaperListeners) {
            super(context, 0, 0, R.layout.preview_card_cover_content, accentColor);
            mRes = context.getResources();
            mTitle = title;
            mHeadlineFont = headlineFont;
            mIcons = icons;
            mCornerRadius = cornerRadius;
            mShapeDrawable = shapeDrawable;
            mShapeAppIcons = shapeAppIcons;
            mEditClickListener = editClickListener;
            mColorButtonIds = colorButtonIds;
            mColorTileIds = colorTileIds;
            mColorTileIconIds = colorTileIconIds;
            mShapeIconIds = shapeIconIds;
            mListeners = wallpaperListeners;
            // Color QS icons:
            int controlGreyColor = mRes.getColor(R.color.control_grey, null);
            mTintList = new ColorStateList(
                    new int[][]{
                            new int[]{android.R.attr.state_selected},
                            new int[]{android.R.attr.state_checked},
                            new int[]{-android.R.attr.state_enabled},
                    },
                    new int[] {
                            accentColor,
                            accentColor,
                            controlGreyColor
                    }
            );
        }

        @Override
        protected void bindBody(boolean forceRebind) {
            if (card == null) {
                return;
            }
            if (mListeners != null) {
                for (OnLayoutChangeListener listener : mListeners) {
                    if (listener != null) {
                        card.addOnLayoutChangeListener(listener);
                    }
                }
            }

            if (forceRebind) {
                card.requestLayout();
            }

            for (int i = 0; i < mColorButtonIds.length; i++) {
                CompoundButton button = card.findViewById(mColorButtonIds[i]);
                if (button != null) {
                    button.setButtonTintList(mTintList);
                }
            }
            for (int i = 0; i < 3 && i < mIcons.size(); i++) {
                Drawable icon = mIcons.get(mColorTileIconIds[i][1]).getConstantState()
                        .newDrawable().mutate();
                Drawable bgShape = mShapeDrawable.getConstantState().newDrawable();
                bgShape.setTint(accentColor);

                ImageView bg = card.findViewById(mColorTileIds[i]);
                bg.setImageDrawable(bgShape);
                ImageView fg = card.findViewById(mColorTileIconIds[i][0]);
                fg.setImageDrawable(icon);
            }

            // Shape preview icons:

            for (int i = 0; i < 3 && i < mShapeAppIcons.size(); i++) {
                ImageView iconView = card.findViewById(mShapeIconIds[i]);
                iconView.setBackground(mShapeAppIcons.get(i));
            }
        }

        @Override
        public void bindPreviewContent() {
            TextView header = card.findViewById(R.id.theme_preview_card_header);
            header.setText(mTitle);
            header.setTextAppearance(R.style.CoverTitleTextAppearance);
            header.setTypeface(mHeadlineFont);

            card.findViewById(R.id.theme_preview_top_bar).setVisibility(View.VISIBLE);
            TextView clock = card.findViewById(R.id.theme_preview_clock);
            clock.setText(getFormattedTime());
            clock.setTypeface(mHeadlineFont);

            ViewGroup iconsContainer = card.findViewById(R.id.theme_preview_top_bar_icons);

            for (int i = 0; i < iconsContainer.getChildCount(); i++) {
                int iconIndex = sTopBarIconToPreviewIcon[i];
                if (iconIndex < mIcons.size()) {
                    ((ImageView) iconsContainer.getChildAt(i))
                            .setImageDrawable(mIcons.get(iconIndex).getConstantState()
                                    .newDrawable().mutate());
                } else {
                    iconsContainer.getChildAt(i).setVisibility(View.GONE);
                }
            }

            ViewGroup body = card.findViewById(R.id.theme_preview_card_body_container);

            inflater.inflate(contentLayoutRes, body, true);

            bindBody(false);

            TextView editLabel = card.findViewById(R.id.edit_label);
            editLabel.setOnClickListener(mEditClickListener);
            card.setOnClickListener(mEditClickListener);
            editLabel.setVisibility(mEditClickListener != null
                    ? View.VISIBLE : View.INVISIBLE);

            View qsb = card.findViewById(R.id.theme_qsb);
            if (qsb != null && qsb.getVisibility() == View.VISIBLE) {
                if (qsb.getBackground() instanceof GradientDrawable) {
                    GradientDrawable bg = (GradientDrawable) qsb.getBackground();
                    float cornerRadius = useRoundedQSB(mCornerRadius)
                            ? (float)qsb.getLayoutParams().height / 2 : mCornerRadius;
                    bg.setCornerRadii(new float[]{
                            cornerRadius, cornerRadius, cornerRadius, cornerRadius,
                            cornerRadius, cornerRadius, cornerRadius, cornerRadius});
                }
            }

            Guideline guideline = card.findViewById(R.id.guideline);
            if (guideline != null) {
                guideline.setGuidelineEnd(card.getResources().getDimensionPixelOffset(
                        R.dimen.preview_theme_cover_content_bottom));
            }
        }

        @Override
        public void updateTime() {
            if (card != null) {
                ((TextView) card.findViewById(R.id.theme_preview_clock)).setText(
                        getFormattedTime());
            }
        }

        private boolean useRoundedQSB(int cornerRadius) {
            return cornerRadius >=
                    card.getResources().getDimensionPixelSize(R.dimen.roundCornerThreshold);
        }

        private String getFormattedTime() {
            DateFormat df = DateFormat.getTimeInstance(DateFormat.SHORT);
            StringBuffer time = new StringBuffer();
            FieldPosition amPmPosition = new FieldPosition(DateFormat.Field.AM_PM);
            df.format(Calendar.getInstance(TimeZone.getDefault()).getTime(), time, amPmPosition);
            if (amPmPosition.getBeginIndex() > 0) {
                time.delete(amPmPosition.getBeginIndex(), amPmPosition.getEndIndex());
            }
            return time.toString();
        }

        @Override
        protected boolean containsWallpaper() {
            return true;
        }
    }
}
