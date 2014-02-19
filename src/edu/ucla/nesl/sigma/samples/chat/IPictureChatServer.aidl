package edu.ucla.nesl.sigma.samples.chat;

import edu.ucla.nesl.sigma.api.ISigmaManager;
import edu.ucla.nesl.sigma.samples.chat.PictureEntry;
import android.os.ParcelFileDescriptor;


interface IPictureChatServer {
  ParcelFileDescriptor /* readFrom */ requestPicturePut(ISigmaManager remote, inout PictureEntry entry);
  PictureEntry requestPictureGet(ISigmaManager remote, String uuid, in ParcelFileDescriptor writeTo);
}