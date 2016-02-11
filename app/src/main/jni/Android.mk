#include $(all-subdir-makefiles)

LOCAL_PATH := $(call my-dir)

#include $(CLEAR_VARS)
#MY_LIB_PATH := lib
#LOCAL_MODULE := bambuser-libswresample
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libswresample.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libavformat
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libavformat.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libavcodec
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libavcodec.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libavdevice
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libavdevice.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libavfilter
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libavfilter.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libavutil
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libavutil.so
#include $(PREBUILT_SHARED_LIBRARY)

#include $(CLEAR_VARS)
#LOCAL_MODULE := bambuser-libswscale
#LOCAL_SRC_FILES := $(MY_LIB_PATH)/libswscale.so
#include $(PREBUILT_SHARED_LIBRARY)


#local_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_CFLAGS := -pthread

LOCAL_MODULE    := video
LOCAL_SRC_FILES := video.c ffmpeg.c ffmpeg/cmdutils.c ffmpeg/ffmpeg_opt.c ffmpeg/ffmpeg_filter.c\

#LOCAL_WHOLE_STATIC_LIBRARIES := \
	
LOCAL_C_INCLUDES := \
	$(LOCAL_PATH)/include $(LOCAL_PATH)/ffmpeg
LOCAL_LDLIBS := -L$(LOCAL_PATH) -L$(LOCAL_PATH)/lib/ 	\
	-lGLESv1_CM -ldl \
	-lavformat -lavcodec -lavdevice -lavfilter -lavutil -lswscale -lswresample\
	-llog -lz -lm

include $(BUILD_SHARED_LIBRARY)
