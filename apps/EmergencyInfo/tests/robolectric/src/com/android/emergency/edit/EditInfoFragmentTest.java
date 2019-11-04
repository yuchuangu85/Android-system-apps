package com.android.emergency.edit;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.pm.PackageManager;
import androidx.preference.Preference;
import androidx.preference.Preference.OnPreferenceChangeListener;
import com.android.emergency.edit.EditInfoFragment.PreferenceChangeListener;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;

@RunWith(RobolectricTestRunner.class)
public class EditInfoFragmentTest {

    @Mock
    private PackageManager mPackageManager;
    private Context mContext;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mContext = spy(RuntimeEnvironment.application);
        doReturn(mPackageManager).when(mContext).getPackageManager();
    }

    @Test
    public void testOnPreferenceChange_without_listener() {
        final PreferenceChangeListener listener = new PreferenceChangeListener(mContext);
        final PreferenceWithoutListener preference = spy(new PreferenceWithoutListener(mContext));
        final Object value = new Object();

        assertThat(listener.onPreferenceChange(preference, value)).isTrue();
        verify(mPackageManager).setComponentEnabledSetting(any(ComponentName.class), anyInt(),
            eq(PackageManager.DONT_KILL_APP));
    }

    @Test
    public void testOnPreferenceChange_with_listener() {
        final PreferenceChangeListener listener = new PreferenceChangeListener(mContext);
        final PreferenceWithListener preference = spy(new PreferenceWithListener(mContext));
        final Object value = new Object();
        final boolean resultValue = false;
        doReturn(resultValue).when(preference).onPreferenceChange(preference, value);

        assertThat(listener.onPreferenceChange(preference, value)).isEqualTo(resultValue);
        verify(mPackageManager).setComponentEnabledSetting(any(ComponentName.class), anyInt(),
            eq(PackageManager.DONT_KILL_APP));
        verify(preference).onPreferenceChange(preference, value);
    }

    public class PreferenceWithoutListener extends Preference {

        public PreferenceWithoutListener(Context context) {
            super(context);
        }
    }

    public class PreferenceWithListener extends Preference implements OnPreferenceChangeListener {

        public PreferenceWithListener(Context context) {
            super(context);
        }

        @Override
        public boolean onPreferenceChange(Preference preference, Object o) {
            return false;
        }
    }
}
