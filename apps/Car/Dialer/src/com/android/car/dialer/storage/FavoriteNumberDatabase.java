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

package com.android.car.dialer.storage;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;

/** Defines the database for the {@link FavoriteNumberEntity}s. */
@Database(entities = {FavoriteNumberEntity.class}, exportSchema = false, version = 1)
@TypeConverters(CipherConverter.class)
public abstract class FavoriteNumberDatabase extends RoomDatabase {

    /** Returns the data access object to interact with the favorite number database. */
    public abstract FavoriteNumberDao favoriteNumberDao();

    private static volatile FavoriteNumberDatabase sFavoriteNumberDatabase;

    static FavoriteNumberDatabase getDatabase(final Context context) {
        if (sFavoriteNumberDatabase == null) {
            synchronized (FavoriteNumberDatabase.class) {
                if (sFavoriteNumberDatabase == null) {
                    sFavoriteNumberDatabase = Room.databaseBuilder(context.getApplicationContext(),
                            FavoriteNumberDatabase.class, "favorite_number_database").build();
                }
            }
        }
        return sFavoriteNumberDatabase;
    }
}
