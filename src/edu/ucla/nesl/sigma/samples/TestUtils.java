package edu.ucla.nesl.sigma.samples;

import android.os.Bundle;
import edu.ucla.nesl.sigma.P.URI;

public class TestUtils {

    private static URI.Builder getUriXmppBuilder() {
        return (new URI.Builder())
                .protocol(URI.Protocol.XMPP)
                .type(URI.ObjectType.BASE)
                        //.host("192.168.1.7")
                .host("ec2-54-227-52-216.compute-1.amazonaws.com")
                .port(5222)
                .domain("localhost");
        //.domain("quark");

    }

    public static URI getUriXmppKK() {
        return getUriXmppBuilder().login("kk").build();
    }

    public static URI getUriXmppRR() {
        return getUriXmppBuilder().login("rr").build();
    }

    private static Bundle getXmppDefaultPasswordBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("password", "qweasd");
        return bundle;
    }

    public static Bundle getXmppPasswordBundleKK() {
        return getXmppDefaultPasswordBundle();
    }

    public static Bundle getXmppPasswordBundleRR() {
        return getXmppDefaultPasswordBundle();
    }

    public static URI getURIHttp8() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.HTTP)
                .type(URI.ObjectType.BASE)
                .host("localhost")
                .port(8080)
                .build();
        return uri;
    }

    public static URI getURIHttp9() {
        URI uri = (new URI.Builder())
                .protocol(URI.Protocol.HTTP)
                .type(URI.ObjectType.BASE)
                .host("localhost")
                .port(9090)
                .build();
        return uri;
    }

}
