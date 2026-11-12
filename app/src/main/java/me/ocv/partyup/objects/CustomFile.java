package me.ocv.partyup.objects;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

import androidx.annotation.NonNull;

import org.jetbrains.annotations.Contract;

public final class CustomFile implements Parcelable {
    public Uri handle = Uri.EMPTY;
    public long size = 0;
    public String name = "unnamed_file";
    public String full_url = "";
    public String share_url = "";
    public String desc = "a binary file";
    public String content = "";
    public String mime = "";
    public String ext = "";

    public boolean isText() {
        return notEmpty(content) && mime.equalsIgnoreCase("text/plain");
    }

    public boolean isValid() {
        return notEmpty(name) && notNull(handle) && size > 0;
    }

    public boolean isReadable() {
        return notNull(handle) && size > 0;
    }

    private boolean notEmpty(String string) {
        return notNull(string) && !string.isEmpty();
    }

    private boolean notNull(Object object) {
        return object != null;
    }

    public CustomFile() {
    }

    public CustomFile(Uri handle, long size, String name, String full_url, String share_url, String desc, String content, String mime, String ext) {
        this.handle = handle;
        this.size = size;
        this.name = name;
        this.full_url = full_url;
        this.share_url = share_url;
        this.desc = desc;
        this.content = content;
        this.mime = mime;
        this.ext = ext;
    }

    // Parcelable implementations
    private CustomFile(@NonNull Parcel in) {
        handle = Uri.parse(in.readString());
        size = in.readLong();
        name = in.readString();
        full_url = in.readString();
        share_url = in.readString();
        desc = in.readString();
        content = in.readString();
        mime = in.readString();
        ext = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(@NonNull Parcel parcel, int i) {
        parcel.writeString(handle.toString());
        parcel.writeLong(size);
        parcel.writeString(name);
        parcel.writeString(full_url);
        parcel.writeString(share_url);
        parcel.writeString(desc);
        parcel.writeString(content);
        parcel.writeString(mime);
        parcel.writeString(ext);
    }

    public static final Creator<CustomFile> CREATOR = new Creator<>() {
        @NonNull
        @Contract("_ -> new")
        @Override
        public CustomFile createFromParcel(Parcel in) {
            return new CustomFile(in);
        }

        @NonNull
        @Contract(value = "_ -> new", pure = true)
        @Override
        public CustomFile[] newArray(int size) {
            return new CustomFile[size];
        }
    };

    @Override
    @NonNull
    public String toString() {
        return "CustomFile (" + "handle=" + handle + ", size=" + size + ", name=" + name + ", full_url=" + full_url + ", share_url=" + share_url + ", desc=" + desc + ", content=" + content + ", mime=" + mime + ", ext=" + ext + ", CREATOR=" + CREATOR + ')';
    }
}