package me.ocv.partyup;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.HashSet;
import java.util.Set;

public final class Config {
    public enum Keys {
        AUTOSEND("auto_send"), LINK_EXPIRATION("link_expiration"), SHARE_PASSWORD("share_password"), SERVER_URL("server_url"), SERVER_PASSWORD("server_password");

        private final String value;

        Keys(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    @NonNull
    public synchronized static String string(@NonNull Keys key) {
        return preferences.getString(key.value(), "");
    }

    @NonNull
    public synchronized static Set<String> strings(@NonNull Keys key) {
        Set<String> value = new HashSet<>();
        Set<String> fromPref = preferences.getStringSet(key.value(), value);
        if (fromPref != value) {
            value.addAll(fromPref);
        }

        return value;
    }

    public synchronized static boolean bool(@NonNull Keys key) {
        return preferences.getBoolean(key.value(), false);
    }

    public synchronized static void string(@NonNull Keys key, String value) {
        if (value != null && !value.isEmpty()) {
            preferences.edit().putString(key.value(), value).apply();
        }
    }

    public synchronized static void strings(@NonNull Keys key, Iterable<String> values) {
        if (values != null && values.iterator().hasNext()) {
            Set<String> toSave = new HashSet<>();
            values.forEach(toSave::add);

            preferences.edit().putStringSet(key.value(), toSave).apply();
        }
    }

    public synchronized static void bool(@NonNull Keys key, boolean value) {
        preferences.edit().putBoolean(key.value(), value).apply();
    }

    private static SharedPreferences preferences;

    public static void init(@NonNull Context context) {
        if (preferences == null) {
            preferences = PreferenceManager.getDefaultSharedPreferences(context.getApplicationContext());
        }
    }

    private Config() {}
}
