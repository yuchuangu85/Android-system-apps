/*
 * Copyright 2018 The Android Open Source Project
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

package com.android.car.media.browse;

import android.content.Context;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.media.common.MediaConstants;
import com.android.car.media.common.MediaItemMetadata;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;

/**
 * A {@link RecyclerView.Adapter} that can be used to display a single level of a {@link
 * android.service.media.MediaBrowserService} media tree into a {@link
 * androidx.car.widget.PagedListView} or any other {@link RecyclerView}.
 *
 * <p>This adapter assumes that the attached {@link RecyclerView} uses a {@link GridLayoutManager},
 * as it can use both grid and list elements to produce the desired representation.
 *
 * <p>Consumers of this adapter should use {@link #registerObserver(Observer)} to receive updates.
 */
public class BrowseAdapter extends ListAdapter<BrowseViewData, BrowseViewHolder> {
    private static final String TAG = "BrowseAdapter";
    @NonNull
    private final Context mContext;
    @NonNull
    private List<Observer> mObservers = new ArrayList<>();
    @Nullable
    private CharSequence mTitle;
    @Nullable
    private MediaItemMetadata mParentMediaItem;
    private int mMaxSpanSize = 1;

    private BrowseItemViewType mRootBrowsableViewType = BrowseItemViewType.LIST_ITEM;
    private BrowseItemViewType mRootPlayableViewType = BrowseItemViewType.LIST_ITEM;

    private static final DiffUtil.ItemCallback<BrowseViewData> DIFF_CALLBACK =
            new DiffUtil.ItemCallback<BrowseViewData>() {
                @Override
                public boolean areItemsTheSame(@NonNull BrowseViewData oldItem,
                        @NonNull BrowseViewData newItem) {
                    return Objects.equals(oldItem.mMediaItem, newItem.mMediaItem)
                            && Objects.equals(oldItem.mText, newItem.mText);
                }

                @Override
                public boolean areContentsTheSame(@NonNull BrowseViewData oldItem,
                        @NonNull BrowseViewData newItem) {
                    return oldItem.equals(newItem);
                }
            };

    /**
     * An {@link BrowseAdapter} observer.
     */
    public static abstract class Observer {

        /**
         * Callback invoked when a user clicks on a playable item.
         */
        protected void onPlayableItemClicked(MediaItemMetadata item) {
        }

        /**
         * Callback invoked when a user clicks on a browsable item.
         */
        protected void onBrowsableItemClicked(MediaItemMetadata item) {
        }

        /**
         * Callback invoked when the user clicks on the title of the queue.
         */
        protected void onTitleClicked() {
        }
    }

    /**
     * Creates a {@link BrowseAdapter} that displays the children of the given media tree node.
     */
    public BrowseAdapter(@NonNull Context context) {
        super(DIFF_CALLBACK);
        mContext = context;
    }

    /**
     * Sets title to be displayed.
     */
    public void setTitle(CharSequence title) {
        mTitle = title;
    }

    /**
     * Registers an {@link Observer}
     */
    public void registerObserver(Observer observer) {
        mObservers.add(observer);
    }

    /**
     * Unregisters an {@link Observer}
     */
    public void unregisterObserver(Observer observer) {
        mObservers.remove(observer);
    }

    /**
     * Sets the number of columns that items can take. This method only needs to be used if the
     * attached {@link RecyclerView} is NOT using a {@link GridLayoutManager}. This class will
     * automatically determine this value on {@link #onAttachedToRecyclerView(RecyclerView)}
     * otherwise.
     */
    public void setMaxSpanSize(int maxSpanSize) {
        mMaxSpanSize = maxSpanSize;
    }

    public void setRootBrowsableViewType(int hintValue) {
        mRootBrowsableViewType = fromMediaHint(hintValue);
    }

    public void setRootPlayableViewType(int hintValue) {
        mRootPlayableViewType = fromMediaHint(hintValue);
    }

    /**
     * @return a {@link GridLayoutManager.SpanSizeLookup} that can be used to obtain the span size
     * of each item in this adapter. This method is only needed if the {@link RecyclerView} is NOT
     * using a {@link GridLayoutManager}. This class will automatically use it on\ {@link
     * #onAttachedToRecyclerView(RecyclerView)} otherwise.
     */
    private GridLayoutManager.SpanSizeLookup getSpanSizeLookup() {
        return new GridLayoutManager.SpanSizeLookup() {
            @Override
            public int getSpanSize(int position) {
                BrowseItemViewType viewType = getItem(position).mViewType;
                return viewType.getSpanSize(mMaxSpanSize);
            }
        };
    }

    @NonNull
    @Override
    public BrowseViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        int layoutId = BrowseItemViewType.values()[viewType].getLayoutId();
        View view = LayoutInflater.from(mContext).inflate(layoutId, parent, false);
        return new BrowseViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull BrowseViewHolder holder, int position) {
        BrowseViewData viewData = getItem(position);
        holder.bind(mContext, viewData);
    }

    @Override
    public int getItemViewType(int position) {
        return getItem(position).mViewType.ordinal();
    }

    public void submitItems(@Nullable MediaItemMetadata parentItem,
            @Nullable List<MediaItemMetadata> children) {
        mParentMediaItem = parentItem;
        if (children == null) {
            submitList(Collections.emptyList());
            return;
        }
        submitList(generateViewData(children));
    }

    private void notify(Consumer<Observer> notification) {
        for (Observer observer : mObservers) {
            notification.accept(observer);
        }
    }

    @Override
    public void onAttachedToRecyclerView(@NonNull RecyclerView recyclerView) {
        if (recyclerView.getLayoutManager() instanceof GridLayoutManager) {
            GridLayoutManager manager = (GridLayoutManager) recyclerView.getLayoutManager();
            mMaxSpanSize = manager.getSpanCount();
            manager.setSpanSizeLookup(getSpanSizeLookup());
        }
    }

    private class ItemsBuilder {
        private List<BrowseViewData> result = new ArrayList<>();

        void addItem(MediaItemMetadata item,
                BrowseItemViewType viewType, Consumer<Observer> notification) {
            View.OnClickListener listener = notification != null ?
                    view -> BrowseAdapter.this.notify(notification) :
                    null;
            result.add(new BrowseViewData(item, viewType, listener));
        }

        void addTitle(CharSequence title, Consumer<Observer> notification) {
            if (title == null) {
                title = "";
            }
            View.OnClickListener listener = notification != null ?
                    view -> BrowseAdapter.this.notify(notification) :
                    null;
            result.add(new BrowseViewData(title, BrowseItemViewType.HEADER, listener));
        }

        void addSpacer() {
            result.add(new BrowseViewData(BrowseItemViewType.SPACER, null));
        }

        List<BrowseViewData> build() {
            return result;
        }
    }

    /**
     * Flatten the given collection of item states into a list of {@link BrowseViewData}s. To avoid
     * flickering, the flatting will stop at the first "loading" section, avoiding unnecessary
     * insertion animations during the initial data load.
     */
    private List<BrowseViewData> generateViewData(List<MediaItemMetadata> items) {
        ItemsBuilder itemsBuilder = new ItemsBuilder();
        if (Log.isLoggable(TAG, Log.VERBOSE)) {
            Log.v(TAG, "Generating browse view from:");
            for (MediaItemMetadata item : items) {
                Log.v(TAG, String.format("[%s%s] '%s' (%s)",
                        item.isBrowsable() ? "B" : " ",
                        item.isPlayable() ? "P" : " ",
                        item.getTitle(),
                        item.getId()));
            }
        }

        if (mTitle != null) {
            itemsBuilder.addTitle(mTitle, Observer::onTitleClicked);
        } else if (!items.isEmpty() && items.get(0).getTitleGrouping() == null) {
            itemsBuilder.addSpacer();
        }
        String currentTitleGrouping = null;
        for (MediaItemMetadata item : items) {
            String titleGrouping = item.getTitleGrouping();
            if (!Objects.equals(currentTitleGrouping, titleGrouping)) {
                currentTitleGrouping = titleGrouping;
                itemsBuilder.addTitle(titleGrouping, null);
            }
            if (item.isBrowsable()) {
                itemsBuilder.addItem(item, getBrowsableViewType(mParentMediaItem),
                        observer -> observer.onBrowsableItemClicked(item));
            } else if (item.isPlayable()) {
                itemsBuilder.addItem(item, getPlayableViewType(mParentMediaItem),
                        observer -> observer.onPlayableItemClicked(item));
            }
        }

        return itemsBuilder.build();
    }

    private BrowseItemViewType getBrowsableViewType(@Nullable MediaItemMetadata mediaItem) {
        if (mediaItem == null) {
            return BrowseItemViewType.LIST_ITEM;
        }
        if (mediaItem.getBrowsableContentStyleHint() == 0) {
            return mRootBrowsableViewType;
        }
        return fromMediaHint(mediaItem.getBrowsableContentStyleHint());
    }

    private BrowseItemViewType getPlayableViewType(@Nullable MediaItemMetadata mediaItem) {
        if (mediaItem == null) {
            return BrowseItemViewType.LIST_ITEM;
        }
        if (mediaItem.getPlayableContentStyleHint() == 0) {
            return mRootPlayableViewType;
        }
        return fromMediaHint(mediaItem.getPlayableContentStyleHint());
    }

    /**
     * Converts a content style hint to the appropriate {@link BrowseItemViewType}, defaulting to
     * list items.
     */
    private BrowseItemViewType fromMediaHint(int hint) {
        switch(hint) {
            case MediaConstants.CONTENT_STYLE_GRID_ITEM_HINT_VALUE:
                return BrowseItemViewType.GRID_ITEM;
            case MediaConstants.CONTENT_STYLE_LIST_ITEM_HINT_VALUE:
                return BrowseItemViewType.LIST_ITEM;
            default:
                return BrowseItemViewType.LIST_ITEM;
        }
    }
}
