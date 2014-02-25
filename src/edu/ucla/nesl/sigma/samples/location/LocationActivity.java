package edu.ucla.nesl.sigma.samples.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import edu.ucla.nesl.sigma.api.RemoteContext;
import edu.ucla.nesl.sigma.api.SigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaServiceA;
import edu.ucla.nesl.sigma.base.SigmaServiceB;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class LocationActivity extends BunchOfButtonsActivity {

  SigmaServiceConnection connA;
  SigmaServiceConnection connB;

  SigmaManager sigmaA;
  SigmaManager sigmaB;

  LocationManager mNativeLocationManager;
  LocationManager mRemoteLocationmanager;

  @Override
  public void onCreateHook() {
    mNativeLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

    connA = new SigmaServiceConnection(this, SigmaServiceA.class);
    connB = new SigmaServiceConnection(this, SigmaServiceB.class);

    addButton("ΣA & ΣB Local HTTP", new Runnable() {
      @Override
      public void run() {
        connectToBothSigmaEngines(false);
      }
    });

    addButton("ΣA & ΣB Proxy HTTP", new Runnable() {
      @Override
      public void run() {
        connectToBothSigmaEngines(true);
      }
    });

    addButton("REQUEST location updates", new Runnable() {
      @Override
      public void run() {
        requestLocationUpdates();
      }
    });

    addButton("REMOVE location updates", new Runnable() {
      @Override
      public void run() {
        removeLocationUpdates();
      }
    });

    addButton("SEND test location updates", new Runnable() {
      @Override
      public void run() {
        sendTestUpdate();
      }
    });
  }


  PendingIntent getLocationPosterPendingIntent(Class<? extends LocationPoster> posterClass) {
    Intent intent = new Intent(this, posterClass);
    return PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT);
  }

  void connectToBothSigmaEngines(boolean useProxy) {
    sigmaA =
        connA.getImpl((useProxy) ? SigmaServiceA.getProxyHttp() : SigmaServiceA.getLocalHttp(),
                      null);
    sigmaB =
        connB.getImpl((useProxy) ? SigmaServiceB.getProxyHttp() : SigmaServiceB.getLocalHttp(),
                      null);
    SigmaManager remote = sigmaA.getRemoteManager(sigmaB.getBaseURI());
    RemoteContext remoteContext = RemoteContext.getRemoteContext(this, remote);
    mRemoteLocationmanager =
        (LocationManager) remoteContext.getSystemService(Context.LOCATION_SERVICE);
  }

  void requestLocationUpdates() {
    mNativeLocationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 0, 0,
        getLocationPosterPendingIntent(NativeLocationPoster.class));
    mRemoteLocationmanager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 0, 0,
        getLocationPosterPendingIntent(RemoteLocationPoster.class));
  }

  void removeLocationUpdates() {
    mRemoteLocationmanager
        .removeUpdates(getLocationPosterPendingIntent(RemoteLocationPoster.class));
    mNativeLocationManager
        .removeUpdates(getLocationPosterPendingIntent(NativeLocationPoster.class));
  }

  void sendTestUpdate() {
    Intent intent = new Intent();
    Location location = new Location(LocationManager.GPS_PROVIDER);
    location.setTime(System.currentTimeMillis());
    location.setElapsedRealtimeNanos(System.nanoTime());
    location.setLatitude(34.0722);
    location.setLongitude(118.4441);
    location.setAltitude(71);
    intent.putExtra(LocationManager.KEY_LOCATION_CHANGED, location);
    try {
      getLocationPosterPendingIntent(NativeLocationPoster.class).send(this, 0, intent);
      getLocationPosterPendingIntent(RemoteLocationPoster.class).send(this, 0, intent);
    } catch (PendingIntent.CanceledException ex) {
      throwUnexpected(ex);
    }
  }
}