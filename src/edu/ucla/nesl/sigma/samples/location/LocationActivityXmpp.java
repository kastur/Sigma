package edu.ucla.nesl.sigma.samples.location;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.location.Location;
import android.location.LocationManager;

import edu.ucla.nesl.sigma.api.RemoteContext;
import edu.ucla.nesl.sigma.api.SigmaFactory;
import edu.ucla.nesl.sigma.base.SigmaFactoryA;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;
import edu.ucla.nesl.sigma.samples.TestXmpp;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class LocationActivityXmpp extends BunchOfButtonsActivity {

  SigmaFactory sigmaConn;
  SigmaManager sigmaMan;


  @Override
  public void onCreateHook() {
    sigmaConn = new SigmaFactory(this, SigmaFactoryA.class);

    addButton("Login XMPP as ΣA", new Runnable() {
      @Override
      public void run() {
        sigmaMan = sigmaConn.newInstance(TestXmpp.getXmppA(), TestXmpp.getPasswordBundleA());
      }
    });

    addButton("Login XMPP as ΣB", new Runnable() {
      @Override
      public void run() {
        sigmaMan = sigmaConn.newInstance(TestXmpp.getXmppB(), TestXmpp.getPasswordBundeB());
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

  void requestLocationUpdates() {
    LocationManager
        nativeLocationManager =
        (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    nativeLocationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 0, 0,
        getLocationPosterPendingIntent(NativeLocationPoster.class));

    SigmaManager remote = sigmaMan.getRemoteManager(TestXmpp.getXmppA());
    RemoteContext remoteContext = RemoteContext.getRemoteContext(LocationActivityXmpp.this, remote);
    LocationManager
        remoteLocationManager =
        (LocationManager) remoteContext.getSystemService(Context.LOCATION_SERVICE);
    remoteLocationManager.requestLocationUpdates(
        LocationManager.GPS_PROVIDER, 0, 0,
        getLocationPosterPendingIntent(RemoteLocationPoster.class));
  }

  void removeLocationUpdates() {
    LocationManager
        nativeLocationManager =
        (LocationManager) getSystemService(Context.LOCATION_SERVICE);
    nativeLocationManager.removeUpdates(getLocationPosterPendingIntent(NativeLocationPoster.class));

    SigmaManager remote = sigmaMan.getRemoteManager(TestXmpp.getXmppA());
    RemoteContext remoteContext = RemoteContext.getRemoteContext(LocationActivityXmpp.this, remote);
    LocationManager
        remoteLocationManager =
        (LocationManager) remoteContext.getSystemService(Context.LOCATION_SERVICE);
    remoteLocationManager.removeUpdates(getLocationPosterPendingIntent(RemoteLocationPoster.class));

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