package edu.ucla.nesl.sigma.impl;


import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Pair;

import java.util.HashMap;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.api.ISigmaFactory;
import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.base.SigmaFactoryService;
import edu.ucla.nesl.sigma.base.SigmaPeerFactory;
import edu.ucla.nesl.sigma.base.SigmaWire;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class LocalSigmaServer extends SigmaPeerFactory<URI> {

  private class ConnectionRecord {

    public Class<? extends SigmaFactoryService> targetClass;
    public ISigmaManager sigmaImpl;
    public ISigmaFactory sigmaConnection;
    public ServiceConnection serviceConnection;
  }

  final HashMap<String, ConnectionRecord> mConnections;

  public LocalSigmaServer() {
    super();
    mConnections = new HashMap<String, ConnectionRecord>();
  }

  @Override
  public URI create(URI baseURI, IRequestHandler requestHandler, Bundle extras) {
    return baseURI;
  }

  @Override
  public void start(URI inst) {

  }

  @Override
  public void stop(URI inst) {

  }

  @Override
  public byte[] send(URI inst, byte[] requestBytes) {
    return LocalSigmaServer.this.send(requestBytes);
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
          record.targetClass = (Class<? extends SigmaFactoryService>) Class.forName(uri.className);
        } catch (ClassNotFoundException ex) {
          throwUnexpected(ex);
        }

        Pair<IBinder, ServiceConnection> pair =
            SigmaEngine.getService(this, new Intent(this, record.targetClass));

        IBinder binder = pair.first;
        record.serviceConnection = pair.second;

        record.sigmaConnection = ISigmaFactory.Stub.asInterface(binder);

        try {
          URI baseURI = SigmaEngine.convertToBaseURI(uri);
          record.sigmaImpl = record.sigmaConnection.newInstance(baseURI.toByteArray(), null);
        } catch (RemoteException ex) {
          throwUnexpected(ex);
        }

        mConnections.put(uri.className, record);
      }
    }
    return record;
  }


  public byte[] send(byte[] requestBytes) {
    final SRequest request = SigmaWire.getInstance().parseFrom(requestBytes, SRequest.class);
    ConnectionRecord record = getOrCreateConnection(request.target);
    try {
      return record.sigmaImpl.handleRequest(requestBytes);
    } catch (RemoteException ex) {
      throwUnexpected(ex);
      return null;
    }
  }
}

