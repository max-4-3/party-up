package me.ocv.partyup;

import android.app.Application;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.work.Configuration;

import me.ocv.partyup.utils.FileCache;
import me.ocv.partyup.utils.Initializer;

public final class PartyUP extends Application implements Configuration.Provider {
    @Override
    public void onCreate() {
        super.onCreate();
        Initializer.init(this);
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
        FileCache.checkCacheSizeAndNotify(this);
    }

    @NonNull
    @Override
    public Configuration getWorkManagerConfiguration() {
        return new Configuration.Builder().setMinimumLoggingLevel(Log.ASSERT).build();
    }
}
