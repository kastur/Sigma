package edu.ucla.nesl.sigma.api;

import android.os.IBinder;

interface ISigmaManager {

    /* Wire URI */ byte[] getBaseURI();
    ISigmaManager getRemoteManager(in byte[] targetBaseURI);
    IBinder getServiceManager();
    IBinder getService(in Intent intent);

    /* Internal methods for clean-up */
    void destroy();
    void stop();

    /* Methods used for debugging and testing. */
    void startTracing(String traceName);
    void stopTracing();

    // Used internally by the LOCAL IRequestFactory.
    /* Wire SReponse */ byte[] handleRequest(in byte[] /* Wire SRequest */ requestBytes);

}