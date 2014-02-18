package edu.ucla.nesl.sigma.samples.chat;

import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Log;
import edu.ucla.nesl.sigma.base.SigmaJNI;

import java.io.*;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.UUID;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PictureChatService extends Service {
    private static final String TAG = PictureChatService.class.getName();


    static class PictureRecord {
        public String uuid;

        public ParcelFileDescriptor writeFd;
        public ParcelFileDescriptor readFd;
        public int numBytes;

        public byte[] bytes;
        public ICommentReceiver commentSender;
    }

    final HashMap<String, PictureRecord> mRecords;
    final NotificationHelper mNotificationHelper;

    public PictureChatService() {
        mRecords = new HashMap<String, PictureRecord>();
        mNotificationHelper = new NotificationHelper(this);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return new IPictureChatServer.Stub() {
            @Override
            public PicturePutInfo putPicture(int numBytes) throws RemoteException {
                return handlePutPicture(numBytes);
            }

            @Override
            public int getPicture(PicturePutInfo pictureInfo) throws RemoteException {
                return handleGetPicture(pictureInfo);
            }
        };
    }


    public PicturePutInfo handlePutPicture(int numBytes) {
        Log.d(TAG, "Called putPicture(...)");
        PictureRecord record = createRecord(numBytes);
        synchronized (record) {
            startReceiveThread(record);
            try {
                record.wait();
            } catch (InterruptedException ex) {
                throwUnexpected(ex);
            }
        }

        return new PicturePutInfo(record.uuid, record.writeFd);
    }

    public int handleGetPicture(PicturePutInfo putInfo) {
        synchronized (mRecords) {
            if (!mRecords.containsKey(putInfo.uuid)) {
                return -1;
            }

            PictureRecord record = mRecords.get(putInfo.uuid);
            startSendThread(record, putInfo);

            synchronized (record) {
                try {
                    record.wait();
                } catch (InterruptedException ex) {
                    throwUnexpected(ex);
                }
            }

            return record.numBytes;
        }
    }

    public void startReceiveThread(PictureRecord record) {
        new AsyncTask<PictureRecord, Integer, PictureRecord>() {

            @Override
            protected void onPreExecute() {
                super.onPreExecute();
                mNotificationHelper.progressStart();
            }

            @Override
            protected void onProgressUpdate(Integer... values) {
                mNotificationHelper.progressUpdate(values[0]);
            }

            @Override
            protected PictureRecord doInBackground(PictureRecord... pictureRecords) {
                PictureRecord record = pictureRecords[0];
                synchronized (record) {
                    record.notifyAll();
                }


                record.bytes =
                        receivePicture(record.readFd, record.numBytes);
                return record;
            }

            @Override
            protected void onPostExecute(PictureRecord record) {
                Intent viewer = new Intent(PictureChatService.this, PictureViewActivity.class);
                viewer.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                viewer.putExtra("uuid", record.uuid);

                //startActivity(viewer);

                PendingIntent pi = PendingIntent.getActivity(
                        PictureChatService.this, 0, viewer, PendingIntent.FLAG_UPDATE_CURRENT);
                mNotificationHelper.link(pi);
            }

            private byte[] receivePicture(ParcelFileDescriptor parcelFd, int numBytes) {
                ByteArrayOutputStream output = new ByteArrayOutputStream();

                int readBytes = 0;
                //InputStream is = new FileInputStream(parcelFd.getFileDescriptor());
                //byte[] data = new byte[4096];


                ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
                int nativeFd = parcelFd.getFd();
                while (true) {
                    buffer.rewind();
                    SigmaJNI.waitForMessage(nativeFd);
                    int len = SigmaJNI.recvMessage(nativeFd, buffer);
                    Log.d(TAG, "Read chunk: [offset=" + readBytes + ", len=" + len + ", out-of=" + numBytes);

                    int progressPercent = (int) ((100.0F * len) / numBytes);
                    publishProgress(progressPercent);

                    byte[] bytes = new byte[len];
                    buffer.get(bytes, 0, len);
                    readBytes += len;
                    try {
                        output.write(bytes);
                    } catch (IOException ex) {
                        throwUnexpected(ex);
                    }

                    if (readBytes == numBytes) {
                        break;
                    }
                }
                return output.toByteArray();
            }
        }.execute(record);
    }

    public void startSendThread(final PictureRecord record, final PicturePutInfo putInfo) {
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected Void doInBackground(Void... records) {
                synchronized (record) {
                    record.notifyAll();
                }

                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                try {
                    byteArrayOutputStream.write(record.bytes);
                } catch (IOException ex) {
                    throwUnexpected(ex);
                }
                sendPicture(putInfo.parcelFd, byteArrayOutputStream.toByteArray());
                return null;
            }
        }.execute();
    }

    public PictureRecord createRecord(int numBytes) {
        ParcelFileDescriptor[] pipe = makePipe();
        PictureRecord record = new PictureRecord();
        record.uuid = UUID.randomUUID().toString();
        record.readFd = pipe[0];
        record.writeFd = pipe[1];
        record.numBytes = numBytes;
        synchronized (mRecords) {
            mRecords.put(record.uuid, record);
        }
        return record;
    }

    public static ParcelFileDescriptor[] makePipe() {
        ParcelFileDescriptor[] ret = new ParcelFileDescriptor[2];
        try {
            int[] pipe = SigmaJNI.socketpair();
            ret[0] = ParcelFileDescriptor.fromFd(pipe[0]);
            ret[1] = ParcelFileDescriptor.fromFd(pipe[1]);
        } catch (IOException ex) {
            throwUnexpected(ex);
        }
        return ret;
    }

    public static byte[] receivePicture(ParcelFileDescriptor parcelFd, int numBytes) {
        ByteArrayOutputStream output = new ByteArrayOutputStream();

        int readBytes = 0;
        //InputStream is = new FileInputStream(parcelFd.getFileDescriptor());
        //byte[] data = new byte[4096];


        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        int nativeFd = parcelFd.getFd();
        while (true) {
            buffer.rewind();
            SigmaJNI.waitForMessage(nativeFd);
            int len = SigmaJNI.recvMessage(nativeFd, buffer);
            Log.d(TAG, "Read chunk: [offset=" + readBytes + ", len=" + len + ", out-of=" + numBytes);
            byte[] bytes = new byte[len];
            buffer.get(bytes, 0, len);
            readBytes += len;
            try {
                output.write(bytes);
            } catch (IOException ex) {
                throwUnexpected(ex);
            }

            if (readBytes == numBytes) {
                break;
            }
        }
        return output.toByteArray();
    }

    public static void sendPicture(ParcelFileDescriptor parcelFd, byte[] bytes) {
        ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
        OutputStream os = new FileOutputStream(parcelFd.getFileDescriptor());

        byte[] buffer = new byte[1024];


        try {
            while (true) {
                int nRead = bis.read(buffer, 0, 1024);
                if (nRead <= 0) {
                    break;
                }
                os.write(buffer, 0, nRead);
                os.flush();
            }
            parcelFd.close();
        } catch (IOException ex) {
            throwUnexpected(ex);
        }
    }


}
