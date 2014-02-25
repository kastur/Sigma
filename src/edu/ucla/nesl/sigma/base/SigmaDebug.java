package edu.ucla.nesl.sigma.base;

import android.util.Log;

import java.util.HashSet;

public class SigmaDebug {

  public static final HashSet<String> DEBUG_TAGS = new HashSet<String>();

  public static final boolean DEBUG_ALL = false;

  static {
    // Add TAGs of classes that should output log messages.
    DEBUG_TAGS.add(SigmaEngine.TAG);
  }

  public static void LogDebug(String TAG, String message) {
    if (DEBUG_ALL) {
      Log.d(TAG, message);
    } else if (DEBUG_TAGS.contains(TAG)) {
      Log.d(TAG, message);
    }
  }

  public static void throwUnexpected(Exception ex) {
    ex.printStackTrace();
    throw new IllegalStateException(ex);
  }
}
