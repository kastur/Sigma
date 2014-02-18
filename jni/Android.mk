LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := sigmalib
LOCAL_SRC_FILES := edu_ucla_nesl_sigma_base_SigmaJNI.c
LOCAL_LDLIBS := -L$(SYSROOT)/usr/lib -llog -landroid

include $(BUILD_SHARED_LIBRARY)
