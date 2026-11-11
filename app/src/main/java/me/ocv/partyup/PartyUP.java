package me.ocv.partyup;

import android.app.Application;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.preference.PreferenceManager;
import androidx.work.Configuration;

import me.ocv.partyup.objects.PrefsKey;
import me.ocv.partyup.room.database.AppDatabase;
import me.ocv.partyup.utils.SoundUtils;

public class PartyUP extends Application implements Configuration.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        AppCompatDelegate.setDefaultNightMode(preferences.getBoolean(PrefsKey.DARK_MODE, false)
                ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_NO);
        SoundUtils.init(this);
        SoundUtils.toggleShut(preferences.getBoolean(PrefsKey.SHUT_UP, true));
        AppDatabase.init(this);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setMinimumLoggingLevel(Log.ASSERT).build();
    }
}
