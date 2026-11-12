package me.ocv.partyup.utils;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.UUID;

public final class FileCache {
    private static final String TAG = "FileCache";
    private static final long CONCERNING_CACHE_SIZE = 1024L * 1024 * 1024; // 1 GiB

    @NonNull
    @Contract("_ -> new")
    private static File getCacheDir(@NonNull Context context) {
        return new File(context.getCacheDir(), "upload_cache");
    }

    private static long getDirectorySize(@NonNull File directory) {
        if (!directory.isDirectory() || !directory.exists()) {
            return directory.isFile() ? directory.length() : 0;
        }

        long size = 0;
        File[] files = directory.listFiles();

        if (files != null) {
            for (File file : files) {
                size += getDirectorySize(file);
            }
        }

        return size;
    }

    public static void checkCacheSizeAndNotify(Context context) {
        long size = getDirectorySize(getCacheDir(context));

        if (size >= CONCERNING_CACHE_SIZE) {
            ToastUtils.show(context, String.format("[%s] Clean cache!!", NumberUtils.formatBytes(size)));
        }
    }

    @NonNull
    public static String getExtensionFromMime(@Nullable String mime) {
        if (mime == null) {
            return "bin";
        }

        mime = mime.replace(';', ' ').split(" ")[0].trim().toLowerCase();

        switch (mime) {
            case "audio/ogg":
                return "ogg";
            case "audio/mpeg":
                return "mp3";
            case "audio/mp4":
                return "m4a";
            case "image/jpeg":
                return "jpg";
            case "application/pdf":
                return "pdf";
        }

        if (mime.startsWith("text/")) {
            return "txt";
        }

        if (mime.contains("/")) {
            String subType = mime.split("/")[1];
            if (subType.matches("^[a-zA-Z0-9]{1,8}$")) {
                return subType;
            }
        }

        return "bin";
    }

    public static long getFileSize(@NonNull Context context, @NonNull Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                File file = new File(path);
                if (file.exists()) {
                    return file.length();
                }
            }
            return -1;
        }

        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.SIZE}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                    return cursor.getLong(sizeIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not query file size for URI: " + uri, e);
        }

        return -1; // Unknown size
    }

    public static boolean isCacheFile(@NonNull Context context, @NonNull Uri uri) {
        if (!"file".equals(uri.getScheme())) {
            return false;
        }

        String path = uri.getPath();
        if (path == null) {
            return false;
        }

        try {
            File file = new File(path);
            File cacheDir = context.getCacheDir();

            // Use canonical paths to resolve any relative path tricks or symlinks
            String canonicalCachePath = cacheDir.getCanonicalPath();
            String canonicalFilePath = file.getCanonicalPath();

            return canonicalFilePath.startsWith(canonicalCachePath);
        } catch (Exception e) {
            return false;
        }
    }

    @Nullable
    public static String getOriginalFileName(@NonNull Context context, @NonNull Uri uri) {
        if ("file".equals(uri.getScheme())) {
            String path = uri.getPath();
            if (path != null) {
                return new File(path).getName();
            }
            return null;
        }

        try (Cursor cursor = context.getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameIndex != -1) {
                    return cursor.getString(nameIndex);
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "Could not resolve display name for URI: " + uri, e);
        }
        return null;
    }

    @Nullable
    public static File copyUriToCache(@NonNull Context context, @NonNull Uri uri) {
        File cacheDir = getCacheDir(context);
        if (!cacheDir.exists() && !cacheDir.mkdirs()) {
            Log.e(TAG, "Failed to create local cache directory.");
            return null;
        }

        // Give the cached file a unique name to prevent collisions
        File cachedFile = new File(cacheDir, "upload_" + UUID.randomUUID() + ".tmp");

        try (InputStream inputStream = context.getContentResolver().openInputStream(uri);
             OutputStream outputStream = new FileOutputStream(cachedFile)) {

            if (inputStream == null) {
                return null;
            }

            byte[] buffer = new byte[64 * 1024]; // 64 KB buffer
            int bytesRead;
            while ((bytesRead = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, bytesRead);
            }
            outputStream.flush();

            return cachedFile;
        } catch (Exception e) {
            Log.e(TAG, "Error copying URI to local cache: " + e.getMessage(), e);
            if (cachedFile.exists()) {
                if (!cachedFile.delete()) {
                    Log.w(TAG, "Orphaned cache file: " + cachedFile.getName());
                }
            }
            return null;
        }
    }

    private FileCache() {}
}
