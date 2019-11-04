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

package com.google.android.car.multidisplaytest.present;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.res.Resources;
import android.graphics.Color;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.os.Parcelable.Creator;
import android.util.Log;
import android.util.SparseArray;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.fragment.app.Fragment;

import com.google.android.car.multidisplaytest.R;

import java.util.Arrays;

/**
 * Modified from
 * //development/samples/ApiDemos/src/com/example/android/apis/app/PresentationActivity.java
 * Show content on selected displays with Presentation and DisplayManager APIs
 */
public class PresentTestFragment extends Fragment {
    private static final String TAG = PresentTestFragment.class.getSimpleName();
    private static final int[] PHOTOS = new int[] {
        R.drawable.photo_landscape, R.drawable.photo_portrait
    };
    private Display mCurrentDisplay;
    private DisplayManager mDisplayManager;
    private DisplayListAdapter mDisplayListAdapter;
    private CheckBox mShowAllDisplaysCheckbox;
    private ListView mListView;
    private int mNextImageNumber;

    // This state persist to restore the old presentation
    private SparseArray<DemoPresentationContents> mSavedPresentationContents;
    // List of all currently visible presentations indexed by display id.
    private final SparseArray<DemoPresentation> mActivePresentations =
            new SparseArray<DemoPresentation>();

    // Listens for displays to be added, changed or removed.
    private DisplayManager.DisplayListener mDisplayListener;
    // Listens for when presentations are dismissed.
    private DialogInterface.OnDismissListener mOnDismissListener;
    // Listens for Info button clicked.
    private View.OnClickListener mOnClickListener;
    // Listens for checked Display changed.
    private CompoundButton.OnCheckedChangeListener mOnCheckedChangedListener;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.presentation_fragment, container, false);
        mSavedPresentationContents = new SparseArray<DemoPresentationContents>();
        // Get the display manager service.
        mDisplayManager = (DisplayManager) getContext().getSystemService(Context.DISPLAY_SERVICE);
        mCurrentDisplay = ((WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE))
            .getDefaultDisplay();

        setupListeners(view);

        mShowAllDisplaysCheckbox = view.findViewById(R.id.show_all_displays);
        mShowAllDisplaysCheckbox.setOnCheckedChangeListener(mOnCheckedChangedListener);

        mDisplayListAdapter = new DisplayListAdapter(getContext());

        mListView = view.findViewById(R.id.display_list);
        mListView.setAdapter(mDisplayListAdapter);

        return view;
    }

    private void setupListeners(View view) {
        mDisplayListener = new DisplayManager.DisplayListener() {
            @Override
            public void onDisplayAdded(int displayId) {
                Log.d(TAG, "Display #" + displayId + " added.");
                mDisplayListAdapter.updateShownDisplays();
            }

            @Override
            public void onDisplayChanged(int displayId) {
                Log.d(TAG, "Display #" + displayId + " changed.");
                mDisplayListAdapter.updateShownDisplays();
            }

            @Override
            public void onDisplayRemoved(int displayId) {
                Log.d(TAG, "Display #" + displayId + " removed.");
                mDisplayListAdapter.updateShownDisplays();
            }
        };

        mOnDismissListener = new DialogInterface.OnDismissListener() {
            @Override
            public void onDismiss(DialogInterface dialog) {
                DemoPresentation presentation = (DemoPresentation) dialog;
                int displayId = presentation.getDisplay().getDisplayId();
                Log.d(TAG, "Presentation on display #" + displayId + " was dismissed.");
                mActivePresentations.delete(displayId);
                mDisplayListAdapter.notifyDataSetChanged();
            }
        };

        mOnClickListener = new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Context context = v.getContext();
                AlertDialog.Builder builder = new AlertDialog.Builder(context);
                final Display display = (Display) v.getTag();
                Resources r = context.getResources();
                AlertDialog alert = builder
                        .setTitle(r.getString(
                            R.string.presentation_alert_info_text, display.getDisplayId()))
                        .setMessage(display.toString())
                        .setNeutralButton(R.string.presentation_alert_dismiss_text,
                            (dialog, which) -> dialog.dismiss())
                        .create();
                alert.show();
            }
        };

        mOnCheckedChangedListener = new CompoundButton.OnCheckedChangeListener(){
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (buttonView == mShowAllDisplaysCheckbox) {
                    // Show all displays checkbox was toggled.
                    mDisplayListAdapter.updateShownDisplays();
                } else {
                    // Display item checkbox was toggled.
                    final Display display = (Display) buttonView.getTag();
                    if (isChecked) {
                        DemoPresentationContents contents =
                                new DemoPresentationContents(getNextPhoto());
                        showPresentation(display, contents);
                    } else {
                        hidePresentation(display);
                    }
                    mDisplayListAdapter.updateShownDisplays();
                }
            }
        };
    }

    @Override
    public void onResume() {
        super.onResume();
        mDisplayListAdapter.updateShownDisplays();
        mSavedPresentationContents.clear();

        // Register to receive events from the display manager.
        mDisplayManager.registerDisplayListener(mDisplayListener, null);
    }

    @Override
    public void onPause() {
        super.onPause();
        mDisplayManager.unregisterDisplayListener(mDisplayListener);
        Log.d(TAG, "Activity is being paused. Dismissing all active presentation.");

        for (int i = 0; i < mActivePresentations.size(); i++) {
            DemoPresentation presentation = mActivePresentations.valueAt(i);
            presentation.dismiss();
        }
        mActivePresentations.clear();
    }

    /**
     * Shows a Presentation on the specified display.
     */
    private void showPresentation(Display display, DemoPresentationContents contents) {
        final int displayId = display.getDisplayId();
        if (mActivePresentations.get(displayId) != null) {
            Log.w(TAG, "Active presentation exists on Display #" + displayId + ".");
            return;
        }

        Log.d(TAG, "Showing presentation photo #" + contents.mPhoto
                + " on display #" + displayId + ".");

        DemoPresentation presentation = new DemoPresentation(getContext(), display, contents);
        presentation.show();
        presentation.setOnDismissListener(mOnDismissListener);
        mActivePresentations.put(displayId, presentation);
    }

    /**
     * Hides a Presentation on the specified display.
     */
    private void hidePresentation(Display display) {
        final int displayId = display.getDisplayId();
        DemoPresentation presentation = mActivePresentations.get(displayId);
        if (presentation == null) {
            return;
        }

        Log.d(TAG, "Dismissing presentation on display #" + displayId + ".");

        presentation.dismiss();
        mActivePresentations.delete(displayId);
    }

    private int getNextPhoto() {
        final int photo = mNextImageNumber;
        mNextImageNumber = (mNextImageNumber + 1) % PHOTOS.length;
        return photo;
    }

    private final class DisplayListAdapter extends ArrayAdapter<Display> {
        private final Activity mActivity;
        private final Context mContext;
        private Display[] mShownDisplays;

        DisplayListAdapter(Context context) {
            super(context, R.layout.presentation_list_item);
            mActivity = (Activity) context;
            mContext = context;
            mShownDisplays = new Display[] {mCurrentDisplay};
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            final View view;
            if (convertView == null) {
                view = ((Activity) mContext).getLayoutInflater().inflate(
                    R.layout.presentation_list_item, null);
            } else {
                view = convertView;
            }
            final Display display = getItem(position);
            final int displayId = display.getDisplayId();

            DemoPresentation presentation = mActivePresentations.get(displayId);
            DemoPresentationContents contents = presentation != null
                    ? presentation.mContents : null;

            CheckBox cb = view.findViewById(R.id.checkbox_presentation);
            cb.setTag(display);
            cb.setOnCheckedChangeListener(mOnCheckedChangedListener);
            cb.setChecked(contents != null);

            TextView tv = (TextView) view.findViewById(R.id.display_id);
            tv.setText(view.getContext().getResources().getString(
                    R.string.presentation_display_id_text, displayId, display.getName()));

            if (displayId == mCurrentDisplay.getDisplayId()) {
                tv.append(" [Current display]");
                cb.setEnabled(false);
            } else {
                cb.setEnabled(true);
            }

            Button b = (Button) view.findViewById(R.id.info);
            b.setTag(display);
            b.setOnClickListener(mOnClickListener);

            return view;
        }

        public void updateShownDisplays() {
            clear();

            String displayCategory = getDisplayCategory();
            Display[] displays = mDisplayManager.getDisplays(displayCategory);
            addAll(displays);

            Log.d(TAG, Arrays.toString(displays));
        }

        private String getDisplayCategory() {
            return mShowAllDisplaysCheckbox.isChecked() ? null :
                DisplayManager.DISPLAY_CATEGORY_PRESENTATION;
        }
    }

    /**
     * The presentation to show on the secondary display.
     */
    private static final class DemoPresentation extends Presentation {
        private DemoPresentationContents mContents;

        DemoPresentation(Context context, Display display, DemoPresentationContents contents) {
            super(context, display);
            mContents = contents;
        }

        @Override
        public void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.presentation_content);

            Resources r = getContext().getResources();
            ImageView image = (ImageView) findViewById(R.id.image);
            TextView text = (TextView) findViewById(R.id.text);

            Display display = getDisplay();
            int displayId = display.getDisplayId();
            int photo = mContents.mPhoto;

            image.setImageDrawable(r.getDrawable(PHOTOS[photo]));
            findViewById(android.R.id.content).setBackgroundColor(mContents.mColor);
            text.setText(r.getString(R.string.presentation_photo_text,
                    photo, displayId, display.getName()));
        }
    }

    /**
     * Content to show in the presentation.
     */
    private static final class DemoPresentationContents implements Parcelable {
        private static final int[] COLORS = {
            Color.RED,
            Color.GREEN,
            Color.BLUE,
            Color.YELLOW,
            Color.MAGENTA,
            Color.BLACK,
            Color.DKGRAY
        };
        private int mPhoto;
        private int mColor;

        public static final Creator<DemoPresentationContents> CREATOR =
                new Creator<DemoPresentationContents>() {
                    @Override
                    public DemoPresentationContents createFromParcel(Parcel in) {
                        return new DemoPresentationContents(in);
                    }

                    @Override
                    public DemoPresentationContents[] newArray(int size) {
                        return new DemoPresentationContents[size];
                    }
                };

        DemoPresentationContents(int photo) {
            this.mPhoto = photo;
            this.mColor = COLORS[(int) (Math.random() * COLORS.length)];
        }

        private DemoPresentationContents(Parcel in) {
            mPhoto = in.readInt();
            mColor = in.readInt();
        }

        @Override
        public int describeContents() {
            return 0;
        }

        @Override
        public void writeToParcel(Parcel dest, int flags) {
            dest.writeInt(mPhoto);
            dest.writeInt(mColor);
        }
    }
}
