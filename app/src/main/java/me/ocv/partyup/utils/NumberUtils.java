package me.ocv.partyup.utils;

import androidx.annotation.NonNull;

import java.util.Locale;

public final class NumberUtils {
    public static final String[] UNITS = { "KiB", "MiB", "GiB", "TiB", "PiB" };

    @NonNull
    public static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }

        double value = bytes;
        int unit = -1;

        do {
            value /= 1024;
            unit++;
        } while (value >= 1024 && unit < UNITS.length - 1);

        return String.format(Locale.getDefault(), "%.2f %s", value, UNITS[unit]);
    }

    private NumberUtils() {}
}
