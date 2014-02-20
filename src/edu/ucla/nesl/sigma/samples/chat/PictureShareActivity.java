package edu.ucla.nesl.sigma.samples.chat;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.drawable.BitmapDrawable;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
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
import edu.ucla.nesl.sigma.samples.TestXmpp;

import java.io.ByteArrayOutputStream;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PictureShareActivity extends BunchOfButtonsActivity {
    public static final boolean USE_HTTP = true;
    public static final boolean USE_HTTP_PROXY = true;


    private static final String TAG = PictureShareActivity.class.getName();
    public static final String kChatServerName =
            "edu.ucla.nesl.sigma.samples.chat.PictureChatService";

    static final int REQUEST_IMAGE_CAPTURE = 1;
    ImageView mImageView;

    SigmaServiceConnection connA;
    SigmaServiceConnection connB;
    SigmaManager sigmaA;
    SigmaManager sigmaB;

    @Override
    protected void onDestroy() {
        connA.disconnect();
        connB.disconnect();
        super.onDestroy();
    }

    @Override
    public void onCreateHook() {
        connA = new SigmaServiceConnection(this, SigmaServiceA.class);
        connB = new SigmaServiceConnection(this, SigmaServiceB.class);

        addButton("Start ΣA", new Runnable() {
            @Override
            public void run() {
                if (USE_HTTP) {
                    URI uri;
                    if (USE_HTTP_PROXY) {
                        uri = SigmaServiceA.getLocalHttp();
                    } else {
                        uri = SigmaServiceA.getProxyHttp();
                    }
                    sigmaA = connA.getImpl(uri, null);
                } else {
                    URI uri = TestXmpp.getXmppA();
                    sigmaA = connB.getImpl(uri, TestXmpp.getPasswordBundleA());
                }
            }
        });

        addButton("Take a picture", new Runnable() {
            @Override
            public void run() {
                dispatchTakePictureIntent();
            }
        });

        mImageView = new ImageView(this);
        mImageView.setAdjustViewBounds(true);
        mImageView.setLayoutParams(new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600));
        mImageView.setAdjustViewBounds(true);
        mImageView.setImageBitmap(
                BitmapFactory.decodeResource(getResources(), android.R.drawable.alert_light_frame));

        getLayout().addView(mImageView);


        addButton("Share with ΣB", new Runnable() {
            @Override
            public void run() {
                sharePicture(sigmaA);
            }
        });


        addButton("", null);
        addButton("", null);

        addButton("Start ΣB", new Runnable() {
            @Override
            public void run() {
                if (USE_HTTP) {
                    URI uri = SigmaServiceB.getLocalHttp();
                    sigmaB = connA.getImpl(uri, null);
                } else {
                    URI uri = TestXmpp.getXmppB();
                    sigmaB = connB.getImpl(uri, TestXmpp.getPasswordBundeB());
                }
            }
        });

        addButton("Share with ΣA", new Runnable() {
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
            Bitmap bitmap = (Bitmap) extras.get("data");
            mImageView.setImageBitmap(bitmap);
        }
    }

    private void sharePicture(SigmaManager manager) {
        Bitmap bitmap = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 85, byteArrayOutputStream);
        int numBytes = byteArrayOutputStream.size();

        URI selfURI = manager.getBaseURI();
        URI remoteURI;
        if ("ΣB".equals(selfURI.name)) {
            remoteURI = (USE_HTTP) ? SigmaServiceA.getLocalHttp() : TestXmpp.getXmppA();
        } else {
            remoteURI = (USE_HTTP) ? SigmaServiceB.getLocalHttp() : TestXmpp.getXmppB();
        }

        SigmaManager remote = manager.getRemoteManager(remoteURI);
        Intent serviceName = new Intent(kChatServerName);
        IPictureChatServer pictureServer = IPictureChatServer.Stub.asInterface(remote.getService(serviceName));

        PictureEntry pictureEntry = new PictureEntry();
        pictureEntry.numBytes = numBytes;
        ParcelFileDescriptor writeTo;
        try {
            // The RPC, if successful, modifies pictureEntry by assigning a uuid
            // and returns a file descriptor to which the picture file can be written.
            writeTo = pictureServer.requestPicturePut(manager.asBinder(), pictureEntry);
        } catch (RemoteException ex) {
            throwUnexpected(ex);
            return;
        }

        // A helper function writes the picture bytes to the file descriptor.
        pictureEntry.bytes = byteArrayOutputStream.toByteArray();
        new PictureIO.SendPictureTask(pictureEntry.bytes, writeTo, true /* Intentionally slow down IO for demo */)
                .execute();
    }


}
