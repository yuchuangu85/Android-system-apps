package com.android.documentsui.sorting;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckedTextView;
import android.widget.FrameLayout;
import android.widget.ListView;

import androidx.annotation.StringRes;
import androidx.fragment.app.DialogFragment;
import androidx.fragment.app.FragmentManager;

import com.android.documentsui.R;
import com.android.documentsui.sorting.SortDimension.SortDirection;
import com.android.documentsui.sorting.SortModel.SortDimensionId;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;

public class SortListFragment extends DialogFragment {

    private static final String TAG_MODEL = "sorting_model";
    private static final String TAG_SORTING_LIST = "sorting_list";

    private SortModel mModel;
    private List<SortItem> mSortingList;

    public static void show(FragmentManager fm, SortModel model) {
        SortListFragment fragment = new SortListFragment();
        Bundle args = new Bundle();
        args.putParcelable(TAG_MODEL, model);
        fragment.setArguments(args);
        fragment.show(fm, TAG_SORTING_LIST);
    }

    public SortListFragment() {
        super();
    }

    private void onItemClicked (AdapterView<?> parent, View view, int position, long id) {
        SortItem item = mSortingList.get(position);
        mModel.sortByUser(item.id, item.direction);
        getDialog().dismiss();
    }

    private void setupSortingList() {
        mSortingList = new ArrayList<>();
        for (int i = 0; i < mModel.getSize(); ++i) {
            SortDimension dimension = mModel.getDimensionAt(i);
            if (dimension.getSortCapability() != SortDimension.SORT_CAPABILITY_NONE) {
                switch (dimension.getId()) {
                    case SortModel.SORT_DIMENSION_ID_TITLE:
                    case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                        addBothDirectionDimension(dimension, true);
                        break;
                    case SortModel.SORT_DIMENSION_ID_DATE:
                    case SortModel.SORT_DIMENSION_ID_SIZE:
                        addBothDirectionDimension(dimension, false);
                        break;
                    default:
                        mSortingList.add(new SortItem(dimension));
                        break;
                }
            }
        }
    }

    private void addBothDirectionDimension(SortDimension source, boolean ascendingFirst) {
        SortItem ascending = new SortItem(source.getId(),
                SortDimension.SORT_DIRECTION_ASCENDING,
                getSheetLabelId(source, SortDimension.SORT_DIRECTION_ASCENDING));
        SortItem descending = new SortItem(source.getId(),
                SortDimension.SORT_DIRECTION_DESCENDING,
                getSheetLabelId(source, SortDimension.SORT_DIRECTION_DESCENDING));
        mSortingList.add(ascendingFirst ? ascending : descending);
        mSortingList.add(ascendingFirst ? descending : ascending);
    }

    public static @StringRes int getSheetLabelId(SortDimension dimension, @SortDirection int direction) {
        boolean isAscending = direction == SortDimension.SORT_DIRECTION_ASCENDING;
        switch (dimension.getId()) {
            case SortModel.SORT_DIMENSION_ID_TITLE:
                return isAscending ? R.string.sort_dimension_name_ascending :
                        R.string.sort_dimension_name_descending;
            case SortModel.SORT_DIMENSION_ID_DATE:
                return isAscending ? R.string.sort_dimension_date_ascending :
                        R.string.sort_dimension_date_descending;
            case SortModel.SORT_DIMENSION_ID_FILE_TYPE:
                return isAscending ? R.string.sort_dimension_file_type_ascending :
                        R.string.sort_dimension_file_type_descending;
            case SortModel.SORT_DIMENSION_ID_SIZE:
                return isAscending ? R.string.sort_dimension_size_ascending :
                        R.string.sort_dimension_size_descending;
            default:
                return dimension.getLabelId();
        }
    }

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            Bundle args = getArguments();
            mModel = args.getParcelable(TAG_MODEL);
        } else {
            mModel = savedInstanceState.getParcelable(TAG_MODEL);
        }
        setupSortingList();

        BottomSheetDialog dialog =
                new BottomSheetDialog(getContext());
        dialog.setContentView(R.layout.dialog_sorting);

        // Workaround for solve issue about dialog not full expanded when landscape.
        FrameLayout bottomSheet = (FrameLayout)
                dialog.findViewById(com.google.android.material.R.id.design_bottom_sheet);
        BottomSheetBehavior.from(bottomSheet)
                .setState(BottomSheetBehavior.STATE_EXPANDED);

        ListView listView = dialog.findViewById(R.id.sorting_dialog_list);

        listView.setAdapter(new SortingListAdapter(getContext(), mSortingList));
        listView.setOnItemClickListener(this::onItemClicked);

        return dialog;
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putParcelable(TAG_MODEL, mModel);
    }

    private class SortingListAdapter extends ArrayAdapter<SortItem> {

        public SortingListAdapter(Context context, List<SortItem> list) {
            super(context, R.layout.sort_list_item, list);
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = super.getView(position, convertView, parent);
            final SortItem item = getItem(position);
            final CheckedTextView text = view.findViewById(android.R.id.text1);
            text.setText(getString(item.labelId));

            boolean selected = item.id == mModel.getSortedDimensionId()
                    && item.direction == mModel.getCurrentSortDirection();
            text.setChecked(selected);
            return view;
        }
    }

    private static class SortItem {

        @SortDimensionId final int id;
        @SortDirection final int direction;
        @StringRes final int labelId;

        SortItem(SortDimension dimension) {
            id = dimension.getId();
            direction = dimension.getDefaultSortDirection();
            labelId = dimension.getLabelId();
        }

        SortItem(@SortDimensionId int id, @SortDirection int direction, @StringRes int labelId) {
            this.id = id;
            this.direction = direction;
            this.labelId = labelId;
        }
    }
}
