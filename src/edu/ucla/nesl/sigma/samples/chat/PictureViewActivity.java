package edu.ucla.nesl.sigma.samples.chat;

import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import android.util.Pair;
import android.view.ViewGroup;
import android.widget.ImageView;

import java.io.ByteArrayInputStream;

import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.samples.BunchOfButtonsActivity;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class PictureViewActivity extends BunchOfButtonsActivity {

  IPictureChatServer mServer;
  ImageView mImageView;

  @Override
  public void onCreateHook() {
    mImageView = new ImageView(this);
    mImageView.setLayoutParams(
        new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 600));
    mImageView.setAdjustViewBounds(true);
    getLayout().addView(mImageView);
  }

  @Override
  protected void onStart() {
    super.onStart();
    Intent startIntent = getIntent();
    final String uuid = startIntent.getStringExtra("uuid");

    new AsyncTask<Void, Void, Void>() {
      @Override
      protected Void doInBackground(Void... voids) {
        Pair<IBinder, ServiceConnection> pair = SigmaEngine.getService(
            PictureViewActivity.this,
            new Intent(PictureViewActivity.this, PictureChatService.class));
        IBinder binder = pair.first;
        mServer = IPictureChatServer.Stub.asInterface(binder);

        ParcelFileDescriptor[] pipe = PictureIO.makePipe();
        PictureEntry pictureEntry;
        try {
          pictureEntry = mServer.requestPictureGet(null, uuid, pipe[0]);
        } catch (RemoteException ex) {
          throwUnexpected(ex);
          return null;
        }

        new PictureIO.RecvPictureTask(pictureEntry, pipe[1]) {
          @Override
          protected void onPostExecute(Void _void) {
            Bitmap bitmap =
                BitmapFactory.decodeStream(new ByteArrayInputStream(mEntry.bytes));
            mImageView.setImageBitmap(bitmap);
          }
        }.execute();

        return null;
      }
    }.execute();


  }
}
