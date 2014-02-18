package edu.ucla.nesl.sigma.samples.basic;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class PendingIntentLocationReceiver extends BroadcastReceiver {
    private static final String TAG = PendingIntentLocationReceiver.class.getName();

    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "onReceive()");
    }
}
