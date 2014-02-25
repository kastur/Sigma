package edu.ucla.nesl.sigma.impl.xmpp;

import android.os.Bundle;

import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.base.SigmaPeerFactory;
import edu.ucla.nesl.sigma.base.SigmaWire;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class XmppSigmaServer extends SigmaPeerFactory<XmppClient> {

  public static final String TAG = XmppSigmaServer.class.getName();

  @Override
  public XmppClient create(URI baseURI, IRequestHandler requestHandler, Bundle extras) {
    String password = extras.getString("password");
    final XmppClient client = new XmppClient(getConfig(baseURI, password), requestHandler);
    return client;
  }

  @Override
  public void start(final XmppClient inst) {
    final Semaphore semaphore = new Semaphore(1);
    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
      throwUnexpected(ex);
    }

    Thread t = new Thread() {
      @Override
      public void run() {
        inst.login();
        semaphore.release();
      }
    };

    t.start();
    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
      throwUnexpected(ex);
    }
  }

  @Override
  public void stop(XmppClient inst) {
    inst.disconnect();
  }

  @Override
  public byte[] send(XmppClient inst, byte[] requestBytes) {
    SRequest request = SigmaWire.getInstance().parseFrom(requestBytes, SRequest.class);
    SResponse response = inst.send(request);
    return response.toByteArray();
  }


  private static XmppClient.XmppConfig getConfig(URI uri, String password) {
    XmppClient.XmppConfig
        config =
        new XmppClient.XmppConfig(uri.host, uri.port, uri.domain, uri.login, password);
    return config;
  }
}


