package me.ocv.partyup.utils;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.List;

public final class PermissionUtils {
    public static final int ALL_PERMISSIONS_REQUEST_CODE = 100;

    public static boolean hasAllPermissions(Activity activity) {
        String[] permissions = getManifestPermissions(activity);
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private static String[] getManifestPermissions(
            @NonNull Activity activity
    ) {
        try {
            PackageInfo info = activity.getPackageManager()
                    .getPackageInfo(
                            activity.getPackageName(),
                            PackageManager.GET_PERMISSIONS
                    );
            if (info.requestedPermissions != null) {
                return info.requestedPermissions;
            }
        } catch (PackageManager.NameNotFoundException e) {
            Log.e("PERMISSION", "Unable to find permissions");
        }
        return new String[0];
    }

    public static void requestAllPermissions(Activity activity) {
        String[] permissions = getManifestPermissions(activity);
        List<String> permissionsToRequest = new ArrayList<>();
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(activity, permission) !=
                    PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(permission);
            }
        }

        if (!permissionsToRequest.isEmpty()) {
            ActivityCompat.requestPermissions(
                    activity,
                    permissionsToRequest.toArray(new String[0]),
                    ALL_PERMISSIONS_REQUEST_CODE
            );
        }
    }

    private PermissionUtils() {}
}
