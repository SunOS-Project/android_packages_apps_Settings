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

package com.android.settings.development;

import static android.os.UserHandle.USER_SYSTEM;

import android.content.Context;
import android.content.om.IOverlayManager;
import android.content.om.OverlayInfo;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.os.UserHandle;
import android.provider.Settings;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.VisibleForTesting;
import androidx.preference.ListPreference;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;

import com.android.settings.R;
import com.android.settings.core.PreferenceControllerMixin;
import com.android.settingslib.development.DeveloperOptionsPreferenceController;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Preference controller to allow users to choose an overlay from a list for a given category.
 * The chosen overlay is enabled exclusively within its category. A default option is also
 * exposed that disables all overlays in the given category.
 */
public class OverlayCategoryPreferenceController extends DeveloperOptionsPreferenceController
        implements Preference.OnPreferenceChangeListener, PreferenceControllerMixin {
    private static final String TAG = "OverlayCategoryPC";
    private static final String FONT_KEY = "android.theme.customization.font";
    private static final String ADAPTIVE_ICON_SHAPE_KEY = "android.theme.customization.adaptive_icon_shape";

    @VisibleForTesting
    static final String PACKAGE_DEVICE_DEFAULT = "package_device_default";
    private static final Comparator<OverlayInfo> OVERLAY_INFO_COMPARATOR =
            Comparator.comparing(OverlayInfo::getPackageName);
    private final IOverlayManager mOverlayManager;
    private final boolean mAvailable;
    private final boolean mIsFonts;
    private final boolean mIsAdaptiveIconShape;
    private final String mCategory;
    private final PackageManager mPackageManager;
    private final String mDeviceDefaultLabel;

    private ListPreference mPreference;

    @VisibleForTesting
    OverlayCategoryPreferenceController(Context context, PackageManager packageManager,
            IOverlayManager overlayManager, String category) {
        super(context);
        mOverlayManager = overlayManager;
        mPackageManager = packageManager;
        mCategory = category;
        mAvailable = overlayManager != null && !getOverlayInfos().isEmpty();
        mDeviceDefaultLabel = mContext.getString(R.string.overlay_option_device_default);
        mIsFonts = FONT_KEY.equals(category);
        mIsAdaptiveIconShape = ADAPTIVE_ICON_SHAPE_KEY.equals(category);
    }

    public OverlayCategoryPreferenceController(Context context, String category) {
        this(context, context.getPackageManager(), IOverlayManager.Stub
                .asInterface(ServiceManager.getService(Context.OVERLAY_SERVICE)), category);
    }

    @Override
    public boolean isAvailable() {
        return mAvailable;
    }

    @Override
    public String getPreferenceKey() {
        return mCategory;
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        setPreference(screen.findPreference(getPreferenceKey()));
    }

    @VisibleForTesting
    void setPreference(ListPreference preference) {
        mPreference = preference;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        return setOverlay((String) newValue);
    }

    private boolean setOverlay(String label) {
        final List<OverlayInfo> infos = getOverlayInfos();

        ArrayList<String> currentPackageNames = new ArrayList<>();;
        ArrayList<String> currentCategoryNames = new ArrayList<>();;
        ArrayList<String> packageNames = new ArrayList<>();;
        ArrayList<String> categoryNames = new ArrayList<>();;

        for (OverlayInfo info : infos) {
            if (info.isEnabled()) {
                currentPackageNames.add(info.packageName);
                currentCategoryNames.add(info.category);
            }
            if (label.equals(getPackageLabel(info.packageName))) {
                packageNames.add(info.packageName);
                categoryNames.add(info.category);
            }
        }

        Log.w(TAG, "setOverlay currentPackageNames=" + currentPackageNames.toString());
        Log.w(TAG, "setOverlay packageNames=" + packageNames.toString());
        Log.w(TAG, "setOverlay label=" + label);

        if (mIsFonts || mIsAdaptiveIconShape) {
            // For overlays, we also need to set this setting
            String value = Settings.Secure.getStringForUser(mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES, UserHandle.USER_CURRENT);
            JSONObject json;
            if (value == null) {
                json = new JSONObject();
            } else {
                try {
                    json = new JSONObject(value);
                } catch (JSONException e) {
                    Log.e(TAG, "Error parsing current settings value:\n" + e.getMessage());
                    return false;
                }
            }
            // removing all currently enabled overlays from the json
            for (String categoryName : currentCategoryNames) {
                json.remove(categoryName);
            }
            // adding the new ones
            for (int i = 0; i < categoryNames.size(); i++) {
                try {
                    json.put(categoryNames.get(i), packageNames.get(i));
                } catch (JSONException e) {
                    Log.e(TAG, "Error adding new settings value:\n" + e.getMessage());
                    return false;
                }
            }
            // updating the setting
            Settings.Secure.putStringForUser(mContext.getContentResolver(),
                    Settings.Secure.THEME_CUSTOMIZATION_OVERLAY_PACKAGES,
                    json.toString(), UserHandle.USER_CURRENT);
        }

        new AsyncTask<Void, Void, Boolean>() {
            @Override
            protected Boolean doInBackground(Void... params) {
                try {
                    if (label.equals(mDeviceDefaultLabel)) {
                        for (String packageName : currentPackageNames) {
                            Log.w(TAG, "setOverlay Disabing overlay" + packageName);
                            mOverlayManager.setEnabled(packageName, false, USER_SYSTEM);
                        }
                    } else {
                        for (String packageName : packageNames) {
                            Log.w(TAG, "setOverlay Enabling overlay" + packageName);
                            mOverlayManager.setEnabledExclusiveInCategory(packageName, USER_SYSTEM);
                        }
                    }
                    return true;
                } catch (Exception e) {
                    Log.w(TAG, "Error enabling overlay.", e);
                    return false;
                }
            }

            @Override
            protected void onPostExecute(Boolean success) {
                updateState(mPreference);
                if (!success) {
                    Toast.makeText(
                            mContext, R.string.overlay_toast_failed_to_apply, Toast.LENGTH_LONG)
                            .show();
                }
            }
        }.execute();

        return true; // Assume success; toast on failure.
    }

    @Override
    public void updateState(Preference preference) {
        final List<String> labels = new ArrayList<>();

        String selectedLabel = mDeviceDefaultLabel;

        // Add the default label before all of the overlays
        labels.add(selectedLabel);

        for (OverlayInfo overlayInfo : getOverlayInfos()) {
            String label = getPackageLabel(overlayInfo.packageName);
            if (!labels.contains(label)) {
                labels.add(label);
            }
            if (overlayInfo.isEnabled()) {
                Log.w(TAG, "updateState Selecting label"+label);
                selectedLabel = label;
            }
        }

        mPreference.setEntries(labels.toArray(new String[labels.size()]));
        mPreference.setEntryValues(labels.toArray(new String[labels.size()]));
        mPreference.setValue(selectedLabel);
        mPreference.setSummary(selectedLabel);
    }

    private List<OverlayInfo> getOverlayInfos() {
        final List<OverlayInfo> filteredInfos = new ArrayList<>();
        try {
            Collection<List<OverlayInfo>> allOverlays = mOverlayManager
                                              .getAllOverlays(USER_SYSTEM).values();
            for (List<OverlayInfo> overlayInfos : allOverlays) {
                for (OverlayInfo overlayInfo : overlayInfos) {
                    if (overlayInfo.category != null) {
                        if (overlayInfo.category.contains(mCategory)) {
                            filteredInfos.add(overlayInfo);
                        }
                    }
                }
            }
        } catch (RemoteException re) {
            throw re.rethrowFromSystemServer();
        }
        filteredInfos.sort(OVERLAY_INFO_COMPARATOR);
        Log.w(TAG, "getOverlays list=" + filteredInfos.toString());
        return filteredInfos;
    }

    private String getPackageLabel(String packageName) {
        try {
            return mPackageManager.getApplicationInfo(packageName, 0)
                                  .loadLabel(mPackageManager).toString();
        } catch (PackageManager.NameNotFoundException e) {
            return mDeviceDefaultLabel;
        }
    }
}
