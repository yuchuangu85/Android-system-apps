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
 * limitations under the License.
 */

package com.android.phone;

import android.content.Context;
import android.telephony.emergency.EmergencyNumber;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.common.collect.LinkedListMultimap;

import java.util.ArrayList;
import java.util.List;

/**
 * An abstract adapter between ECC data and the view contains ECC shortcuts.
 * This adapter prepares description and icon for every promoted emergency number.
 * The subclass should implements {@link #inflateView} to provide the view for an ECC data, when the
 * view container calls {@link #getView}.
 */
public abstract class EccShortcutAdapter extends BaseAdapter {
    private List<EccDisplayMaterial> mEccDisplayMaterialList;

    private CharSequence mPoliceDescription;
    private CharSequence mAmbulanceDescription;
    private CharSequence mFireDescription;

    private static class EccDisplayMaterial {
        public CharSequence number = null;
        public int iconRes = 0;
        public CharSequence description = null;
    }

    public EccShortcutAdapter(@NonNull Context context) {
        mPoliceDescription = context.getText(R.string.police_type_description);
        mAmbulanceDescription = context.getText(R.string.ambulance_type_description);
        mFireDescription = context.getText(R.string.fire_type_description);

        mEccDisplayMaterialList = new ArrayList<>();
    }

    @Override
    public int getCount() {
        return mEccDisplayMaterialList.size();
    }

    @Override
    public EccDisplayMaterial getItem(int position) {
        return mEccDisplayMaterialList.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        EccDisplayMaterial material = getItem(position);
        return inflateView(convertView, parent, material.number, material.description,
                material.iconRes);
    }

    /**
     * Get a View that display the given ECC data: number, description and iconRes.
     *
     * @param convertView The old view to reuse, if possible. Note: You should check that this view
     *                    is non-null and of an appropriate type before using. If it is not possible
     *                    to convert this view to display the correct data, this method can create a
     *                    new view. Heterogeneous lists can specify their number of view types, so
     *                    that this View is always of the right type (see {@link
     *                    BaseAdapter#getViewTypeCount()} and {@link
     *                    BaseAdapter#getItemViewType(int)}).
     * @param parent      The parent that this view will eventually be attached to.
     * @param number      The number of the ECC shortcut to display in the view.
     * @param description The description of the ECC shortcut to display in the view.
     * @param iconRes     The icon resource ID represent for the ECC shortcut.
     * @return A View corresponding to the data at the specified position.
     */
    public abstract View inflateView(View convertView, ViewGroup parent, CharSequence number,
            CharSequence description, int iconRes);

    /**
     * Update country ECC info. This method converts given country ECC info to ECC data that could
     * be display by the short container View.
     *
     * @param context The context used to access resources.
     * @param phoneInfo Information of the phone to make an emergency call.
     */
    public void updateCountryEccInfo(@NonNull Context context,
            @Nullable ShortcutViewUtils.PhoneInfo phoneInfo) {
        List<EccDisplayMaterial> displayMaterials = new ArrayList<>();

        try {
            if (phoneInfo == null) {
                return;
            }

            LinkedListMultimap<String, Integer> emergencyNumbers = LinkedListMultimap.create();
            for (int category : ShortcutViewUtils.PROMOTED_CATEGORIES) {
                String number = pickEmergencyNumberForCategory(category,
                        phoneInfo.getPromotedEmergencyNumbers());
                if (number != null) {
                    emergencyNumbers.put(number, category);
                }
            }

            // prepare display material for picked ECC
            for (String number : emergencyNumbers.keySet()) {
                EccDisplayMaterial material = prepareEccMaterial(context, number,
                        emergencyNumbers.get(number));
                if (material != null) {
                    displayMaterials.add(material);
                }
            }
        } finally {
            mEccDisplayMaterialList = displayMaterials;
            notifyDataSetChanged();
        }
    }

    boolean hasShortcut(String number) {
        if (mEccDisplayMaterialList == null) {
            return false;
        }

        for (EccDisplayMaterial displayMaterial : mEccDisplayMaterialList) {
            if (displayMaterial.number.equals(number)) {
                return true;
            }
        }
        return false;
    }

    @Nullable
    private String pickEmergencyNumberForCategory(int category,
            @NonNull List<EmergencyNumber> emergencyNumbers) {
        for (EmergencyNumber number : emergencyNumbers) {
            if ((number.getEmergencyServiceCategoryBitmask() & category) != 0) {
                return number.getNumber();
            }
        }
        return null;
    }

    @Nullable
    private EccDisplayMaterial prepareEccMaterial(@NonNull Context context, @NonNull String number,
            @NonNull List<Integer> categories) {
        EccDisplayMaterial material = new EccDisplayMaterial();
        material.number = number;
        for (int category : categories) {
            CharSequence description;
            switch (category) {
                case EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_POLICE:
                    description = mPoliceDescription;
                    material.iconRes = R.drawable.ic_local_police_gm2_24px;
                    break;
                case EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_AMBULANCE:
                    description = mAmbulanceDescription;
                    material.iconRes = R.drawable.ic_local_hospital_gm2_24px;
                    break;
                case EmergencyNumber.EMERGENCY_SERVICE_CATEGORY_FIRE_BRIGADE:
                    description = mFireDescription;
                    material.iconRes = R.drawable.ic_local_fire_department_gm2_24px;
                    break;
                default:
                    // ignore unknown types
                    continue;
            }

            if (TextUtils.isEmpty(material.description)) {
                material.description = description;
            } else {
                // concatenate multiple types
                material.iconRes = R.drawable.ic_local_hospital_gm2_24px;
                material.description = context.getString(R.string.description_concat_format,
                        material.description, description);
            }
        }

        if (TextUtils.isEmpty(material.description) || material.iconRes == 0) {
            return null;
        }
        return material;
    }
}
