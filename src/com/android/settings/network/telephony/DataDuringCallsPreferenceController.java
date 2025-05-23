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
 * limitations under the License
 */

package com.android.settings.network.telephony;

import static androidx.lifecycle.Lifecycle.Event.ON_PAUSE;
import static androidx.lifecycle.Lifecycle.Event.ON_RESUME;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SubscriptionManager;
import android.telephony.TelephonyManager;
import android.util.Log;

import androidx.lifecycle.LifecycleObserver;
import androidx.lifecycle.OnLifecycleEvent;
import androidx.preference.Preference;
import androidx.preference.PreferenceScreen;
import androidx.preference.SwitchPreferenceCompat;

import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.telephony.TelephonyIntents;
import com.android.settings.datausage.DataUsageUtils;
import com.android.settings.network.MobileDataContentObserver;
import com.android.settings.network.SubscriptionsChangeListener;

public class DataDuringCallsPreferenceController extends TelephonyTogglePreferenceController
        implements LifecycleObserver,
        SubscriptionsChangeListener.SubscriptionsChangeListenerClient {
    private static final String TAG = "DataDuringCallsPreferenceController";

    private SwitchPreferenceCompat mPreference;
    private SubscriptionsChangeListener mChangeListener;
    private TelephonyManager mManager;
    private MobileDataContentObserver mMobileDataContentObserver;
    private PreferenceScreen mScreen;

    private final BroadcastReceiver mDefaultDataChangedReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(TelephonyManager.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED)) {
                Log.d(TAG, "ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED");
                refreshPreference();
            }
        }
    };

    public DataDuringCallsPreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
    }

    void init(int subId) {
        this.mSubId = subId;
        mManager = mContext.getSystemService(TelephonyManager.class).createForSubscriptionId(subId);
    }

    @OnLifecycleEvent(ON_RESUME)
    public void onResume() {
        if (mChangeListener == null) {
            mChangeListener = new SubscriptionsChangeListener(mContext, this);
        }
        mChangeListener.start();
        if (mMobileDataContentObserver == null) {
            mMobileDataContentObserver = new MobileDataContentObserver(
                    new Handler(Looper.getMainLooper()));
            mMobileDataContentObserver.setOnMobileDataChangedListener(() -> refreshPreference());
        }
        mMobileDataContentObserver.register(mContext, mSubId);
        final int defaultDataSub = SubscriptionManager.getDefaultDataSubscriptionId();
        // Listen to mobile data status of DDS on non-DDS SUB
        if (defaultDataSub != mSubId) {
            mMobileDataContentObserver.register(mContext, defaultDataSub);
        }
        mContext.registerReceiver(mDefaultDataChangedReceiver,
                new IntentFilter(TelephonyIntents.ACTION_DEFAULT_DATA_SUBSCRIPTION_CHANGED));
    }

    @OnLifecycleEvent(ON_PAUSE)
    public void onPause() {
        if (mChangeListener != null) {
            mChangeListener.stop();
        }
        if (mMobileDataContentObserver != null) {
            mMobileDataContentObserver.unRegister(mContext);
        }
        mContext.unregisterReceiver(mDefaultDataChangedReceiver);
    }

    @Override
    public void displayPreference(PreferenceScreen screen) {
        super.displayPreference(screen);
        mPreference = screen.findPreference(getPreferenceKey());
        mScreen = screen;
    }

    @Override
    public boolean isChecked() {
        if (mManager == null) {
            return false;
        }
        return mManager.isMobileDataPolicyEnabled(
                TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL);
    }

    @Override
    public boolean setChecked(boolean isChecked) {
        mManager.setMobileDataPolicyEnabled(
                TelephonyManager.MOBILE_DATA_POLICY_DATA_ON_NON_DEFAULT_DURING_VOICE_CALL,
                isChecked);
        return true;
    }

    @VisibleForTesting
    protected boolean hasMobileData() {
        return DataUsageUtils.hasMobileData(mContext);
    }

    @Override
    public int getAvailabilityStatus(int subId) {
        if (!SubscriptionManager.isValidSubscriptionId(subId)
                || SubscriptionManager.getDefaultDataSubscriptionId() == subId
                || (!hasMobileData())) {
            return CONDITIONALLY_UNAVAILABLE;
        }
        if (mManager == null) {
            return CONDITIONALLY_UNAVAILABLE;
        }

        boolean isDefDataEnabled = mManager.createForSubscriptionId(
                SubscriptionManager.getDefaultDataSubscriptionId()).isDataEnabled();
        // Do not show 'Data during calls' preference when mobile data switch
        // for the DDS sub is turned off.
        if (!isDefDataEnabled) {
            return CONDITIONALLY_UNAVAILABLE;
        }

        if (TelephonyUtils.isSubsidyFeatureEnabled(mContext) &&
                !TelephonyUtils.isSubsidySimCard(mContext,
                SubscriptionManager.getSlotIndex(mSubId))) {
            return CONDITIONALLY_UNAVAILABLE;
        }

        if (!TelephonyUtils.isSmartDdsSwitchFeatureAvailable()) {
            Log.d(TAG, "Smart DDS switch feature is not available");
            return CONDITIONALLY_UNAVAILABLE;
        }

        return AVAILABLE;
    }

    @Override
    public void updateState(Preference preference) {
        super.updateState(preference);
        if (preference == null) {
            return;
        }
        preference.setVisible(isAvailable());
    }

    @Override
    public void onAirplaneModeChanged(boolean airplaneModeEnabled) {}

    @Override
    public void onSubscriptionsChanged() {
        updateState(mPreference);
    }

    /**
     * Trigger displaying preference when Mobilde data content changed.
     */
    @VisibleForTesting
    public void refreshPreference() {
        if (mScreen != null) {
            super.displayPreference(mScreen);
        }
    }
}
