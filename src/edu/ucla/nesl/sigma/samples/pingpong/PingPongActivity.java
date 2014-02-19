package edu.ucla.nesl.sigma.samples.pingpong;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.SigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.impl.HttpSigmaServer;
import edu.ucla.nesl.sigma.base.SigmaServiceA;
import edu.ucla.nesl.sigma.base.SigmaServiceB;
import edu.ucla.nesl.sigma.samples.TestXmllUtils;
import edu.ucla.nesl.sigma.samples.TimeStats;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PingPongActivity extends BunchOfButtonsActivity {
    private static final String TAG = PingPongActivity.class.getName();
    public static final String kPingPongServerName =
            "edu.ucla.nesl.sigma.samples.pingpong.PingPongServer";

    public static final boolean RUN_PROFILER_ON_LAST_REP = true;
    public static final int NUM_TEST_REPS = 24; // number of times to repeat tests

    SigmaServiceConnection connA;
    SigmaServiceConnection connB;
    String ipaddr;
    Intent mPingPongServiceName;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        ipaddr = HttpSigmaServer.getIPAddress(true);
        super.onCreate(savedInstanceState);
        connA = new SigmaServiceConnection(this, SigmaServiceA.class);
        connB = new SigmaServiceConnection(this, SigmaServiceB.class);

        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... voids) {
                connA.connect();
                connB.connect();
                return null;
            }
        }.execute();

        mPingPongServiceName = new Intent(kPingPongServerName);
    }

    @Override
    protected void onDestroy() {
        connA.disconnect();
        connB.disconnect();
        super.onDestroy();
    }

    public void onCreateHook() {

        addButton("\"Σ\" testNative", new Runnable() {
            @Override
            public void run() {
                testNative();
            }
        });

        addButton("\"Σ\" testLocal", new Runnable() {
            @Override
            public void run() {
                testLocal();
            }
        });

        addButton("\"Σ\" testHttp", new Runnable() {
            @Override
            public void run() {
                testHttp();
            }
        });

        addButton("\"Σ\" testXmpp", new Runnable() {
            @Override
            public void run() {
                testXmpp();
            }
        });

        addButton("IP: " + ipaddr, null);
    }

    public void testNative() {
        TimeStats.resetAll();
        for (int ii = 0; ii < NUM_TEST_REPS; ++ii) {
            boolean lastRep = (ii == NUM_TEST_REPS - 1);
            SigmaManager sigmaA = connA.getImpl(SigmaServiceA.getURINative(), null);
            SigmaManager sigmaB = connB.getImpl(SigmaServiceB.getURINative(), null);
            runTest(sigmaA, sigmaB, "native", (ii < 3), RUN_PROFILER_ON_LAST_REP && lastRep);
        }
    }

    public void testLocal() {
        TimeStats.resetAll();
        for (int ii = 0; ii < NUM_TEST_REPS; ++ii) {
            boolean lastRep = (ii == NUM_TEST_REPS - 1);
            SigmaManager sigmaA = connA.getImpl(SigmaServiceA.getURILocal(), null);
            SigmaManager sigmaB = connB.getImpl(SigmaServiceB.getURILocal(), null);
            runTest(sigmaA, sigmaB, "local", (ii < 3), RUN_PROFILER_ON_LAST_REP && lastRep);
        }
    }

    public void testHttp() {
        TimeStats.resetAll();
        for (int ii = 0; ii < NUM_TEST_REPS; ++ii) {
            boolean lastRep = (ii == NUM_TEST_REPS - 1);
            SigmaManager sigmaA = connA.getImpl(SigmaServiceB.getLocalHttp(), null);
            SigmaManager sigmaB = connB.getImpl(SigmaServiceA.getLocalHttp(), null);
            runTest(sigmaA, sigmaB, "http", (ii < 3), RUN_PROFILER_ON_LAST_REP && lastRep);
        }
    }

    public void testXmpp() {
        TimeStats.resetAll();
        int numReps = Math.max(5, NUM_TEST_REPS / 10);
        for (int ii = 0; ii < numReps; ++ii) {
            boolean lastRep = (ii == numReps - 1);
            SigmaManager sigmaA = connA.getImpl(TestXmllUtils.getXmppA(), TestXmllUtils.getPasswordBundleA());
            SigmaManager sigmaB = connB.getImpl(TestXmllUtils.getXmppB(), TestXmllUtils.getPasswordBundeB());
            runTest(sigmaA, sigmaB, "xmpp", (ii < 1), RUN_PROFILER_ON_LAST_REP && lastRep);
        }
    }

    public void runTest(SigmaManager sigmaA, SigmaManager sigmaB, String name, boolean warmUpRun, boolean withProfiler) {
        IPingPongServer pingPongA = null;
        {
            IBinder binder = sigmaA.getService(mPingPongServiceName);
            pingPongA = IPingPongServer.Stub.asInterface(binder);
        }
        URI remoteURI = sigmaB.getBaseURI();

        IPingPongServer pingPongB = null;
        {
            TimeStats.Timer timer = null;
            if (withProfiler) {
                sigmaA.startTracing(name + ".A.connect");
                sigmaB.startTracing(name + ".B.connect");
            } else if (!warmUpRun) {
                timer = TimeStats.getInstance(name + ".connect").startTiming();
            }

            SigmaManager remote = sigmaA.getRemoteManager(remoteURI);
            IBinder binder = remote.getService(mPingPongServiceName);
            pingPongB = IPingPongServer.Stub.asInterface(binder);

            if (withProfiler) {
                sigmaA.stopTracing();
                sigmaB.stopTracing();
            } else if (!warmUpRun) {
                timer.addElapsed();
            }
        }

        {
            TimeStats stats = null;
            if (withProfiler) {
                sigmaA.startTracing(name + ".A.getRandom");
                sigmaB.startTracing(name + ".B.getRandom");
            } else if (!warmUpRun) {
                stats = TimeStats.getInstance(name + ".getRandom");
            }
            try {
                for (int ii = 0; ii < 10; ++ii) {
                    TimeStats.Timer timer = null;
                    if (!warmUpRun && !withProfiler) {
                        timer = stats.startTiming();
                    }
                    int randInt = pingPongB.getRandom();
                    if (!warmUpRun && !withProfiler) {
                        timer.addElapsed();
                    }
                    Log.d(TAG, "GOT_RANDOM = " + randInt);
                }
            } catch (RemoteException ex) {
                throwUnexpected(ex);
            }

            if (withProfiler) {
                sigmaA.stopTracing();
                sigmaB.stopTracing();
            }
        }

        {
            TimeStats stats = null;
            if (withProfiler) {
                sigmaA.startTracing(name + ".A.putObject");
                sigmaB.startTracing(name + ".B.putObject");
            } else if (!warmUpRun) {
                stats = TimeStats.getInstance(name + ".putObject");
            }
            try {
                for (int ii = 0; ii < 10; ++ii) {
                    TimeStats.Timer timer = null;
                    if (!warmUpRun && !withProfiler) {
                        timer = stats.startTiming();
                    }
                    pingPongB.putObject(pingPongA);
                    if (!warmUpRun && !withProfiler) {
                        timer.addElapsed();
                    }
                }
            } catch (RemoteException ex) {
                throwUnexpected(ex);
            }

            if (withProfiler) {
                sigmaA.stopTracing();
                sigmaB.stopTracing();
            }
        }


        {
            TimeStats.Timer timer = null;
            if (withProfiler) {
                sigmaA.startTracing(name + ".A.pingPong");
                sigmaB.startTracing(name + ".B.pingPong");
            } else if (!warmUpRun) {
                timer = TimeStats.getInstance(name + ".pingPong").startTiming();
            }

            try {
                pingPongB.ping(pingPongA, 20);
            } catch (RemoteException ex) {
                throwUnexpected(ex);
            }

            if (withProfiler) {
                sigmaA.stopTracing();
                sigmaB.stopTracing();
            } else if (!warmUpRun) {
                timer.addElapsed();
            }
        }

        TimeStats.logAllTimers();

        sigmaA.destroy();
        sigmaB.destroy();

        sigmaA.stop();
        sigmaB.stop();
        System.gc();
    }
}
