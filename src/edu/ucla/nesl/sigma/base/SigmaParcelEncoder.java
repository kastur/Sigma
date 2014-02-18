package edu.ucla.nesl.sigma.base;

import android.os.IBinder;
import android.os.Parcel;
import android.os.ParcelFileDescriptor;
import android.util.Base64;
import edu.ucla.nesl.sigma.P.SParcel;
import edu.ucla.nesl.sigma.P.URI;

import java.util.ArrayList;
import java.util.List;

public class SigmaParcelEncoder {
    public static final String TAG = SigmaParcelEncoder.class.getName();

    private SigmaEngine mEngine;

    public SigmaParcelEncoder(SigmaEngine engine) {
        mEngine = engine;
    }

    public SParcel encodeParcel(URI targetURI, Parcel parcel) {
        parcel.setDataPosition(0);
        byte[] data = parcel.marshall();
        String dataString = Base64.encodeToString(data, Base64.DEFAULT);
        List<URI> objects = encodeObjects(targetURI, parcel);
        parcel.setDataPosition(0);
        return (new SParcel.Builder()).bytes(dataString).objects(objects).build();
    }

    public void decodeParcel(SParcel sp, Parcel parcel) {
        parcel.setDataPosition(0);
        byte[] dataArray = Base64.decode(sp.bytes, Base64.DEFAULT);
        parcel.unmarshall(dataArray, 0, dataArray.length);

        if (sp.objects != null) {
            for (URI object : sp.objects) {
                if (URI.ObjectType.BINDER.equals(object.type)) {
                    decodeBinder(object, parcel);
                } else if (URI.ObjectType.UNIX_SOCKET.equals(object.type)) {
                    decodeFile(object, parcel);
                }
            }
        }
        parcel.setDataPosition(0);
    }

    private List<URI> encodeObjects(URI targetURI, Parcel parcel) {
        List<URI> objectArray = new ArrayList<URI>();

        int[] objectPositions = parcel.getObjectPositions();
        for (int dataPosition : objectPositions) {
            IBinder binder = null;
            try {
                parcel.setDataPosition(dataPosition);
                binder = parcel.readStrongBinder();
                parcel.readException();
            } catch (Exception ex) {
              /* EAT IT */
                // Expect an exception if the object is not a binder.
                // e.g. it could be a file.
            }

            if (binder != null) {
                URI object = encodeBinder(binder, dataPosition);
                objectArray.add(object);
            }

            ParcelFileDescriptor parcelFd = null;
            try {
                parcel.setDataPosition(dataPosition);
                parcelFd = parcel.readFileDescriptor();
            } catch (Exception ex) {
                /* EAT IT */
                // Expect an exception if the object is not a file.
            }

            if (parcelFd != null) {
                URI object = encodeFile(targetURI, parcelFd, dataPosition);
                objectArray.add(object);
            }
        }

        return objectArray;
    }

    private URI encodeBinder(IBinder binder, int offset) {
        URI uri = mEngine.serveBinder(binder, null);
        return (new URI.Builder(uri)).offset(offset).build();
    }

    private void decodeBinder(URI uri, Parcel parcel) {
        URI target = (new URI.Builder(uri).offset(null).build());
        IBinder binderProxy = mEngine.proxyBinder(target);
        parcel.setDataPosition(uri.offset);
        parcel.writeStrongBinder(binderProxy);
    }

    private URI encodeFile(URI targetBaseURI, ParcelFileDescriptor parcelFd, int offset) {
        URI fileURI = mEngine.receiveAndForwardFile(parcelFd, targetBaseURI);
        return (new URI.Builder(fileURI)).offset(offset).build();
    }

    private void decodeFile(URI fileURI, Parcel parcel) {
        URI targetBaseURI = (new URI.Builder(fileURI).offset(null).build());
        ParcelFileDescriptor parcelFd = mEngine.receiveAndForwardFile(targetBaseURI);
        parcel.setDataPosition(fileURI.offset);
        parcel.writeFileDescriptor(parcelFd.getFileDescriptor());
    }
}
