package me.ocv.partyup.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

import org.jetbrains.annotations.Contract;

@Entity(tableName = "upload_status")
public final class UploadStatus implements Parcelable {
    private static final float PERCENT_MUL = 100F;

    public enum Status {
        ENQUEUED, RUNNING, COMPLETED, FAILED
    }

    @PrimaryKey(autoGenerate = true)
    @ColumnInfo(name = "id")
    public long id = 0;

    @ColumnInfo(name = "title")
    public String title = "unnamed_file";

    @ColumnInfo(name = "done")
    public long done = 0L;

    @ColumnInfo(name = "total")
    public long total = 0L;

    @ColumnInfo(name = "started_when")
    public long startedWhen = 0;

    @ColumnInfo(name = "status_text")
    public String statusText = "Idle...";

    @ColumnInfo(name = "status")
    public Status status;

    @Ignore
    public float getPercentage() {
        return total > 0 ? ((float) done / total) * PERCENT_MUL : 0;
    }

    @Ignore
    public UploadStatus snapshot() {
        return new UploadStatus(id, title, done, total, startedWhen, statusText, status);
    }

    public UploadStatus(long id, String title, long done, long total, long startedWhen, String statusText, Status status) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.total = total;
        this.startedWhen = startedWhen;
        this.statusText = statusText;
        this.status = status;
    }

    @NonNull
    @Contract(" -> new")
    public static UploadStatus sample() {
        return new UploadStatus(1, "Zuper_file.zip", 12, 200, System.currentTimeMillis(), "Uploading...", Status.RUNNING);
    }

    // Parable implementations
    private UploadStatus(@NonNull Parcel in) {
        id = in.readLong();
        title = in.readString();
        done = in.readLong();
        total = in.readLong();
        startedWhen = in.readLong();
        statusText = in.readString();
        status = Status.valueOf(in.readString());
    }

    public static final Creator<UploadStatus> CREATOR = new Creator<>() {
        @Override
        public UploadStatus createFromParcel(Parcel in) {
            return new UploadStatus(in);
        }

        @Override
        public UploadStatus[] newArray(int size) {
            return new UploadStatus[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel dest, int flags) {
        dest.writeLong(id);
        dest.writeString(title);
        dest.writeLong(done);
        dest.writeLong(total);
        dest.writeLong(startedWhen);
        dest.writeString(statusText);
        dest.writeString(status.name());
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadStatus(" + "id=" + id + ", title=" + title + ", done=" + done + ", total=" + total + ", startedWhen=" + startedWhen + ", statusText=" + statusText + ", status=" + status + ')';
    }
}
