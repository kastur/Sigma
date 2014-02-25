package edu.ucla.nesl.sigma.samples.sensor;

import android.content.Context;
import android.graphics.Color;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.ViewGroup;
import android.widget.Toast;

import org.achartengine.GraphicalView;
import org.achartengine.model.XYSeries;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeSet;

import edu.ucla.nesl.sigma.api.RemoteContext;
import edu.ucla.nesl.sigma.api.SigmaFactory;
import edu.ucla.nesl.sigma.base.SigmaFactoryA;
import edu.ucla.nesl.sigma.base.SigmaFactoryB;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;
import edu.ucla.nesl.sigma.samples.TestXmpp;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;

public class SensorActivity extends BunchOfButtonsActivity {

  public static final boolean ENABLE_PLOTTING = true;
  private static final String TAG = "SensorActivity";

  SigmaFactory connA, connB;
  SigmaManager sigmaA, sigmaB;
  SensorManager remoteSensorManager;

  PlottingSensorEventListener mNativeListener;
  PlottingSensorEventListener mRemoteListener;

  LineChart mChart;

  long mBaseTime = System.currentTimeMillis();


  class EventSample {

    public long nativeReceiveTime;
    public long remoteReceiveTime;

    public EventSample() {
      nativeReceiveTime = -1;
      remoteReceiveTime = -1;
    }
  }

  final HashMap<Long, EventSample> mEventSamples = new HashMap<Long, EventSample>();


  private class PlottingSensorEventListener implements SensorEventListener {

    public static final String NATIVE_LISTENER = "native";
    public static final String REMOTE_LISTENER = "remote";

    public static final int NUM_POINTS = 10;

    final String mName;
    final XYSeries series;
    final DecimalFormat mFormat;

    int mIndex;
    int nEvents;

    public PlottingSensorEventListener(LineChart chart, String name, int color) {
      mName = name;
      series = chart.addSeries(name, color);
      mFormat = new DecimalFormat("+#.###");
      reset();
    }

    public synchronized void reset() {
      mIndex = 0;
      series.clear();
      mChart.getView().repaint();

      for (int ii = 0; ii < NUM_POINTS; ++ii) {
        series.add(ii, ii, 0);
      }

      nEvents = 0;
    }

    @Override
    public synchronized void onSensorChanged(SensorEvent sensorEvent) {
      nEvents++;

      if (nEvents % 100 == 0) {
        Log.d(TAG, "SensorEventListener-" + mName + " received " + nEvents + " events");
      }

      long eventTimestamp = sensorEvent.timestamp;
      long receivedNanoTime = System.nanoTime();

      EventSample entry;
      synchronized (mEventSamples) {
        if (!mEventSamples.containsKey(eventTimestamp)) {
          entry = new EventSample();
          mEventSamples.put(eventTimestamp, entry);
        } else {
          entry = mEventSamples.get(eventTimestamp);
        }

        if (NATIVE_LISTENER.equals(mName)) {
          entry.nativeReceiveTime = receivedNanoTime;
        }

        if (REMOTE_LISTENER.equals(mName)) {
          entry.remoteReceiveTime = receivedNanoTime;
        }
      }

      if (ENABLE_PLOTTING) {
        double yval = sensorEvent.values[0];
                /*
                if (mName.equals(NATIVE_LISTENER)) {
                    yval += 1;
                } else {
                    yval -= 1;
                }
                */
        double xval = sensorEvent.timestamp / (1000.0 * 1000.0) % LineChart.X_AXIS_MAX;
        series.remove(mIndex);
        series.add(mIndex, xval, yval);
        mIndex = (mIndex + 1) % NUM_POINTS;
      }

      LogDebug(TAG, mName + " SENSOR_EVENT:" +
                    mFormat.format(sensorEvent.values[0]) + ", " +
                    mFormat.format(sensorEvent.values[1]) + ", " +
                    mFormat.format(sensorEvent.values[2]) + ", " +
                    sensorEvent.timestamp);
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int i) {
    }
  }

  @Override
  protected void onDestroy() {
    connA.disconnect();
    connB.disconnect();
    super.onDestroy();
  }

  @Override
  public void onCreateHook() {
    connA = new SigmaFactory(this, SigmaFactoryA.class);

    connB = new SigmaFactory(this, SigmaFactoryB.class);

    mChart = new LineChart(this);

    if (ENABLE_PLOTTING) {
      GraphicalView view = mChart.getView();
      view.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600));
      getLayout().addView(view);
      TimerTask task = new TimerTask() {
        @Override
        public void run() {
          mChart.getView().repaint();
        }
      };
      Timer timer = new Timer();
      timer.scheduleAtFixedRate(task, 500, 50);
    }

    mNativeListener =
        new PlottingSensorEventListener(mChart, PlottingSensorEventListener.NATIVE_LISTENER,
                                        Color.GREEN);
    mRemoteListener =
        new PlottingSensorEventListener(mChart, PlottingSensorEventListener.REMOTE_LISTENER,
                                        Color.RED);

    addButton("ΣA & ΣB LOCAL", new Runnable() {
      @Override
      public void run() {
        sigmaA = connA.newInstance(SigmaFactoryA.getURILocal(), null);
        sigmaB = connB.newInstance(SigmaFactoryB.getURILocal(), null);

        SigmaManager remote = sigmaA.getRemoteManager(sigmaB.getBaseURI());
        RemoteContext remoteContext = RemoteContext.getRemoteContext(SensorActivity.this, remote);
        remoteSensorManager =
            (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);
      }
    });

    addButton("ΣA & ΣB Local HTTP", new Runnable() {
      @Override
      public void run() {
        sigmaA = connA.newInstance(SigmaFactoryA.getLocalHttp(), null);
        sigmaB = connB.newInstance(SigmaFactoryB.getLocalHttp(), null);

        SigmaManager remote = sigmaA.getRemoteManager(sigmaB.getBaseURI());
        RemoteContext remoteContext = RemoteContext.getRemoteContext(SensorActivity.this, remote);
        remoteSensorManager =
            (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);
      }
    });

    addButton("ΣA & ΣB Proxy HTTP", new Runnable() {
      @Override
      public void run() {
        sigmaA = connA.newInstance(SigmaFactoryA.getProxyHttp(), null);
        sigmaB = connB.newInstance(SigmaFactoryB.getProxyHttp(), null);

        SigmaManager remote = sigmaA.getRemoteManager(sigmaB.getBaseURI());
        RemoteContext remoteContext = RemoteContext.getRemoteContext(SensorActivity.this, remote);
        remoteSensorManager =
            (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);
      }
    });

    addButton("ΣA & ΣB XMPP", new Runnable() {
      @Override
      public void run() {
        Toast.makeText(SensorActivity.this, "Trying to login to XMPP", Toast.LENGTH_LONG).show();
        sigmaA = connA.newInstance(TestXmpp.getXmppB(), TestXmpp.getPasswordBundeB());
        Toast.makeText(SensorActivity.this, "Logged in as rr@", Toast.LENGTH_LONG).show();
        sigmaB = connB.newInstance(TestXmpp.getXmppA(), TestXmpp.getPasswordBundleA());
        Toast.makeText(SensorActivity.this, "Trying in as kk@", Toast.LENGTH_LONG).show();
        SigmaManager remote = sigmaA.getRemoteManager(sigmaB.getBaseURI());
        RemoteContext remoteContext = RemoteContext.getRemoteContext(SensorActivity.this, remote);
        remoteSensorManager =
            (SensorManager) remoteContext.getSystemService(Context.SENSOR_SERVICE);
      }
    });

    addButton("Register native+remote sensor events", new Runnable() {
      @Override
      public void run() {
        mNativeListener.reset();
        mRemoteListener.reset();

        {
          SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
          Sensor accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
          sensorManager
              .registerListener(mNativeListener, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
        }

        {
          Sensor accelerometer = remoteSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
          remoteSensorManager
              .registerListener(mRemoteListener, accelerometer, SensorManager.SENSOR_DELAY_UI);
        }
      }
    });

    addButton("Unregister remote+native", new Runnable() {
      @Override
      public void run() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(mNativeListener);
        remoteSensorManager.unregisterListener(mRemoteListener);

        try {
          Thread.sleep(2000);
        } catch (InterruptedException ex) {
        }

        computeAndLogEventStats();
      }
    });

    addButton("Native unregister", new Runnable() {
      @Override
      public void run() {
        SensorManager sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);
        sensorManager.unregisterListener(mNativeListener);
      }
    });
  }

  void computeAndLogEventStats() {
    int numSamples = 0;
    int nativeNull = 0;
    int remoteNull = 0;
    int remoteNeverReceived = 0;

    ArrayList<Double> diffs = new ArrayList<Double>();

    synchronized (mEventSamples) {
      SortedSet<Long> keys = new TreeSet<Long>(mEventSamples.keySet());
      for (Long key : keys) {
        EventSample sample = mEventSamples.get(key);

        numSamples++;

        if (sample.nativeReceiveTime == -1) {
          nativeNull++;
        }

        if (sample.remoteReceiveTime == -1) {
          remoteNull++;
        }

        if (sample.nativeReceiveTime > -1 && sample.remoteReceiveTime == -1) {
          remoteNeverReceived++;
        }

        if (sample.nativeReceiveTime > -1 && sample.remoteReceiveTime > -1) {
          diffs.add(new Double(sample.remoteReceiveTime - sample.nativeReceiveTime));
        } else {
          diffs.add(Double.NaN);
        }
      }
    }

    Log.i(TAG,
          "numSamples=" + numSamples + "\n" +
          "nativeNullt=" + nativeNull + "\n" +
          "remoteNullt=" + remoteNull + "\n" +
          "remoteNeverReceived=" + remoteNeverReceived + "\n");

    StringBuilder sb = new StringBuilder();
    sb.append("--------------------------------------------------\n");
    sb.append("diffsNano = [");
    int ii = 0;
    for (double diff : diffs) {
      sb.append(diff).append(", ");
      ii++;
      if (ii % 100 == 0) {
        sb.append("\n");
      }
    }
    sb.append("]\n--------------------------------------------------");

    System.out.println(sb.toString());
  }

}
