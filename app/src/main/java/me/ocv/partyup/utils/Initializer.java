package me.ocv.partyup.utils;

import android.content.Context;

import me.ocv.partyup.room.database.AppDatabase;

public final class Initializer {
    private Initializer() {}

    public static void init(Context context) {
        AppDatabase.init(context);
    }
}
