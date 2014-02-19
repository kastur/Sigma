package edu.ucla.nesl.sigma.samples.chat;

import android.os.Parcel;
import android.os.Parcelable;

public class PictureEntry implements Parcelable {
    public String uuid;
    public int numBytes;
    public byte[] bytes;
    public String from;

    public PictureEntry() { }

    private PictureEntry(Parcel in) {
        readFromParcel(in);
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel parcel, int flags) {
        parcel.writeString(uuid);
        parcel.writeString(from);
        parcel.writeInt(numBytes);
    }

    public void readFromParcel(Parcel in) {
        uuid = in.readString();
        from = in.readString();
        numBytes = in.readInt();
    }

    public static final Parcelable.Creator<PictureEntry> CREATOR
            = new Parcelable.Creator<PictureEntry>() {
        public PictureEntry createFromParcel(Parcel in) {
            return new PictureEntry(in);
        }

        public PictureEntry[] newArray(int size) {
            return new PictureEntry[size];
        }
    };

}
