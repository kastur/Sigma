package edu.ucla.nesl.sigma.samples.chat;

import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.*;
import android.view.ViewGroup;
import android.widget.ImageView;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;

import java.io.ByteArrayInputStream;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PictureViewActivity extends BunchOfButtonsActivity {

    IPictureChatServer mServer;
    ImageView mImageView;
    Context mContext;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mContext = this;

    }

    @Override
    public void onCreateHook() {
        mImageView = new ImageView(this);
        mImageView.setLayoutParams(new ViewGroup.LayoutParams(20, 30));
        mImageView.setAdjustViewBounds(true);
        getLayout().addView(mImageView);
    }

    @Override
    protected void onStart() {
        super.onStart();
        Intent startIntent = getIntent();
        String uuid = startIntent.getStringExtra("uuid");


        new AsyncTask<String, Void, Bitmap>() {
            @Override
            protected Bitmap doInBackground(String... uuids) {
                final String uuid = uuids[0];
                IBinder binder = SigmaEngine.getService(mContext, new Intent(mContext, PictureChatService.class)).first;
                mServer = IPictureChatServer.Stub.asInterface(binder);

                ParcelFileDescriptor[] fds = PictureChatService.makePipe();
                PicturePutInfo putInfo = new PicturePutInfo(uuid, fds[0]);

                int numBytes = 0;
                try {
                    numBytes = mServer.getPicture(putInfo);
                } catch (RemoteException ex) {
                    throwUnexpected(ex);
                }

                byte[] bytes = PictureChatService.receivePicture(fds[1], numBytes);

                Bitmap bitmap =
                        BitmapFactory.decodeStream(new ByteArrayInputStream(bytes));

                return bitmap;
            }

            @Override
            protected void onPostExecute(Bitmap bitmap) {
                mImageView.setImageBitmap(bitmap);
            }
        }.execute(uuid);
    }


}
