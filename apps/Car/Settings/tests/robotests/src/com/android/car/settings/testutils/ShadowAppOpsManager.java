/*
 * Copyright 2019 The Android Open Source Project
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

package com.android.car.settings.testutils;

import android.app.AppOpsManager;
import android.app.AppOpsManager.OpEntry;
import android.app.AppOpsManager.PackageOps;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Table;

import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Implements(value = AppOpsManager.class)
public class ShadowAppOpsManager {

    private Table<Integer, InternalKey, Integer> mOpToKeyToMode = HashBasedTable.create();

    @Implementation
    protected void setMode(int code, int uid, String packageName, int mode) {
        InternalKey key = new InternalKey(uid, packageName);
        mOpToKeyToMode.put(code, key, mode);
    }

    /** Convenience method to get the mode directly instead of wrapped in an op list. */
    public int getMode(int code, int uid, String packageName) {
        Integer mode = mOpToKeyToMode.get(code, new InternalKey(uid, packageName));
        return mode == null ? AppOpsManager.opToDefaultMode(code) : mode;
    }

    @Implementation
    protected List<PackageOps> getPackagesForOps(int[] ops) {
        if (ops == null) {
            return Collections.emptyList();
        }
        ImmutableList.Builder<PackageOps> result = new ImmutableList.Builder<>();
        for (int i = 0; i < ops.length; i++) {
            int op = ops[i];
            Map<InternalKey, Integer> keyToModeMap = mOpToKeyToMode.rowMap().get(op);
            if (keyToModeMap == null) {
                continue;
            }
            for (InternalKey key : keyToModeMap.keySet()) {
                Integer mode = keyToModeMap.get(key);
                if (mode == null) {
                    mode = AppOpsManager.opToDefaultMode(op);
                }
                OpEntry opEntry = new OpEntry(op, mode);
                PackageOps packageOp = new PackageOps(key.mPackageName, key.mUid,
                        Collections.singletonList(opEntry));
                result.add(packageOp);
            }
        }
        return result.build();
    }

    private static class InternalKey {
        private int mUid;
        private String mPackageName;

        InternalKey(int uid, String packageName) {
            mUid = uid;
            mPackageName = packageName;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj instanceof InternalKey) {
                InternalKey that = (InternalKey) obj;
                return mUid == that.mUid && mPackageName.equals(that.mPackageName);
            }
            return false;
        }

        @Override
        public int hashCode() {
            return Objects.hash(mUid, mPackageName);
        }
    }
}
