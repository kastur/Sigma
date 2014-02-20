package edu.ucla.nesl.sigma.samples.location;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;
import android.util.Log;

public abstract class LocationPoster extends BroadcastReceiver {
    public static final String TAG = LocationPoster.class.getName();
    public final String mName;

    public LocationPoster(String name) {
        mName = name;
    }

    public void onReceive(Context context, Intent intent) {
        Location location = (Location) intent.getExtras().get(LocationManager.KEY_LOCATION_CHANGED);
        Log.d(TAG, "----------> Received location update: " +
                mName + " ------>(" +
                location.getTime() + ", " +
                location.getLatitude() + ", " +
                location.getLongitude() + ")");
    }
}
