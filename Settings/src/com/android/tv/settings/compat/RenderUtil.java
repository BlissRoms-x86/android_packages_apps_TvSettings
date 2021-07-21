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

package com.android.tv.settings.compat;

import static com.android.tv.settings.compat.TsCollapsibleCategory.COLLAPSE;
import static com.android.tv.settings.library.ManagerUtil.INFO_NEXT_STATE;
import static com.android.tv.settings.library.ManagerUtil.INFO_WIFI_SIGNAL_LEVEL;
import static com.android.tv.settings.library.ManagerUtil.STATE_APP_MANAGEMENT;
import static com.android.tv.settings.library.ManagerUtil.STATE_WIFI_DETAILS;
import static com.android.tv.settings.library.PreferenceCompat.TYPE_LIST;
import static com.android.tv.settings.library.PreferenceCompat.TYPE_PREFERENCE_ACCESS_POINT;
import static com.android.tv.settings.library.PreferenceCompat.TYPE_PREFERENCE_CATEGORY;
import static com.android.tv.settings.library.PreferenceCompat.TYPE_PREFERENCE_WIFI_COLLAPSE_CATEGORY;
import static com.android.tv.settings.library.PreferenceCompat.TYPE_RPEFERENCE;

import android.content.Context;

import androidx.preference.Preference;
import androidx.preference.PreferenceGroup;
import androidx.preference.TwoStatePreference;

import com.android.tv.settings.R;
import com.android.tv.settings.library.PreferenceCompat;

import java.util.Arrays;
import java.util.List;
import java.util.OptionalInt;
import java.util.stream.IntStream;

/** Provide utility methods for rendering PreferenceFragment. */
public final class RenderUtil {

    private RenderUtil() {
    }

    public static void updatePreferenceGroup(
            PreferenceGroup preferenceGroup, List<PreferenceCompat> newPrefParcelables) {
        if (preferenceGroup == null || newPrefParcelables == null) {
            return;
        }
        Context context = preferenceGroup.getContext();
        // Remove old preferences that do not exist in the new list
        int index = 0;
        while (index < getPreferenceCount(preferenceGroup)) {
            if (!(preferenceGroup.getPreference(index) instanceof HasKeys)) {
                return;
            }
            HasKeys pref = (HasKeys) preferenceGroup.getPreference(index);
            boolean match =
                    newPrefParcelables.stream().anyMatch(
                            prefParcelable -> keyMatch(pref, prefParcelable));
            if (!match) {
                preferenceGroup.removePreference((Preference) pref);
            } else {
                index++;
            }
        }

        // Add or update preferences following the order in the new list.
        IntStream.range(0, newPrefParcelables.size())
                .forEach(
                        i -> {
                            PreferenceCompat preferenceCompat = newPrefParcelables.get(i);
                            OptionalInt matchedIndex =
                                    IntStream.range(0, getPreferenceCount(preferenceGroup))
                                            .filter(
                                                    j -> keyMatch(
                                                            (HasKeys) preferenceGroup.getPreference(
                                                                    j), preferenceCompat))
                                            .findFirst();
                            HasKeys newPref;
                            if (matchedIndex.isPresent()) {
                                newPref = (HasKeys) preferenceGroup.getPreference(
                                        matchedIndex.getAsInt());
                            } else {
                                newPref = createPreference(context, preferenceCompat);
                                preferenceGroup.addPreference((Preference) newPref);
                            }
                            updatePreference(context, newPref, preferenceCompat, i);
                        });
    }

    private static int getPreferenceCount(PreferenceGroup preferenceGroup) {
        return (preferenceGroup instanceof TsCollapsibleCategory)
                ? ((TsCollapsibleCategory) preferenceGroup).getRealPreferenceCount()
                : preferenceGroup.getPreferenceCount();
    }

    static boolean keyMatch(HasKeys preference, PreferenceCompat preferenceCompat) {
        return Arrays.equals(preference.getKeys(), preferenceCompat.getKey());
    }

    public static void updatePreference(
            Context context,
            HasKeys hasKeysPreference,
            PreferenceCompat preferenceCompat,
            int order) {
        switch (preferenceCompat.getType()) {
            case TYPE_PREFERENCE_ACCESS_POINT:
                updateAccessPointPreference(
                        (TsAccessPointPreference) hasKeysPreference,
                        Integer.parseInt(preferenceCompat.getInfo(INFO_WIFI_SIGNAL_LEVEL)),
                        context);
                break;
            case TYPE_PREFERENCE_WIFI_COLLAPSE_CATEGORY:
                ((TsCollapsibleCategory) hasKeysPreference)
                        .setCollapsed(getInfoBoolean(COLLAPSE, preferenceCompat));
                break;
            case TYPE_LIST:
                if (hasKeysPreference instanceof TsListPreference) {
                    TsListPreference pref = (TsListPreference) hasKeysPreference;
                    pref.setValue(preferenceCompat.getValue());

                }
            default:
                // no-op
        }

        String[] keys = preferenceCompat.getKey();
        hasKeysPreference.setKeys(keys);
        Preference preference = (Preference) hasKeysPreference;
        preference.setKey(keys[keys.length - 1]);
        if (preferenceCompat.getTitle() != null) {
            preference.setTitle(preferenceCompat.getTitle());
        }
        if (preferenceCompat.getSummary() != null) {
            preference.setSummary(preferenceCompat.getSummary());
        }
        if (preferenceCompat.getIntent() != null) {
            preference.setIntent(preferenceCompat.getIntent());
        }
        if (preferenceCompat.getExtras() != null) {
            preference.getExtras().putAll(preferenceCompat.getExtras());
        }
        Integer nextState = RenderUtil.getInfoInt(INFO_NEXT_STATE, preferenceCompat);
        if (nextState != null) {
            preference.setFragment(getNextFragment(nextState));
        }
        setVisible(preference, preferenceCompat);
        setSelectable(preference, preferenceCompat);
        setEnabled(preference, preferenceCompat);
        if (preference instanceof TwoStatePreference) {
            setChecked((TwoStatePreference) preference, preferenceCompat);
        }
        preference.setOrder(order);
    }

    static HasKeys createPreference(Context context, PreferenceCompat preferenceCompat) {
        switch (preferenceCompat.getType()) {
            case TYPE_PREFERENCE_ACCESS_POINT:
                TsAccessPointPreference accessPointPreference =
                        new TsAccessPointPreference(context, preferenceCompat.getKey());
                return accessPointPreference;
            case TYPE_PREFERENCE_CATEGORY:
                return new TsPreferenceCategory(context, preferenceCompat.getKey());
            case TYPE_PREFERENCE_WIFI_COLLAPSE_CATEGORY:
                return new TsCollapsibleCategory(context, preferenceCompat.getKey());
            case TYPE_LIST:
                return new TsListPreference(context, preferenceCompat.getKey());
            case TYPE_RPEFERENCE:
            default:
                return new TsPreference(context, preferenceCompat.getKey());
        }
    }

    public static Boolean getInfoBoolean(String key, PreferenceCompat preferenceCompat) {
        String value = preferenceCompat.getInfo(key);
        return value == null ? null : Boolean.parseBoolean(value);
    }

    public static Integer getInfoInt(String key, PreferenceCompat preferenceCompat) {
        String value = preferenceCompat.getInfo(key);
        return value == null ? null : Integer.parseInt(value);
    }

    public static String getInfoString(String key, PreferenceCompat preferenceParcelable) {
        return preferenceParcelable.getInfo(key);
    }

    public static void setChecked(
            TwoStatePreference preference, PreferenceCompat preferenceParcelable) {
        if (preferenceParcelable.getChecked() == PreferenceCompat.STATUS_ON) {
            preference.setChecked(true);
        } else if (preferenceParcelable.getChecked() == PreferenceCompat.STATUS_OFF) {
            preference.setChecked(false);
        }
    }

    public static void setSelectable(Preference preference, PreferenceCompat preferenceParcelable) {
        if (preferenceParcelable.getSelectable() == PreferenceCompat.STATUS_ON) {
            preference.setVisible(true);
        } else if (preferenceParcelable.getVisible() == PreferenceCompat.STATUS_OFF) {
            preference.setVisible(false);
        }
    }

    public static void setVisible(Preference preference, PreferenceCompat preferenceParcelable) {
        if (preferenceParcelable.getVisible() == PreferenceCompat.STATUS_ON) {
            preference.setVisible(true);
        } else if (preferenceParcelable.getVisible() == PreferenceCompat.STATUS_OFF) {
            preference.setVisible(false);
        }
    }

    public static void setEnabled(Preference preference, PreferenceCompat preferenceParcelable) {
        if (preferenceParcelable.getEnabled() == PreferenceCompat.STATUS_ON) {
            preference.setEnabled(true);
        } else if (preferenceParcelable.getEnabled() == PreferenceCompat.STATUS_OFF) {
            preference.setEnabled(false);
        }
    }

    static void updateAccessPointPreference(
            TsAccessPointPreference preference, int level, Context context) {
        switch (level) {
            case 4:
                preference.setIcon(R.drawable.ic_wifi_signal_4_white);
                return;
            case 3:
                preference.setIcon(R.drawable.ic_wifi_signal_3_white);
                return;
            case 2:
                preference.setIcon(R.drawable.ic_wifi_signal_2_white);
                return;
            case 1:
                preference.setIcon(R.drawable.ic_wifi_signal_1_white);
                return;
            case 0:
                // fall through
            default:
                preference.setIcon(R.drawable.ic_wifi_signal_0_white);
                return;
        }
    }

    private static String getNextFragment(int nextState) {
        switch (nextState) {
            case STATE_WIFI_DETAILS:
                return "com.android.tv.settings.connectivity.WifiDetailsFragmentCompat";
            case STATE_APP_MANAGEMENT:
                return "com.android.tv.settings.device.apps.AppManagementFragmentCompat";
            default:
                return null;
        }
    }
}