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
package android.car.cluster;

import android.annotation.Nullable;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.VectorDrawable;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;

import android.car.cluster.navigation.NavigationState.ImageReference;
import android.car.cluster.navigation.NavigationState.Lane;
import android.car.cluster.navigation.NavigationState.Lane.LaneDirection;

import java.util.ArrayList;
import java.util.List;

/**
 * View component that displays the Lane preview information on the instrument cluster display
 */
public class LaneView extends LinearLayout {
    private static final String TAG = "Cluster.LaneView";

    private Handler mHandler = new Handler();

    private ArrayList<Lane> mLanes;

    private final int mWidth = (int) getResources().getDimension(R.dimen.lane_width);
    private final int mHeight = (int) getResources().getDimension(R.dimen.lane_height);
    private final int mOffset = (int) getResources().getDimension(R.dimen.lane_icon_offset);

    private enum Shift {
        LEFT,
        RIGHT,
        BOTH
    }

    public LaneView(Context context) {
        super(context);
    }

    public LaneView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public LaneView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setLanes(ImageReference imageReference, ImageResolver imageResolver) {
        imageResolver
                .getBitmap(imageReference, 0, getHeight())
                .thenAccept(bitmap -> {
                    mHandler.post(() -> {
                        removeAllViews();
                        ImageView imgView = new ImageView(getContext());
                        imgView.setImageBitmap(bitmap);
                        imgView.setAdjustViewBounds(true);
                        addView(imgView);
                    });
                })
                .exceptionally(ex -> {
                    removeAllViews();
                    if (Log.isLoggable(TAG, Log.DEBUG)) {
                        Log.d(TAG, "Unable to fetch image for lane: " + imageReference);
                    }
                    return null;
                });
    }

    public void setLanes(List<Lane> lanes) {
        mLanes = new ArrayList<>(lanes);
        removeAllViews();

        // Use drawables for lane directional guidance
        for (Lane lane : mLanes) {
            Bitmap bitmap = combineBitmapFromLane(lane);
            ImageView imgView = new ImageView(getContext());
            imgView.setImageBitmap(bitmap);
            imgView.setAdjustViewBounds(true);
            addView(imgView);
        }
    }

    private Bitmap combineBitmapFromLane(Lane lane) {
        if (lane.getLaneDirectionsList().isEmpty()) {
            return null;
        }

        Bitmap bitmap = Bitmap.createBitmap(mWidth, mHeight, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Shift shift = getShift(lane);

        for (LaneDirection laneDir : lane.getLaneDirectionsList()) {
            if (!laneDir.getIsHighlighted()) {
                drawToCanvas(laneDir, canvas, false, shift);
            }
        }

        for (LaneDirection laneDir : lane.getLaneDirectionsList()) {
            if (laneDir.getIsHighlighted()) {
                drawToCanvas(laneDir, canvas, true, shift);
            }
        }

        return bitmap;
    }

    private void drawToCanvas(LaneDirection laneDir, Canvas canvas, boolean isHighlighted,
            Shift shift) {
        int offset = getOffset(laneDir, shift);
        VectorDrawable icon = (VectorDrawable) getLaneIcon(laneDir);
        icon.setBounds(offset, 0, mWidth + offset, mHeight);
        icon.setColorFilter(new PorterDuffColorFilter(isHighlighted
                ? getContext().getColor(R.color.laneDirectionHighlighted)
                : getContext().getColor(R.color.laneDirection),
                PorterDuff.Mode.SRC_ATOP));
        icon.draw(canvas);
    }

    /**
     * Determines the offset direction to line up overlapping lane directions.
     */
    private Shift getShift(Lane lane) {
        boolean containsRight = false;
        boolean containsLeft = false;
        boolean containsStraight = false;

        for (LaneDirection laneDir : lane.getLaneDirectionsList()) {
            if (laneDir.getShape().equals(LaneDirection.Shape.NORMAL_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SLIGHT_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SHARP_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.U_TURN_RIGHT)) {
                containsRight = true;
            }
            if (laneDir.getShape().equals(LaneDirection.Shape.NORMAL_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SLIGHT_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SHARP_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.U_TURN_LEFT)) {
                containsLeft = true;
            }
            if (laneDir.getShape().equals(LaneDirection.Shape.STRAIGHT)) {
                containsStraight = true;
            }
        }

        if (containsLeft && containsRight) {
            //shift turns outwards
            return Shift.BOTH;
        } else if (containsStraight && containsRight) {
            //shift straight lane dir to the left
            return Shift.LEFT;
        } else if (containsStraight && containsLeft) {
            //shift straight lane dir to the right
            return Shift.RIGHT;
        }

        return null;
    }

    /**
     * Returns the offset value of the lane direction based on the given shift direction.
     */
    private int getOffset(LaneDirection laneDir, Shift shift) {
        if (shift == Shift.BOTH) {
            if (laneDir.getShape().equals(LaneDirection.Shape.NORMAL_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SLIGHT_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SHARP_LEFT)
                    || laneDir.getShape().equals(LaneDirection.Shape.U_TURN_LEFT)) {
                return -mOffset;
            }
            if (laneDir.getShape().equals(LaneDirection.Shape.NORMAL_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SLIGHT_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.SHARP_RIGHT)
                    || laneDir.getShape().equals(LaneDirection.Shape.U_TURN_RIGHT)) {
                return mOffset;
            }
        } else if (shift == Shift.LEFT) {
            if (laneDir.getShape().equals(LaneDirection.Shape.STRAIGHT)) {
                return -mOffset;
            }
        } else if (shift == Shift.RIGHT) {
            if (laneDir.getShape().equals(LaneDirection.Shape.STRAIGHT)) {
                return mOffset;
            }
        }

        return 0;
    }

    private Drawable getLaneIcon(@Nullable LaneDirection laneDir) {
        if (laneDir == null) {
            return null;
        }
        switch (laneDir.getShape()) {
            case UNKNOWN:
                return null;
            case STRAIGHT:
                return mContext.getDrawable(R.drawable.direction_continue);
            case SLIGHT_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_slight_left);
            case SLIGHT_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_slight_right);
            case NORMAL_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_left);
            case NORMAL_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_right);
            case SHARP_LEFT:
                return mContext.getDrawable(R.drawable.direction_turn_sharp_left);
            case SHARP_RIGHT:
                return mContext.getDrawable(R.drawable.direction_turn_sharp_right);
            case U_TURN_LEFT:
                return mContext.getDrawable(R.drawable.direction_uturn_left);
            case U_TURN_RIGHT:
                return mContext.getDrawable(R.drawable.direction_uturn_right);
        }
        return null;
    }
}
