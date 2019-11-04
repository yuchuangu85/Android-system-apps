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

package com.android.phone.testapps.telephonymanagertestapp;

import android.app.ListActivity;
import android.app.SearchManager;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.view.Menu;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.SearchView;
import android.widget.TextView;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Main activity.
 * Activity to choose which method to call.
 */
public class TelephonyManagerTestApp extends ListActivity implements
        SearchView.OnQueryTextListener {
    public static String TAG = "TMTestApp";

    private List<Method> mMethods = new ArrayList<>();
    private List<Method> mFilteredMethods = new ArrayList<>();
    static Method sCurrentMethod;

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Initialize search view
        getMenuInflater().inflate(R.menu.search_input, menu);
        SearchView searchView = (SearchView) menu.findItem(R.id.search_input).getActionView();
        SearchManager searchManager = (SearchManager) getSystemService(SEARCH_SERVICE);
        searchView.setSearchableInfo(searchManager.getSearchableInfo(getComponentName()));
        searchView.setOnQueryTextListener(this);
        searchView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
            @Override
            public void onViewDetachedFromWindow(View arg0) {
                mFilteredMethods.clear();
                mFilteredMethods.addAll(mMethods);
                ((ListViewAdapter) mAdapter).notifyDataSetChanged();
            }

            @Override
            public void onViewAttachedToWindow(View arg0) {
            }
        });
        return true;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        try {
            Class c = TelephonyManager.class;
            mMethods = Arrays.asList(c.getDeclaredMethods());
            mFilteredMethods.addAll(mMethods);
            mAdapter = new ListViewAdapter();
            setListAdapter(mAdapter);
        } catch (Throwable e) {
            System.err.println(e);
            finish();
        }
    }

    private class ListViewAdapter extends BaseAdapter {
        @Override
        public int getCount() {
            return mFilteredMethods.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup container) {
            if (mFilteredMethods.size() <= position) {
                return null;
            }

            if (convertView == null) {
                convertView = getLayoutInflater().inflate(
                        R.layout.abstract_method_view, container, false);
            }

            Method method = mFilteredMethods.get(position);
            String tags = Modifier.toString(method.getModifiers()) + ' '
                    + getShortTypeName(method.getReturnType().toString());
            String parameters = getParameters(method.getParameterTypes(), method.getParameters());
            String methodName = (parameters == null) ? (method.getName() + "()") : method.getName();

            ((TextView) convertView.findViewById(R.id.tags)).setText(tags);
            ((TextView) convertView.findViewById(R.id.method_name)).setText(methodName);
            ((TextView) convertView.findViewById(R.id.parameters)).setText(parameters);
            return convertView;
        }

        @Override
        public Object getItem(int position) {
            if (mFilteredMethods.size() <= position) {
                return null;
            }

            return mFilteredMethods.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }

    @Override
    public void onListItemClick(ListView l, View v, int position, long id) {
        sCurrentMethod = mFilteredMethods.get(position);
        Intent intent = new Intent(this, CallingMethodActivity.class);

        startActivity(intent);
    }

    @Override
    public boolean onQueryTextSubmit(String query) {
        filterMethods(query);
        return true;
    }

    @Override
    public boolean onQueryTextChange(String newText) {
        return false;
    }

    private void filterMethods(String text) {
        mFilteredMethods.clear();

        if (text == null || text.isEmpty()) {
            mFilteredMethods.addAll(mMethods);
        } else {
            for (Method method : mMethods) {
                if (method.getName().toLowerCase().contains(text.toLowerCase())) {
                    mFilteredMethods.add(method);
                }
            }
        }

        ((ListViewAdapter) mAdapter).notifyDataSetChanged();

    }

    private String getParameters(Class<?>[] types, Parameter[] parameters) {
        if (types == null || types.length == 0) {
            return null;
        }

        StringBuilder sb = new StringBuilder();
        sb.append('(');
        for (int j = 0; j < types.length; j++) {
            String typeName = getShortTypeName(types[j].getTypeName());
            sb.append(typeName);
            if (j < (types.length - 1)) {
                sb.append(", ");
            }
        }
        sb.append(')');

        return sb.toString();
    }

    static String getShortTypeName(String typeName) {
        if (typeName == null) {
            return null;
        }

        String[] parts = typeName.split("[. ]");
        return parts[parts.length - 1];
    }
}
