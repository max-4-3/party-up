package me.ocv.partyup.utils;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.util.Consumer;

import java.io.File;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

import me.ocv.partyup.objects.CustomFile;

@SuppressWarnings("deprecation")
public final class Analyzer {
    private static final String TAG = "Analyzer";
    private static final int BUFFER_SIZE = 128 * 1024; // 128 KB

    private final Context context;

    private Analyzer(@NonNull Context context) {
        this.context = context.getApplicationContext();
    }

    @NonNull
    private List<CustomFile> analyze_(@NonNull Intent intent, @NonNull Consumer<String> onError) {
        List<CustomFile> files = new ArrayList<>();

        String action = intent.getAction();
        String eType = intent.getType();
        boolean isSingleSend = Intent.ACTION_SEND.equals(action);
        boolean isMultipleSend = Intent.ACTION_SEND_MULTIPLE.equals(action);

        if (eType == null || (!isSingleSend && !isMultipleSend)) {
            onError.accept("Cannot share content; action: " + action + ", type: " + eType);
            return files;
        }

        List<Uri> handles = new ArrayList<>();

        if (isMultipleSend) {
            Collection<Uri> extraStreams = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM);
            if (extraStreams != null) {
                handles.addAll(extraStreams);
            }
        } else {
            Uri singleUri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (singleUri != null) {
                handles.add(singleUri);
            } else {
                String textMessage = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (textMessage != null) {
                    CustomFile cf = new CustomFile();
                    cf.content = textMessage;
                    cf.mime = "text/plain";
                    cf.size = textMessage.length();
                    files.add(cf);
                    return files;
                }
            }
        }

        if (handles.isEmpty()) {
            onError.accept("Cannot decide on what to send for type: " + eType);
            return files;
        }

        for (Uri uriRaw : handles) {
            Uri uriSharable = giveSendableUri(context, uriRaw);
            if (uriRaw == null || uriSharable == null) {
                continue;
            }
            // Use original for name, size, etc.
            // and cached (or not) for actual upload

            CustomFile cf = new CustomFile();
            cf.handle = uriRaw;
            cf.mime = eType;

            parseFile(cf, onError);

            if (cf.size != 0) {
                files.add(cf);
                cf.handle = uriSharable;
            }
        }

        return files;
    }

    @Nullable
    private Uri giveSendableUri(Context context, Uri uri) {
        Uri result = uri;
        long fileSize = FileCache.getFileSize(context, uri);
        long maxCacheLimit = 100L * 1024 * 1024; // 100 MB

        if (fileSize > maxCacheLimit) {
            // Return the original uri... and hope that it'll survive...
            Log.i("Analyzer", "file too big not gonna copy to cache!");
            return result;
        }

        try {
            context.getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            Log.d("Analyzer", "Persistable permission secured for URI: " + uri);
        } catch (SecurityException e) {
            Log.w("Analyzer", "Could not take persistable permission (copying to cache): " + e.getMessage());
            File cacheFile = FileCache.copyUriToCache(context, uri);
            if (cacheFile != null) {
                result = Uri.fromFile(cacheFile);
            } else {
                Log.i("Analyzer", "copy to cache failed, passing uri as is!");
            }
        }

        return result;
    }

    private void parseFile(@NonNull CustomFile cf, @NonNull Consumer<String> onError) {
        cf.ext = FileCache.getExtensionFromMime(cf.mime);
        cf.name = FileCache.getOriginalFileName(context, cf.handle);
        cf.size = FileCache.getFileSize(context, cf.handle);

        MessageDigest md = null;
        if (cf.name == null) {
            try {
                md = MessageDigest.getInstance("SHA-512");
            } catch (Exception e) {
                Log.e(TAG, "Unable to get MessageDigest instance", e);
            }
        }

        // Stream and read file data securely
        try (InputStream ins = context.getContentResolver().openInputStream(cf.handle)) {
            if (ins == null) {
                onError.accept("Failed to open input stream for URI: " + cf.handle);
                return;
            }

            byte[] buf = new byte[BUFFER_SIZE];
            long totalBytes = 0;
            int n;

            while ((n = ins.read(buf)) != -1) {
                if (n > 0) {
                    totalBytes += n;
                    if (md != null) {
                        md.update(buf, 0, n);
                    }
                }
            }
            cf.size = totalBytes;
        } catch (Exception ex) {
            Log.w(TAG, "Storage access error or permission missing: " + ex);
            onError.accept("StoragePermissionNeeded: " + ex.getMessage());
            return;
        }

        if (md != null) {
            String checksum = Base64.encodeToString(md.digest(), Base64.URL_SAFE | Base64.NO_PADDING | Base64.NO_WRAP).substring(0, 15);
            cf.name = String.format(Locale.getDefault(), "mystery-file-%s.%s", checksum, cf.ext);
        }

        cf.desc = String.format(Locale.getDefault(), "%s\n\nsize: %,d bytes\ntype: %s", cf.name, cf.size, cf.mime);
    }

    @NonNull
    public static List<CustomFile> analyze(@NonNull Context context, @NonNull Intent intent) {
        return new Analyzer(context).analyze_(intent, err -> Log.e(TAG, String.format("ERROR: error parsing intent: %s", err)));
    }
}
