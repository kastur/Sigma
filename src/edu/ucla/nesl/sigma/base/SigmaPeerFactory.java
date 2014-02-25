package edu.ucla.nesl.sigma.base;

import android.app.Service;
import android.content.Intent;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;

import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.api.ISigmaPeer;
import edu.ucla.nesl.sigma.api.ISigmaPeerFactory;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public abstract class SigmaPeerFactory<T> extends Service {

  final HashMap<URI, T> mInstances;

  public abstract T create(URI baseURI, IRequestHandler requestHandler, Bundle extras);

  public abstract void start(T inst);

  public abstract void stop(T inst);

  public abstract byte[] /* Wire SResponse */ send(T inst, byte[] /* Wire SRequest */ requestBytes);


  public SigmaPeerFactory() {
    mInstances = new HashMap<URI, T>();
  }

  @Override
  public IBinder onBind(Intent intent) {
    return null;
  }

  public IBinder mBinder = new ISigmaPeerFactory.Stub() {
    @Override
    public ISigmaPeer newInstance(byte[] uriBytes, IRequestHandler requestHandler, Bundle extras)
        throws RemoteException {
      URI baseURI = SigmaWire.getInstance().parseURI(uriBytes);
      if (mInstances.containsKey(baseURI)) {
        throwUnexpected(new IllegalStateException("Instance is already running"));
      }
      T inst = create(baseURI, requestHandler, extras);
      start(inst);
      mInstances.put(baseURI, inst);
      return new Sender(inst);
    }

    @Override
    public void destroyInstance(byte[] uriBytes) throws RemoteException {
      URI baseURI = SigmaWire.getInstance().parseURI(uriBytes);
      if (!mInstances.containsKey(baseURI)) {
        throwUnexpected(new IllegalStateException("Instance does not exist"));
      }

      T instance = mInstances.get(baseURI);
      stop(instance);

      mInstances.remove(baseURI);
    }
  };

  class Sender extends ISigmaPeer.Stub {

    final T mInstance;

    public Sender(T instance) {
      mInstance = instance;
    }

    @Override
    public byte[] send(byte[] requestBytes) throws RemoteException {
      return SigmaPeerFactory.this.send(mInstance, requestBytes);
    }
  }
}
