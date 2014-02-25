#include <jni.h>

#include <stdint.h>
#include <sys/types.h>
#include <sys/socket.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>

#include <android/log.h>
#include <android/looper.h>

#define TAG "sigmalib"

//#define DEBUG_MODE

#ifdef DEBUG_MODE
  #define ALOGD(x...)  __android_log_print(ANDROID_LOG_INFO, TAG, x)
  #define ALOGE(x...)  __android_log_print(ANDROID_LOG_ERROR, TAG, x)
#else
  #define ALOGD(x...)
  #define ALOGE(x...)
#endif
#include "edu_ucla_nesl_sigma_base_SigmaJNI.h"

JNIEXPORT jstring JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_getMessage
    (JNIEnv *env, jobject thisObj) {
   return (*env)->NewStringUTF(env, "Hello from native code!");
}

JNIEXPORT jintArray JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_socketpair
    (JNIEnv *env, jclass _class) {
  jintArray retArray = (*env)->NewIntArray(env, 2);
  jint* sockets = (*env)->GetIntArrayElements(env, retArray, NULL);

  socketpair(AF_UNIX, SOCK_SEQPACKET, 0, sockets);
  int size = 4096;
  setsockopt(sockets[0], SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));
  setsockopt(sockets[0], SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
  setsockopt(sockets[1], SOL_SOCKET, SO_SNDBUF, &size, sizeof(size));
  setsockopt(sockets[1], SOL_SOCKET, SO_RCVBUF, &size, sizeof(size));
  fcntl(sockets[0], F_SETFL, O_NONBLOCK);
  fcntl(sockets[1], F_SETFL, O_NONBLOCK);
  //mReceiveFd = sockets[0];
  //mSendFd = sockets[1];
  ALOGD("Created socketpair() with receiveFd=%d and sendFd=%d", sockets[0], sockets[1]);

  return retArray;
}

JNIEXPORT void JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_closeFd
    (JNIEnv *env, jclass _class, jint fd) {
  close(fd);
}

JNIEXPORT jint JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_sendMessage
    (JNIEnv *env, jclass _class, jint fd, jobject buf, jint len) {
  void* vaddr = (*env)->GetDirectBufferAddress(env, buf);
  ALOGD("sendMessage buffer@%d", (int)vaddr);
  int ii;

  /*
  for (ii = 0; ii < len; ++ii) {
    ALOGD("Content: %c", *(char*)(vaddr + ii));
  }
  */

  int sentBytes = send(fd, vaddr, len, MSG_WAITALL);
  int err = sentBytes < 0 ? -errno : sentBytes;

  ALOGD("sendMessage len=%d", (int)err);
  return err;
}

JNIEXPORT jint JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_recvMessage
    (JNIEnv *env, jclass _class, jint fd, jobject buf) {
  void* vaddr = (*env)->GetDirectBufferAddress(env, buf);
  ALOGD("recvMessage buffer@%d", (int)vaddr);
  //::recv(fd, (void*)vaddr, 4096, MSG_DONTWAIT);
  int readBytes = recv(fd, vaddr, 4096, MSG_WAITALL);
  int err = readBytes < 0 ? -errno : readBytes;

  /*
  if (readBytes > 0) {
    int ii;
    for (ii = 0; ii < readBytes; ++ii) {
      ALOGD("Content: %c", *(char*)(vaddr + ii));
    }
  }
  */

  ALOGD("recvMessage len=%d", (int)readBytes);
  return readBytes;
}

JNIEXPORT jboolean JNICALL Java_edu_ucla_nesl_sigma_base_SigmaJNI_waitForMessage
    (JNIEnv *env, jclass _class, jint fd) {
  ALooper* looper = ALooper_prepare(ALOOPER_PREPARE_ALLOW_NON_CALLBACKS);
  ALOGD("waitForMessage: pollOnce looper@%d fd@%d", (int)looper, fd);
  ALooper_addFd(looper, fd, fd, ALOOPER_EVENT_INPUT, NULL, NULL);

  int events;
  int result = ALooper_pollOnce(-1, NULL, &events, NULL);
  if (result == ALOOPER_POLL_ERROR) {
      ALOGE("waitForMessage error (errno=%d)", errno);
      result = -1337; // unknown error, so we make up one
      return JNI_FALSE;
  }
  if (events & ALOOPER_EVENT_HANGUP) {
      // the other-side has died
      ALOGE("waitForMessage error HANGUP");
      result = -1337; // unknown error, so we make up one
      return JNI_FALSE;
  }

  return JNI_TRUE;

  ALOGD("waitForMessage: got something.");
}
