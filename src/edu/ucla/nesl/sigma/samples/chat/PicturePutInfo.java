package edu.ucla.nesl.sigma.samples.chat;


import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.os.Parcelable;

public class PicturePutInfo implements Parcelable {

  public final ParcelFileDescriptor parcelFd;
  public final String uuid;

  public PicturePutInfo(String uuid, ParcelFileDescriptor parcelFd) {
    this.uuid = uuid;
    this.parcelFd = parcelFd;
  }

  private PicturePutInfo(Parcel in) {
    uuid = in.readString();
    parcelFd = ParcelFileDescriptor.CREATOR.createFromParcel(in);
  }

  @Override
  public int describeContents() {
    return 0;
  }

  @Override
  public void writeToParcel(Parcel parcel, int i) {
    parcel.writeString(uuid);
    parcelFd.writeToParcel(parcel, i);
  }

  public static final Parcelable.Creator<PicturePutInfo> CREATOR
      = new Parcelable.Creator<PicturePutInfo>() {
    public PicturePutInfo createFromParcel(Parcel in) {
      return new PicturePutInfo(in);
    }

    public PicturePutInfo[] newArray(int size) {
      return new PicturePutInfo[size];
    }
  };
}
