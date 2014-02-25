package edu.ucla.nesl.sigma.base;

import android.content.Context;
import android.content.Intent;
import android.os.Debug;
import android.os.IBinder;
import android.os.RemoteException;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaManager;


public class SigmaManagerInternal extends ISigmaManager.Stub {

  final Context mContext;
  final SigmaEngine mEngine;

  public SigmaManagerInternal(Context context, SigmaEngine engine) {
    mContext = context;
    mEngine = engine;
  }

  @Override
  public ISigmaManager getRemoteManager(byte[] bytes) {
    URI targetBaseURI = SigmaWire.getInstance().parseURI(bytes);
    return mEngine.getRemoteManager(targetBaseURI);
  }

  @Override
  public byte[] getBaseURI() {
    return mEngine.getBaseURI().toByteArray();
  }

  @Override
  public IBinder getServiceManager() throws RemoteException {
    IBinder binder = mEngine.getServiceManager();
    return binder;
  }

  @Override
  public IBinder getService(Intent intent) throws RemoteException {
    // TODO: Handle this in mEngine so that the returned serviceConnection later unbound from this Context.
    IBinder binder = SigmaEngine.getService(mContext, intent).first;
    return binder;
  }

  @Override
  public void stop() {
  }

  @Override
  public void destroy() {
    mEngine.destroy();
  }

  @Override
  public void startTracing(String name) {
    Debug.startMethodTracing(name);
  }

  @Override
  public void stopTracing() {
    Debug.stopMethodTracing();
  }

  @Override
  public byte[] handleRequest(byte[] requestBytes) {
    SRequest request = SigmaWire.getInstance().parseFrom(requestBytes, SRequest.class);
    SResponse response = mEngine.handleRequest(request);
    return response.toByteArray();
  }
}
