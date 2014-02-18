package edu.ucla.nesl.sigma.base;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.api.ISigmaServer;
import edu.ucla.nesl.sigma.api.ISigmaServiceConnection;
import edu.ucla.nesl.sigma.impl.HttpSigmaServer;
import edu.ucla.nesl.sigma.impl.LocalSigmaServer;
import edu.ucla.nesl.sigma.impl.xmpp.XmppSigmaServer;

import java.util.HashMap;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public abstract class SigmaService extends Service {
    private static final String TAG = SigmaService.class.getSimpleName();

    public final String mName;

    public SigmaService(String name) {
        mName = name;
    }

    final HashMap<URI, ISigmaServer> mActiveServices = new HashMap<URI, ISigmaServer>();


    @Override
    public IBinder onBind(Intent intent) {
        Log.d(TAG, "onBind()");
        return new ISigmaServiceConnection.Stub() {
            @Override
            public ISigmaManager getImpl(byte[] bytes, Bundle extras) throws RemoteException {
                URI uri = SigmaWire.getInstance().parseURI(bytes);

                if (!URI.ObjectType.BASE.equals(uri.type)) {
                    throwUnexpected(new IllegalArgumentException(
                            "Expecting URI { type=BASE }!" +
                            "Got instead: " + uri.type.toString()));
                }

                synchronized (mActiveServices) {
                    boolean isNew = true;

                    ISigmaServer ret = null;
                    if (mActiveServices.containsKey(uri)) {
                        isNew = false;
                        ret = mActiveServices.get(uri);
                    } else if (URI.Protocol.NATIVE.equals(uri.protocol)) {
                        throw new IllegalStateException("Unimplemented protocol");
                        // In the course of refactoring, this got unimplemented!
                        //ret = new NativeSigmaManager(SigmaService.this, uri);
                    } else if (URI.Protocol.LOCAL.equals(uri.protocol)) {
                        ret = new LocalSigmaServer(SigmaService.this, uri);
                    } else if (URI.Protocol.HTTP.equals(uri.protocol)) {
                        ret = new HttpSigmaServer(SigmaService.this, uri);
                    } else if (URI.Protocol.XMPP.equals(uri.protocol)) {
                        ret = new XmppSigmaServer(SigmaService.this, uri, extras.getString("password"));

                    }

                    if (isNew) {
                        mActiveServices.put(uri, ret);
                        ret.start();
                    }

                    return ret.getEngine().getManager();
                }
            }
        };
    }
}
