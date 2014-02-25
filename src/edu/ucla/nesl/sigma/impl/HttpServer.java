package edu.ucla.nesl.sigma.impl;


import android.os.RemoteException;

import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ByteArrayEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.api.IRequestHandler;
import edu.ucla.nesl.sigma.api.IRequestSender;
import edu.ucla.nesl.sigma.base.SigmaDebug;
import edu.ucla.nesl.sigma.base.SigmaWire;
import fi.iki.elonen.NanoHTTPD;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

class HttpServer extends NanoHTTPD {

  IRequestHandler mRequestHandler;
  IRequestSender mSender;

  public HttpServer(int port, IRequestHandler requestHandler) {
    super(port);
    mRequestHandler = requestHandler;
    mSender = new HttpSender();
  }

  @Override
  public Response serve(IHTTPSession session) {
    if (session.getMethod() != Method.POST || !"/sigma".equals(session.getUri())) {
      return new Response(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "NOT SUPPORTED");
    }

    Map<String, String> headers = session.getHeaders();
    int contentLength = Integer.parseInt(headers.get("content-length"));

    byte[] requestBytes;
    try {
      DataInputStream requestStream = new DataInputStream(session.getInputStream());
      requestBytes = new byte[contentLength];
      requestStream.readFully(requestBytes);
    } catch (IOException ex) {
      SigmaDebug.throwUnexpected(ex);
      return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, ex.getMessage());
    }

    byte[] responseBytes;
    try {
      responseBytes = mRequestHandler.handleRequest(requestBytes);
    } catch (RemoteException ex) {
      throwUnexpected(ex);
      return new Response(Response.Status.INTERNAL_ERROR, MIME_PLAINTEXT, ex.getMessage());
    }

    InputStream responseStream = new ByteArrayInputStream(responseBytes);
    return new Response(Response.Status.OK, MIME_PLAINTEXT, responseStream);
  }

  public IRequestSender getRequestSender() {
    return mSender;
  }
}

class HttpSender extends IRequestSender.Stub {

  @Override
  public byte[] send(byte[] requestBytes) {
    final SRequest request = SigmaWire.getInstance().parseFrom(requestBytes, SRequest.class);
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

    SResponse response = responseHolder.get();
    return response.toByteArray();
  }
}
