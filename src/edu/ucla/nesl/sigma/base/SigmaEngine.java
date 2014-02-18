package edu.ucla.nesl.sigma.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.util.Base64;
import android.util.Pair;
import com.google.common.collect.HashBiMap;
import edu.ucla.nesl.sigma.P.*;
import edu.ucla.nesl.sigma.api.ISigmaManager;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class SigmaEngine {
    public static final String TAG = SigmaEngine.class.getName();

    protected class BinderRecord {
        public String uuid;
        public IBinder binder;
        public ServiceConnection serviceConnection;
        public IBinder.DeathRecipient deathRecipient;
        public HashSet<URI> activeConnections;
        public String _interface;
    }

    public static interface IRequestFactory {
        public SResponse doTransaction(SRequest request);
    }

    final IRequestFactory mFactory;

    final HashBiMap<String, SigmaProxy> mProxyObjects;

    final Context mContext;
    final List<BinderRecord> mBinderRecords;
    final HashMap<String, BinderRecord> mUuidToRecord;
    final HashMap<IBinder, BinderRecord> mBinderToRecord;
    final HashBiMap<String, ParcelFileDescriptor> mServedFiles;

    final SigmaParcelEncoder mEncoder;
    final URI mBaseURI;
    final ISigmaManager mService;


    public SigmaEngine(Context context, URI baseURI, IRequestFactory requestFactory) {
        mContext = context;

        mProxyObjects = HashBiMap.create();
        mFactory = requestFactory;

        mUuidToRecord = new HashMap<String, BinderRecord>();
        mBinderRecords = new ArrayList<BinderRecord>();
        mBinderToRecord = new HashMap<IBinder, BinderRecord>();
        mEncoder = new SigmaParcelEncoder(this);
        mServedFiles = HashBiMap.create();
        mBaseURI = baseURI;
        mService = new SigmaManagerInternal(mContext, this);
        ;
        serveBinder(mService.asBinder(), null);
    }

    public ISigmaManager getManager() {
        return mService;
    }

    public URI getBaseURI() {
        return mBaseURI;
    }

    public URI getSelfURI() {
        return serveBinder(mService.asBinder(), null);
    }

    private void handleLocalBinderDied(String uuid) {
        synchronized (mBinderRecords) {
            if (!mUuidToRecord.containsKey(uuid)) {
                return;
            }

            BinderRecord record = mUuidToRecord.get(uuid);
            mUuidToRecord.remove(uuid);
            mBinderToRecord.remove(record.binder);
            mBinderRecords.remove(record);
        }
    }

    public URI serveBinder(IBinder binder, ServiceConnection serviceConnection) {
        BinderRecord record = null;
        synchronized (mBinderRecords) {
            if (mBinderToRecord.containsKey(binder)) {
                record = mBinderToRecord.get(binder);
            }

            if (record == null) {
                record = new BinderRecord();
                record.binder = binder;
                record.serviceConnection = serviceConnection;
                record.uuid = UUID.randomUUID().toString();

                class DeathRecipient implements IBinder.DeathRecipient {
                    String uuid;

                    public DeathRecipient(String uuid) {
                        this.uuid = uuid;
                    }

                    @Override
                    public void binderDied() {
                        handleLocalBinderDied(uuid);
                    }
                }

                record.deathRecipient = new DeathRecipient(record.uuid);

                try {
                    record._interface = binder.getInterfaceDescriptor();
                    binder.linkToDeath(record.deathRecipient, 0);
                } catch (RemoteException ex) {
                    throwUnexpected(ex);
                }

                record.activeConnections = new HashSet<URI>();

                mBinderRecords.add(record);
                mBinderToRecord.put(record.binder, record);
                mUuidToRecord.put(record.uuid, record);
            }
        }

        String _interface = null;
        try {
            _interface = binder.getInterfaceDescriptor();
        } catch (RemoteException ex) { /* EAT IT */ }

        URI uri = (new URI.Builder(getBaseURI()))
                .type(URI.ObjectType.BINDER)
                .uuid(record.uuid)
                ._interface(_interface)
                .build();

        LogDebug(TAG, "serveBinder uri: " + uri.toString());

        return uri;
    }

    public URI serveUnixSocket(ParcelFileDescriptor parcelFd, String uuid) {
        synchronized (mServedFiles) {
            if (uuid != null && mServedFiles.containsKey(uuid)) {
                throwUnexpected(new IllegalStateException("Broken: UUID already in map."));
            }

            if (uuid == null) {
                if (mServedFiles.containsValue(parcelFd)) {
                    uuid = mServedFiles.inverse().get(parcelFd);
                } else {
                    uuid = UUID.randomUUID().toString();
                }
            }

            mServedFiles.put(uuid, parcelFd);

        }

        URI uri = (new URI.Builder(getBaseURI()))
                .type(URI.ObjectType.UNIX_SOCKET)
                .uuid(uuid)
                .build();
        LogDebug(TAG, "Serve file: " + uri.toString());
        return uri;
    }

    // TODO: Reinstate this function...
    /*
    public URI getServiceURI(Intent intent) {
        Pair<IBinder, ServiceConnection> pair = getService(mContext, intent);
        IBinder binder = pair.first;
        ServiceConnection connection = pair.second;
        return serveBinder(binder, connection);
    }
    */

    private BinderRecord lookupRecord(URI uri) {
        String uuid = uri.uuid;
        BinderRecord record = null;
        synchronized (mBinderRecords) {
            if (mUuidToRecord.containsKey(uuid)) {
                record = mUuidToRecord.get(uuid);
            }
        }
        return record;
    }

    public SResponse serve(SRequest request) {
        LogDebug(TAG, request.toString());

        if (request.self.equals(request.target)) {
            throwUnexpected(new IllegalStateException("Cannot make request to self."));
        }

        SResponse response;

        SRequest.ActionType type = request.action;
        switch (type) {
            case BINDER_TRANSACTION: {
                response = handleBinderTransact(request);
                break;
            }
            case GET_SIGMA_MANAGER: {
                response = makeURIResponse(getSelfURI());
                break;
            }
            case FILE_RECV: {
                byte[] bytes = Base64.decode(
                        request.socket_data_received.bytes,
                        Base64.DEFAULT);
                response = makeBooleanResponse(
                        handleFileReceive(request.target, bytes));
                break;
            }
            case BINDER_CONNECTED: {
                response = makeBooleanResponse(
                        handleBinderConnected(request.target));
                break;
            }
            case BINDER_DISCONNECTED: {
                response = makeBooleanResponse(
                        handleBinderDisconnected(request.target));
                break;
            }
            case BINDER_LINK_TO_DEATH: {
                response = makeNotYetImplementedResponse();
                break;
            }
            case BINDER_UNLINK_TO_DEATH: {
                response = makeNotYetImplementedResponse();
                break;
            }
            case FILE_CLOSE: {
                response = makeBooleanResponse(
                        handleFileClose(request.target));
                break;
            }
            case GET_SERVICE: {
                response = makeNotYetImplementedResponse();
                break;
            }
            default: {
                response = makeNotYetImplementedResponse();
            }
        }

        LogDebug(TAG, response.toString());
        return response;
    }

    public SResponse handleBinderTransact(SRequest request) {
        STransactionRequest txnInfo = request.transaction_request;

        BinderRecord record = lookupRecord(request.target);
        if (record == null || record.binder == null) {
            return makeBooleanResponse(false);
        }

        IBinder binder = record.binder;

        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        mEncoder.decodeParcel(txnInfo.data, _data);

        boolean _return;
        try {
            LogDebug(TAG, "binder.transact() -- ping:" + binder.pingBinder() +
                    ", interface:" + binder.getInterfaceDescriptor());
            _return = binder.transact(txnInfo.code, _data, _reply, txnInfo.flags);
            SParcel _replyArray = mEncoder.encodeParcel(request.self, _reply);
            STransactionResponse txnResponse = (new STransactionResponse.Builder())
                    .reply(_replyArray)
                    ._return(_return).build();
            return (new SResponse.Builder())
                    .self(getBaseURI())
                    .type(SResponse.Type.BINDER_TRANSACTION_RESPONSE)
                    .transaction_response(txnResponse)
                    .build();

        } catch (RemoteException ex) {
            return (new SResponse.Builder())
                    .self(getBaseURI())
                    .type(SResponse.Type.ERROR)
                    .error(ex.getMessage())
                    .build();

        }
    }

    public boolean handleBinderConnected(URI uri) {
        BinderRecord record = lookupRecord(uri);
        if (record == null) {
            return false;
        }

        synchronized (mBinderRecords) {
            URI peer = (new URI.Builder(uri))
                    .type(URI.ObjectType.BASE)
                    .uuid(null)
                    .build();
            record.activeConnections.add(peer);
            return true;
        }
    }

    public boolean handleBinderDisconnected(URI uri) {
        BinderRecord record = lookupRecord(uri);
        if (record == null) {
            return false;
        }

        synchronized (mBinderRecords) {
            URI peer = (new URI.Builder(uri))
                    .type(URI.ObjectType.BASE)
                    .uuid(null)
                    .build();
            record.activeConnections.remove(peer);
            if (record.activeConnections.isEmpty()) {
                LogDebug(TAG, "record.activeConnections.isEmpty() -- Discarding BinderRecord" + record._interface);
                mUuidToRecord.remove(record.uuid);
                mBinderToRecord.remove(record.binder);
                mBinderRecords.remove(record);
                if (record.deathRecipient != null) {
                    record.binder.unlinkToDeath(record.deathRecipient, 0);
                }
                if (record.serviceConnection != null) {
                    mContext.unbindService(record.serviceConnection);
                }
            }
        }
        return true;
    }

    public boolean handleFileReceive(URI uri, byte[] bytes) {
        ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
        buffer.put(bytes);

        ParcelFileDescriptor parcelFd;
        synchronized (mServedFiles) {
            if (!mServedFiles.containsKey(uri.uuid)) {
                return false;
            }

            parcelFd = mServedFiles.get(uri.uuid);
        }

        int len;
        synchronized (parcelFd) {
            len = SigmaJNI.sendMessage(parcelFd.getFd(), buffer, bytes.length);
        }

        if (len < 0) {
            synchronized (mServedFiles) {
                try {
                    parcelFd.close();
                } catch (IOException ex) {
                }
                mServedFiles.remove(uri);
            }
        }
        LogDebug(TAG, "handleFilePut forwarded locally len=" + len);
        return true;
    }

    public boolean handleFileClose(URI uri) {
        ParcelFileDescriptor parcelFd;
        synchronized (mServedFiles) {
            if (!mServedFiles.containsKey(uri.uuid)) {
                return false;
            }

            parcelFd = mServedFiles.get(uri.uuid);
        }
        synchronized (parcelFd) {
            try {
                parcelFd.close();
            } catch (IOException ex) {
            }
        }
        return true;
    }

    public SResponse makeBooleanResponse(boolean success) {
        if (success) {
            return (new SResponse.Builder())
                    .self(getBaseURI())
                    .type(SResponse.Type.OK)
                    .build();
        } else {
            return (new SResponse.Builder())
                    .self(getBaseURI())
                    .type(SResponse.Type.ERROR)
                    .error("UNSPECIFIED ERROR")
                    .build();
        }
    }

    public SResponse makeURIResponse(URI uri) {
        return (new SResponse.Builder())
                .self(getBaseURI())
                .type(SResponse.Type.URI)
                .uri(uri)
                .build();
    }

    public SResponse makeNotYetImplementedResponse() {
        return (new SResponse.Builder())
                .self(getBaseURI())
                .type(SResponse.Type.ERROR)
                .error("Not yet implemented")
                .build();
    }

    public static IBinder getServiceManager() {
        try {
            return (IBinder) Class.forName("com.android.internal.os.BinderInternal")
                    .getMethod("getContextObject")
                    .invoke(null);
        } catch (Exception ex) {
            throwUnexpected(ex);
        }
        return null;
    }

    public static IBinder getActivityManager() {
        try {
            Object activityManager = Class.forName("android.app.ActivityManagerNative")
                    .getMethod("getDefault")
                    .invoke(null);
            return (IBinder) activityManager.getClass().getMethod("getSelfService").invoke(activityManager);
        } catch (Exception ex) {
            throwUnexpected(ex);
        }
        return null;
    }

    public static Pair<IBinder, ServiceConnection> getService(Context context, Intent intent) {
        final Semaphore semaphore = new Semaphore(1);
        try {
            semaphore.acquire();
        } catch (InterruptedException ex) {
            throwUnexpected(ex);
        }

        class Holder<T> {
            public T value;

            public Holder() {
            }

            public void setValue(T val) {
                this.value = val;
            }
        }
        final Holder<IBinder> holder = new Holder<IBinder>();

        ServiceConnection connection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName componentName, IBinder binder) {
                String interfaceDescriptor = null;
                try {
                    interfaceDescriptor = binder.getInterfaceDescriptor();
                } catch (RemoteException ex) {
                    throwUnexpected(ex);
                }
                LogDebug(TAG, "onServiceConnected. interface:" + interfaceDescriptor);
                holder.setValue(binder);
                semaphore.release();
            }

            @Override
            public void onServiceDisconnected(ComponentName componentName) {
            }
        };

        context.bindService(intent, connection, Context.BIND_AUTO_CREATE);

        try {
            boolean acquired = semaphore.tryAcquire(1000, TimeUnit.MILLISECONDS);
            if (!acquired) {
                return null;
            }
        } catch (InterruptedException ex) {
            throwUnexpected(ex);
        }

        return new Pair<IBinder, ServiceConnection>(holder.value, connection);
    }

    public static URI convertToBaseURI(URI uri) {
        return (new URI.Builder(uri))
                .type(URI.ObjectType.BASE)
                .uuid(null)
                ._interface(null)
                .build();
    }

    // -- Methods to deal with connecting to remote binder objects

    public ISigmaManager getRemoteManager(URI targetBaseURI) {
        SRequest request = (new SRequest.Builder())
                .self(getBaseURI())
                .target(targetBaseURI)
                .action(SRequest.ActionType.GET_SIGMA_MANAGER)
                .build();
        return ISigmaManager.Stub.asInterface(proxyBinder(mFactory.doTransaction(request).uri));
    }

    public IBinder proxyBinder(URI uri) {
        String uuid = uri.uuid;
        synchronized (mProxyObjects) {
            if (mProxyObjects.containsKey(uuid)) {
                return mProxyObjects.get(uuid);
            } else {
                SigmaProxy proxy = new SigmaProxy(uri);
                mProxyObjects.put(uuid, proxy);
                return proxy;
            }
        }
    }

    public ParcelFileDescriptor receiveAndForwardFile(URI uri) {
        String uuid = uri.uuid;

        ParcelFileDescriptor serveFd;
        ParcelFileDescriptor returnFd;
        try {
            int[] pipe = SigmaJNI.socketpair();
            serveFd = ParcelFileDescriptor.fromFd(pipe[0]);
            returnFd = ParcelFileDescriptor.fromFd(pipe[1]);
        } catch (IOException ex) {
            throwUnexpected(ex);
            return null;
        }

        Thread fileForwarder = new Forwarder(uri, serveFd);
        fileForwarder.start();

        serveUnixSocket(serveFd, uuid);

        return returnFd;
    }

    public URI receiveAndForwardFile(ParcelFileDescriptor parcelFd, URI remoteBaseURI) {
        URI fileURI = serveUnixSocket(parcelFd, null);
        URI target = (new URI.Builder(remoteBaseURI))
                ._interface("")
                .type(URI.ObjectType.UNIX_SOCKET)
                .uuid(fileURI.uuid)
                .build();
        Thread fileForwarder = new Forwarder(target, parcelFd);
        fileForwarder.start();

        return fileURI;
    }

    private void notifyConnected(URI target) {
        SRequest request = (new SRequest.Builder())
                .self(getBaseURI())
                .target(target)
                .action(SRequest.ActionType.BINDER_CONNECTED)
                .build();
        mFactory.doTransaction(request);
    }

    private void notifyDisconnected(URI target) {
        SRequest request = (new SRequest.Builder())
                .action(SRequest.ActionType.BINDER_DISCONNECTED)
                .self(getBaseURI())
                .target(target)
                .build();
        mFactory.doTransaction(request);
    }

    public void destroy() {
        for (Map.Entry<String, SigmaProxy> entry : mProxyObjects.entrySet()) {
            entry.getValue().destroy();
        }
        mProxyObjects.clear();
    }

    private class SigmaProxy extends Binder {
        URI mTarget;

        public SigmaProxy(URI target) {
            mTarget = target;
            notifyConnected(target);
        }

        public void destroy() {
            notifyDisconnected(mTarget);
        }

        @Override
        public String getInterfaceDescriptor() {
            return mTarget._interface;
        }

        @Override
        public IInterface queryLocalInterface(String descriptor) {
            return null;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            try {
                SParcel _data = mEncoder.encodeParcel(mTarget, data);
                STransactionRequest transactionRequest =
                        (new STransactionRequest.Builder())
                                .data(_data)
                                .code(code)
                                .flags(flags)
                                .build();
                SRequest request = (new SRequest.Builder())
                        .self(getBaseURI())
                        .target(mTarget)
                        .action(SRequest.ActionType.BINDER_TRANSACTION)
                        .transaction_request(transactionRequest)
                        .build();
                SResponse response = mFactory.doTransaction(request);
                boolean _return = response.transaction_response._return;
                SParcel _reply = response.transaction_response.reply;
                mEncoder.decodeParcel(_reply, reply);
                return _return;
            } catch (Exception ex) {
                throwUnexpected(ex);
            }

            return false;
        }
    }

    private class Forwarder extends Thread {
        private final String TAG = Forwarder.class.getName();
        final URI mTarget;
        final ParcelFileDescriptor mParcelFd;

        public Forwarder(URI target, ParcelFileDescriptor parcelFd) {
            mParcelFd = parcelFd;
            mTarget = target;
            LogDebug(TAG, "created Forwarder for fd=" + parcelFd.getFd() + " --> target=" + target.toString());
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            while (true) {
                buffer.rewind();
                SigmaJNI.waitForMessage(mParcelFd.getFd());
                int len = SigmaJNI.recvMessage(mParcelFd.getFd(), buffer);
                if (len <= 0) {
                    try {
                        mParcelFd.close();
                    } catch (IOException ex) {
                    }

                    SRequest request = (new SRequest.Builder())
                            .self(getBaseURI())
                            .target(mTarget)
                            .action(SRequest.ActionType.FILE_CLOSE)
                            .build();

                    mFactory.doTransaction(request);
                    break;
                }

                byte[] bytes = new byte[len];
                buffer.get(bytes, 0, len);

                SSocketDataReceived data_received =
                        (new SSocketDataReceived.Builder())
                                .bytes(Base64.encodeToString(bytes, Base64.DEFAULT))
                                .build();

                SRequest request = (new SRequest.Builder())
                        .self(getBaseURI())
                        .target(mTarget)
                        .action(SRequest.ActionType.FILE_RECV)
                        .socket_data_received(data_received)
                        .build();

                mFactory.doTransaction(request);
            }

            LogDebug(TAG, "File closed, forwarder shutting down...");
        }
    }

}