package edu.ucla.nesl.sigma.impl.xmpp;

import android.content.Context;
import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.ISigmaServer;
import edu.ucla.nesl.sigma.base.SigmaEngine;

import java.util.concurrent.Semaphore;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class XmppSigmaServer implements ISigmaServer, SigmaEngine.IRequestFactory {
    public static final String TAG = XmppSigmaServer.class.getName();
    final SigmaEngine mEngine;
    final XmppClient mClient;

    private static XmppClient.XmppConfig getConfig(URI uri, String password) {
        XmppClient.XmppConfig config = new XmppClient.XmppConfig(uri.host, uri.port, uri.domain, uri.login, password);
        return config;
    }

    public XmppSigmaServer(Context context, URI uri, String password) {
        XmppClient.InitializeWithContext(context);
        mEngine = new SigmaEngine(context, uri, this);
        mClient = new XmppClient(getConfig(uri, password), mEngine);
    }

    @Override
    public SResponse doTransaction(SRequest request) {
        return mClient.doTransaction(request);
    }

    @Override
    public SigmaEngine getEngine() {
        return mEngine;
    }

    @Override
    public void start() {
        final Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            throwUnexpected(ex);
        }

        Thread t = new Thread() {
            @Override
            public void run() {
                mClient.login();
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
    public void stop() {
        mClient.disconnect();
    }
}
