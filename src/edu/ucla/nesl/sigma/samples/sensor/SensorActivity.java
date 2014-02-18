package edu.ucla.nesl.sigma.samples.sensor;

import android.content.Context;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.RemoteContext;
import edu.ucla.nesl.sigma.api.SigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaServiceA;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;
import edu.ucla.nesl.sigma.samples.TestUtils;

public class SensorActivity extends BunchOfButtonsActivity {
    private static final String TAG = "SensorActivity";

    SigmaServiceConnection sigma;
    SigmaManager sigmaXmpp;
    RemoteContext remoteContext;
    SensorManager mSensorManager;

    private SensorEventListener listener = new SensorEventListener() {
        @Override
        public void onSensorChanged(SensorEvent sensorEvent) {
            Log.d(TAG, "SENSOR_EVENT:" +
                    sensorEvent.values[0] + ", " +
                    sensorEvent.values[1] + ", " +
                    sensorEvent.values[2] + ", " +
                    sensorEvent.timestamp);
        }

        @Override
        public void onAccuracyChanged(Sensor sensor, int i) {

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sigma = new SigmaServiceConnection(this, SigmaServiceA.class);
        sigma.connect();
    }

    @Override
    protected void onDestroy() {
        sigma.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCreateHook() {

        addButton("\"Σ\" xmpp kk@", new Runnable() {
            @Override
            public void run() {
                sigmaXmpp = sigma.getImpl(TestUtils.getUriXmppKK(), TestUtils.getXmppPasswordBundleKK());
                initRemoteContext();
            }
        });

        addButton("\"Σ\" xmpp rr@", new Runnable() {
            @Override
            public void run() {
                sigmaXmpp = sigma.getImpl(TestUtils.getUriXmppRR(), TestUtils.getXmppPasswordBundleRR());
                initRemoteContext();
            }
        });

        /*
        addButton("Native register", new Runnable() {
            @Override
            public void run() {
                SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                sensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });

        addButton("Native unregister", new Runnable() {
            @Override
            public void run() {
                SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
                sensorManager.unregisterListener(listener);
            }
        });
        */


        addButton("Xmpp receive sensor events", new Runnable() {
            @Override
            public void run() {
                Sensor accelerometer = mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
                mSensorManager.registerListener(listener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            }
        });

        addButton("Xmpp unregister", new Runnable() {
            @Override
            public void run() {
                mSensorManager.unregisterListener(listener);
            }
        });
    }

    private void initRemoteContext() {
        URI selfURI = sigmaXmpp.getBaseURI();
        String remoteLogin = selfURI.login.equals("kk") ? "rr" : "kk";
        URI remoteURI = (new URI.Builder(selfURI)).login(remoteLogin).build();
        SigmaManager remote = sigmaXmpp.getRemoteManager(remoteURI);
        remoteContext = new RemoteContext(SensorActivity.this, remote.getServiceManager());
        Log.d(TAG, "Initialized remote context");
        Log.d(TAG, "selfURI: " + selfURI.toString());
        Log.d(TAG, "remoteURI: " + remoteURI.toString());
        mSensorManager =
                (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);

    }
}
