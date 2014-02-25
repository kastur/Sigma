package edu.ucla.nesl.sigma.samples.chat;

import android.os.AsyncTask;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.ByteArrayInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;

import edu.ucla.nesl.sigma.base.SigmaJNI;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;


public class PictureIO {

  public static final String TAG = PictureIO.class.getName();

  public static ParcelFileDescriptor[] makePipe() {
    ParcelFileDescriptor[] ret = new ParcelFileDescriptor[2];
    int[] pipe = SigmaJNI.socketpair();
    ret[0] = ParcelFileDescriptor.adoptFd(pipe[0]);
    ret[1] = ParcelFileDescriptor.adoptFd(pipe[1]);
    return ret;
  }

  public static class RecvPictureTask extends AsyncTask<Void, Integer, Void> {

    protected final PictureEntry mEntry;
    protected final ParcelFileDescriptor mReadFrom;

    public RecvPictureTask(PictureEntry entry, ParcelFileDescriptor readFrom) {
      mEntry = entry;
      mReadFrom = readFrom;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      Log.d(TAG, "Received: [" + values[0] + " / " + mEntry.numBytes);
    }

    @Override
    protected Void doInBackground(Void... voids) {
      int readFd = mReadFrom.getFd();
      int totalBytesOut = 0;
      ByteBuffer output = ByteBuffer.allocateDirect(mEntry.numBytes);
      while (true) {
        output.position(totalBytesOut);
        ByteBuffer buffer = output.slice();
        SigmaJNI.waitForMessage(readFd);
        int bytesIn = SigmaJNI.recvMessage(readFd, buffer);
        totalBytesOut += bytesIn;
        ;
        publishProgress(totalBytesOut);
        if (totalBytesOut >= mEntry.numBytes) {
          break;
        }
      }
      mEntry.bytes = output.array();
      return null;
    }
  }

  public static class SendPictureTask extends AsyncTask<Void, Integer, Void> {

    protected final boolean mSlowIO;
    protected final byte[] mData;
    protected final ParcelFileDescriptor mParcelFd;

    public SendPictureTask(byte[] data, ParcelFileDescriptor parcelFd,
                           boolean intentionally_slow_io) {
      mData = data;
      mParcelFd = parcelFd;
      mSlowIO = intentionally_slow_io;
    }

    @Override
    protected void onProgressUpdate(Integer... values) {
      Log.d(TAG, "Sent [" + values[0] + "/" + mData.length + "] bytes");
    }

    @Override
    protected Void doInBackground(Void... voids) {
      ByteArrayInputStream pictureIn = new ByteArrayInputStream(mData);
      OutputStream os = new FileOutputStream(mParcelFd.getFileDescriptor());
      byte[] buffer = new byte[1024];
      try {
        int totalBytesOut = 0;
        while (true) {
          int bytesIn = pictureIn.read(buffer, 0, 1024);
          if (bytesIn <= 0) {
            Log.d(TAG, "Finished with pictureIn");
            break;
          }
          os.write(buffer, 0, bytesIn);
          os.flush();
          totalBytesOut += bytesIn;
          publishProgress(totalBytesOut);

          if (mSlowIO) {
            try {
              Thread.sleep(1000);
            } catch (InterruptedException ex) { /* EAT */ }
          }
        }
        mParcelFd.close();
      } catch (IOException ex) {
        throwUnexpected(ex);
      }
      return null;
    }
  }
}
