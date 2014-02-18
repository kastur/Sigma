package edu.ucla.nesl.sigma.samples;

import android.util.Log;

import java.text.DecimalFormat;
import java.util.HashMap;
import java.util.SortedSet;
import java.util.TreeSet;

public class TimeStats {
    private static final String TAG = TimeStats.class.getName();
    private static HashMap<String, TimeStats> counters = new HashMap<String, TimeStats>();

    public static final String kHttpExecute = "httpExecute";
    public static final String kHttpServe = "httpExecute";
    public static final String kXmppSendMessage = "xmppSendMessage";
    public static final String kXmppProcessMessage = "xmppProcessmessage";
    public static final String kEncodeParcel = "encodeParcel";
    public static final String kDecodeParcel = "decodeParcel";
    public static final String kSigmaServe = "sigmaServe";

    public static TimeStats getInstance(String name) {
        if (!counters.containsKey(name)) {
            counters.put(name, new TimeStats());
        }

        return counters.get(name);
    }

    public static void resetAll() {
        counters.clear();
    }

    public static void logAllTimers() {
        StringBuilder sb = new StringBuilder("TIMERS:\n");
        SortedSet<String> keys = new TreeSet<String>(counters.keySet());

        DecimalFormat formatter = new DecimalFormat("0.000");
        for (String key : keys) {
            TimeStats stats = counters.get(key);
            String elapsed = formatter.format(stats.getTotalElapsed());
            String avg = formatter.format(stats.getTotalElapsed() / stats.getNumRecords());
            sb.append("      ").append(key)
                    .append("\t\ttime=").append(elapsed)
                    .append("\t\tcalls=").append(stats.getNumRecords())
                    .append("\t\tavg_time=").append(avg)
                    .append("\n");
        }
        Log.d(TAG, sb.toString());
    }

    private double totalElapsed;
    private int numRecords;

    public TimeStats() {
        totalElapsed = 0;
        numRecords = 0;
    }

    public synchronized void addTime(double val) {
        totalElapsed += val;
        numRecords += 1;
    }

    public synchronized double getTotalElapsed() {
        return totalElapsed;
    }

    public synchronized int getNumRecords() {
        return numRecords;
    }

    public Timer startTiming() {
        return new Timer();
    }

    public class Timer {
        private long mStart;

        public Timer() {
            mStart = System.nanoTime();
        }

        public double addElapsed() {
            long now = System.nanoTime();
            double elapsed = (now - mStart) / 1.0e9;
            TimeStats.this.addTime(elapsed);
            return elapsed;
        }

    }
}
