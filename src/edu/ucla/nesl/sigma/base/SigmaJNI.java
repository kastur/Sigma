package edu.ucla.nesl.sigma.base;
import android.os.ParcelFileDescriptor;
import android.util.Log;

import java.io.IOException;
import java.nio.ByteBuffer;

public class SigmaJNI {
    public static final String TAG = SigmaJNI.class.getName();

    static {
        System.loadLibrary("sigmalib");
    }

    public static native String getMessage();

    public static native int[] socketpair();

    public static native void closeFd(int fd);

    public static native int recvMessage(int fd, ByteBuffer buffer);

    public static native int sendMessage(int fd, ByteBuffer buffer, int len);

    public static native boolean waitForMessage(int fd);

    public static void testLocalReadWrite() {
        int[] pair = socketpair();
        final ParcelFileDescriptor readFd = ParcelFileDescriptor.adoptFd(pair[0]);
        final ParcelFileDescriptor writeFd = ParcelFileDescriptor.adoptFd(pair[1]);

        Thread readThread = new Thread("readThread") {
            @Override
            public void run() {
                ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                int fd = readFd.getFd();
                while (true) {
                    boolean _return = waitForMessage(fd);
                    int len = recvMessage(fd, buffer);
                    if (_return == false && len == 0) {
                        Log.d(TAG, "readThread finished.");
                        break;
                    } else {
                        Log.d(TAG, "readThread read len=" + len);
                    }
                }
            }
        };
        readThread.start();

        Thread writeThread = new Thread("writeThread") {
            @Override
            public void run() {
                int fd = writeFd.getFd();
                ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                sendMessage(fd, buffer, 104);
                try {
                writeFd.close();
                } catch (IOException ex) {

                }
            }
        };
        writeThread.start();
    }
}