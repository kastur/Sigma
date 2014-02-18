package edu.ucla.nesl.sigma.impl;

import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;
import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.api.ISigmaServer;
import edu.ucla.nesl.sigma.api.ISigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.base.SigmaService;
import edu.ucla.nesl.sigma.base.SigmaWire;

import java.util.HashMap;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class LocalSigmaServer implements ISigmaServer {
    public static final String TAG = LocalSigmaServer.class.getName();
    final SigmaEngine mEngine;

    public LocalSigmaServer(Context context, URI uri) {
        mEngine = new SigmaEngine(context, uri, new LocalRequestFactory(context));
    }

    @Override
    public SigmaEngine getEngine() {
        return mEngine;
    }

    @Override
    public void start() {
        LogDebug(TAG, "start(): Stub!");

    }

    @Override
    public void stop() {
        LogDebug(TAG, "destroy(): Stub!");
    }

    private static class LocalRequestFactory implements SigmaEngine.IRequestFactory {
        final Context mContext;
        final HashMap<String, ConnectionRecord> mConnections;

        public LocalRequestFactory(Context context) {
            mContext = context;
            mConnections = new HashMap<String, ConnectionRecord>();
        }

        private ConnectionRecord getOrCreateConnection(URI uri) {
            ConnectionRecord record = null;
            synchronized (mConnections) {
                if (mConnections.containsKey(uri.className)) {
                    record = mConnections.get(uri.className);
                }

                if (record == null) {
                    record = new ConnectionRecord();
                    try {
                        record.targetClass = (Class<? extends SigmaService>) Class.forName(uri.className);
                    } catch (ClassNotFoundException ex) {
                        throwUnexpected(ex);
                    }

                    Pair<IBinder, ServiceConnection> pair =
                            SigmaEngine.getService(mContext, new Intent(mContext, record.targetClass));

                    IBinder binder = pair.first;
                    record.serviceConnection = pair.second;

                    record.sigmaConnection = ISigmaServiceConnection.Stub.asInterface(binder);

                    try {
                        URI baseURI = SigmaEngine.convertToBaseURI(uri);
                        record.sigmaImpl = record.sigmaConnection.getImpl(baseURI.toByteArray(), null);
                    } catch (RemoteException ex) {
                        throwUnexpected(ex);
                    }

                    mConnections.put(uri.className, record);
                }
            }
            return record;
        }

        private class ConnectionRecord {
            public Class<? extends SigmaService> targetClass;
            public ISigmaManager sigmaImpl;
            public ServiceConnection serviceConnection;
            public ISigmaServiceConnection sigmaConnection;
        }

        public SResponse doTransaction(final SRequest request) {
            ConnectionRecord record = getOrCreateConnection(request.target);
            try {
                byte[] responseBytes = record.sigmaImpl.handleRequest(request.toByteArray());
                return SigmaWire.getInstance().parseFrom(responseBytes, SResponse.class);
            } catch (RemoteException ex) {
                throwUnexpected(ex);
            }
            return null;
        }
    }
}
