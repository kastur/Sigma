package edu.ucla.nesl.sigma.base;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import java.nio.ByteBuffer;

public class SigmaJNI {

    static {
        System.loadLibrary("sigmalib");
    }

    public static native String getMessage();

    public static native int[] socketpair();

    public static native void closeFd(int fd);

    public static native int recvMessage(int fd, ByteBuffer buffer);

    public static native int sendMessage(int fd, ByteBuffer buffer, int len);

    public static native void waitForMessage(int fd);
}