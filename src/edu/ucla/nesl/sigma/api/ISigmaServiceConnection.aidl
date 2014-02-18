package edu.ucla.nesl.sigma.api;

import android.os.IBinder;
import edu.ucla.nesl.sigma.api.ISigmaManager;

interface ISigmaServiceConnection {
    ISigmaManager getImpl(in byte[] uri, in Bundle extras);
}