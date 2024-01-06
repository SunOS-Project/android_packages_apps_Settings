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

package com.android.settings.deviceinfo.firmwareversion;

import android.content.Context;
import android.os.Build;
import android.text.BidiFormatter;
import android.text.TextUtils;

import com.android.settings.core.BasePreferenceController;

import org.sun.settings.deviceinfo.VersionUtils;

public class SimpleBuildNumberPreferenceController extends BasePreferenceController {

    public SimpleBuildNumberPreferenceController(Context context,
            String preferenceKey) {
        super(context, preferenceKey);
    }

    @Override
    public int getAvailabilityStatus() {
        return AVAILABLE_UNSEARCHABLE;
    }

    @Override
    public CharSequence getSummary() {
        final StringBuilder sb = new StringBuilder();
        sb.append(BidiFormatter.getInstance().unicodeWrap(Build.DISPLAY));
        final String sunVersion = VersionUtils.getSunVersion();
        if (!TextUtils.isEmpty(sunVersion)) {
            sb.append("\n");
            sb.append(sunVersion);
        }
        return sb.toString();
    }
}
