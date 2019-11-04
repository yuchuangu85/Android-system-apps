package com.android.customization.picker.theme;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;

import com.android.customization.model.theme.custom.CustomThemeManager;
import com.android.customization.model.theme.custom.ThemeComponentOption;
import com.android.customization.model.theme.custom.ThemeComponentOptionProvider;
import com.android.wallpaper.R;
import com.android.wallpaper.picker.ToolbarFragment;

abstract class CustomThemeStepFragment extends ToolbarFragment {
    protected static final String ARG_KEY_POSITION = "CustomThemeStepFragment.position";
    protected static final String ARG_KEY_TITLE_RES_ID = "CustomThemeStepFragment.title_res";
    protected CustomThemeComponentStepHost mHost;
    protected CustomThemeManager mCustomThemeManager;
    protected int mPosition;
    protected ViewGroup mPreviewContainer;
    protected TextView mTitle;
    @StringRes
    protected int mTitleResId;

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mHost = (CustomThemeComponentStepHost) context;
    }

    @Override
    public void onResume() {
        super.onResume();
        mHost.setCurrentStep(mPosition);
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mPosition = getArguments().getInt(ARG_KEY_POSITION);
        mTitleResId = getArguments().getInt(ARG_KEY_TITLE_RES_ID);
        mCustomThemeManager = mHost.getCustomThemeManager();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                getFragmentLayoutResId(), container, /* attachToRoot */ false);
        // No original theme means it's a new one, so no toolbar icon for deleting it is needed
        if (mCustomThemeManager.getOriginalTheme() == null
                || !mCustomThemeManager.getOriginalTheme().isDefined()) {
            setUpToolbar(view);
        } else {
            setUpToolbar(view, R.menu.custom_theme_editor_menu);
            mToolbar.getMenu().getItem(0).setIconTintList(
                    getContext().getColorStateList(R.color.toolbar_icon_color));
        }
        Drawable closeIcon = getResources().getDrawable(R.drawable.ic_close_24px, null).mutate();
        closeIcon.setTintList(getResources().getColorStateList(R.color.toolbar_icon_color, null));
        mToolbar.setNavigationIcon(closeIcon);

        mToolbar.setNavigationContentDescription(R.string.cancel);
        mToolbar.setNavigationOnClickListener(v -> mHost.cancel());

        mPreviewContainer = view.findViewById(R.id.component_preview_content);
        return view;
    }

    @Override
    public boolean onMenuItemClick(MenuItem item) {
        if (item.getItemId() == R.id.custom_theme_delete) {
            AlertDialog.Builder builder = new AlertDialog.Builder(getContext());
            builder.setMessage(R.string.delete_custom_theme_confirmation)
                    .setPositiveButton(R.string.delete_custom_theme_button,
                            (dialogInterface, i) -> mHost.delete())
                    .setNegativeButton(R.string.cancel, null)
                    .create()
                    .show();
            return true;
        }
        return super.onMenuItemClick(item);
    }

    protected abstract int getFragmentLayoutResId();

    public interface CustomThemeComponentStepHost {
        void delete();
        void cancel();
        ThemeComponentOptionProvider<? extends ThemeComponentOption> getComponentOptionProvider(
                int position);

        CustomThemeManager getCustomThemeManager();

        void setCurrentStep(int step);
    }
}
