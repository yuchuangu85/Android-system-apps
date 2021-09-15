/*
 * Copyright (C) 2020 The Android Open Source Project
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

package com.android.deskclock

import android.os.Bundle
import android.util.SparseArray
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.NO_ID

import com.android.deskclock.ItemAdapter.ItemHolder
import com.android.deskclock.ItemAdapter.ItemViewHolder

import kotlin.math.min

/**
 * Base adapter class for displaying a collection of items. Provides functionality for handling
 * changing items, persistent item state, item click events, and re-usable item views.
 */
class ItemAdapter<T : ItemHolder<*>> : RecyclerView.Adapter<ItemViewHolder<T>>() {
    /**
     * Finds the position of the changed item holder and invokes [.notifyItemChanged] or
     * [.notifyItemChanged] if payloads are present (in order to do in-place
     * change animations).
     */
    private val mItemChangedNotifier: OnItemChangedListener = object : OnItemChangedListener {
        override fun onItemChanged(itemHolder: ItemHolder<*>) {
            mOnItemChangedListener?.onItemChanged(itemHolder)
            val position = items!!.indexOf(itemHolder)
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position)
            }
        }

        override fun onItemChanged(itemHolder: ItemHolder<*>, payload: Any) {
            mOnItemChangedListener?.onItemChanged(itemHolder, payload)
            val position = items!!.indexOf(itemHolder)
            if (position != RecyclerView.NO_POSITION) {
                notifyItemChanged(position, payload)
            }
        }
    }

    /**
     * Invokes the [OnItemClickedListener] in [.mListenersByViewType] corresponding
     * to [ItemViewHolder.getItemViewType]
     */
    private val mOnItemClickedListener: OnItemClickedListener = object : OnItemClickedListener {
        override fun onItemClicked(viewHolder: ItemViewHolder<*>, id: Int) {
            val listener = mListenersByViewType[viewHolder.getItemViewType()]
            listener?.onItemClicked(viewHolder, id)
        }
    }

    /**
     * Invoked when any item changes.
     */
    private var mOnItemChangedListener: OnItemChangedListener? = null

    /**
     * Factories for creating new [ItemViewHolder] entities.
     */
    private val mFactoriesByViewType: SparseArray<ItemViewHolder.Factory> = SparseArray()

    /**
     * Listeners to invoke in [.mOnItemClickedListener].
     */
    private val mListenersByViewType: SparseArray<OnItemClickedListener?> = SparseArray()

    /**
     * List of current item holders represented by this adapter.
     */
    @JvmField var items: MutableList<T>? = null

    /**
     * Convenience for calling [.setHasStableIds] with `true`.
     *
     * @return this object, allowing calls to methods in this class to be chained
     */
    fun setHasStableIds(): ItemAdapter<T> {
        setHasStableIds(true)
        return this
    }

    /**
     * Sets the [ItemViewHolder.Factory] and [OnItemClickedListener] used to create
     * new item view holders in [.onCreateViewHolder].
     *
     * @param factory the [ItemViewHolder.Factory] used to create new item view holders
     * @param listener the [OnItemClickedListener] to be invoked by [.mItemChangedNotifier]
     * @param viewTypes the unique identifier for the view types to be created
     * @return this object, allowing calls to methods in this class to be chained
     */
    fun withViewTypes(
        factory: ItemViewHolder.Factory,
        listener: OnItemClickedListener?,
        vararg viewTypes: Int
    ): ItemAdapter<T> {
        for (viewType in viewTypes) {
            mFactoriesByViewType.put(viewType, factory)
            mListenersByViewType.put(viewType, listener)
        }
        return this
    }

    /**
     * Sets the list of item holders to serve as the dataset for this adapter and invokes
     * [.notifyDataSetChanged] to update the UI.
     *
     * If [.hasStableIds] returns `true`, then the instance state will preserved
     * between new and old holders that have matching [itemId] values.
     *
     * @param itemHolders the new list of item holders
     * @return this object, allowing calls to methods in this class to be chained
     */
    fun setItems(itemHolders: List<T>?): ItemAdapter<T> {
        val oldItemHolders = items
        if (oldItemHolders !== itemHolders) {
            if (oldItemHolders != null) {
                // remove the item change listener from the old item holders
                for (oldItemHolder in oldItemHolders) {
                    oldItemHolder.removeOnItemChangedListener(mItemChangedNotifier)
                }
            }

            if (oldItemHolders != null && itemHolders != null && hasStableIds()) {
                // transfer instance state from old to new item holders based on item id,
                // we use a simple O(N^2) implementation since we assume the number of items is
                // relatively small and generating a temporary map would be more expensive
                val bundle = Bundle()
                for (newItemHolder in itemHolders) {
                    for (oldItemHolder in oldItemHolders) {
                        if (newItemHolder.itemId == oldItemHolder.itemId &&
                                newItemHolder !== oldItemHolder) {
                            // clear any existing state from the bundle
                            bundle.clear()

                            // transfer instance state from old to new item holder
                            oldItemHolder.onSaveInstanceState(bundle)
                            newItemHolder.onRestoreInstanceState(bundle)
                            break
                        }
                    }
                }
            }

            if (itemHolders != null) {
                // add the item change listener to the new item holders
                for (newItemHolder in itemHolders) {
                    newItemHolder.addOnItemChangedListener(mItemChangedNotifier)
                }
            }

            // finally update the current list of item holders and inform the RV to update the UI
            items = itemHolders?.toMutableList()
            notifyDataSetChanged()
        }

        return this
    }

    /**
     * Inserts the specified item holder at the specified position. Invokes
     * [.notifyItemInserted] to update the UI.
     *
     * @param position the index to which to add the item holder
     * @param itemHolder the item holder to add
     * @return this object, allowing calls to methods in this class to be chained
     */
    fun addItem(position: Int, itemHolder: T): ItemAdapter<T> {
        var variablePosition = position
        itemHolder.addOnItemChangedListener(mItemChangedNotifier)
        variablePosition = min(variablePosition, items!!.size)
        items!!.add(variablePosition, itemHolder)
        notifyItemInserted(variablePosition)
        return this
    }

    /**
     * Removes the first occurrence of the specified element from this list, if it is present
     * (optional operation). If this list does not contain the element, it is unchanged. Invokes
     * [.notifyItemRemoved] to update the UI.
     *
     * @param itemHolder the item holder to remove
     * @return this object, allowing calls to methods in this class to be chained
     */
    fun removeItem(itemHolder: T): ItemAdapter<T> {
        var variableItemHolder = itemHolder
        val index = items!!.indexOf(variableItemHolder)
        if (index >= 0) {
            variableItemHolder = items!!.removeAt(index)
            variableItemHolder.removeOnItemChangedListener(mItemChangedNotifier)
            notifyItemRemoved(index)
        }
        return this
    }

    /**
     * Sets the listener to be invoked whenever any item changes.
     */
    fun setOnItemChangedListener(listener: OnItemChangedListener) {
        mOnItemChangedListener = listener
    }

    override fun getItemCount(): Int = items?.size ?: 0

    override fun getItemId(position: Int): Long {
        return if (hasStableIds()) items!![position].itemId else NO_ID
    }

    fun findItemById(id: Long): T? {
        for (holder in items!!) {
            if (holder.itemId == id) {
                return holder
            }
        }
        return null
    }

    override fun getItemViewType(position: Int): Int {
        return items!![position].getItemViewType()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<T> {
        val factory = mFactoriesByViewType[viewType]
        if (factory != null) {
            return factory.createViewHolder(parent, viewType) as ItemViewHolder<T>
        }
        throw IllegalArgumentException("Unsupported view type: $viewType")
    }

    override fun onBindViewHolder(viewHolder: ItemViewHolder<T>, position: Int) {
        // suppress any unchecked warnings since it is up to the subclass to guarantee
        // compatibility of their view holders with the item holder at the corresponding position
        viewHolder.bindItemView(items!![position])
        viewHolder.setOnItemClickedListener(mOnItemClickedListener)
    }

    override fun onViewRecycled(viewHolder: ItemViewHolder<T>) {
        viewHolder.setOnItemClickedListener(null)
        viewHolder.recycleItemView()
    }

    /**
     * Base class for wrapping an item for compatibility with an [ItemHolder].
     *
     * An [ItemHolder] serves as bridge between the model and view layer; subclassers should
     * implement properties that fall beyond the scope of their model layer but are necessary for
     * the view layer. Properties that should be persisted across dataset changes can be
     * preserved via the [.onSaveInstanceState] and
     * [.onRestoreInstanceState] methods.
     *
     * Note: An [ItemHolder] can be used by multiple [ItemHolder] and any state changes
     * should simultaneously be reflected in both UIs.  It is not thread-safe however and should
     * only be used on a single thread at a given time.
     *
     * @param <T> the item type wrapped by the holder
    </T> */
    abstract class ItemHolder<T>(
        /** The item held by this holder. */
        val item: T,
        /** Globally unique id corresponding to the item. */
        val itemId: Long
    ) {
        /** Listeners to be invoked by [.notifyItemChanged]. */
        private val mOnItemChangedListeners: MutableList<OnItemChangedListener> = ArrayList()

        /**
         * @return the unique identifier for the view that should be used to represent the item,
         * e.g. the layout resource id.
         */
        abstract fun getItemViewType(): Int

        /**
         * Adds the listener to the current list of registered listeners if it is not already
         * registered.
         *
         * @param listener the listener to add
         */
        fun addOnItemChangedListener(listener: OnItemChangedListener) {
            if (!mOnItemChangedListeners.contains(listener)) {
                mOnItemChangedListeners.add(listener)
            }
        }

        /**
         * Removes the listener from the current list of registered listeners.
         *
         * @param listener the listener to remove
         */
        fun removeOnItemChangedListener(listener: OnItemChangedListener) {
            mOnItemChangedListeners.remove(listener)
        }

        /**
         * Invokes [OnItemChangedListener.onItemChanged] for all listeners added
         * via [.addOnItemChangedListener].
         */
        fun notifyItemChanged() {
            for (listener in mOnItemChangedListeners) {
                listener.onItemChanged(this)
            }
        }

        /**
         * Invokes [OnItemChangedListener.onItemChanged] for all
         * listeners added via [.addOnItemChangedListener].
         */
        fun notifyItemChanged(payload: Any) {
            for (listener in mOnItemChangedListeners) {
                listener.onItemChanged(this, payload)
            }
        }

        /**
         * Called to retrieve per-instance state when the item may disappear or change so that
         * state can be restored in [.onRestoreInstanceState].
         *
         * Note: Subclasses must not maintain a reference to the [Bundle] as it may be
         * reused for other items in the [ItemHolder].
         *
         * @param bundle the [Bundle] in which to place saved state
         */
        open fun onSaveInstanceState(bundle: Bundle) {
            // for subclassers
        }

        /**
         * Called to restore any per-instance state which was previously saved in
         * [.onSaveInstanceState] for an item with a matching [.itemId].
         *
         * Note: Subclasses must not maintain a reference to the [Bundle] as it may be
         * reused for other items in the [ItemHolder].
         *
         * @param bundle the [Bundle] in which to retrieve saved state
         */
        open fun onRestoreInstanceState(bundle: Bundle) {
            // for subclassers
        }
    }

    /**
     * Base class for a reusable [RecyclerView.ViewHolder] compatible with an
     * [ItemViewHolder]. Provides an interface for binding to an [ItemHolder] and later
     * being recycled.
     */
    open class ItemViewHolder<T : ItemHolder<*>>(itemView: View)
        : RecyclerView.ViewHolder(itemView) {
        /**
         * The current [ItemHolder] bound to this holder, or `null` if unbound.
         */
        var itemHolder: T? = null
            private set

        /**
         * The current [OnItemClickedListener] associated with this holder.
         */
        private var mOnItemClickedListener: OnItemClickedListener? = null

        /**
         * Binds the holder's [.itemView] to a particular item.
         *
         * @param itemHolder the [ItemHolder] to bind
         */
        fun bindItemView(itemHolder: T) {
            this.itemHolder = itemHolder
            onBindItemView(itemHolder)
        }

        /**
         * Called when a new item is bound to the holder. Subclassers should override to bind any
         * relevant data to their [.itemView] in this method.
         *
         * @param itemHolder the [ItemHolder] to bind
         */
        protected open fun onBindItemView(itemHolder: T) {
            // for subclassers
        }

        /**
         * Recycles the current item view, unbinding the current item holder and state.
         */
        fun recycleItemView() {
            itemHolder = null
            mOnItemClickedListener = null

            onRecycleItemView()
        }

        /**
         * Called when the current item view is recycled. Subclassers should override to release
         * any bound item state and prepare their [.itemView] for reuse.
         */
        protected fun onRecycleItemView() {
            // for subclassers
        }

        /**
         * Sets the current [OnItemClickedListener] to be invoked via
         * [.notifyItemClicked].
         *
         * @param listener the new [OnItemClickedListener], or `null` to clear
         */
        fun setOnItemClickedListener(listener: OnItemClickedListener?) {
            mOnItemClickedListener = listener
        }

        /**
         * Called by subclasses to invoke the current [OnItemClickedListener] for a
         * particular click event so it can be handled at a higher level.
         *
         * @param id the unique identifier for the click action that has occurred
         */
        fun notifyItemClicked(id: Int) {
            mOnItemClickedListener?.onItemClicked(this, id)
        }

        /**
         * Factory interface used by [ItemAdapter] for creating new [ItemViewHolder].
         */
        interface Factory {
            /**
             * Used by [ItemAdapter.createViewHolder] to make new
             * [ItemViewHolder] for a given view type.
             *
             * @param parent the `ViewGroup` that the [ItemViewHolder.itemView] will be attached
             * @param viewType the unique id of the item view to create
             * @return a new initialized [ItemViewHolder]
             */
            fun createViewHolder(parent: ViewGroup, viewType: Int): ItemViewHolder<*>
        }
    }

    /**
     * Callback interface for when an item changes and should be re-bound.
     */
    interface OnItemChangedListener {
        /**
         * Invoked by [ItemHolder.notifyItemChanged].
         *
         * @param itemHolder the item holder that has changed
         */
        fun onItemChanged(itemHolder: ItemHolder<*>)

        /**
         * Invoked by [ItemHolder.notifyItemChanged].
         *
         * @param itemHolder the item holder that has changed
         * @param payload the payload object
         */
        fun onItemChanged(itemHolder: ItemHolder<*>, payload: Any)
    }

    /**
     * Callback interface for handling when an item is clicked.
     */
    interface OnItemClickedListener {
        /**
         * Invoked by [ItemViewHolder.notifyItemClicked]
         *
         * @param viewHolder the [ItemViewHolder] containing the view that was clicked
         * @param id the unique identifier for the click action that has occurred
         */
        fun onItemClicked(viewHolder: ItemViewHolder<*>, id: Int)
    }
}