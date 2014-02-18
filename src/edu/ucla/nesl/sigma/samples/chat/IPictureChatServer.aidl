package edu.ucla.nesl.sigma.samples.chat;

import edu.ucla.nesl.sigma.samples.chat.ICommentReceiver;
import edu.ucla.nesl.sigma.samples.chat.PicturePutInfo;



interface IPictureChatServer {
  PicturePutInfo putPicture(int numBytes);
  int getPicture(in PicturePutInfo pictureInfp);
}