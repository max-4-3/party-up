package me.ocv.partyup.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public final class UploadStatus implements Parcelable {
    public static final Creator<UploadStatus> CREATOR = new Creator<>() {
        @NonNull
        @Contract("_ -> new")
        @Override
        public UploadStatus createFromParcel(Parcel in) {
            return new UploadStatus(in);
        }

        @NonNull
        @Contract(value = "_ -> new", pure = true)
        @Override
        public UploadStatus[] newArray(int size) {
            return new UploadStatus[size];
        }
    };
    private static final float PERCENT_MUL = 100F;
    public int id = 0;
    public String title = "unnamed_file";
    public int files = 1;
    public long done = 0L;
    public long total = 0L;
    public long startedWhen = System.currentTimeMillis();
    public String statusText = "Idle...";

    public UploadStatus(int id, String title, long done, long total, int files, long startedWhen, String statusText) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.total = total;
        this.files = files;
        this.startedWhen = startedWhen;
        this.statusText = statusText;
    }

    private UploadStatus(@NonNull Parcel in) {
        id = in.readInt();
        title = in.readString();
        done = in.readLong();
        total = in.readLong();
        files = in.readInt();
        startedWhen = in.readLong();
        statusText = in.readString();
    }

    public float getPercentage() {
        return total > 0 ? ((float) done / total) * PERCENT_MUL : 0;
    }

    public double getElapsedMillis() {
        return startedWhen > 0 ? System.currentTimeMillis() - startedWhen : 0;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeInt(id);
        dest.writeString(title);
        dest.writeLong(done);
        dest.writeLong(total);
        dest.writeInt(files);
        dest.writeLong(startedWhen);
        dest.writeString(statusText);
    }
}
