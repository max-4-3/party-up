package me.ocv.partyup.models;

import android.net.Uri;

import androidx.annotation.NonNull;
import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Ignore;
import androidx.room.Index;
import androidx.room.PrimaryKey;

@SuppressWarnings("UnusedAssignment")
@Entity(tableName = "upload_info", foreignKeys = @ForeignKey(entity = UploadStatus.class, parentColumns = "id", childColumns = "status_id", onDelete = ForeignKey.CASCADE), indices = {@Index("status_id")})
public final class UploadInfo {
    @PrimaryKey(autoGenerate = true)
    public long id = 0;

    @ColumnInfo(name = "status_id")
    public long statusId;

    @ColumnInfo(name = "uri")
    public final String handle;

    @ColumnInfo(name = "size")
    public long size = 0;

    @ColumnInfo(name = "content_type")
    public String contentType = "text/plain";

    @ColumnInfo(name = "file_name")
    public String fileName = "unnamed_file.bin";

    @ColumnInfo(name = "content")
    public String textContent = "";

    @ColumnInfo(name = "share_url")
    public String shareUrl = "";

    @ColumnInfo(name = "full_url")
    public String fullUrl = "";

    @Ignore
    @NonNull
    public Uri getUri() {
        return Uri.parse(handle);
    }

    public UploadInfo(long id, long statusId, String handle, long size, String contentType, String fileName, String textContent, String shareUrl, String fullUrl) {
        this.id = id;
        this.statusId = statusId;
        this.handle = handle;
        this.size = size;
        this.contentType = contentType;
        this.fileName = fileName;
        this.textContent = textContent;
        this.shareUrl = shareUrl;
        this.fullUrl = fullUrl;
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadInfo( " +
                "id=" + id +
                ", statusId=" + statusId +
                ", handle='" + handle + '\'' +
                ", size=" + size +
                ", contentType='" + contentType + '\'' +
                ", fileName='" + fileName + '\'' +
                ", textContent='" + textContent + '\'' +
                ", shareUrl='" + shareUrl + '\'' +
                ", fullUrl='" + fullUrl + '\'' +
                ')';
    }
}