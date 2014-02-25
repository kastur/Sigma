package edu.ucla.nesl.sigma.impl;

import android.os.Bundle;
import android.os.RemoteException;

import org.apache.http.conn.util.InetAddressUtils;

import java.io.IOException;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.base.SigmaPeerFactory;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class HttpSigmaServer extends SigmaPeerFactory<HttpServer> {

  private static final String TAG = HttpSigmaServer.class.getSimpleName();

  @Override
  public HttpServer create(URI baseURI, IRequestHandler requestHandler, Bundle extras) {
    int port = baseURI.port;
    HttpServer server = new HttpServer(port, requestHandler);
    return server;
  }

  @Override
  public void start(HttpServer inst) {
    Semaphore semaphore = new Semaphore(1);
    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
      throwUnexpected(ex);
    }
    try {
      inst.start();
      semaphore.release();
    } catch (IOException ex) {
      throwUnexpected(ex);
    }

    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
      throwUnexpected(ex);
    }
  }

  @Override
  public void stop(HttpServer inst) {
    inst.stop();
  }

  @Override
  public byte[] send(HttpServer inst, byte[] requestBytes) {
    try {
      return inst.getRequestSender().send(requestBytes);
    } catch (RemoteException ex) {
      throwUnexpected(new IllegalStateException("Cannot get Http request sender."));
      return null;
    }
  }

  public static String getIPAddress(boolean useIPv4) {
    try {
      List<NetworkInterface> interfaces = Collections.list(NetworkInterface.getNetworkInterfaces());
      for (NetworkInterface intf : interfaces) {
        List<InetAddress> addrs = Collections.list(intf.getInetAddresses());
        for (InetAddress addr : addrs) {
          if (!addr.isLoopbackAddress()) {
            String sAddr = addr.getHostAddress().toUpperCase();
            boolean isIPv4 = InetAddressUtils.isIPv4Address(sAddr);
            if (useIPv4) {
              if (isIPv4) {
                return sAddr;
              }
            } else {
              if (!isIPv4) {
                int delim = sAddr.indexOf('%'); // drop ip6 port suffix
                return delim < 0 ? sAddr : sAddr.substring(0, delim);
              }
            }
          }
        }
      }
    } catch (Exception ex) { /* EAT */ }
    return "";
  }
}
