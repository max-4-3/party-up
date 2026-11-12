package me.ocv.partyup.models.pojo;

import androidx.annotation.NonNull;
import androidx.room.Embedded;
import androidx.room.Ignore;
import androidx.room.Relation;

import me.ocv.partyup.models.UploadInfo;
import me.ocv.partyup.models.UploadStatus;
import me.ocv.partyup.objects.CustomFile;
import me.ocv.partyup.utils.FileCache;

public class UploadWithInfo {
    @Embedded
    public final UploadStatus status;

    @Relation(parentColumn = "id", entityColumn = "status_id")
    public final UploadInfo info;

    public UploadWithInfo snapshot() {
        return new UploadWithInfo(status, info);
    }

    public UploadWithInfo(UploadStatus status, UploadInfo info) {
        this.status = status;
        this.info = info;
    }

    @Ignore
    public CustomFile toCustomFile() {
        CustomFile file = new CustomFile();

        file.handle = info.getUri();
        file.name = info.fileName;
        file.content = info.textContent;
        file.mime = info.contentType;
        file.ext = FileCache.getExtensionFromMime(file.mime);
        file.size = info.size;

        return file;
    }

    @NonNull
    @Override
    public String toString() {
        return "UploadWithInfo (" + "status=" + status + ", info=" + info + ')';
    }
}
