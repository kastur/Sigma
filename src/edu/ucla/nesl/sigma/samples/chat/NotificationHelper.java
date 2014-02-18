package edu.ucla.nesl.sigma.samples.chat;

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;

public class NotificationHelper {
    private Context mContext;
    private int NID = 1;
    private Notification n;
    private NotificationManager nManager;

    public NotificationHelper(Context context) {
        mContext = context;
        nManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
    }

    /**
     * Put the notification into the status bar
     */
    public void progressStart() {
        nManager = (NotificationManager) mContext.getSystemService(Context.NOTIFICATION_SERVICE);
        n = new Notification.Builder(mContext)
                .setTicker("Receiving share!")
                .setOngoing(true)
                .setProgress(100, 0, true)
                .setContentText("Receiving share!")
                .build();
        nManager.notify(0, n);
    }


    public void progressUpdate(int percentageComplete) {
        n = new Notification.Builder(mContext)
                .setTicker("Receiving share!")
                .setOngoing(true)
                .setProgress(100, percentageComplete, true)
                .setContentText("Receiving share!")
                .build();
        nManager.notify(NID, n);
    }


    public void completed() {
        //remove the notification from the status bar
        nManager.cancel(NID);
    }

    public void link(PendingIntent pi) {
        n = new Notification.Builder(mContext)
                .setTicker("Click to see picture")
                .addAction(android.R.drawable.sym_def_app_icon, "Open", pi)
                .build();
        nManager.notify(NID, n);
    }
}