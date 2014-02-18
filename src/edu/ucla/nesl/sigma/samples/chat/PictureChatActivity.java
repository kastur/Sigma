package edu.ucla.nesl.sigma.samples.chat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.RemoteException;
import android.provider.MediaStore;
import android.view.ViewGroup;
import android.widget.ImageView;
import edu.ucla.nesl.sigma.P.URI;
import edu.ucla.nesl.sigma.api.SigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaServiceA;
import edu.ucla.nesl.sigma.base.SigmaServiceB;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;
import edu.ucla.nesl.sigma.samples.TestUtils;

import java.io.ByteArrayOutputStream;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PictureChatActivity extends BunchOfButtonsActivity {
    public static final boolean USE_HTTP = false;
    private static final String TAG = PictureChatActivity.class.getName();
    public static final String kChatServerName =
            "edu.ucla.nesl.sigma.samples.chat.PictureChatService";

    static final int REQUEST_IMAGE_CAPTURE = 1;
    ImageView mImageView;
    Bitmap mBitmap;

    SigmaServiceConnection connA;
    SigmaServiceConnection connB;
    SigmaManager sigmaA;
    SigmaManager sigmaB;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBitmap = null;

        connA = new SigmaServiceConnection(this, SigmaServiceA.class);
        connA.connect();

        connB = new SigmaServiceConnection(this, SigmaServiceB.class);
        connB.connect();
    }

    @Override
    protected void onDestroy() {
        connA.disconnect();
        connB.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCreateHook() {
        addButton("Take picture", new Runnable() {
            @Override
            public void run() {
                dispatchTakePictureIntent();
            }
        });

        mImageView = new ImageView(this);
        mImageView.setAdjustViewBounds(true);
        mImageView.setLayoutParams(new ViewGroup.LayoutParams(20, 30));

        getLayout().addView(mImageView);

        addButton("\"Σ\" xmpp kk@", new Runnable() {
            @Override
            public void run() {
                if (USE_HTTP) {
                    URI uri = TestUtils.getURIHttp8();
                    sigmaA = connA.getImpl(uri, null);
                } else {
                    URI uri = TestUtils.getUriXmppKK();
                    sigmaA = connB.getImpl(uri, TestUtils.getXmppPasswordBundleKK());
                }
            }
        });

        addButton("\"Σ\" share A -> B", new Runnable() {
            @Override
            public void run() {
                sharePicture(sigmaA);
            }
        });

        addButton("\"Σ\" xmpp rr@", new Runnable() {
            @Override
            public void run() {
                if (USE_HTTP) {
                    URI uri = TestUtils.getURIHttp9();
                    sigmaB = connA.getImpl(uri, null);
                } else {
                    URI uri = TestUtils.getUriXmppRR();
                    sigmaB = connB.getImpl(uri, TestUtils.getXmppPasswordBundleRR());
                }
            }
        });

        addButton("\"Σ\" share B -> A", new Runnable() {
            @Override
            public void run() {
                sharePicture(sigmaB);
            }
        });


    }

    private void dispatchTakePictureIntent() {
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            startActivityForResult(takePictureIntent, REQUEST_IMAGE_CAPTURE);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            Bundle extras = data.getExtras();
            mBitmap = (Bitmap) extras.get("data");
            mImageView.setImageBitmap(mBitmap);
        }
    }

    private void sharePicture(SigmaManager manager) {


        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        mBitmap.compress(Bitmap.CompressFormat.JPEG, 25, byteArrayOutputStream);

        /*
        try {
            byteArrayOutputStream.write(new String("hello world").getBytes());
        } catch (IOException ex) {
            throwUnexpected(ex);
        }
        */

        int numBytes = byteArrayOutputStream.size();

        URI remoteURI;
        if (USE_HTTP) {
            URI selfURI = manager.getBaseURI();
            Integer port = (new Integer(8080).equals(selfURI.port)) ? 9090 : 8080;
            remoteURI = (new URI.Builder(selfURI).port(port).build());
        } else {
            URI selfURI = manager.getBaseURI();
            String to = "kk".equals(selfURI.login) ? "rr" : "kk";
            remoteURI = (new URI.Builder(selfURI).login(to).build());
        }


        SigmaManager remote = manager.getRemoteManager(remoteURI);
        Intent serviceName = new Intent(kChatServerName);
        IPictureChatServer pictureServer = IPictureChatServer.Stub.asInterface(remote.getService(serviceName));

        //byte[] byteArray = new String("A MAGNIFICIENT ROCK").getBytes();

        PicturePutInfo putInfo = null;
        try {
            putInfo = pictureServer.putPicture(numBytes);
        } catch (RemoteException ex) {
            throwUnexpected(ex);
        }

        PictureChatService.sendPicture(putInfo.parcelFd, byteArrayOutputStream.toByteArray());


    }


}
