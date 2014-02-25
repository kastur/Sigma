package edu.ucla.nesl.sigma.samples.pingpong;


import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.Random;

public class PingPongServer extends Service {

  public static final String TAG = PingPongServer.class.getName();

  @Override
  public IBinder onBind(Intent intent) {
    final Random random = new Random(0);
    return new IPingPongServer.Stub() {

      @Override
      public int getRandom() throws RemoteException {
        int randInt = random.nextInt();
        Log.d(TAG, "GET_RANDOM = " + randInt);
        return randInt;
      }

      @Override
      public void ping(IPingPongServer other, int count) throws RemoteException {
        if (count > 0) {
          Log.d(TAG, "PING Count = " + count + "[" + getCallingUid() + "," + getCallingPid() + "]");
          other.pong(this, count - 1);
        }
      }

      @Override
      public void putObject(IPingPongServer obj) throws RemoteException {
        Log.d(TAG, "Got object");
      }

      @Override
      public void pong(IPingPongServer other, int count) throws RemoteException {
        if (count > 0) {
          Log.d(TAG, "PONG Count = " + count + "[" + getCallingUid() + "," + getCallingPid() + "]");
          other.ping(this, count - 1);
        }
      }
    };
  }


}
