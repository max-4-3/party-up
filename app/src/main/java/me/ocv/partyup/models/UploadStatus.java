package me.ocv.partyup.models;

import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

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

    @ColumnInfo(name = "worker_id")
    public String workerId = "";

    @Ignore
    public float getPercentage() {
        return total > 0 ? ((float) done / total) * PERCENT_MUL : 0;
    }

    @Ignore
    public boolean isCompleted() {
        return isFinished() || (total >= 0 && done >= total);
    }

    @Ignore
    public boolean isOnGoing() {
        return isStatus(Status.RUNNING);
    }

    @Ignore
    public boolean isFinished() {
        return isStatus(Status.FAILED) || isStatus(Status.COMPLETED);
    }

    @Ignore
    public boolean isErrored() {
        return isStatus(Status.FAILED) || total <= 0;
    }

    @Ignore
    private boolean isStatus(Status expected) {
        return status == expected;
    }

    public UploadStatus(long id, String title, long done, long total, long startedWhen, String statusText, Status status, String workerId) {
        this.id = id;
        this.title = title;
        this.done = done;
        this.total = total;
        this.startedWhen = startedWhen;
        this.statusText = statusText;
        this.status = status;
        this.workerId = workerId;
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
        workerId = in.readString();
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
        dest.writeString(workerId);
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadStatus(" + "id=" + id + ", title=" + title + ", done=" + done + ", total=" + total + ", startedWhen=" + startedWhen + ", statusText=" + statusText + ", status=" + status + ", workerId=" + workerId + ')';
    }
}
