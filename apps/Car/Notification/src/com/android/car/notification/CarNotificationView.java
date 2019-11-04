package com.android.car.notification;

import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.recyclerview.widget.RecyclerView.OnScrollListener;
import androidx.recyclerview.widget.SimpleItemAnimator;

import java.util.List;


/**
 * Layout that contains Car Notifications.
 *
 * It does some extra setup in the onFinishInflate method because it may not get used from an
 * activity where one would normally attach RecyclerViews
 */
public class CarNotificationView extends ConstraintLayout
        implements CarUxRestrictionsManager.OnUxRestrictionsChangedListener {

    private CarNotificationViewAdapter mAdapter;
    private Context mContext;
    private LinearLayoutManager mLayoutManager;
    private NotificationDataManager mNotificationDataManager;

    public CarNotificationView(Context context, AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
    }

    /**
     * Attaches the CarNotificationViewAdapter and CarNotificationItemTouchListener to the
     * notification list.
     */
    @Override
    protected void onFinishInflate() {
        super.onFinishInflate();
        RecyclerView listView = findViewById(R.id.notifications);

        listView.setClipChildren(false);
        mLayoutManager = new LinearLayoutManager(mContext);
        listView.setLayoutManager(mLayoutManager);
        listView.addItemDecoration(new TopAndBottomOffsetDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));
        listView.addItemDecoration(new ItemSpacingDecoration(
                mContext.getResources().getDimensionPixelSize(R.dimen.item_spacing)));

        mAdapter = new CarNotificationViewAdapter(mContext, /* isGroupNotificationAdapter= */
                false);
        listView.setAdapter(mAdapter);

        ((SimpleItemAnimator) listView.getItemAnimator()).setSupportsChangeAnimations(false);
        listView.addOnItemTouchListener(new CarNotificationItemTouchListener(mContext, mAdapter));

        Button clearAllButton = findViewById(R.id.clear_all_button);
        if (clearAllButton != null) {
            clearAllButton.setOnClickListener(view -> mAdapter.clearAllNotifications());
        }

        listView.addOnScrollListener(new OnScrollListener() {
            @Override
            public void onScrollStateChanged(@NonNull RecyclerView recyclerView, int newState) {
                super.onScrollStateChanged(recyclerView, newState);

                // RecyclerView is not currently scrolling.
                if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                    setVisibleNotificationsAsSeen();
                }
            }
        });
    }

    /**
     * Updates notifications and update views.
     */
    public void setNotifications(List<NotificationGroup> notifications) {
        mAdapter.setNotifications(notifications, /* setRecyclerViewListHeaderAndFooter= */ true);
    }

    /**
     * Collapses all expanded groups.
     */
    public void collapseAllGroups() {
        mAdapter.collapseAllGroups();
    }

    @Override
    public void onUxRestrictionsChanged(CarUxRestrictions restrictionInfo) {
        mAdapter.setCarUxRestrictions(restrictionInfo);
    }

    /**
     * Sets the NotificationClickHandlerFactory that allows for a hook to run a block off code
     * when  the notification is clicked. This is useful to dismiss a screen after
     * a notification list clicked.
     */
    public void setClickHandlerFactory(NotificationClickHandlerFactory clickHandlerFactory) {
        mAdapter.setClickHandlerFactory(clickHandlerFactory);
    }

    /**
     * Sets NotificationDataManager that handles additional states for notifications such as "seen",
     * and muting a messaging type notification.
     *
     * @param notificationDataManager An instance of NotificationDataManager.
     */
    public void setNotificationDataManager(NotificationDataManager notificationDataManager) {
        mNotificationDataManager = notificationDataManager;
        mAdapter.setNotificationDataManager(notificationDataManager);
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add a top offset to the first item and bottom
     * offset to the last item in the RecyclerView it is added to.
     */
    private static class TopAndBottomOffsetDecoration extends RecyclerView.ItemDecoration {
        private int mTopAndBottomOffset;

        private TopAndBottomOffsetDecoration(int topOffset) {
            mTopAndBottomOffset = topOffset;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            if (position == 0) {
                outRect.top = mTopAndBottomOffset;
            }
            if (position == state.getItemCount() - 1) {
                outRect.bottom = mTopAndBottomOffset;
            }
        }
    }

    /**
     * A {@link RecyclerView.ItemDecoration} that will add spacing between each item in the
     * RecyclerView that it is added to.
     */
    private static class ItemSpacingDecoration extends RecyclerView.ItemDecoration {
        private int mItemSpacing;

        private ItemSpacingDecoration(int itemSpacing) {
            mItemSpacing = itemSpacing;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent,
                RecyclerView.State state) {
            super.getItemOffsets(outRect, view, parent, state);
            int position = parent.getChildAdapterPosition(view);

            // Skip offset for last item.
            if (position == state.getItemCount() - 1) {
                return;
            }

            outRect.bottom = mItemSpacing;
        }
    }

    /**
     * Sets currently visible notifications as "seen".
     */
    public void setVisibleNotificationsAsSeen() {
        int firstVisible = mLayoutManager.findFirstVisibleItemPosition();
        int lastVisible = mLayoutManager.findLastVisibleItemPosition();

        // No visible items are found.
        if (firstVisible == RecyclerView.NO_POSITION) return;


        for (int i = firstVisible; i <= lastVisible; i++) {
            mAdapter.setNotificationAsSeen(i);
        }
    }
}
