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

package com.android.car;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * A simple implementation of a multimap that maps keys to sets of values.
 *
 * This class is (and should remain) drop-in replaceable with Guava's SetMultimap.
 *
 * @param <K> The type of the keys in the map.
 * @param <V> The type of the values in the map.
 */
public class SetMultimap<K, V> {
    private Map<K, Set<V>> mMap;

    /** Creates a new {@link #SetMultimap}. */
    public SetMultimap() {
        mMap = new HashMap<>();
    }

    /** Gets the set of values associated with a given key. */
    public Set<V> get(K key) {
        return Collections.unmodifiableSet(mMap.getOrDefault(key, Collections.emptySet()));
    }

    /** Adds a value to the set associated with a key. */
    public boolean put(K key, V value) {
        return mMap.computeIfAbsent(key, k -> new HashSet<>()).add(value);
    }

    /** Checks if the multimap contains the given key and value. */
    public boolean containsEntry(K key, V value) {
        Set<V> set = mMap.get(key);
        return set != null && set.contains(value);
    }

    /** Removes the given value from the set of the given key. */
    public boolean remove(K key, V value) {
        Set<V> set = mMap.get(key);
        if (set == null) {
            return false;
        }

        boolean removed = set.remove(value);
        if (set.isEmpty()) {
            mMap.remove(key);
        }
        return removed;
    }

    /** Clears all entries in the map. */
    public void clear() {
        mMap.clear();
    }

    /** Gets the set of keys stored in the map. */
    public Set<K> keySet() {
        return Collections.unmodifiableSet(mMap.keySet());
    }
}
