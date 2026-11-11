package me.ocv.partyup.models.pojo;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Relation;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;

public class UploadWithInfo {
    @Embedded
    public UploadStatus status;

    @Relation(parentColumn = "id", entityColumn = "status_id")
    public UploadInfo info;

    public UploadWithInfo snapshot() {
        return new UploadWithInfo(status, info);
    }

    public UploadWithInfo(UploadStatus status, UploadInfo info) {
        this.status = status;
        this.info = info;
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadWithInfo (" + "status=" + status + ", info=" + info + ')';
    }
}
