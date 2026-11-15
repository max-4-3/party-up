package me.ocv.partyup.utils;

import android.content.Context;
import android.net.Uri;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;

import me.ocv.partyup.Config;
import me.ocv.partyup.objects.CustomFile;

public final class Uploader {
    public static final class BaseUploadProgress {
        public long done = 0;
        public long total = 0;
        public long delta = 0;
    }

    private static final String TAG = "Uploader";

    private final String serverUrl;
    private final String password;

    private final OnCompleteListener onComplete;
    private final OnErrorListener onError;
    private final OnProgressListener onProgress;

    private final Context context;
    private final int chunkSize;
    private StringBuilder serverResponse = new StringBuilder();

    private Uploader(@NonNull Builder builder) {
        this.serverUrl = builder.serverUrl;
        this.password = builder.serverPassword;
        this.context = builder.context;
        this.onError = builder.onError;
        this.onProgress = builder.onProgress;
        this.onComplete = builder.onComplete;
        this.chunkSize = builder.chunkSize;
    }

    public void uploadAndUpdateFile(CustomFile cf) throws Exception {
        HttpURLConnection conn = makeConnection(cf);
        cf.full_url = conn.getURL().toString();

        Log.d(TAG, String.format("Uploading started of file: %s", cf));
        boolean uploadSuccess;

        if (cf.mime.equals("text/plain")) {
            // Text (aka links) Upload POST
            uploadSuccess = this.uploadText(cf, conn);
        } else {
            // Files Upload PUT
            uploadSuccess = this.uploadFile(cf, conn);
        }

        Log.d(TAG, "informing complete");
        this.onComplete.run(serverResponse.toString());
        Log.i(TAG, String.format("Uploader result: %s %s", uploadSuccess, cf.share_url != null ? cf.share_url : "(empty)"));
        conn.disconnect();
    }

    private boolean uploadFile(@NonNull CustomFile cf, HttpURLConnection conn) throws Exception {
        if (!cf.isReadable()) {
            Log.e(TAG, String.format("Bad media file: %s", cf));
            conn.disconnect();
            return false;
        }
        Log.d(TAG, String.format("Identified '%s' as a file (%s) with size: %d", cf.name, cf.mime, cf.size));

        conn.setRequestMethod("PUT");
        conn.setRequestProperty("Content-Type", "application/octet-stream");
        conn.setChunkedStreamingMode(this.chunkSize);

        MessageDigest md = MessageDigest.getInstance("SHA-512");
        conn.connect();

        try (InputStream ins = this.context.getContentResolver().openInputStream(cf.handle);
             OutputStream os = conn.getOutputStream()) {
            if (ins == null) {
                throw new RuntimeException("Input stream is null!");
            }
            byte[] buf = new byte[this.chunkSize];

            BaseUploadProgress up = new BaseUploadProgress();
            up.total = cf.size;
            up.delta = this.chunkSize;
            up.done = 0;

            int n;
            while ((n = ins.read(buf, 0, buf.length)) != -1) {
                up.done += n;

                Log.d(TAG, String.format("read %d bytes now writing...", n));

                os.write(buf, 0, n);
                md.update(buf, 0, n);

                Log.d(TAG, String.format("[Progress] delta, total, done: %d, %d, %d", up.delta, up.total, up.done));
                Log.d(TAG, "informing listener");
                this.onProgress.run(up);
            }

            if (up.done != up.total) {
                Log.e(TAG, "size mismatch", new IOException("Size mismatch: expected=" + cf.size + " actual=" + up.done));
            }

            up.total = up.done;
            this.onProgress.run(up);
        }

        Log.d(TAG, "reading connection...");
        int rc = conn.getResponseCode();
        serverResponse = NetworkUtils.readConnection(conn);
        Log.d(TAG, "connection read!");

        if (rc >= 300) {
            String message = String.format(Locale.getDefault(), "[%d] Server error:\n%s", rc, serverResponse.toString());
            this.onError.run(new RuntimeException(message));
            conn.disconnect();
            return false;
        }

        return uploadSuccess(md, cf);
    }

    private boolean uploadSuccess(@NonNull MessageDigest md, CustomFile cf) {
        Log.d(TAG, "determining success");
        StringBuilder sha = new StringBuilder();
        byte[] bSha = md.digest();
        for (int a = 0; a < 28; a++) {
            sha.append(String.format("%02x", bSha[a]));
        }

        String[] lines = serverResponse.toString().split("\n");
        if (lines.length < 3) {
            this.onError.run(new RuntimeException("SERVER ERROR:\n" + lines[0]));
            return false;
        }

        if (lines[2].indexOf(sha.toString()) != 0) {
            this.onError.run(new RuntimeException(String.format("ERROR: file got corrupted during the uploadAndUpdateFile; '%s' expected '%s' from server", lines[2], sha)));
            return false;
        }

        if (lines.length > 3 && !lines[3].isEmpty()) {
            cf.share_url = lines[3];
        } else {
            cf.share_url = cf.full_url.split("\\?")[0];
        }

        return true;
    }

    private boolean uploadText(@NonNull CustomFile cf, HttpURLConnection conn) throws Exception {
        if (cf.content == null) {
            Log.e(TAG, String.format("Bad text file: %s", cf));
            conn.disconnect();
            return false;
        }

        Log.d(TAG, "Creating body...");

        @SuppressWarnings("CharsetObjectCanBeUsed") byte[] body = ("msg=" + URLEncoder.encode(cf.content, StandardCharsets.UTF_8.name())).getBytes(StandardCharsets.UTF_8);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8");
        conn.connect();

        Log.d(TAG, "[POST] Body creation successful: " + body.length);

        BaseUploadProgress up = new BaseUploadProgress();
        up.delta = body.length;
        up.total = body.length;
        up.done = body.length;

        Log.d(TAG, String.format("[Progress] delta, total, done: %d, %d, %d", up.delta, up.total, up.done));

        OutputStream os = conn.getOutputStream();
        os.write(body);
        os.flush();
        this.onProgress.run(up);

        serverResponse = NetworkUtils.readConnection(conn);

        int rc = conn.getResponseCode();
        if (rc >= 300) {
            String message = String.format(Locale.getDefault(), "[%d] Server error:\n%s", rc, serverResponse.toString());
            this.onError.run(new RuntimeException(message));
            conn.disconnect();
            return false;
        }

        if (serverResponse.length() > 0) {
            cf.share_url = serverResponse.toString().trim();
        } else {
            cf.share_url = "Server not happy!";
        }

        return true;
    }

    @NonNull
    private HttpURLConnection makeConnection(@NonNull CustomFile customFile) throws Exception {
        String base = serverUrl.endsWith("/") ? serverUrl : serverUrl + "/";
        String encodedName = customFile.mime.equals("text/plain") ? "" : Uri.encode(customFile.name, "/");
        String fullUrl = base + encodedName;

        URL url = new URL(fullUrl);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        if (this.password != null) {
            conn.setRequestProperty("PW", this.password);
        }

        conn.setDoOutput(true);
        Log.d(TAG, "Sending to: " + fullUrl);
        return conn;
    }

    @Nullable
    public static String getShareUrl(Iterable<CustomFile> files) throws Exception {
        if (files == null || !files.iterator().hasNext()) {
            return null;
        }

        try {
            // Build the body
            JSONObject body = buildShareBody(files);
            String shareServerUrl = getShareServerUrl();
            if (isNullOrEmpty(shareServerUrl)) {
                throw new IllegalArgumentException(String.format("provided an empty or null server url: %s", shareServerUrl));
            }

            HttpURLConnection conn = (HttpURLConnection) new URL(shareServerUrl).openConnection();
            try {
                conn.setDoOutput(true);
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "text/plain");
                String password = getServerPassword();

                if (!isNullOrEmpty(password)) {
                    conn.setRequestProperty("PW", password);
                }

                byte[] _body = body.toString().getBytes(StandardCharsets.UTF_8);
                conn.setFixedLengthStreamingMode(_body.length);

                conn.connect();

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(_body);
                }

                int response = conn.getResponseCode();
                if (response >= 300) {
                    throw new RuntimeException(String.format("server returned not ok response code: %s\nresponse: \n%s", response, NetworkUtils.readConnection(conn)));
                }

                String resp = NetworkUtils.readInputStream(conn.getInputStream()).toString();
                if (!isNullOrEmpty(resp) && resp.startsWith("created share: ")) {
                    return resp.substring(15);
                } else {
                    throw new RuntimeException(String.format("server didn't responded correctly: '%s'", resp));
                }
            } finally {
                conn.disconnect();
            }
        } catch (Exception e) {
            Log.e("ShareCreation", String.format("ERROR: unable to create share url: %s", e.getMessage()));
            throw e;
        }
    }

    @Nullable
    private static String getServerPassword() {
        String pass = Config.string(Config.Keys.SERVER_PASSWORD);
        return !isNullOrEmpty(pass) ? pass.strip() : null;
    }

    @Nullable
    private static String getShareServerUrl() {
        String serverUrl = getServerUrl();
        if (isNullOrEmpty(serverUrl)) {
            return null;
        }

        try {
            URL parsed = new URL(serverUrl.strip());
            String domain = parsed.getAuthority();
            if (isNullOrEmpty(domain)) {
                throw new MalformedURLException(String.format("No authority in: %s", domain));
            }

            // i.e.: http://192.168.1.1:3923/Upload/Some/Path  -> http://192.168.1.1:3923/?share
            return String.format("%s://%s/?share", parsed.getProtocol(), domain);
        } catch (MalformedURLException e) {
            return null;
        }
    }

    @NonNull
    private static JSONObject buildShareBody(@NonNull Iterable<CustomFile> files) throws JSONException {
        JSONArray vps = new JSONArray();
        for (CustomFile file: files) {
            if (!isNullOrEmpty(file.share_url)) {
                vps.put(file.share_url);
            } else if (!isNullOrEmpty(file.full_url)) {
                vps.put(file.full_url);
            }
        }

        /*
         * body = {
         *   "k": "...",        # some random key of length 12
         *   "vp": ["..."],     # array of string(path on server, e.g.: http://192.168.1.1:3923/file/uploaded.txt)
         *   "pw": "...",       # share password, password that will be applied to shareUrl generated
         *   "exp": "...",      # expiration duration in minutes
         *   "perms": ["read"]  # permission for the share, readonly for now
         * }
         *
         */
        JSONObject body = new JSONObject();
        body.put("k", makeShareKey());
        body.put("pw", getSharePassword());
        body.put("exp", getShareExpiration());

        body.put("vp", vps);
        body.put("perms", new JSONArray(List.of("read")));

        return body;
    }

    /**
     * parse given expiration string (i.e.: 0 -> -1, 42 -> 42, 29m -> 29, 4h -> 240)
     * @return expiration in minutes, -1 if invalid
     */
    private static long parseExpirationInMinutes(String value) {
        if (isNullOrEmpty(value) || value.startsWith("-") || !value.trim().toLowerCase().matches("^\\d+[mhd]?$")) {
            return -1;
        }

        try {
            char unit = value.toLowerCase().charAt(value.length() - 1);
            if (Character.isDigit(unit)) {
                return Long.parseLong(value);
            }

            int mul = 1;

            switch (unit) {
                case 'h':
                    mul = 60;
                    break;
                case 'd':
                    mul = 1440;
                    break;
            }

            return Long.parseLong(value.substring(0, value.length() - 1)) * mul;
        } catch (Exception e) {
            return -1L;
        }
    }

    @NonNull
    private static String getShareExpiration() {
        return String.valueOf(parseExpirationInMinutes(Config.string(Config.Keys.LINK_EXPIRATION)));
    }

    @Nullable
    private static String getSharePassword() {
        String pass = Config.string(Config.Keys.SHARE_PASSWORD);
        if (isNullOrEmpty(pass)) {
            return null;
        }

        return pass.strip();
    }

    private static final String chars = "abcdefghijklmnopqrstuvwxyz0123456789";

    @NonNull
    private static String makeShareKey() {
        StringBuilder key = new StringBuilder();
        SecureRandom random = new SecureRandom();

        for (int i = 0; i < 12; ++i) {
            key.append(chars.charAt(random.nextInt(chars.length())));
        }

        return key.toString();
    }

    private static boolean isNullOrEmpty(String str) {
        return str == null || str.isEmpty();
    }

    @FunctionalInterface
    public interface Listener<T> {
        void run(T listen);
    }

    @FunctionalInterface
    public interface OnCompleteListener extends Listener<String> {}

    @FunctionalInterface
    public interface OnErrorListener extends Listener<Throwable> {}

    @FunctionalInterface
    public interface OnProgressListener extends Listener<BaseUploadProgress> {}

    public static final class Builder {
        private static final int MIN_CHUNK_SIZE = 128 * 1024;
        private final int chunkSize = MIN_CHUNK_SIZE;

        private final Context context;
        private String serverUrl = null;
        private String serverPassword = null;
        private OnErrorListener onError = null;
        private OnProgressListener onProgress = null;
        private OnCompleteListener onComplete = null;

        public Builder(@NonNull Context context) {
            this.context = context.getApplicationContext();
        }

        public Builder setOnComplete(@Nullable OnCompleteListener onComplete) {
            this.onComplete = onComplete;
            return this;
        }

        public Builder setOnError(@Nullable OnErrorListener onError) {
            this.onError = onError;
            return this;
        }

        public Builder setOnProgress(@Nullable OnProgressListener onProgress) {
            this.onProgress = onProgress;
            return this;
        }

        public Builder setServerPassword(String serverPassword) {
            if (!isNullOrEmpty(serverPassword)) {
                this.serverPassword = serverPassword;
            }

            return this;
        }

        public Builder setServerUrl(String serverUrl) {
            if (isNullOrEmpty(serverUrl)) {
                return this;
            }

            this.serverUrl = serverUrl;

            if (!this.serverUrl.startsWith("http")) {
                this.serverUrl = "http://" + this.serverUrl;
            }

            if (!this.serverUrl.endsWith("/")) {
                this.serverUrl += "/";
            }

            if (this.serverUrl.contains("%")) {
                String[] dtc = "%Y %q %m %d %j %H %M %S".split(" ");

                SimpleDateFormat sdf = new SimpleDateFormat("yyyy Q MM dd DDD HH mm ss", Locale.US);
                sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
                String[] dtp = sdf.format(new Date()).split(" ");

                for (int a = 0; a < dtc.length; a++) {
                    this.serverUrl = this.serverUrl.replace(dtc[a], dtp[a]);
                }
            }

            return this;
        }

        @NonNull
        public Uploader build() {
            if (onProgress == null) {
                onProgress = (progress) -> Log.i(TAG, String.format(Locale.getDefault(), "Uploaded: %d/%d bytes (delta=%d)", progress.done, progress.total, progress.delta)
                );
            }
            if (onError == null) {
                onError = (err) -> Log.e(TAG, "Upload error: " + err.toString());
            }
            if (onComplete == null) {
                onComplete = response -> Log.i(TAG, "Uploaded Response: " + response);
            }

            if (isNullOrEmpty(this.serverUrl)) {
                throw new IllegalStateException("serverUrl is empty!");
            }

            return new Uploader(this);
        }
    }

    @Nullable
    private static String getServerUrl() {
        String url = Config.string(Config.Keys.SERVER_URL);
        return !isNullOrEmpty(url) ? url.strip() : null;
    }

    @NonNull
    @Contract("_, _, _, _ -> new")
    public static Uploader fromConfig(@NonNull Context context, @Nullable OnProgressListener onProgress, @Nullable OnErrorListener onError, @Nullable OnCompleteListener onComplete) {
        return new Builder(context).setOnProgress(onProgress).setOnComplete(onComplete).setOnError(onError).setServerUrl(getServerUrl()).setServerPassword(getServerPassword()).build();
    }
}
