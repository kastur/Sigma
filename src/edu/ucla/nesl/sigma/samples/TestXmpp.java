package edu.ucla.nesl.sigma.samples;

import android.os.Bundle;
import edu.ucla.nesl.sigma.P.URI;

public class TestXmpp {

    private static URI.Builder getUriXmppBuilder() {
        return (new URI.Builder())
                .protocol(URI.Protocol.XMPP)
                .type(URI.ObjectType.BASE)
                        //.host("192.168.1.7")
                .host("ec2-54-227-52-216.compute-1.amazonaws.com")
                        //.host("192.168.1.7")
                .port(5222)
                .domain("localhost");
    }

    public static URI getXmppA() {
        return getUriXmppBuilder().name("ΣA").login("kk").build();
    }

    public static URI getXmppB() {
        return getUriXmppBuilder().name("ΣB").login("rr").build();
    }

    private static Bundle getXmppDefaultPasswordBundle() {
        Bundle bundle = new Bundle();
        bundle.putString("password", "qweasd");
        return bundle;
    }

    public static Bundle getPasswordBundleA() {
        return getXmppDefaultPasswordBundle();
    }

    public static Bundle getPasswordBundeB() {
        return getXmppDefaultPasswordBundle();
    }

}
