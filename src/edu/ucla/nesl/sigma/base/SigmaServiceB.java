package edu.ucla.nesl.sigma.base;

import edu.ucla.nesl.sigma.P.URI;

// Since a service can only be bound to once, we have two Sigma services (A and B) to be able to
// test Sigma with two Sigma Engines running on the same device.
// Each service must declare an "android:process" attribute in the AndroidManifest.xml.
public class SigmaServiceB extends SigmaService {

  public SigmaServiceB() {
    super("SigmaServiceB");
  }

  public static URI getURILocal() {
    URI uri = (new URI.Builder())
        .protocol(URI.Protocol.LOCAL)
        .type(URI.ObjectType.BASE)
        .name("ΣA")
        .className(SigmaServiceB.class.getName())
        .build();
    return uri;
  }

  public static URI getURINative() {
    URI uri = (new URI.Builder())
        .protocol(URI.Protocol.NATIVE)
        .type(URI.ObjectType.BASE)
        .name("ΣB")
        .className(SigmaServiceB.class.getName())
        .build();
    return uri;
  }

  public static URI getLocalHttp() {
    URI uri = (new URI.Builder())
        .protocol(URI.Protocol.HTTP)
        .type(URI.ObjectType.BASE)
        .name("ΣB")
        .host("localhost")
        .port(8080)
        .build();
    return uri;
  }


  public static URI getProxyHttp() {
    URI uri = (new URI.Builder())
        .protocol(URI.Protocol.HTTP)
        .type(URI.ObjectType.BASE)
        .name("ΣB")
        .host("localhost")
        .proxyhost("192.168.1.7")
        .port(8080)
        .build();
    return uri;
  }
}
