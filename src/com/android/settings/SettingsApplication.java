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

/*
 * Changes from Qualcomm Innovation Center, Inc. are provided under the following license:
 * Copyright (c) 2025 Qualcomm Innovation Center, Inc. All rights reserved.
 * SPDX-License-Identifier: BSD-3-Clause-Clear
 */

package com.android.settings;

import android.app.Application;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.Intent;
import android.database.ContentObserver;
import android.net.Uri;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.util.FeatureFlagUtils;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.settings.activityembedding.ActivityEmbeddingRulesController;
import com.android.settings.activityembedding.ActivityEmbeddingUtils;
import com.android.settings.biometrics.fingerprint2.BiometricsEnvironment;
import com.android.settings.core.instrumentation.ElapsedTimeUtils;
import com.android.settings.development.DeveloperOptionsActivityLifecycle;
import com.android.settings.fuelgauge.BatterySettingsStorage;
import com.android.settings.homepage.SettingsHomepageActivity;
import com.android.settings.localepicker.LocaleNotificationDataManager;
import com.android.settings.network.telephony.TelephonyUtils;
import com.android.settings.overlay.FeatureFactory;
import com.android.settings.overlay.FeatureFactoryImpl;
import com.android.settings.spa.SettingsSpaEnvironment;
import com.android.settingslib.applications.AppIconCacheManager;
import com.android.settingslib.datastore.BackupRestoreStorageManager;
import com.android.settingslib.spa.framework.common.SpaEnvironmentFactory;

import com.google.android.setupcompat.util.WizardManagerHelper;

import java.lang.ref.WeakReference;

/** Settings application which sets up activity embedding rules for the large screen device. */
public class SettingsApplication extends Application {

    private WeakReference<SettingsHomepageActivity> mHomeActivity = new WeakReference<>(null);
    private BiometricsEnvironment mBiometricsEnvironment;

    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED)) {
                System.exit(0);
            }
        }
    };

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(base);
        FeatureFactory.setFactory(this, getFeatureFactory());
    }

    @Override
    public void onCreate() {
        super.onCreate();

        BackupRestoreStorageManager.getInstance(this)
                .add(
                        new BatterySettingsStorage(this),
                        LocaleNotificationDataManager.getSharedPreferencesStorage(this));

        // Add null checking to avoid test case failed.
        if (getApplicationContext() != null) {
            ElapsedTimeUtils.assignSuwFinishedTimeStamp(getApplicationContext());
        }

        TelephonyUtils.connectExtTelephonyService(getApplicationContext());

        // Set Spa environment.
        setSpaEnvironment();
        mBiometricsEnvironment = new BiometricsEnvironment(this);

        if (ActivityEmbeddingUtils.isSettingsSplitEnabled(this)
                && FeatureFlagUtils.isEnabled(this,
                        FeatureFlagUtils.SETTINGS_SUPPORT_LARGE_SCREEN)) {
            if (WizardManagerHelper.isUserSetupComplete(this)) {
                new ActivityEmbeddingRulesController(this).initRules();
            } else {
                new DeviceProvisionedObserver().registerContentObserver();
            }
        }

        registerReceiver(mBroadcastReceiver,
                new IntentFilter(TelephonyManager.ACTION_MULTI_SIM_CONFIG_CHANGED));

        registerActivityLifecycleCallbacks(new DeveloperOptionsActivityLifecycle());
    }

    @Override
    public void onTerminate() {
        BackupRestoreStorageManager.getInstance(this).removeAll();
        super.onTerminate();
    }

    @NonNull
    protected FeatureFactory getFeatureFactory() {
        return new FeatureFactoryImpl();
    }

    /**
     * Set the spa environment instance.
     * Override this function to set different spa environment for different Settings app.
     */
    protected void setSpaEnvironment() {
        SpaEnvironmentFactory.INSTANCE.reset(new SettingsSpaEnvironment(this));
    }

    public void setHomeActivity(SettingsHomepageActivity homeActivity) {
        mHomeActivity = new WeakReference<>(homeActivity);
    }

    public SettingsHomepageActivity getHomeActivity() {
        return mHomeActivity.get();
    }

    @Nullable
    public BiometricsEnvironment getBiometricEnvironment() {
        return mBiometricsEnvironment;
    }

    @Override
    public void onTrimMemory(int level) {
        super.onTrimMemory(level);
        AppIconCacheManager.getInstance().trimMemory(level);
    }

    private class DeviceProvisionedObserver extends ContentObserver {
        private final Uri mDeviceProvisionedUri = Settings.Secure.getUriFor(
                Settings.Secure.USER_SETUP_COMPLETE);

        DeviceProvisionedObserver() {
            super(null /* handler */);
        }

        @Override
        public void onChange(boolean selfChange, Uri uri, int flags) {
            if (!mDeviceProvisionedUri.equals(uri)) {
                return;
            }

            SettingsApplication.this.getContentResolver().unregisterContentObserver(this);
            new ActivityEmbeddingRulesController(SettingsApplication.this).initRules();
        }

        public void registerContentObserver() {
            SettingsApplication.this.getContentResolver().registerContentObserver(
                    mDeviceProvisionedUri,
                    false /* notifyForDescendants */,
                    this);
        }
    }
}
