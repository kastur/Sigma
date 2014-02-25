package edu.ucla.nesl.sigma.api;

import android.os.RemoteException;

import edu.ucla.nesl.sigma.P.SRequest;
import edu.ucla.nesl.sigma.P.SResponse;
import edu.ucla.nesl.sigma.base.SigmaWire;

import static edu.ucla.nesl.sigma.base.SigmaDebug.throwUnexpected;

public class RequestSender {

  final IRequestSender mSender;

  public RequestSender(IRequestSender sender) {
    mSender = sender;
  }

  public SResponse send(SRequest request) {
    try {
      byte[] resposneBytes = mSender.send(request.toByteArray());
      return SigmaWire.getInstance().parseFrom(resposneBytes, SResponse.class);
    } catch (RemoteException ex) {
      throwUnexpected(ex);
      return null;
    }
  }
}
