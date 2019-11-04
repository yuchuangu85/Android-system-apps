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

package com.android.car.radio;

import android.hardware.radio.ProgramSelector;
import android.hardware.radio.RadioManager.ProgramInfo;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.LifecycleOwner;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.RecyclerView;

import com.android.car.broadcastradio.support.Program;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;


/**
 * Adapter that will display a list of radio stations that represent the user's presets.
 */
public class BrowseAdapter extends RecyclerView.Adapter<ProgramViewHolder> {
    // Only one type of view in this adapter.
    private static final int PRESETS_VIEW_TYPE = 0;

    private final Object mLock = new Object();

    private @NonNull List<Entry> mPrograms = new ArrayList<>();
    private @Nullable ProgramInfo mCurrentProgram;

    private OnItemClickListener mItemClickListener;
    private OnItemFavoriteListener mItemFavoriteListener;

    /**
     * Interface for a listener that will be notified when an item in the program list has been
     * clicked.
     */
    public interface OnItemClickListener {
        /**
         * Method called when an item in the list has been clicked.
         *
         * @param selector The {@link ProgramSelector} corresponding to the clicked preset.
         */
        void onItemClicked(ProgramSelector selector);
    }

    /**
     * Interface for a listener that will be notified when a favorite in the list has been
     * toggled.
     */
    public interface OnItemFavoriteListener {

        /**
         * Method called when an item's favorite status has been toggled
         *
         * @param program The {@link Program} corresponding to the clicked item.
         * @param saveAsFavorite Whether the program should be saved or removed as a favorite.
         */
        void onItemFavoriteChanged(Program program, boolean saveAsFavorite);
    }

    private class Entry {
        public Program program;
        public boolean isFavorite;
        public boolean wasFavorite;

        Entry(Program program, boolean isFavorite) {
            this.program = program;
            this.isFavorite = isFavorite;
            this.wasFavorite = isFavorite;
        }
    }

    public BrowseAdapter(@NonNull LifecycleOwner lifecycleOwner,
            @NonNull LiveData<ProgramInfo> currentProgram,
            @NonNull LiveData<List<Program>> favorites) {
        favorites.observe(lifecycleOwner, this::onFavoritesChanged);
        currentProgram.observe(lifecycleOwner, this::onCurrentProgramChanged);
    }

    /**
     * Set a listener to be notified whenever a program card is pressed.
     */
    public void setOnItemClickListener(@NonNull OnItemClickListener listener) {
        synchronized (mLock) {
            mItemClickListener = Objects.requireNonNull(listener);
        }
    }

    /**
     * Set a listener to be notified whenever a program favorite is changed.
     */
    public void setOnItemFavoriteListener(@NonNull OnItemFavoriteListener listener) {
        synchronized (mLock) {
            mItemFavoriteListener = Objects.requireNonNull(listener);
        }
    }

    /**
     * Sets the given list as the list of programs to display.
     */
    public void setProgramList(@NonNull List<ProgramInfo> programs) {
        Map<ProgramSelector.Identifier, ProgramInfo> liveMap = programs.stream().collect(
                Collectors.toMap(p -> p.getSelector().getPrimaryId(), p -> p));
        synchronized (mLock) {
            // Remove entries no longer on live list, except those which were favorites previously
            List<Entry> remove = new ArrayList<>();
            for (Entry entry : mPrograms) {
                ProgramSelector.Identifier id = entry.program.getSelector().getPrimaryId();
                ProgramInfo liveEntry = liveMap.get(id);
                if (liveEntry != null) {
                    liveMap.remove(id);  // item is already on the list, don't add twice
                } else if (!entry.wasFavorite) {
                    remove.add(entry);  // no longer live and was never favorite - remove it
                }
            }
            mPrograms.removeAll(remove);

            // Add new entries from live list
            liveMap.values().stream()
                    .map(pi -> new Entry(Program.fromProgramInfo(pi), false))
                    .forEachOrdered(mPrograms::add);

            notifyDataSetChanged();
        }
    }

    /**
     * Remove formerly favorite stations from the list of stations, e.g. a station that started as a
     * favorite, but is no longer a favorite
     */
    public void removeFormerFavorites() {
        synchronized (mLock) {
            // Remove all programs that are no longer a favorite,
            // except those that were never favorites (i.e. currently tuned)
            mPrograms = mPrograms.stream()
                    .filter(e -> e.isFavorite || !e.wasFavorite)
                    .collect(Collectors.toList());
        }
        notifyDataSetChanged();
    }

    /**
     * Updates the stations that are favorites, while keeping unfavorited stations in the list
     */
    private void onFavoritesChanged(List<Program> favorites) {
        Map<ProgramSelector.Identifier, Program> favMap = favorites.stream().collect(
                Collectors.toMap(p -> p.getSelector().getPrimaryId(), p -> p));
        synchronized (mLock) {
            // Mark existing elements as favorites or not
            for (Entry entry : mPrograms) {
                ProgramSelector.Identifier id = entry.program.getSelector().getPrimaryId();
                entry.isFavorite = favMap.containsKey(id);
                if (entry.isFavorite) favMap.remove(id);  // don't add twice
            }

            // Add new items
            favMap.values().stream().map(p -> new Entry(p, true)).forEachOrdered(mPrograms::add);

            notifyDataSetChanged();
        }
    }

    /**
     * Indicates which radio station is the active one inside the list of programs that are set on
     * this adapter. This will cause that station to be highlighted in the list. If the station
     * passed to this method does not match any of the programs, then none will be highlighted.
     */
    private void onCurrentProgramChanged(@NonNull ProgramInfo info) {
        synchronized (mLock) {
            mCurrentProgram = Objects.requireNonNull(info);
            notifyDataSetChanged();
        }
    }

    @Override
    public ProgramViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.radio_browse_item, parent, false);

        return new ProgramViewHolder(
                view, this::handlePresetClicked, this::handlePresetFavoriteChanged);
    }

    @Override
    public void onBindViewHolder(ProgramViewHolder holder, int position) {
        synchronized (mLock) {
            Entry entry = getEntryLocked(position);
            boolean isCurrent = mCurrentProgram != null
                    && entry.program.getSelector().equals(mCurrentProgram.getSelector());
            holder.bindPreset(entry.program, isCurrent, getItemCount(), entry.isFavorite);
        }
    }

    @Override
    public int getItemViewType(int position) {
        return PRESETS_VIEW_TYPE;
    }

    private Entry getEntryLocked(int position) {
        // if there are no elements on the list, return current program
        if (position == 0 && mPrograms.size() == 0) {
            return new Entry(Program.fromProgramInfo(mCurrentProgram), false);
        }
        return mPrograms.get(position);
    }

    @Override
    public int getItemCount() {
        synchronized (mLock) {
            int cnt = mPrograms.size();
            if (cnt == 0 && mCurrentProgram != null) return 1;
            return cnt;
        }
    }

    private void handlePresetClicked(int position) {
        synchronized (mLock) {
            if (mItemClickListener == null) return;
            if (position >= getItemCount()) return;

            mItemClickListener.onItemClicked(getEntryLocked(position).program.getSelector());
        }
    }

    private void handlePresetFavoriteChanged(int position, boolean saveAsFavorite) {
        synchronized (mLock) {
            if (mItemFavoriteListener == null) return;
            if (position >= getItemCount()) return;

            mItemFavoriteListener.onItemFavoriteChanged(
                    getEntryLocked(position).program, saveAsFavorite);
        }
    }
}
