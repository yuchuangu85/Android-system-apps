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

import android.app.Activity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * Activity to call a specific method of TelephonyManager.
 */
public class CallingMethodActivity extends Activity {
    private Class[] mParameterTypes;
    private Object[] mParameterValues;
    EditText[] mEditTexts;
    private Button mGoButton;
    private Method mMethod;
    private TextView mReturnValue;
    private EditText mSubIdField;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.calling_method);

        if (TelephonyManagerTestApp.sCurrentMethod == null) {
            finish();
            return;
        }

        mMethod = TelephonyManagerTestApp.sCurrentMethod;

        mGoButton = findViewById(R.id.go_button);
        mReturnValue = findViewById(R.id.return_value);
        mSubIdField = findViewById(R.id.sub_id_value);

        mParameterTypes = mMethod.getParameterTypes();
        mParameterValues = new Object[mParameterTypes.length];
        mEditTexts = new EditText[mParameterTypes.length];
        populateParamList();

        String tags = Modifier.toString(mMethod.getModifiers()) + ' '
                + TelephonyManagerTestApp.getShortTypeName(mMethod.getReturnType().toString());
        ((TextView) findViewById(R.id.tags)).setText(tags);
        ((TextView) findViewById(R.id.method_name)).setText(mMethod.getName());

        mGoButton.setOnClickListener((View v) -> executeCallMethod());
        mReturnValue.setText("Return value: ");
    }

    private void executeCallMethod() {
        try {
            int subId = Integer.parseInt(mSubIdField.getText().toString());

            for (int i = 0; i < mParameterTypes.length; i++) {
                String text = mEditTexts[i].getText().toString();
                if (mParameterTypes[i] == int.class) {
                    mParameterValues[i] = Integer.parseInt(text);
                } else if (mParameterTypes[i] == boolean.class) {
                    mParameterValues[i] = Boolean.parseBoolean(text);
                } else if (mParameterTypes[i] == long.class) {
                    mParameterValues[i] = Long.parseLong(text);
                } else if (mParameterTypes[i] == String.class) {
                    mParameterValues[i] = text;
                } else {
                    mParameterValues[i] =
                            ParameterParser.get(this).executeParser(mParameterTypes[i], text);
                }
            }
            Log.d(TelephonyManagerTestApp.TAG, "Invoking method " + mMethod.getName());

            mMethod.setAccessible(true);
            if (!mMethod.getReturnType().equals(Void.TYPE)) {
                Object result = mMethod.invoke(new TelephonyManager(this, subId), mParameterValues);
                if (result instanceof String) {
                    if (((String) result).isEmpty()) {
                        result = "empty string";
                    }
                }
                Log.d(TelephonyManagerTestApp.TAG, "result is " + result);
                mReturnValue.setText("Return value: " + result);
            } else {
                mMethod.invoke(new TelephonyManager(this, subId), mParameterValues);
                mReturnValue.setText("Return value: successfully returned");
            }

        } catch (Exception exception) {
            Log.d(TelephonyManagerTestApp.TAG, "Exception: " + exception);
            StringWriter s = new StringWriter();
            PrintWriter stack = new PrintWriter(s);
            exception.printStackTrace(stack);
            mReturnValue.setText("Exception " + exception.getMessage() + "\n" + s.toString());
        }
    }

    private void populateParamList() {
        for (int i = 0; i < mParameterTypes.length; i++) {
            View view = getLayoutInflater().inflate(R.layout.parameter_field, null);
            Class aClass = mParameterTypes[i];
            ((TextView) view.findViewById(R.id.field_name)).setText(
                    TelephonyManagerTestApp.getShortTypeName(aClass.toString()) + ": ");
            mEditTexts[i] = view.findViewById(R.id.field_value);
            ((LinearLayout) findViewById(R.id.method_params)).addView(view);
        }
    }
}
