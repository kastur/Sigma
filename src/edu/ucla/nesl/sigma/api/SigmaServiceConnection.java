package edu.ucla.nesl.sigma.api;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaService;

import java.util.concurrent.Semaphore;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class SigmaServiceConnection implements ServiceConnection {
    private static final String TAG = SigmaServiceConnection.class.getName();

    private final Context mContext;
    private ISigmaServiceConnection mService;
    final private Semaphore mSemaphore = new Semaphore(1);

    public final Class<? extends SigmaService> mServiceClass;

    public SigmaServiceConnection(Context context, Class<? extends SigmaService> serviceClass) {
        mServiceClass = serviceClass;
        mContext = context;
        mService = null;
        connect();
    }

    public void finalize() {
        disconnect();
    }

    public synchronized SigmaManager getImpl(URI uri, Bundle extras) {
        if (mService == null) {
            throwUnexpected(new IllegalStateException("ISigmaServiceConnection is not set."));
        }
        try {
            return new SigmaManager(mService.getImpl(uri.toByteArray(), extras));
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return null;
        }
    }

    private synchronized void connect() {
        if (mService != null) {
            throwUnexpected(new IllegalStateException("ISigmaServiceConnection is already connected."));
        }
        Intent intent = new Intent(mContext, mServiceClass);
        mContext.bindService(intent, SigmaServiceConnection.this, Context.BIND_AUTO_CREATE);
    }

    public synchronized void disconnect() {
        mContext.unbindService(this);
    }

    @Override
    public synchronized void onServiceConnected(ComponentName componentName, IBinder binder) {
        if (mService != null) {
            throwUnexpected(new IllegalStateException("ISigmaServiceConnection is already connected."));
        }

        String descriptor = "";
        try {
            descriptor = binder.getInterfaceDescriptor();
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }

        LogDebug(TAG, "onServiceConnected(): " + descriptor);
        mService = ISigmaServiceConnection.Stub.asInterface(binder);
        mSemaphore.release();
    }

    @Override
    public synchronized void onServiceDisconnected(ComponentName componentName) {
        LogDebug(TAG, "onServiceDisconnected()");
        mService = null;
    }

}
