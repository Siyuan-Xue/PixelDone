package com.milesxue.pixeldone.widget;

import androidx.datastore.preferences.core.Preferences;
import androidx.datastore.preferences.core.PreferencesKeys;

/**
 * Java bridge for DataStore's file-facade key factory.
 *
 * <p>The Android artifact exposes {@code PreferencesKeys} to Java while its Kotlin package
 * metadata is not present on this project's compile classpath. Keeping the bridge here avoids
 * reflection and still constructs the exact official DataStore key type used by Glance.</p>
 */
final class PixelDonePreferenceKeys {
    private PixelDonePreferenceKeys() {}

    static Preferences.Key<String> stringKey(String name) {
        return PreferencesKeys.stringKey(name);
    }
}
