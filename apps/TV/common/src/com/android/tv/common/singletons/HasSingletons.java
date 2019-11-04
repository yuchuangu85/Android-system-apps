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
package com.android.tv.common.singletons;

import android.content.Context;

/**
 * A type that can know about and supply a singleton, typically a type t such as an android activity
 * or application.
 */
public interface HasSingletons<C> {

    @SuppressWarnings("unchecked") // injection
    static <C> C get(Class<C> clazz, Context context) {
        return ((HasSingletons<C>) context).singletons();
    }

    /** Returns the strongly typed singleton. */
    C singletons();
}
