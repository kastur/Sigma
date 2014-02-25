package edu.ucla.nesl.sigma.impl;

import android.content.Context;
import android.util.Log;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.conn.util.InetAddressUtils;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaServer;
import edu.ucla.nesl.sigma.base.SigmaDebug;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.base.SigmaWire;
import fi.iki.elonen.NanoHTTPD;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class HttpSigmaServer implements ISigmaServer {

  private static final String TAG = HttpSigmaServer.class.getSimpleName();
  final private HttpServer mServer;
  final private SigmaEngine mEngine;

  public HttpSigmaServer(Context context, URI baseURI) {
    mEngine = new SigmaEngine(context, baseURI, new HttpRequestFactory());
    mServer = new HttpServer();
  }

  @Override
  public SigmaEngine getEngine() {
    return mEngine;
  }

  @Override
  public void start() {
    Semaphore semaphore = new Semaphore(1);
    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
    }
    try {
      mServer.start();
      semaphore.release();
    } catch (IOException ex) {
      SigmaDebug.throwUnexpected(ex);
    }

    try {
      semaphore.acquire();
    } catch (InterruptedException ex) {
    }

    Log.d(TAG,
          "- - - - - HTTP alive:" + mServer.isAlive() + ", listening: " + mServer.getListeningPort()
          + "- - - - ");
  }

  @Override
  public void stop() {
    mServer.stop();
  }

  private class HttpServer extends NanoHTTPD {

    public HttpServer() {
      super(mEngine.getBaseURI().port);
    }

    @Override
    public Response serve(IHTTPSession session) {
      if (session.getMethod() != Method.POST || !"/sigma".equals(session.getUri())) {
        return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "NOT SUPPORTED");
      }

      Map<String, String> headers = session.getHeaders();
      int contentLength = Integer.parseInt(headers.get("content-length"));

      SRequest request;
      try {
        DataInputStream requestStream = new DataInputStream(session.getInputStream());
        byte[] requestArray = new byte[contentLength];
        requestStream.readFully(requestArray);
        request = SigmaWire.getInstance().parseFrom(requestArray, SRequest.class);
      } catch (IOException ex) {
        SigmaDebug.throwUnexpected(ex);
        return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, ex.getMessage());
      }

      SResponse response = mEngine.serve(request);

      InputStream responseStream = new ByteArrayInputStream(response.toByteArray());
      return new Response(Response.Status.OK, MIME_PLAINTEXT, responseStream);

    }
  }

  private class HttpRequestFactory implements SigmaEngine.IRequestFactory {

    public SResponse doTransaction(final SRequest request) {
      class Holder<T> {

        T mObject;

        public synchronized void put(T object) {
          mObject = object;
        }

        public synchronized T get() {
          return mObject;
        }

      }
      final Holder<SResponse> responseHolder = new Holder<SResponse>();

      Thread t = new Thread() {
        @Override
        public void run() {

          String host;
          if (request.target.proxyhost != null) {
            host = request.target.proxyhost;
          } else {
            host = request.target.host;
          }

          String url = "http://" + host + ":" + request.target.port + "/sigma";
          try {
            DefaultHttpClient client = new DefaultHttpClient();
            HttpPost httpPost = new HttpPost(url);
            ByteArrayEntity entity = new ByteArrayEntity(request.toByteArray());
            entity.setContentType("application/wire");
            httpPost.setEntity(entity);
            HttpResponse httpResponse = client.execute(httpPost);
            byte[] responseArray = EntityUtils.toByteArray(httpResponse.getEntity());

            SResponse response = SigmaWire.getInstance().parseFrom(responseArray, SResponse.class);
            responseHolder.put(response);
          } catch (Exception ex) {
            throwUnexpected(ex);
          }
        }
      };
      t.start();
      try {
        t.join();
      } catch (InterruptedException ex) {
        throwUnexpected(ex);
      }

      return responseHolder.get();
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
