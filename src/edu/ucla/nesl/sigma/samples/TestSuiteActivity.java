package edu.ucla.nesl.sigma.samples;

import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Pair;
import android.widget.Toast;
import edu.ucla.nesl.sigma.api.SigmaServiceConnection;
import edu.ucla.nesl.sigma.base.SigmaManager;
import edu.ucla.nesl.sigma.base.SigmaServiceB;
import edu.ucla.nesl.sigma.impl.HttpSigmaServer;
import edu.ucla.nesl.sigma.base.SigmaServiceA;
import edu.ucla.nesl.sigma.samples.basic.MainActivity;
import edu.ucla.nesl.sigma.samples.chat.PictureShareActivity;
import edu.ucla.nesl.sigma.samples.pingpong.PingPongActivity;
import edu.ucla.nesl.sigma.samples.sensor.SensorActivity;
import edu.ucla.nesl.sigma.test.BasicTests;

import javax.net.SocketFactory;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;

public class TestSuiteActivity extends BunchOfButtonsActivity {
    SigmaManager mHttp;
    SigmaServiceConnection mSigma;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSigma = new SigmaServiceConnection(this, SigmaServiceA.class);
        mSigma.connect();
    }

    @Override
    public void onCreateHook() {

        addButton("IP:" + HttpSigmaServer.getIPAddress(true), null);

        addButton("PING:" + TestXmllUtils.getXmppA().host + ":" + TestXmllUtils.getXmppA().port, new Runnable() {
            @Override
            public void run() {
                pingXxmpp(TestXmllUtils.getXmppA().host, TestXmllUtils.getXmppA().port);
            }
        });

        addButton("PING:" + TestXmllUtils.getXmppA().host + ":80", new Runnable() {
            @Override
            public void run() {
                pingXxmpp(TestXmllUtils.getXmppA().host, 80);
            }
        });


        addButton("PING ROUTER:", new Runnable() {
            @Override
            public void run() {
                pingXxmpp("192.168.1.1", 80);
            }
        });

        addButton("START HTTP", new Runnable() {
            @Override
            public void run() {
                startHttp();
            }
        });


        addLaunchActivityButton(MainActivity.class);
        addLaunchActivityButton(PingPongActivity.class);
        addLaunchActivityButton(PictureShareActivity.class);
        addLaunchActivityButton(SensorActivity.class);

        addLaunchActivityButton(BasicTests.class);
    }

    private void addLaunchActivityButton(final Class _class) {
        addButton(_class.getSimpleName(), new Runnable() {
            @Override
            public void run() {
                startActivity(new Intent(TestSuiteActivity.this, _class));
            }
        });
    }

    private void pingXxmpp(String host, int port) {

        Pair<String, Integer> pair = new Pair<String, Integer>(host, port);
        String message = "Connecting to socket: " + host + ":" + port;
        Toast.makeText(TestSuiteActivity.this, message, Toast.LENGTH_LONG).show();

        new AsyncTask<Pair<String, Integer>, Void, String>() {
            @Override
            protected String doInBackground(Pair<String, Integer>... pairs) {
                Pair<String, Integer> pair = pairs[0];
                String response;
                try {
                    //Socket socket = SocketFactory.getDefault().createSocket(pair.first, pair.second);
                    Socket socket = SocketFactory.getDefault().createSocket();
                    socket.connect(new InetSocketAddress(pair.first, pair.second), 25000);
                    socket.setSoTimeout(10000);

                    socket.close();
                    response = "OK";
                } catch (IOException ex) {

                    response = ex.getMessage();
                }

                return response;
            }

            @Override
            protected void onPostExecute(String response) {
                Toast.makeText(TestSuiteActivity.this, response, Toast.LENGTH_LONG).show();
            }
        }.execute(pair);
    }

    private void startHttp() {
        mHttp = mSigma.getImpl(SigmaServiceB.getLocalHttp(), null);
    }

    @Override
    protected void onDestroy() {
        mHttp.destroy();
    }
}
