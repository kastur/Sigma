package edu.ucla.nesl.sigma.impl.xmpp;

import android.content.Context;
import android.util.Base64;
import android.util.Log;

import org.jivesoftware.smack.AndroidConnectionConfiguration;
import org.jivesoftware.smack.Chat;
import org.jivesoftware.smack.ChatManagerListener;
import org.jivesoftware.smack.MessageListener;
import org.jivesoftware.smack.SmackAndroid;
import org.jivesoftware.smack.XMPPConnection;
import org.jivesoftware.smack.XMPPException;
import org.jivesoftware.smack.packet.Message;

import java.util.HashSet;
import java.util.UUID;
import java.util.concurrent.Semaphore;

import edu.ucla.nesl.sigma.P.SMessage;
import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.base.SigmaDebug;
import edu.ucla.nesl.sigma.base.SigmaEngine;
import edu.ucla.nesl.sigma.base.SigmaWire;
import edu.ucla.nesl.sigma.samples.TimeStats;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class XmppClient implements ChatManagerListener, SigmaEngine.IRequestFactory {

  public static final String TAG = XmppClient.class.getName();

  public static void InitializeWithContext(Context context) {
    SmackAndroid.init(context);
  }

  public static class XmppConfig {

    public final String host;
    public final int port;
    public final String domain;
    public final String username;
    public final String password;

    public XmppConfig(String host, int port, String domain, String username, String password) {
      this.host = host;
      this.port = port;
      this.domain = domain;
      this.username = username;
      this.password = password;
    }
  }

  @Override
  public SResponse doTransaction(SRequest request) {
    String chatTo = request.target.login + "@" + request.target.domain;
    LogDebug(TAG, " --- DO_TRANSACTION --- to:" + chatTo + ", action:" + request.action);
    String chatThreadId = UUID.randomUUID().toString();
    Chat chat = connection.getChatManager().createChat(chatTo, chatThreadId, null);
    TransactionClientThread transaction = new TransactionClientThread(chat, request);
    transaction.start();

    try {
      transaction.join();
    } catch (InterruptedException ex) {
      throwUnexpected(ex);
    }

    return transaction.getResponse();
  }

  public void handleTransaction(Chat chat) {
    LogDebug(TAG, " --- HANDLE_TRANSACTION --- " + chat.getThreadID());
    TransactionHandlerThread transactionThread = new TransactionHandlerThread(chat);
    transactionThread.start();
  }

  private abstract class BaseTransaction implements MessageListener {

    protected String TAG = getTag();

    public abstract String getTag();

    final protected Chat mChat;
    final protected Semaphore mSemaphore;
    protected SMessage mMessage;

    HashSet<String> mSelfPackets;

    public BaseTransaction(Chat chat) {
      mChat = chat;
      mSemaphore = new Semaphore(1);
      tryAcquire();
      mMessage = null;
      mSelfPackets = new HashSet<String>();
      chat.addMessageListener(this);
    }

    @Override
    public void processMessage(Chat chat, Message message) {
      TimeStats.Timer timer = TimeStats.getInstance(TimeStats.kXmppProcessMessage).startTiming();

      if (mState != State.ACTIVE) {
        throwUnexpected(new IllegalStateException());
      }

      String packetId = message.getPacketID();
      if (message.getType() == Message.Type.error) {
        (new SMessage.Builder())
            .type(SMessage.Type.ERROR)
            .error(message.getBody())
            .build();
        tryRelease();
        timer.addElapsed();
        return;
      }

      if (mSelfPackets.contains(packetId)) {
        LogDebug(TAG, "Ignoring packet: " + packetId);
        return;
      }

      if (message.getType() != Message.Type.chat ||
          message.getBody() == null) {
        LogDebug(TAG, "Wrong type of message: " + packetId);
        return;
      }

      byte[] messageBytes = Base64.decode(message.getBody(), Base64.DEFAULT);

      SMessage sMessage = null;
      sMessage = SigmaWire.getInstance().parseFrom(messageBytes, SMessage.class);

      mMessage = sMessage;
      tryRelease();
      timer.addElapsed();
    }

    public void sendMessage(SMessage sMessage) {
      TimeStats.Timer timer = TimeStats.getInstance(TimeStats.kXmppSendMessage).startTiming();
      if (mState != State.ACTIVE) {
        throw new IllegalStateException();
      }

      Message m = new Message();
      m.setThread(mChat.getThreadID());
      m.setType(Message.Type.chat);
      m.setBody(Base64.encodeToString(sMessage.toByteArray(), Base64.DEFAULT));
      m.setPacketID(UUID.randomUUID().toString());
      String packetId = m.getPacketID();

      mSelfPackets.add(packetId);
      try {
        mChat.sendMessage(m);
      } catch (XMPPException ex) {
        throwUnexpected(ex);
      }

      timer.addElapsed();
    }

    private void tryAcquire() {
      try {
        mSemaphore.acquire();
      } catch (InterruptedException ex) {
        throwUnexpected(ex);
      }
    }

    private void tryRelease() {
      mSemaphore.release();
    }

    protected SMessage waitForMessage() {
      tryAcquire();
      return mMessage;
    }
  }

  private class TransactionHandlerThread extends Thread {

    private class TransactionHandler extends BaseTransaction {

      public String getTag() {
        return "TransactionHandler";
      }

      public TransactionHandler(Chat chat) {
        super(chat);
      }

      public void processTransact() {
        LogDebug(getTag(), "processTransact() chatThread: " + mChat.getThreadID() +
                           ", javaThread:" + Thread.currentThread().getId());
        SRequest request = waitForMessage().request;
        if (request == null) {
          LogDebug(TAG, "request is null!");
          return;
        }
        SResponse response = mEngine.serve(request);
        SMessage message = (new SMessage.Builder())
            .type(SMessage.Type.RESPONSE)
            .response(response).build();
        sendMessage(message);
      }
    }

    private TransactionHandler handler;

    public TransactionHandlerThread(Chat chat) {
      handler = new TransactionHandler(chat);
    }

    @Override
    public void run() {
      handler.processTransact();
    }
  }

  private class TransactionClientThread extends Thread {

    private TransactionClient mClient;
    SRequest mRequest;
    SResponse mResponse;

    public TransactionClientThread(Chat chat, SRequest request) {
      mClient = new TransactionClient(chat);
      mRequest = request;
    }

    public SResponse getResponse() {
      return mResponse;
    }

    @Override
    public void run() {
      mResponse = mClient.execTransact();
    }

    private class TransactionClient extends BaseTransaction {

      public String getTag() {
        return "TransactionClient";
      }

      public TransactionClient(Chat chat) {
        super(chat);
      }

      public SResponse execTransact() {
        LogDebug(getTag(), "execTransact() chatThread: " + mChat.getThreadID() +
                           ", javaThread:" + Thread.currentThread().getId());
        sendMessage((new SMessage.Builder())
                        .type(SMessage.Type.REQUEST)
                        .request(mRequest).build());
        SResponse response = waitForMessage().response;
        return response;
      }
    }
  }

  private XmppConfig mConfig;
  //private IDataListener mReceiver;
  private XMPPConnection connection;

  enum State {
    INITIALIZED,
    ACTIVE,
  }

  State mState;

  final SigmaEngine mEngine;

  public XmppClient(XmppConfig config, SigmaEngine engine) {
    mConfig = config;
    mEngine = engine;
    mState = State.INITIALIZED;

  }

  public boolean login() {
    if (mState != State.INITIALIZED) {
      throw new IllegalStateException();
    }

    AndroidConnectionConfiguration config =
        new AndroidConnectionConfiguration(mConfig.host, mConfig.port, mConfig.domain);

    if (SigmaDebug.DEBUG_TAGS.contains(TAG)) {
      config.setDebuggerEnabled(true);
    }
    connection = new XMPPConnection(config);

    try {
      connection.connect();
      connection.login(mConfig.username, mConfig.password);
    } catch (XMPPException ex) {
      throwUnexpected(ex);
    }

    Log.d(TAG, "------ XMPP Connected as: " + connection.getUser() + " --------");
    connection.getChatManager().addChatListener(this);

    mState = State.ACTIVE;
    return true;
  }

  public void disconnect() {
    if (mState != State.ACTIVE) {
      throw new IllegalStateException();
    }
    connection.disconnect();
    mState = State.INITIALIZED;
  }

  @Override
  public void chatCreated(Chat chat, boolean createdLocally) {
    if (!createdLocally) {
      handleTransaction(chat);
    }
  }
}

