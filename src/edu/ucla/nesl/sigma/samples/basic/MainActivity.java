package edu.ucla.nesl.sigma.samples.basic;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;

import java.util.List;
import java.util.Random;

import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.RemoteContext;
import edu.ucla.nesl.sigma.api.SigmaFactory;
import edu.ucla.nesl.sigma.base.SigmaFactoryA;
import edu.ucla.nesl.sigma.base.SigmaFactoryB;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.samples.location.NativeLocationPoster;

public class MainActivity extends Activity {

  private static final String TAG = MainActivity.class.getSimpleName();
  private Context mContext = null;

  private SigmaFactory sigmaA;
  private SigmaFactory sigmaB;
  private SigmaManager mService8 = null;
  private SigmaManager mService9 = null;

  private Runnable mStartServices = new Runnable() {
    @Override
    public void run() {
      Log.d(TAG, "sending startService");
      mService8 = sigmaA.newInstance(SigmaFactoryB.getLocalHttp(), null);
      mService9 = sigmaB.newInstance(SigmaFactoryA.getLocalHttp(), null);
    }
  };

  private Runnable mStopServices = new Runnable() {
    @Override
    public void run() {
      // NOP
    }
  };

  private Runnable mTestSensorEvents = new Runnable() {
    @Override
    public void run() {
      try {
        SigmaManager remote = mService8.getRemoteManager(mService9.getBaseURI());
        RemoteContext remoteContext = RemoteContext.getRemoteContext(mContext, remote);
        SensorManager sensorManager =
            (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);

        Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        SensorEventListener eventListener = new SensorEventListener() {
          @Override
          public void onSensorChanged(SensorEvent sensorEvent) {
            Log.d(TAG, "onSensorChanged");
          }

          @Override
          public void onAccuracyChanged(Sensor sensor, int i) {

          }
        };

        sensorManager
            .registerListener(eventListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

      } catch (Exception ex) {
        ex.printStackTrace();
        throw new RuntimeException(ex);
      }

    }
  };

  public void addButton(LinearLayout layout, String text, final Runnable runnable) {
    Button button = new Button(mContext);
    button.setText(text);
    button.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        new AsyncTask<Void, Void, Void>() {
          @Override
          protected Void doInBackground(Void... voids) {
            runnable.run();
            return null;
          }
        }.execute();
      }
    });

    layout.addView(button);
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    mContext = this;

    sigmaA = new SigmaFactory(this, SigmaFactoryA.class);

    sigmaB = new SigmaFactory(this, SigmaFactoryB.class);

    LinearLayout layout = new LinearLayout(mContext);
    layout.setOrientation(LinearLayout.VERTICAL);

    addButton(layout, "Start services", mStartServices);
    addButton(layout, "Stop services", mStopServices);

    addButton(layout, "SensorEvents", mTestSensorEvents);

    {
      Button button = new Button(mContext);
      button.setText("getRemoteSystemServiceAsBinder");
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
              URI remoteHost = mService9.getBaseURI();
              SigmaManager remote = mService8.getRemoteManager(remoteHost);

              RemoteContext remoteContext = RemoteContext.getRemoteContext(mContext, remote);
              final LocationManager remoteLocationManager =
                  (LocationManager) remoteContext.getSystemService(Context.LOCATION_SERVICE);

              List<String> allProviders = remoteLocationManager.getAllProviders();
              Log.d(TAG, allProviders.toString());
              //Location lastLocation = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
              //Log.d(TAG, lastLocation.toString());
              remoteLocationManager.requestLocationUpdates("test", 0, 0, mLocationListener,
                                                           MainActivity.this.getMainLooper());

              Intent intent = new Intent(mContext, NativeLocationPoster.class);
              PendingIntent pi = PendingIntent.getBroadcast(mContext, 0, intent,
                                                            PendingIntent.FLAG_UPDATE_CURRENT);
              remoteLocationManager.requestLocationUpdates("test", 0, 0, pi);

              Log.d(TAG, "Done registering location listener!");

              return null;
            }
          }.execute((Void) null);
        }
      });
      layout.addView(button);
    }

    {
      Button button = new Button(mContext);
      button.setText("enableTestProvider");
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {
          try {
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .removeUpdates(mLocationListenerB);
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .removeTestProvider("test");
          } catch (Exception ex) {
            Log.d(TAG, "Error REMOVING test provider");
          }

          try {
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .addTestProvider("test", false, false,
                                 false, false, true, true, true, 0, 5);
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .setTestProviderEnabled("test", true);
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .requestLocationUpdates("test", 0, 0, mLocationListenerB,
                                        MainActivity.this.getMainLooper());
          } catch (Exception ex) {
            Log.d(TAG, "Error ADDING test provider");
          }
        }
      });
      layout.addView(button);
    }

    {
      Button button = new Button(mContext);
      button.setText("setRandomLocation");
      button.setOnClickListener(new View.OnClickListener() {
        @Override
        public void onClick(View view) {

          try {
            ((LocationManager) getSystemService(LOCATION_SERVICE)).removeTestProvider("test");
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .addTestProvider("test", false, false,
                                 false, false, true, true, true, 0, 5);
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .setTestProviderEnabled("test", true);
            ((LocationManager) getSystemService(LOCATION_SERVICE))
                .requestLocationUpdates("test", 0, 0, mLocationListenerB,
                                        MainActivity.this.getMainLooper());
          } catch (Exception ex) {
            ex.printStackTrace();
          }

                    /*
                    ((LocationManager)getSystemService(LOCATION_SERVICE))
                            .setTestProviderEnabled(LocationManager.GPS_PROVIDER, true);
                    */
          Location loc = new Location("test");
          Random r = new Random();
          loc.setLatitude(13.37 + r.nextGaussian());
          loc.setLongitude(13.37 + r.nextGaussian());
          loc.setAltitude(10);
          loc.setAccuracy(1);
          loc.setTime(System.currentTimeMillis());
          loc.setElapsedRealtimeNanos(System.nanoTime());
          ((LocationManager) getSystemService(LOCATION_SERVICE))
              .setTestProviderLocation("test", loc);
        }
      });
      layout.addView(button);
    }

    setContentView(layout);
  }

  private LocationListener mLocationListener = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
      Log.d(TAG, "onLocationChanged");
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
      Log.d(TAG, "onStatusChanged");


    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
  };

  private LocationListener mLocationListenerB = new LocationListener() {
    @Override
    public void onLocationChanged(Location location) {
      Log.d(TAG, "B - onLocationChanged");
    }

    @Override
    public void onStatusChanged(String s, int i, Bundle bundle) {
      Log.d(TAG, "B - onStatusChanged");


    }

    @Override
    public void onProviderEnabled(String s) {

    }

    @Override
    public void onProviderDisabled(String s) {

    }
  };


}
