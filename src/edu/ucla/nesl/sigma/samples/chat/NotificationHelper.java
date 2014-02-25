package edu.ucla.nesl.sigma.samples.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicInteger;

public class NotificationHelper {

  private Context mContext;
  final HashMap<String, Integer> mNotifications;
  final NotificationManager nManager;
  final AtomicInteger nextId;


  public NotificationHelper(Context context) {
    mContext = context;
    nManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    mNotifications = new HashMap<String, Integer>();
    nextId = new AtomicInteger(1);
  }

  public void progressStart(PictureEntry record) {
    int nid = nextId.addAndGet(1);
    mNotifications.put(record.uuid, nid);
    Notification n = new Notification.Builder(mContext)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setTicker("Incoming picture!")
        .setContentTitle("Waiting for data")
        .setContentText("from " + record.from)
        .setOngoing(true)
        .setProgress(100, 0, true)
        .setContentText(record.uuid)
        .build();
    nManager.notify(nid, n);
  }

  public void progressUpdate(PictureEntry record, int max, int progress) {
    int nid = mNotifications.get(record.uuid);
    Notification n = new Notification.Builder(mContext)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setOngoing(true)
        .setProgress(max, progress, false)
        .setContentTitle("Receiving...hold tight.")
        .setContentText("from " + record.from)
        .build();
    nManager.notify(nid, n);
  }

  public void progressCompleted(String uuid) {
    int nid = mNotifications.get(uuid);
    nManager.cancel(nid);
  }

  public void createLink(PictureEntry record, PendingIntent pi) {
    int nid = mNotifications.get(record.uuid);

    Bitmap bitmap = BitmapFactory.decodeByteArray(record.bytes, 0, record.bytes.length);

    Notification n = new Notification.Builder(mContext)
        .setSmallIcon(android.R.drawable.stat_notify_chat)
        .setContentTitle("Received picture")
        .setContentText("from " + record.from)
        .addAction(android.R.drawable.sym_def_app_icon, "Open fullscreen", pi)
        .setLargeIcon(bitmap)
        .build();
    nManager.notify(nid, n);
  }
}