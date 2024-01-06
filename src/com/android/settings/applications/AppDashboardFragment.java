/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.settings.applications;

import android.app.settings.SettingsEnums;
import android.content.Context;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.SearchIndexableResource;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.ListPreference;
import androidx.preference.Preference;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.sun.CustomUtils;
import com.android.internal.util.sun.LauncherUtils;
import com.android.settings.R;
import com.android.settings.applications.appcompat.UserAspectRatioAppsPreferenceController;
import com.android.settings.dashboard.DashboardFragment;
import com.android.settings.search.BaseSearchIndexProvider;
import com.android.settings.widget.PreferenceCategoryController;
import com.android.settingslib.core.AbstractPreferenceController;
import com.android.settingslib.search.SearchIndexable;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Settings page for apps. */
@SearchIndexable
public class AppDashboardFragment extends DashboardFragment implements
        Preference.OnPreferenceChangeListener {

    private static final String TAG = "AppDashboardFragment";
    private static final String ADVANCED_CATEGORY_KEY = "advanced_category";
    private static final String ASPECT_RATIO_PREF_KEY = "aspect_ratio_apps";
    private AppsPreferenceController mAppsPreferenceController;

    private static final String KEY_LAUNCHER_SWITCHER_CATEGORY = "launcher_switcher_category";
    private static final String KEY_LAUNCHER_SWITCHER = "launcher_switcher_preference";

    private ListPreference mLauncherSwitcher;

    private static List<AbstractPreferenceController> buildPreferenceControllers(Context context) {
        final List<AbstractPreferenceController> controllers = new ArrayList<>();
        controllers.add(new AppsPreferenceController(context));

        final UserAspectRatioAppsPreferenceController aspectRatioAppsPreferenceController =
                new UserAspectRatioAppsPreferenceController(context, ASPECT_RATIO_PREF_KEY);
        final AdvancedAppsPreferenceCategoryController advancedCategoryController =
                new AdvancedAppsPreferenceCategoryController(context, ADVANCED_CATEGORY_KEY);
        advancedCategoryController.setChildren(List.of(aspectRatioAppsPreferenceController));
        controllers.add(advancedCategoryController);

        return controllers;
    }

    @Override
    public int getMetricsCategory() {
        return SettingsEnums.MANAGE_APPLICATIONS;
    }

    @Override
    protected String getLogTag() {
        return TAG;
    }

    @Override
    public int getHelpResource() {
        return R.string.help_url_apps_and_notifications;
    }

    @Override
    protected int getPreferenceScreenResId() {
        return R.xml.apps;
    }

    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mAppsPreferenceController = use(AppsPreferenceController.class);
        mAppsPreferenceController.setFragment(this /* fragment */);
        getSettingsLifecycle().addObserver(mAppsPreferenceController);

        final HibernatedAppsPreferenceController hibernatedAppsPreferenceController =
                use(HibernatedAppsPreferenceController.class);
        getSettingsLifecycle().addObserver(hibernatedAppsPreferenceController);
    }

    @VisibleForTesting
    PreferenceCategoryController getAdvancedAppsPreferenceCategoryController() {
        return use(AdvancedAppsPreferenceCategoryController.class);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final ArrayList<String> launcherList = LauncherUtils.getLauncherList(getContext());
        final ArrayList<String> pkgList = new ArrayList<>();
        final ArrayList<String> nameList = new ArrayList<>();
        for (String launcher : launcherList) {
            if (CustomUtils.isSystemApp(getContext(), launcher)) {
                pkgList.add(launcher);
                nameList.add(CustomUtils.getAppName(getContext(), launcher));
            }
        }

        mLauncherSwitcher = (ListPreference) findPreference(KEY_LAUNCHER_SWITCHER);
        if (pkgList.size() == 0) {
            getPreferenceScreen().removePreference(
                    getPreferenceScreen().findPreference(KEY_LAUNCHER_SWITCHER_CATEGORY));
            return;
        }

        CharSequence[] entries = new CharSequence[pkgList.size()];
        CharSequence[] values = new CharSequence[nameList.size()];
        for (int i = 0; i < entries.length; ++i) {
            entries[i] = nameList.get(i);
            values[i] = pkgList.get(i);
        }

        mLauncherSwitcher.setEntries(entries);
        mLauncherSwitcher.setEntryValues(values);
        String selectedLauncher = LauncherUtils.getSelectedLauncher();
        if (selectedLauncher.isEmpty()) {
            selectedLauncher = LauncherUtils.getResComponentName(getContext()).split("/")[0];
        }
        CharSequence summary = selectedLauncher;
        for (int i = 0; i < entries.length; ++i) {
            if (values[i].equals(selectedLauncher)) {
                summary = entries[i];
                break;
            }
        }
        mLauncherSwitcher.setValue(selectedLauncher);
        mLauncherSwitcher.setSummary(summary);
        mLauncherSwitcher.setOnPreferenceChangeListener(this);
    }

    @Override
    protected List<AbstractPreferenceController> createPreferenceControllers(Context context) {
        return buildPreferenceControllers(context);
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        String oldLauncher = LauncherUtils.getSelectedLauncher();
        if (oldLauncher.isEmpty()) {
            oldLauncher = LauncherUtils.getResComponentName(getContext()).split("/")[0];
        }
        final String launcher = (String) newValue;
        LauncherUtils.setSelectedLauncher(launcher);

        CharSequence[] entries = mLauncherSwitcher.getEntries();
        CharSequence[] values = mLauncherSwitcher.getEntryValues();
        CharSequence summary = launcher;
        for (int i = 0; i < entries.length; ++i) {
            if (values[i].equals(launcher)) {
                summary = entries[i];
                break;
            }
        }
        mLauncherSwitcher.setSummary(summary);

        if (!oldLauncher.equals(launcher)) {
            new AlertDialog.Builder(getContext())
                .setTitle(getContext().getText(R.string.launcher_switcher_reboot_title))
                .setMessage(getContext().getText(R.string.launcher_switcher_reboot_message))
                .setPositiveButton(R.string.launcher_switcher_reboot, (dialog, which) -> {
                    final PowerManager pm = getContext().getSystemService(PowerManager.class);
                    pm.reboot(null);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
        }

        return true;
    }

    public static final BaseSearchIndexProvider SEARCH_INDEX_DATA_PROVIDER =
            new BaseSearchIndexProvider() {
                @Override
                public List<SearchIndexableResource> getXmlResourcesToIndex(
                        Context context, boolean enabled) {
                    final SearchIndexableResource sir = new SearchIndexableResource(context);
                    sir.xmlResId = R.xml.apps;
                    return Arrays.asList(sir);
                }

                @Override
                public List<AbstractPreferenceController> createPreferenceControllers(
                        Context context) {
                    return buildPreferenceControllers(context);
                }
            };
}
