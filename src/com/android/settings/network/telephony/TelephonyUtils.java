/**
 * Copyright (c) 2020, The Linux Foundation. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *     * Redistributions of source code must retain the above copyright
 *       notice, this list of conditions and the following disclaimer.
 *     * Redistributions in binary form must reproduce the above
 *       copyright notice, this list of conditions and the following
 *       disclaimer in the documentation and/or other materials provided
 *       with the distribution.
 *     * Neither the name of The Linux Foundation nor the names of its
 *       contributors may be used to endorse or promote products derived
 *       from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED "AS IS" AND ANY EXPRESS OR IMPLIED
 * WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT
 * ARE DISCLAIMED.  IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS
 * BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR
 * BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE
 * OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN
 * IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 *
 */

package com.android.settings.network.telephony;

import android.content.Context;
import android.os.RemoteException;
import android.os.ServiceManager;
import android.provider.Settings;
import android.telephony.TelephonyManager;
import android.telephony.UiccSlotInfo;
import android.text.TextUtils;
import android.util.Log;

import com.qti.extphone.ExtTelephonyManager;
import com.qti.extphone.QtiImeiInfo;
import com.qti.extphone.ServiceCallback;

import org.codeaurora.internal.IExtTelephony;
import java.util.Optional;

/**
 * Add static utility functions to get information about Primary Card and Subsidy Lock features.
 */
public final class TelephonyUtils {

    private static final String TAG = "TelephonyUtils";

    private static int sDsdsToSsConfigStatus = -1;
    private static UiccSlotInfo[] sSlotsInfo;

    // Flag to control debug logging for primary card and subsidy lock features
    public static boolean DBG = Log.isLoggable(TAG, Log.DEBUG);

    private static final String PROPERTY_ADVANCED_SCAN  = "persist.vendor.radio.enableadvancedscan";
    private static final String PROPERTY_DSDS_TO_SS = "persist.vendor.radio.dsds_to_ss";
    private static final String PROPERTY_SUBSIDY_DEVICE  = "persist.vendor.radio.subsidydevice";
    private static final String ALLOW_USER_SELECT_DDS = "allow_user_select_dds";

    // Modem version prefix tag
    private static final String MODEM_VERSION_PREFIX_HI_TAG = "MPSS.HI."; // Himalaya
    private static final String MODEM_VERSION_PREFIX_DE_TAG = "MPSS.DE."; // Denali

    // UICC provisioning status
    public static final int CARD_NOT_PROVISIONED = 0;
    public static final int CARD_PROVISIONED = 1;
    public static final int CARD_INVALID_STATE = -1;

    private static ExtTelephonyManager mExtTelephonyManager;
    private static boolean mIsServiceBound;
    private static boolean mIsSmartDdsSwitchFeatureAvailable = true; // default to true
    private static Optional<Boolean> mIsSubsidyFeatureEnabled = Optional.empty();

    private TelephonyUtils() {
    }

    private static UiccSlotInfo[] getUiccSlotsInfo(Context context) {
        UiccSlotInfo[] slotsInfo = null;
        TelephonyManager telephonyManager =
                (TelephonyManager) context.getSystemService(Context.TELEPHONY_SERVICE);

        if (telephonyManager != null) {
            slotsInfo = telephonyManager.getUiccSlotsInfo();
        }
        return slotsInfo;
    }

    public static int getUiccSlotsCount(Context context){
        if (sSlotsInfo == null) {
            sSlotsInfo = getUiccSlotsInfo(context);
        }
        return sSlotsInfo.length;
    }

    public static boolean isDsdsToSsConfigValid(){
        if (sSlotsInfo == null) {
            return false;
        }
        return sDsdsToSsConfigStatus == 1 && sSlotsInfo.length > 1 && sSlotsInfo[1] != null;
    }

    /**
     * Querying the DSDS to SSSS configuration status.
     *
     * If sDsdsToSsConfigStatus is 1, it means the dsds_to_ss property is enabled.
     * If sDsdsToSsConfigStatus is 0, it means the dsds_to_ss property is not enabled.
     */
    private static void queryDsdsToSsConfig() {
        if (sDsdsToSsConfigStatus == -1) {
            sDsdsToSsConfigStatus = mExtTelephonyManager.
                    getPropertyValueInt(PROPERTY_DSDS_TO_SS, 0);
        }
        Log.d(TAG, "queryDsdsToSsConfig value = " + sDsdsToSsConfigStatus);
    }

    public static boolean isAdvancedPlmnScanSupported(Context context) {
        boolean propVal = false;
        if (mIsServiceBound) {
            try {
                propVal = mExtTelephonyManager.getPropertyValueBool(PROPERTY_ADVANCED_SCAN, false);
            } catch (NullPointerException ex) {
                Log.e(TAG, "isAdvancedPlmnScanSupported: , Exception: ", ex);
            }
        } else {
            Log.e(TAG, "isAdvancedPlmnScanSupported: ExtTelephony Service not connected!");
        }
        return propVal;
    }

    public static boolean performIncrementalScan(Context context, int slotId) {
        boolean success = false;
        if (mIsServiceBound) {
            success = mExtTelephonyManager.performIncrementalScan(slotId);
        } else {
            Log.e(TAG, "performIncrementalScan: ExtTelephony Service not connected!");
        }
        return success;
    }

    public static void abortIncrementalScan(Context context, int slotId) {
        if (mIsServiceBound) {
            mExtTelephonyManager.abortIncrementalScan(slotId);
        } else {
            Log.e(TAG, "abortIncrementalScan: ExtTelephony Service not connected!");
        }
    }

    /*
     * As many products come from different modem version, it is hard to maintain one
     * carrier config along with vendor product SKU. But MPSS version code is stable
     * very much, it is a good way rather than config's approach.
     */
    public static boolean isDual5gSupported(TelephonyManager telephonyManager) {
        if (telephonyManager == null) {
            Log.e(TAG, "telephonyManager is null");
            return false;
        }
        final String version = telephonyManager.getBasebandVersion();
        Log.d(TAG, "Base band version = " + version);
        if (!TextUtils.isEmpty(version)) {
            String[] tokens = version.split("-");
            if (tokens != null) {
                for (String token : tokens) {
                    if (token != null && token.startsWith(MODEM_VERSION_PREFIX_HI_TAG)) {
                        String verCode =
                                token.substring(MODEM_VERSION_PREFIX_HI_TAG.length(),
                                token.length());
                        Log.d(TAG, "verCode = " + verCode);
                        if (verCode != null && verCode.length() > 2) {
                            String[] subCode = verCode.split("\\.");
                            try {
                                int major = Integer.parseInt(subCode[0]);
                                int minor = Integer.parseInt(subCode[1]);
                                Log.d(TAG, "Ver major = " + major + " minor = " + minor);
                                if (major >= 4 && minor >= 3) {
                                    return true;
                                }
                            } catch (NumberFormatException e) {
                                Log.e(TAG, "Fail to parse version");
                                return false;
                            }
                        }
                    } else if (token != null && token.startsWith(MODEM_VERSION_PREFIX_DE_TAG)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    public static boolean isSubsidyFeatureEnabled(Context context) {
        if (!mIsSubsidyFeatureEnabled.isPresent()) {
            if (!mIsServiceBound) {
                Log.e(TAG, "isSubsidyFeatureEnabled: ExtTelephony Service not connected!");
                connectExtTelephonyService(context);
            }

            try {
                mIsSubsidyFeatureEnabled =
                        Optional.of(mExtTelephonyManager.getPropertyValueBool(
                        PROPERTY_SUBSIDY_DEVICE, false));
            } catch (NullPointerException ex) {
                Log.e(TAG, "isSubsidyFeatureEnabled: , Exception: ", ex);
            }
        }
        return mIsSubsidyFeatureEnabled.get();
    }

    public static boolean allowUsertoSetDDS(Context context) {
        return Settings.Global.getInt(context.getContentResolver(), ALLOW_USER_SELECT_DDS, 0) == 1;
    }

    public static boolean isSubsidySimCard(Context context, int slotId) {
        boolean isSubsidySim = false;
        if (!mIsServiceBound) {
            Log.e(TAG, "isSubsidySimCard: ExtTelephony Service not connected!");
            connectExtTelephonyService(context);
        }

        try {
            isSubsidySim = mExtTelephonyManager.isPrimaryCarrierSlotId(slotId);
        } catch (NullPointerException ex) {
            Log.e(TAG, "isSubsidySimCard: , Exception: ", ex);
        }
        return isSubsidySim;
    }

    public static QtiImeiInfo[] getImeiInfo() {
        QtiImeiInfo[] qtiImeiInfo = null;
        if (isServiceConnected()) {
            qtiImeiInfo = mExtTelephonyManager.getImeiInfo();
        } else {
            Log.e(TAG, "getImeiInfo: ExtTelephony Service not connected!");
        }
        return qtiImeiInfo;
    }

    public static boolean isSmartDdsSwitchFeatureAvailable() {
        return mIsSmartDdsSwitchFeatureAvailable;
    }

    public static void connectExtTelephonyService(Context context) {
        sSlotsInfo = getUiccSlotsInfo(context);
        if (!mIsServiceBound) {
            Log.d(TAG, "Connect to ExtTelephonyService...");
            mExtTelephonyManager = ExtTelephonyManager.getInstance(context);
            mExtTelephonyManager.connectService(mServiceCallback);
        }
    }

    public static boolean isServiceConnected() {
        return mIsServiceBound;
    }

    private static ServiceCallback mServiceCallback = new ServiceCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "ExtTelephony Service connected");
            mIsServiceBound = true;
            try {
                queryDsdsToSsConfig();
                mIsSmartDdsSwitchFeatureAvailable =
                        mExtTelephonyManager.isSmartDdsSwitchFeatureAvailable();
                Log.d(TAG, "isSmartDdsSwitchFeatureAvailable: " +
                        mIsSmartDdsSwitchFeatureAvailable);
            } catch (RemoteException ex) {
                Log.e(TAG, "isSmartDdsSwitchFeatureAvailable exception " + ex);
            }
        }

        @Override
        public void onDisconnected() {
            Log.d(TAG, "ExtTelephony Service disconnected...");
            mIsServiceBound = false;
        }
    };
}
