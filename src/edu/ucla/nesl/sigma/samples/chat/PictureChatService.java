package edu.ucla.nesl.sigma.samples.chat;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;

import java.util.UUID;

import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaWire;

public class PictureChatService extends Service {

  private static final String TAG = PictureChatService.class.getName();
  NotificationHelper mNotificationHelper;
  PictureDB mDB;

  @Override
  public void onCreate() {
    super.onCreate();
    mNotificationHelper = new NotificationHelper(this);
    mDB = new PictureDB();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return new IPictureChatServer.Stub() {

      @Override
      public ParcelFileDescriptor requestPicturePut(ISigmaManager remote, PictureEntry entry)
          throws RemoteException {
        String from;
        if (remote != null) {
          URI fromURI = new SigmaManager(remote).getBaseURI();
          from = SigmaWire.prettyURI(fromURI);
        } else {
          from = "LOCAL";
        }
        entry.from = from;
        return handlePutPicture(entry);
      }

      @Override
      public PictureEntry requestPictureGet(ISigmaManager remote, String uuid,
                                            ParcelFileDescriptor writeTo) throws RemoteException {
        return handleGetPicture(uuid, writeTo);
      }
    };
  }


  public ParcelFileDescriptor handlePutPicture(PictureEntry request) {
    Log.d(TAG, "handlePutPicture(...)");
    request.uuid = UUID.randomUUID().toString();
    mDB.addEntry(request);
    ParcelFileDescriptor[] pipe = PictureIO.makePipe();
    recvPictureAsync(request, pipe[1]);
    return pipe[0];
  }

  public PictureEntry handleGetPicture(String uuid, ParcelFileDescriptor writeTo) {
    Log.d(TAG, "Called getPicture(...)");
    PictureEntry entry = mDB.getEntry(uuid);
    if (entry == null) {
      Log.e(TAG, "Did not find a picture record matching: " + uuid);
      return null;
    } else {
      sendPictureAsync(entry, writeTo);
      return entry;
    }
  }

  public void recvPictureAsync(PictureEntry pictureEntry, final ParcelFileDescriptor readFrom) {
    new PictureIO.RecvPictureTask(pictureEntry, readFrom) {
      @Override
      protected void onPreExecute() {
        super.onPreExecute();
        mNotificationHelper.progressStart(mEntry);
      }

      @Override
      protected void onProgressUpdate(Integer... values) {
        super.onProgressUpdate(values);
        mNotificationHelper.progressUpdate(mEntry, mEntry.numBytes, values[0]);
      }

      @Override
      protected void onPostExecute(Void _void) {
        Intent viewer = new Intent(PictureChatService.this, PictureViewActivity.class);
        viewer.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        viewer.putExtra("uuid", mEntry.uuid);

        //startActivity(viewer);
        PendingIntent pi = PendingIntent.getActivity(
            PictureChatService.this, 0, viewer, PendingIntent.FLAG_UPDATE_CURRENT);
        mNotificationHelper.createLink(mEntry, pi);
      }
    }.execute();
  }

  public void sendPictureAsync(PictureEntry record, ParcelFileDescriptor writeTo) {
    new PictureIO.SendPictureTask(record.bytes, writeTo, false /* not intentionally slow IO */)
        .execute();
  }


}
