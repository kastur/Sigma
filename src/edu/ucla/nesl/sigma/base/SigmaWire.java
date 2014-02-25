package edu.ucla.nesl.sigma.base;

import com.squareup.wire.Message;
import com.squareup.wire.Wire;

import java.io.IOException;

import edu.ucla.nesl.sigma.P.URI;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

// SigmaWire is a singleton class wrapping Wire, andshould be used for all parsing operations.
// Using (new Wire()).parseFrom(...) each time is expensive since it involves reflection.
// However, a single Wire() instance caches MessageTypes, so parsing is fast only when reusing the same Wire instance!
public class SigmaWire {

  static SigmaWire mInstance = null;

  public synchronized static SigmaWire getInstance() {
    if (mInstance == null) {
      mInstance = new SigmaWire();
    }

    return mInstance;
  }

  final Wire wire;

  private SigmaWire() {
    wire = new Wire();
  }

  public URI parseURI(byte[] bytes) {
    return parseFrom(bytes, URI.class);
  }

  public synchronized <M extends Message> M parseFrom(byte[] bytes, Class<M> messageClass) {
    try {
      return wire.parseFrom(bytes, messageClass);
    } catch (IOException ex) {
      throwUnexpected(ex);
      return null;
    }
  }

  public static String prettyURI(URI uri) {
    return uri.name + "@" + uri.protocol.toString().toLowerCase() + "://" + uri.host;
  }
}
