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

package com.android.settings.gestures;

import static com.android.internal.policy.GestureLongSwipeUtils.ACTION_EMPTY;
import static com.android.internal.policy.GestureLongSwipeUtils.DEFAULT_LONG_SWIPE_THRESHOLD_LAND;
import static com.android.internal.policy.GestureLongSwipeUtils.DEFAULT_LONG_SWIPE_THRESHOLD_PORT;
import static com.android.internal.policy.GestureLongSwipeUtils.LONG_SWIPE_THRESHOLD_LAND_VALUES;
import static com.android.internal.policy.GestureLongSwipeUtils.LONG_SWIPE_THRESHOLD_PORT_VALUES;
import static com.android.internal.policy.GestureNavigationSettingsObserverExt.GESTURE_HEIGHT_1_2;
import static com.android.internal.policy.GestureNavigationSettingsObserverExt.GESTURE_HEIGHT_1_4;
import static com.android.internal.policy.GestureNavigationSettingsObserverExt.GESTURE_HEIGHT_3_4;
import static com.android.internal.policy.GestureNavigationSettingsObserverExt.GESTURE_HEIGHT_FULL;

import static org.sun.view.PopUpViewManager.FEATURE_SUPPORTED;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.UserHandle;
import android.provider.Settings;
import android.view.WindowManager;

import androidx.preference.ListPreference;

import com.android.internal.policy.GestureNavigationSettingsObserverExt;

import com.android.settings.R;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.LabeledSeekBarPreference;
import com.android.settings.widget.SeekBarPreference;
import com.android.settingslib.search.SearchIndexable;

import java.util.Arrays;

import org.sun.provider.SettingsExt;

/**
 * A fragment to include all the settings related to Gesture Navigation mode.
 */
@SearchIndexable(forTarget = SearchIndexable.ALL & ~SearchIndexable.ARC)
public class GestureNavigationSettingsFragment extends DashboardFragment {

    public static final String TAG = "GestureNavigationSettingsFragment";

    public static final String GESTURE_NAVIGATION_SETTINGS =
            "com.android.settings.GESTURE_NAVIGATION_SETTINGS";

    private static final String GESTURE_BACK_HEIGHT_KEY = "gesture_back_height";
    private static final String LEFT_EDGE_SEEKBAR_KEY = "gesture_left_back_sensitivity";
    private static final String RIGHT_EDGE_SEEKBAR_KEY = "gesture_right_back_sensitivity";
    private static final String LONG_SWIPE_ACTION_LEFT_KEY = "left_long_back_swipe_action";
    private static final String LONG_SWIPE_ACTION_RIGHT_KEY = "right_long_back_swipe_action";
    private static final String LONG_SWIPE_THRESHOLD_PORT_KEY = "long_back_swipe_threshold_port";
    private static final String LONG_SWIPE_THRESHOLD_LAND_KEY = "long_back_swipe_threshold_land";
    private static final String GESTURE_NAVBAR_LENGTH_KEY = "gesture_navbar_length_preference";
    private static final String GESTURE_NAVBAR_RADIUS_KEY = "gesture_navbar_radius_preference";

    private final int[] mGestureHeightScale = {
        GESTURE_HEIGHT_1_4,
        GESTURE_HEIGHT_1_2,
        GESTURE_HEIGHT_3_4,
        GESTURE_HEIGHT_FULL
    };

    private WindowManager mWindowManager;
    private BackGestureIndicatorView mIndicatorView;

    private float[] mBackGestureInsetScales;
    private float mDefaultBackGestureInset;

    public GestureNavigationSettingsFragment() {
        super();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mIndicatorView = new BackGestureIndicatorView(getActivity());
        mWindowManager = (WindowManager) getActivity().getSystemService(Context.WINDOW_SERVICE);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        super.onCreatePreferences(savedInstanceState, rootKey);

        final Resources res = getActivity().getResources();
        mDefaultBackGestureInset = res.getDimensionPixelSize(
                com.android.internal.R.dimen.config_backGestureInset);
        mBackGestureInsetScales = getFloatArray(res.obtainTypedArray(
                com.android.internal.R.array.config_backGestureInsetScales));

        initSeekBarPreference(LEFT_EDGE_SEEKBAR_KEY);
        initSeekBarPreference(RIGHT_EDGE_SEEKBAR_KEY);

        initGestureHeightPreference();

        initLongSwipeActionPreference();
        initLongSwipeThresholdPreference();

        initGestureNavbarLengthPreference();
        initGestureBarRadiusPreference();
    }

    @Override
    public void onResume() {
        super.onResume();

        final int height = Settings.System.getIntForUser(getContext().getContentResolver(),
                SettingsExt.System.BACK_GESTURE_HEIGHT, GESTURE_HEIGHT_FULL, UserHandle.USER_CURRENT);
        mIndicatorView.setIndicatorHeightScale(height);

        mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                getActivity().getWindow().getAttributes()));
    }

    @Override
    public void onPause() {
        super.onPause();

        mWindowManager.removeView(mIndicatorView);
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.gesture_navigation_settings;
    }

    @Override
    public int getHelpResource() {
        // TODO(b/146001201): Replace with gesture navigation help page when ready.
        return R.string.help_uri_default;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.SETTINGS_GESTURE_NAV_BACK_SENSITIVITY_DLG;
    }

    private void initSeekBarPreference(final String key) {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(key);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);

        final String settingsKey = key == LEFT_EDGE_SEEKBAR_KEY
                ? Settings.Secure.BACK_GESTURE_INSET_SCALE_LEFT
                : Settings.Secure.BACK_GESTURE_INSET_SCALE_RIGHT;
        final float initScale = Settings.Secure.getFloat(
                getContext().getContentResolver(), settingsKey, 1.0f);

        // Find the closest value to initScale
        float minDistance = Float.MAX_VALUE;
        int minDistanceIndex = -1;
        for (int i = 0; i < mBackGestureInsetScales.length; i++) {
            float d = Math.abs(mBackGestureInsetScales[i] - initScale);
            if (d < minDistance) {
                minDistance = d;
                minDistanceIndex = i;
            }
        }
        pref.setProgress(minDistanceIndex);

        pref.setOnPreferenceChangeListener((p, v) -> {
            final int width = (int) (mDefaultBackGestureInset * mBackGestureInsetScales[(int) v]);
            mIndicatorView.setIndicatorWidth(width, key == LEFT_EDGE_SEEKBAR_KEY);
            return true;
        });

        pref.setOnPreferenceChangeStopListener((p, v) -> {
            mIndicatorView.setIndicatorWidth(0, key == LEFT_EDGE_SEEKBAR_KEY);
            final float scale = mBackGestureInsetScales[(int) v];
            Settings.Secure.putFloat(getContext().getContentResolver(), settingsKey, scale);
            return true;
        });
    }

    private void initGestureHeightPreference() {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(GESTURE_BACK_HEIGHT_KEY);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);

        final int initHeight = Settings.System.getIntForUser(getContext().getContentResolver(),
                SettingsExt.System.BACK_GESTURE_HEIGHT, GESTURE_HEIGHT_FULL, UserHandle.USER_CURRENT);
        pref.setProgress(mGestureHeightScale.length - 1 - initHeight);

        pref.setOnPreferenceChangeListener((p, v) -> {
            final int height = mGestureHeightScale[(int) v];
            mIndicatorView.setIndicatorHeightScale(height);
            mWindowManager.removeView(mIndicatorView);
            mWindowManager.addView(mIndicatorView, mIndicatorView.getLayoutParams(
                    getActivity().getWindow().getAttributes()));
            final int width = (int) (getDisplayWidth() * 0.1f);
            mIndicatorView.setIndicatorWidth(width, true);
            mIndicatorView.setIndicatorWidth(width, false);
            return true;
        });

        pref.setOnPreferenceChangeStopListener((p, v) -> {
            final int height = mGestureHeightScale[(int) v];
            mIndicatorView.setIndicatorWidth(0, true);
            mIndicatorView.setIndicatorWidth(0, false);
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    SettingsExt.System.BACK_GESTURE_HEIGHT, height, UserHandle.USER_CURRENT);
            return true;
        });
    }

    private void initLongSwipeActionPreference() {
        GestureNavigationSettingsObserverExt.getInstance().init(getContext());

        final CharSequence[] entriesArr = getContext().getResources()
                .getTextArray(R.array.back_gesture_long_swipe_action_entries);
        final CharSequence[] entryValuesArr = getContext().getResources()
                .getTextArray(R.array.back_gesture_long_swipe_action_values);
        final int size = FEATURE_SUPPORTED ? entriesArr.length + 1 : entriesArr.length;

        final CharSequence[] entries = new CharSequence[size];
        final CharSequence[] entryValues = new CharSequence[size];
        for (int i = 0; i < entriesArr.length; i++) {
            entries[i] = entriesArr[i];
            entryValues[i] = entryValuesArr[i];
        }
        if (FEATURE_SUPPORTED) {
            entries[size - 1] = getContext().getResources().getText(R.string.long_swipe_action_enter_pinned_window);
            entryValues[size - 1] = String.valueOf(size - 1);
        }

        final ListPreference prefLeft = getPreferenceScreen().findPreference(LONG_SWIPE_ACTION_LEFT_KEY);
        prefLeft.setTitle(R.string.left_back_long_swipe_title);
        prefLeft.setDialogTitle(R.string.left_back_long_swipe_title);
        prefLeft.setEntries(entries);
        prefLeft.setEntryValues(entryValues);
        prefLeft.setValueIndex(GestureNavigationSettingsObserverExt
                .getInstance().getLongSwipeLeftAction());
        prefLeft.setSummary(prefLeft.getEntry());
        prefLeft.setOnPreferenceChangeListener((p, v) -> {
            final int val = Integer.parseInt(v.toString());
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    SettingsExt.System.LEFT_LONG_BACK_SWIPE_ACTION,
                    val, UserHandle.USER_CURRENT);
            prefLeft.setSummary(prefLeft.getEntries()[val]);
            return true;
        });

        final ListPreference prefRight = getPreferenceScreen().findPreference(LONG_SWIPE_ACTION_RIGHT_KEY);
        prefRight.setTitle(R.string.right_back_long_swipe_title);
        prefRight.setDialogTitle(R.string.right_back_long_swipe_title);
        prefRight.setEntries(entries);
        prefRight.setEntryValues(entryValues);
        prefRight.setValueIndex(GestureNavigationSettingsObserverExt
                .getInstance().getLongSwipeRightAction());
        prefRight.setSummary(prefRight.getEntry());
        prefRight.setOnPreferenceChangeListener((p, v) -> {
            final int val = Integer.parseInt(v.toString());
            Settings.System.putIntForUser(getContext().getContentResolver(),
                    SettingsExt.System.RIGHT_LONG_BACK_SWIPE_ACTION,
                    val, UserHandle.USER_CURRENT);
            prefRight.setSummary(prefRight.getEntries()[val]);
            return true;
        });
    }

    private void initLongSwipeThresholdPreference() {
        final LabeledSeekBarPreference prefPort = getPreferenceScreen().findPreference(LONG_SWIPE_THRESHOLD_PORT_KEY);
        prefPort.setContinuousUpdates(true);
        prefPort.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        final float portVal = Settings.System.getFloatForUser(
                getContext().getContentResolver(),
                SettingsExt.System.LONG_BACK_SWIPE_THRESHOLD_PORT,
                DEFAULT_LONG_SWIPE_THRESHOLD_PORT, UserHandle.USER_CURRENT);
        int index = 0;
        for (int i = 0; i < LONG_SWIPE_THRESHOLD_PORT_VALUES.length; i++) {
            if (LONG_SWIPE_THRESHOLD_PORT_VALUES[i] == portVal) {
                index = i;
                break;
            }
        }
        prefPort.setProgress(index);
        prefPort.setOnPreferenceChangeListener((p, v) -> {
            final float val = LONG_SWIPE_THRESHOLD_PORT_VALUES[(int) v];
            if (!isLandscape()) {
                final int width = (int) (getDisplayWidth() * val);
                mIndicatorView.setIndicatorWidth(width, false);
            }
            return true;
        });
        prefPort.setOnPreferenceChangeStopListener((p, v) -> {
            final float val = LONG_SWIPE_THRESHOLD_PORT_VALUES[(int) v];
            Settings.System.putFloatForUser(getContext().getContentResolver(),
                    SettingsExt.System.LONG_BACK_SWIPE_THRESHOLD_PORT,
                    val, UserHandle.USER_CURRENT);
            if (!isLandscape()) {
                mIndicatorView.setIndicatorWidth(0, false);
            }
            return true;
        });

        final LabeledSeekBarPreference prefLand = getPreferenceScreen().findPreference(LONG_SWIPE_THRESHOLD_LAND_KEY);
        prefLand.setContinuousUpdates(true);
        prefLand.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        final float landVal = Settings.System.getFloatForUser(
                getContext().getContentResolver(),
                SettingsExt.System.LONG_BACK_SWIPE_THRESHOLD_LAND,
                DEFAULT_LONG_SWIPE_THRESHOLD_LAND, UserHandle.USER_CURRENT);
        index = 0;
        for (int i = 0; i < LONG_SWIPE_THRESHOLD_LAND_VALUES.length; i++) {
            if (LONG_SWIPE_THRESHOLD_LAND_VALUES[i] == landVal) {
                index = i;
                break;
            }
        }
        prefLand.setProgress(index);
        prefLand.setOnPreferenceChangeListener((p, v) -> {
            final float val = LONG_SWIPE_THRESHOLD_LAND_VALUES[(int) v];
            if (isLandscape()) {
                final int width = (int) (getDisplayWidth() * val);
                mIndicatorView.setIndicatorWidth(width, false);
            }
            return true;
        });
        prefLand.setOnPreferenceChangeStopListener((p, v) -> {
            final float val = LONG_SWIPE_THRESHOLD_LAND_VALUES[(int) v];
            Settings.System.putFloatForUser(getContext().getContentResolver(),
                    SettingsExt.System.LONG_BACK_SWIPE_THRESHOLD_LAND,
                    val, UserHandle.USER_CURRENT);
            if (isLandscape()) {
                mIndicatorView.setIndicatorWidth(0, false);
            }
            return true;
        });
    }

    private void initGestureNavbarLengthPreference() {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(GESTURE_NAVBAR_LENGTH_KEY);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        pref.setProgress(Settings.System.getIntForUser(
                getContext().getContentResolver(),
                SettingsExt.System.GESTURE_NAVBAR_LENGTH_MODE,
                1, UserHandle.USER_CURRENT));
        pref.setOnPreferenceChangeListener((p, v) ->
                Settings.System.putIntForUser(getContext().getContentResolver(),
                        SettingsExt.System.GESTURE_NAVBAR_LENGTH_MODE,
                        (Integer) v, UserHandle.USER_CURRENT));
    }

    private void initGestureBarRadiusPreference() {
        final LabeledSeekBarPreference pref = getPreferenceScreen().findPreference(GESTURE_NAVBAR_RADIUS_KEY);
        pref.setContinuousUpdates(true);
        pref.setHapticFeedbackMode(SeekBarPreference.HAPTIC_FEEDBACK_MODE_ON_TICKS);
        pref.setProgress(Settings.System.getIntForUser(
                getContext().getContentResolver(),
                SettingsExt.System.GESTURE_NAVBAR_RADIUS_MODE,
                0, UserHandle.USER_CURRENT));
        pref.setOnPreferenceChangeListener((p, v) ->
                Settings.System.putIntForUser(getContext().getContentResolver(),
                        SettingsExt.System.GESTURE_NAVBAR_RADIUS_MODE,
                        (Integer) v, UserHandle.USER_CURRENT));
    }

    private boolean isLandscape() {
        return getContext().getResources().getConfiguration().orientation
                == Configuration.ORIENTATION_LANDSCAPE;
    }

    private int getDisplayWidth() {
        final Rect bounds = mWindowManager.getCurrentWindowMetrics().getBounds();
        return bounds.width();
    }

    private static float[] getFloatArray(TypedArray array) {
        int length = array.length();
        float[] floatArray = new float[length];
        for (int i = 0; i < length; i++) {
            floatArray[i] = array.getFloat(i, 1.0f);
        }
        array.recycle();
        return floatArray;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider(R.xml.gesture_navigation_settings) {

                @Override
                protected boolean isPageSearchEnabled(Context context) {
                    return SystemNavigationPreferenceController.isGestureAvailable(context);
                }
            };

}
