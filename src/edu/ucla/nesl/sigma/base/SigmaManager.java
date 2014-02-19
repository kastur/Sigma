package edu.ucla.nesl.sigma.base;

import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaManager;

import java.util.HashSet;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class SigmaManager {
    public static final String TAG = SigmaManager.class.getName();

    final ISigmaManager mService;
    final HashSet<URI> mConnectURIs;

    public SigmaManager(ISigmaManager service) {
        mService = service;
        mConnectURIs = new HashSet<URI>();
    }

    public URI getBaseURI() {
        try {
            return SigmaWire.getInstance().parseURI(mService.getBaseURI());
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return null;
        }
    }

    public ISigmaManager asBinder() {
        return mService;
    }

    public IBinder getServiceManager() {
        try {
            return mService.getServiceManager();
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return null;
        }
    }

    public IBinder getService(Intent intent) {
        try {
            return mService.getService(intent);
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return null;
        }
    }

    public SigmaManager getRemoteManager(URI remoteURI) {
        try {
            return new SigmaManager(mService.getRemoteManager(remoteURI.toByteArray()));
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return null;
        }
    }

    public void startTracing(String traceName) {
        try {
            mService.startTracing(traceName);
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }
    }

    public void stopTracing() {
        try {
            mService.stopTracing();
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }
    }

    public void stop() {
        try {
            mService.destroy();
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }
    }

    public void destroy() {
        try {
            mService.destroy();
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }
    }
}
