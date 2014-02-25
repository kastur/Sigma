package edu.ucla.nesl.sigma.api;

import android.os.IBinder;
import edu.ucla.nesl.sigma.api.ISigmaManager;

interface ISigmaFactory {
    ISigmaManager newInstance(in byte[] uriBytes, in Bundle extras);
    void destroyInstance(in byte[] uriBytes);
}