package me.ocv.partyup.utils;

import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

public final class ToastUtils {
    private static final String TAG = "ToastUtils";
    private static final Handler handler = new Handler(Looper.getMainLooper());

    public static synchronized void show(Context context, String msg) {
        if (context instanceof Activity) {
            Activity activity = (Activity) context;
            if (activity.isFinishing() || activity.isDestroyed()) {
                Log.i(TAG, "Context is finishing or destroyed: " + context);
                return;
            }
        }

        handler.post(() -> Toast.makeText(context.getApplicationContext(), msg, Toast.LENGTH_SHORT).show());
    }

    private ToastUtils() {
    }
}
