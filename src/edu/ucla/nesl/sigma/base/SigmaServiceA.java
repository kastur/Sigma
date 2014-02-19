package edu.ucla.nesl.sigma.base;

import edu.ucla.nesl.sigma.P.URI;

// Since a service can only be bound to once, we have two Sigma services (A and B) to be able to
// test Sigma with two Sigma Engines running on the same device.
// Each service must declare an "android:process" attribute in the AndroidManifest.xml.
public class SigmaServiceA extends SigmaService {
    public SigmaServiceA() {
        super("SigmaServiceA");
    }

    public static URI getURILocal() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.LOCAL)
                .type(URI.ObjectType.BASE)
                .name("ΣA")
                .className(SigmaServiceA.class.getName())
                .build();
        return uri;
    }

    public static URI getURINative() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.NATIVE)
                .type(URI.ObjectType.BASE)
                .name("ΣA")
                .className(SigmaServiceA.class.getName())
                .build();
        return uri;
    }

    public static URI getLocalHttp() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.HTTP)
                .type(URI.ObjectType.BASE)
                .name("ΣA")
                .host("localhost")
                .port(9090)
                .build();
        return uri;
    }

    public static URI getProxyHttp() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.HTTP)
                .type(URI.ObjectType.BASE)
                .name("ΣA")
                .host("localhost")
                .proxyhost("192.168.1.7")
                .port(9090)
                .build();
        return uri;
    }
}
