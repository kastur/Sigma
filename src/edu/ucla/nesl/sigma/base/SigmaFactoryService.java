package edu.ucla.nesl.sigma.base;

import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.HashMap;
import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.api.IRequestSender;
import edu.ucla.nesl.sigma.api.ISigmaFactory;
import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.api.ISigmaPeer;
import edu.ucla.nesl.sigma.api.ISigmaPeerFactory;
import edu.ucla.nesl.sigma.impl.HttpSigmaServer;
import edu.ucla.nesl.sigma.impl.LocalSigmaServer;
import edu.ucla.nesl.sigma.impl.xmpp.XmppSigmaServer;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public abstract class SigmaFactoryService extends Service {
  public static final String TAG = SigmaFactoryService.class.getSimpleName();

  final String mName;
  final HashMap<URI, SigmaRecord> mInstances;

  public SigmaFactoryService(String name) {
    mName = name;
    mInstances = new HashMap<URI, SigmaRecord>();
  }

  final HashMap<URI.Protocol, ISigmaPeerFactory> mPeerFactories =
      new HashMap<URI.Protocol, ISigmaPeerFactory>();

  @Override
  public void onCreate() {
    final Semaphore s = new Semaphore(3);
    try { s.acquire(3); } catch(InterruptedException ex) { throwUnexpected(ex); }
    bindTo(URI.Protocol.LOCAL, s);
    bindTo(URI.Protocol.HTTP, s);
    bindTo(URI.Protocol.XMPP, s);
    try { s.acquire(); } catch(InterruptedException ex) { throwUnexpected(ex); }
  }

  private void bindTo(final URI.Protocol protocol, final Semaphore s) {
    Class serviceClass = null;
    switch (protocol) {
      case LOCAL:
        serviceClass = LocalSigmaServer.class;
        break;
      case HTTP:
        serviceClass = HttpSigmaServer.class;
        break;
      case XMPP:
        serviceClass = XmppSigmaServer.class;
        break;
      default:
        throwUnexpected(new IllegalArgumentException("Unsupported or deprecated protocol."));
    }

    final Intent service = new Intent(SigmaFactoryService.this, serviceClass);
    final ServiceConnection conn = new ServiceConnection() {
      @Override
      public void onServiceConnected(ComponentName componentName, IBinder binder) {
        LogDebug(TAG, "PEER onServiceConnected");
        ISigmaPeerFactory peerFactory = ISigmaPeerFactory.Stub.asInterface(binder);
        mPeerFactories.put(protocol, peerFactory);
        s.release();
      }

      @Override
      public void onServiceDisconnected(ComponentName componentName) {
        LogDebug(TAG, "PEER onServiceDisconnected");
      }
    };

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... voids) {
        bindService(service, conn, BIND_AUTO_CREATE | BIND_ABOVE_CLIENT);
        return null;
      }
    }.execute();

  }

  class Holder<T> {

    T val;

    public void set(T value) {
      val = value;
    }

    public T get() {
      return val;
    }
  }

  class SigmaRecord {

    public final URI uri;
    public final Holder<SigmaEngine> mEngine;
    public final Holder<ISigmaPeer> mPeer;
    public final IRequestSender requestSender;
    public final IRequestHandler requestHandler;

    public SigmaRecord(URI uri) {
      this.uri = uri;
      mPeer = new Holder<ISigmaPeer>();
      mEngine = new Holder<SigmaEngine>();

      requestSender = new IRequestSender.Stub() {
        @Override
        public byte[] send(byte[] requestBytes) throws RemoteException {
          synchronized (mPeer) {
            if (mPeer.get() == null) {
              try {
                LogDebug(TAG, "Waiting for mPeer");
                mPeer.wait();
              } catch (InterruptedException ex) {
                throwUnexpected(ex);
              }
            }
            return mPeer.get().send(requestBytes);
          }
        }
      };

      requestHandler = new IRequestHandler.Stub() {
        @Override
        public byte[] handleRequest(byte[] requestBytes) throws RemoteException {
          synchronized (mEngine) {
            if (mEngine.get() == null) {
              try {
                LogDebug(TAG, "Waiting for mEngine");
                mEngine.wait();
              } catch (InterruptedException ex) {
                throwUnexpected(ex);
              }
            }
            SRequest request = SigmaWire.getInstance().parseFrom(requestBytes, SRequest.class);
            SResponse response = mEngine.get().handleRequest(request);
            return response.toByteArray();
          }
        }
      };
    }

    public void setPeer(ISigmaPeer peer) {
      synchronized (mPeer) {
        mPeer.set(peer);
        mPeer.notifyAll();
      }
    }

    public void setEngine(SigmaEngine engine) {
      synchronized (mEngine) {
        mEngine.set(engine);
        mEngine.notifyAll();
      }
    }

  }

  @Override
  public IBinder onBind(Intent intent) {
    return new ISigmaFactory.Stub() {
      @Override
      public ISigmaManager newInstance(byte[] bytes, final Bundle extras) throws RemoteException {
        final URI uri = SigmaWire.getInstance().parseURI(bytes);
        checkBaseURI(uri);

        if (mInstances.containsKey(uri)) {
          SigmaRecord record = mInstances.get(uri);
          return record.mEngine.get().getManager();
        }

        final SigmaRecord record = new SigmaRecord(uri);

        SigmaEngine engine = new SigmaEngine(SigmaFactoryService.this, uri, record.requestSender);
        record.setEngine(engine);

        ISigmaPeerFactory peerFactory = mPeerFactories.get(uri.protocol);
        ISigmaPeer peer = peerFactory.newInstance(uri.toByteArray(), record.requestHandler, extras);
        record.setPeer(peer);

        mInstances.put(uri, record);
        return engine.getManager();
      }

      public void destroyInstance(byte[] bytes) {
        URI uri = SigmaWire.getInstance().parseURI(bytes);
        checkBaseURI(uri);
        if (!mInstances.containsKey(uri)) {
          throwUnexpected(new IllegalArgumentException("No instances exists with this URI"));
          return;
        }

        SigmaRecord record = mInstances.get(uri);
        mInstances.remove(uri);
      }
    };
  }

  private static void checkBaseURI(URI uri) {
    if (!URI.ObjectType.BASE.equals(uri.type)) {
      throwUnexpected(new IllegalArgumentException(
          "Expecting URI { type=BASE }!" +
          "Got instead: " + uri.type.toString()));
    }
  }
}
