package edu.ucla.nesl.sigma.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.base.SigmaFactoryService;
import edu.ucla.nesl.sigma.base.SigmaManager;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class SigmaFactory implements ServiceConnection {

  private static final String TAG = SigmaFactory.class.getName();

  private final Context mContext;
  private ISigmaFactory mFactory;
  final private Semaphore mSemaphore = new Semaphore(1);

  public final Class<? extends SigmaFactoryService> mFactoryClass;

  public SigmaFactory(Context context, Class<? extends SigmaFactoryService> factoryClass) {
    mFactoryClass = factoryClass;
    mContext = context;
    mFactory = null;
    connect();
  }

  public void finalize() {
    disconnect();
  }

  public synchronized SigmaManager newInstance(URI uri, Bundle extras) {
    if (mFactory == null) {
      throwUnexpected(new IllegalStateException("ISigmaFactory is not set."));
    }
    try {
      return new SigmaManager(mFactory.newInstance(uri.toByteArray(), extras));
    } catch (RemoteException ex) {
      throwUnexpected(ex);
      return null;
    }
  }

  private synchronized void connect() {
    if (mFactory != null) {
      throwUnexpected(new IllegalStateException("ISigmaServiceConnection is already connected."));
    }
    Intent intent = new Intent(mContext, mFactoryClass);
    //mContext.bindService(intent, SigmaFactory.this, Context.BIND_AUTO_CREATE);
    mFactory = ISigmaFactory.Stub.asInterface(SigmaEngine.getService(mContext, intent).first);
  }

  public synchronized void disconnect() {
    mContext.unbindService(this);
  }

  @Override
  public synchronized void onServiceConnected(ComponentName componentName, IBinder binder) {
    if (mFactory != null) {
      throwUnexpected(new IllegalStateException("ISigmaServiceConnection is already connected."));
    }

    String descriptor = "";
    try {
      descriptor = binder.getInterfaceDescriptor();
    } catch (RemoteException ex) {
      throwUnexpected(ex);
    }

    LogDebug(TAG, "onServiceConnected(): " + descriptor);
    mFactory = ISigmaFactory.Stub.asInterface(binder);
    mSemaphore.release();
  }

  @Override
  public synchronized void onServiceDisconnected(ComponentName componentName) {
    LogDebug(TAG, "onServiceDisconnected()");
    mFactory = null;
  }

}
