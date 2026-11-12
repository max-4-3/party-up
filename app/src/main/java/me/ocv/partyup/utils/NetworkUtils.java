package me.ocv.partyup.utils;

import androidx.annotation.NonNull;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;

public final class NetworkUtils {
    private static final int BUFFER_SIZE = 1024 * 1024;
    private NetworkUtils() {
    }

    @NonNull
    public static StringBuilder readInputStream(InputStream inputStream) throws IOException {
        StringBuilder builder = new StringBuilder();
        if (inputStream == null) {
            return builder;
        }

        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream))) {
            char[] buffer = new char[BUFFER_SIZE];
            int charsRead;

            while ((charsRead = bufferedReader.read(buffer)) != -1) {
                builder.append(buffer, 0, charsRead);
            }

            return builder;
        }
    }

    @NonNull
    public static StringBuilder readConnection(@NonNull HttpURLConnection connection) throws IOException {
        InputStream is;
        if (connection.getResponseCode() >= 400) {
            is = connection.getErrorStream();
        } else {
            is = connection.getInputStream();
        }

        return readInputStream(is);
    }

    @FunctionalInterface
    public interface ResponseOk {
        boolean check(int status);
    }
}
