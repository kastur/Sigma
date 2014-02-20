package edu.ucla.nesl.sigma.base;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.*;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.util.Pair;
import com.google.common.collect.HashBiMap;
import edu.ucla.nesl.sigma.P.*;
import edu.ucla.nesl.sigma.api.ISigmaManager;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.nio.ByteBuffer;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

import static edu.ucla.nesl.sigma.base.SigmaDebug.LogDebug;
import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class SigmaEngine {
    public static final String TAG = SigmaEngine.class.getName();

    final Context mContext;
    final URI mBaseURI;
    final ISigmaManager mService;
    final IBinder mServiceManager;
    final SigmaParcelEncoder mEncoder;

    public static interface IRequestFactory {
        public SResponse doTransaction(SRequest request);
    }
    final IRequestFactory mFactory;
    final List<BinderRecord> mServedBinders;
    final HashMap<String, BinderRecord> mUuidToRecord;
    final WeakHashMap<IBinder, BinderRecord> mBinderToRecord;
    final HashBiMap<String, WeakReference<SigmaProxy>> mProxyObjects;
    final HashBiMap<String, ParcelFileDescriptor> mServedFiles;

    public SigmaEngine(Context context, URI baseURI, IRequestFactory requestFactory) {
        mContext = context;
        mBaseURI = baseURI;
        mFactory = requestFactory;
        mEncoder = new SigmaParcelEncoder(this);

        mServedBinders = new ArrayList<BinderRecord>();
        mUuidToRecord = new HashMap<String, BinderRecord>();
        mBinderToRecord = new WeakHashMap<IBinder, BinderRecord>();

        mServedFiles = HashBiMap.create();

        mProxyObjects = HashBiMap.create();

        // Create and serve engine's binder service.
        mService = new SigmaManagerInternal(mContext, this);
        serveBinder(mService.asBinder(), null);

        // ServiceManager has an address of "0", so it parses as a null
        // pointer when encoding / decoding into Parcels. To avoid this,
        // serve the ServiceManager specially through a local proxy.
        mServiceManager = new SigmaProxy(getServiceManagerInternal());
    }

    public URI getBaseURI() {
        return mBaseURI;
    }

    public URI getSelfURI() {
        return serveBinder(mService.asBinder(), null);
    }

    public ISigmaManager getManager() {
        return mService;
    }

    public IBinder getServiceManager() {
        return mServiceManager;
    }

    public ISigmaManager getRemoteManager(URI targetBaseURI) {
        SRequest request = (new SRequest.Builder())
                .self(getBaseURI())
                .target(targetBaseURI)
                .action(SRequest.ActionType.GET_SIGMA_MANAGER)
                .build();
        return ISigmaManager.Stub.asInterface(proxyBinder(mFactory.doTransaction(request).uri));
    }

    private class BinderRecord implements IBinder.DeathRecipient {
        final public String uuid;
        public IBinder localBinder;
        public ServiceConnection serviceConnection;
        public HashSet<URI> connectedProxies;
        public String _interface;

        public BinderRecord(String uuid, IBinder binder) {
            this.uuid = uuid;
            this.localBinder = binder;
            this.connectedProxies = new HashSet<URI>();

            try {
                binder.linkToDeath(this, 0);
            } catch (RemoteException ex) {
                throwUnexpected(ex);
            }

            synchronized (mServedBinders) {
                mServedBinders.add(this);
                mUuidToRecord.put(uuid, this);
                mBinderToRecord.put(binder, this);
            }
        }

        public void finalize() {
            binderClose(this);
        }

        @Override
        public void binderDied() {
            binderClose(this);
        }
    }

    private BinderRecord binderLookup(URI uri) {
        String uuid = uri.uuid;
        BinderRecord record = null;
        synchronized (mServedBinders) {
            if (mUuidToRecord.containsKey(uuid)) {
                record = mUuidToRecord.get(uuid);
            }
        }
        return record;
    }

    private void binderClose(BinderRecord record) {
        synchronized (mServedBinders) {
            mServedBinders.remove(record);
            mUuidToRecord.remove(record.uuid);
            mBinderToRecord.remove(record.localBinder);

            // Remove as a DeathReceipient, should be safe to call
            // even if binder is already dead.
            record.localBinder.unlinkToDeath(record, 0);

            // This should be the last and only strong reference to BinderProxy
            // Nulling out will eventually finalize it.
            record.localBinder = null;

            if (record.serviceConnection != null) {
                mContext.unbindService(record.serviceConnection);
            }
        }

        // Tell all remote, active proxies about the death.
        // Should only be needed if the binder dies locally.
        for (URI remote : record.connectedProxies) {
            URI target = (new URI.Builder(remote))
                    .type(URI.ObjectType.BINDER)
                    .uuid(record.uuid)
                    .build();
            SRequest request = (new SRequest.Builder())
                    .action(SRequest.ActionType.BINDER_DIED)
                    .self(getBaseURI())
                    .target(target)
                    .build();
            SResponse unusedResponse = mFactory.doTransaction(request);
        }

        // !!Force garbage collection.
        // Useful to immediately visualize effect with a command like:
        // watch -n1 adb -d shell dumpsys meminfo .SigmaService{A|B}
        System.gc();
    }

    public URI serveBinder(IBinder binder, ServiceConnection serviceConnection) {
        BinderRecord record = null;
        synchronized (mServedBinders) {
            if (mBinderToRecord.containsKey(binder)) {
                record = mBinderToRecord.get(binder);
            }

            if (record == null) {
                record = new BinderRecord(UUID.randomUUID().toString(), binder);
                record.serviceConnection = serviceConnection;

                //record.deathRecipient = new DeathRecipient(record.uuid);

                try {
                    record._interface = binder.getInterfaceDescriptor();
                    //binder.linkToDeath(record.deathRecipient, 0);
                } catch (RemoteException ex) {
                    throwUnexpected(ex);
                }
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

        LogInstanceDebug(TAG, "serveBinder uri: " + uri.toString());

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
        LogInstanceDebug(TAG, "Serve file: " + uri.toString());
        return uri;
    }

    public SResponse serve(SRequest request) {
        LogInstanceDebug(TAG, request.toString());

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
                        handleProxyAttached(request.self, request.target));
                break;
            }
            case BINDER_DISCONNECTED: {
                response = makeBooleanResponse(
                        handleProxyDetached(request.self, request.target));
                break;
            }
            case BINDER_DIED: {
                response = makeBooleanResponse(
                        handleRemoteBinderDied(request.self, request.target));
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

        LogInstanceDebug(TAG, response.toString());
        return response;
    }

    public SResponse handleBinderTransact(SRequest request) {
        STransactionRequest txnInfo = request.transaction_request;

        BinderRecord record = binderLookup(request.target);
        if (record == null || record.localBinder == null) {
            return makeBooleanResponse(false);
        }

        IBinder binder = record.localBinder;

        Parcel _data = Parcel.obtain();
        Parcel _reply = Parcel.obtain();
        mEncoder.decodeParcel(txnInfo.data, _data);

        boolean _return;
        try {
            LogInstanceDebug(TAG, "binder.transact() -- ping:" + binder.pingBinder() +
                    ", interface:" + binder.getInterfaceDescriptor());
            _return = binder.transact(txnInfo.code, _data, _reply, txnInfo.flags);
            if (_return == false) {
                Log.e(TAG, "binder.transact returned false!" + binder.getInterfaceDescriptor());
            }
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

    public boolean handleProxyAttached(URI source, URI target) {
        BinderRecord record = binderLookup(target);
        if (record == null) {
            return false;
        }

        if (source.type != URI.ObjectType.BASE) {
            throwUnexpected(new IllegalStateException("Expecting source of proxy connection to be BASE URI"));
        }

        synchronized (mServedBinders) {
            record.connectedProxies.add(source);
        }
        return true;
    }

    public boolean handleProxyDetached(URI source, URI target) {
        BinderRecord record = binderLookup(target);
        if (record == null) {
            return false;
        }

        if (source.type != URI.ObjectType.BASE) {
            throwUnexpected(new IllegalStateException("Expecting source of proxy connection to be BASE URI"));
        }

        synchronized (mServedBinders) {
            record.connectedProxies.remove(source);
        }

        if (record.connectedProxies.isEmpty()) {
            binderClose(record);
        }

        return true;

    }

    public boolean handleRemoteBinderDied(URI source, URI target) {
        String uuid = target.uuid;

        synchronized (mProxyObjects) {
            if (!mProxyObjects.containsKey(target.uuid)) {
                return false;
            }

            mProxyObjects.remove(target.uuid);
            return true;
        }
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
        LogInstanceDebug(TAG, "handleFilePut forwarded locally len=" + len);
        return true;
    }

    public boolean handleFileClose(URI uri) {
        Log.d(TAG, "handleFileClose()");
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

        synchronized (mServedFiles) {
            mServedFiles.remove(uri.uuid);
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
                    .error("UNSPECIFIED ERROR\n" + TextUtils.join("\n", Thread.currentThread().getStackTrace()))
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



    public IBinder proxyBinder(URI uri) {
        String uuid = uri.uuid;
        synchronized (mProxyObjects) {
            if (mProxyObjects.containsKey(uuid)) {
                return mProxyObjects.get(uuid).get();
            } else {
                SigmaProxy proxy = new SigmaProxy(uri);
                mProxyObjects.put(uuid, new WeakReference<SigmaProxy>(proxy));
                return proxy;
            }
        }
    }

    private void proxyAttach(URI target) {
        SRequest request = (new SRequest.Builder())
                .self(getBaseURI())
                .target(target)
                .action(SRequest.ActionType.BINDER_CONNECTED)
                .build();
        mFactory.doTransaction(request);
    }

    private void proxyDetach(URI target) {
        SRequest request = (new SRequest.Builder())
                .action(SRequest.ActionType.BINDER_DISCONNECTED)
                .self(getBaseURI())
                .target(target)
                .build();
        mFactory.doTransaction(request);
    }

    public void destroy() {
        /*
        for (Map.Entry<String, SigmaProxy> entry : mProxyObjects.entrySet()) {
            entry.getValue().destroy();
        }
        mProxyObjects.clear();
        */
    }

    private class SigmaProxy extends Binder {
        final URI mRemoteTarget;
        final IBinder mLocalTarget;

        public SigmaProxy(URI target) {
            LogInstanceDebug(TAG, "Created a SigmaProxy ----> " + target.toString());
            mRemoteTarget = target;
            mLocalTarget = null;
            proxyAttach(target);
        }

        public SigmaProxy(IBinder target) {
            LogInstanceDebug(TAG, "Created a SigmaProxy ----> " + target.toString());
            mLocalTarget = target;
            mRemoteTarget = null;
        }

        public void finalize() {
            destroy();
        }


        public void destroy() {
            if (mRemoteTarget != null) {
                proxyDetach(mRemoteTarget);
            }
        }

        @Override
        public String getInterfaceDescriptor() {
            if (mRemoteTarget != null) {
                return mRemoteTarget._interface;
            } else {
                try {
                    return mLocalTarget.getInterfaceDescriptor();
                } catch (RemoteException ex) {
                    throwUnexpected(ex);
                    return null;
                }
            }
        }

        @Override
        public IInterface queryLocalInterface(String descriptor) {
            return null;
        }

        @Override
        protected boolean onTransact(int code, Parcel data, Parcel reply, int flags) throws RemoteException {
            try {

                if (mRemoteTarget != null) {
                    if (code == INTERFACE_TRANSACTION) {
                        reply.writeString(mRemoteTarget._interface);
                        return true;
                    }
                    SParcel _data = mEncoder.encodeParcel(mRemoteTarget, data);
                    STransactionRequest transactionRequest =
                            (new STransactionRequest.Builder())
                                    .data(_data)
                                    .code(code)
                                    .flags(flags)
                                    .build();
                    SRequest request = (new SRequest.Builder())
                            .self(getBaseURI())
                            .target(mRemoteTarget)
                            .action(SRequest.ActionType.BINDER_TRANSACTION)
                            .transaction_request(transactionRequest)
                            .build();
                    SResponse response = mFactory.doTransaction(request);
                    boolean _return = response.transaction_response._return;
                    SParcel _reply = response.transaction_response.reply;
                    mEncoder.decodeParcel(_reply, reply);
                    //return _return;
                    return true;
                } else {
                    if (code == INTERFACE_TRANSACTION) {
                        reply.writeString(mLocalTarget.getInterfaceDescriptor());
                        return true;
                    }
                    mLocalTarget.transact(code, data, reply, flags);
                }
            } catch (Exception ex) {
                throwUnexpected(ex);
            }

            return true;
        }
    }


    public ParcelFileDescriptor receiveAndForwardFile(URI uri) {
        String uuid = uri.uuid;


        int[] pipe = SigmaJNI.socketpair();
        ParcelFileDescriptor serveFd = ParcelFileDescriptor.adoptFd(pipe[0]);
        ParcelFileDescriptor returnFd = ParcelFileDescriptor.adoptFd(pipe[1]);

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

    private class Forwarder extends Thread {
        final URI mTarget;
        final ParcelFileDescriptor mParcelFd;

        public Forwarder(URI target, ParcelFileDescriptor parcelFd) {
            LogInstanceDebug(TAG, "created Forwarder for fd=" + parcelFd.getFd() + " --> target=" + target.toString());
            mParcelFd = parcelFd;
            mTarget = target;
        }

        @Override
        public void run() {
            ByteBuffer buffer = ByteBuffer.allocateDirect(4096);
            while (true) {
                buffer.rewind();
                // FIXME: The parcel file descriptor never triggers a close event, threads are left in phantom state!
                SigmaJNI.waitForMessage(mParcelFd.getFd());
                int len = SigmaJNI.recvMessage(mParcelFd.getFd(), buffer);
                if (len <= 0) {
                    try {
                        mParcelFd.close();
                    } catch (IOException ex) {
                        Log.e(TAG, "Forwarder : IOException");
                    }

                    Log.d(TAG, "Forwarder closed");

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

            LogInstanceDebug(TAG, "File closed, forwarder shutting down...");
        }
    }


    private void LogInstanceDebug(String TAG, String message) {
        LogDebug(TAG, "_____" + mBaseURI.name + "_____" + message);
    }


    public static IBinder getServiceManagerInternal() {
        try {
            IBinder binder = (IBinder) Class.forName("com.android.internal.os.BinderInternal")
                    .getMethod("getContextObject")
                    .invoke(null);
            return binder;
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
            boolean acquired = semaphore.tryAcquire(10000, TimeUnit.MILLISECONDS);
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

}
