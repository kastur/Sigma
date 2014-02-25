package edu.ucla.nesl.sigma.api;

import android.content.Context;
import android.os.IBinder;
import android.os.Looper;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import edu.ucla.nesl.sigma.base.SigmaManager;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class RemoteContext {

  final Context mLocalContext;
  final Object mRemoteServiceManager;

  public static RemoteContext getRemoteContext(Context context, SigmaManager sigmaManager) {
    IBinder remoteBinder = sigmaManager.getServiceManager();
    return new RemoteContext(context, remoteBinder);
  }

  private RemoteContext(Context localContext, IBinder remoteBinder) {
    mLocalContext = localContext;
    try {
      mRemoteServiceManager = Class.forName("android.os.ServiceManagerNative")
          .getMethod("asInterface", IBinder.class)
          .invoke(null, remoteBinder);
    } catch (Exception ex) {
      throw new RuntimeException(ex.getMessage());
    }
  }

  private IBinder getServiceAsBinder(String name) {
    try {
      Method
          getServiceMethod =
          mRemoteServiceManager.getClass().getMethod("getService", String.class);
      Object serviceBinder = getServiceMethod.invoke(mRemoteServiceManager, name);
      return (IBinder) serviceBinder;
    } catch (InvocationTargetException ex) {
      throwUnexpected(ex);
    } catch (Exception ex) {
      throwUnexpected(ex);
    }
    return null;
  }

  public Object getSystemService(String name) {
    if (name.equals(Context.LOCATION_SERVICE)) {
      IBinder binderProxy = getServiceAsBinder(name);

      try {
        Object interfaceProxy = Class.forName("android.location.ILocationManager$Stub")
            .getMethod("asInterface", IBinder.class).invoke(null, binderProxy);
        Object locationManager = Class.forName("android.location.LocationManager")
            .getDeclaredConstructor(Context.class,
                                    Class.forName("android.location.ILocationManager"))
            .newInstance(mLocalContext, interfaceProxy);
        return locationManager;
      } catch (InvocationTargetException ex) {
        ex.getCause().printStackTrace();
        ex.printStackTrace();
      } catch (Exception ex) {
        ex.printStackTrace();
      }
    }

    // SensorManager should really be a Singleton instance shared by the thread, but is not that way right now.
    // Multiple calls to getSystemService(SENSOR_SERVICE) will return new instances. Since SensorEventConnections
    // are established from each instance of SensorManager, registering a listener to one sensorManager then
    // unregistering from the other will not work as expected. It would have to be unregistered from the
    // original sensorManager instance to which it was registered to.
    if (name.equals(Context.SENSOR_SERVICE)) {
      try {
        IBinder binderProxy = getServiceAsBinder("sensorservice");

        if (binderProxy == null) {
          throwUnexpected(new NullPointerException("Remote Service Binder Proxy is null!"));
        }

        Integer
            nativeHandleObject =
            (Integer) Class.forName("com.android.internal.os.BinderInternal")
                .getMethod("nativeHandleForBinder", IBinder.class)
                .invoke(null, binderProxy);
        int nativeHandle = nativeHandleObject.intValue();
        Object sensorManager = Class.forName("android.hardware.SystemSensorManager")
            .getConstructor(int.class, Looper.class)
            .newInstance(nativeHandle, mLocalContext.getMainLooper());
        return sensorManager;
      } catch (Exception ex) {
        ex.printStackTrace();
        throw new RuntimeException(ex);
      }
    }

    throwUnexpected(
        new IllegalStateException("Getting the \"" + name + "\" system service is not supported!"));

    // Similarly, other SystemServices can be retrieved by constructing
    // *Manager classes with the appropriate IBinder fetched from IServiceManager.

    return null;
  }
}
