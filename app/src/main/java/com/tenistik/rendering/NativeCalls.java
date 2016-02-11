package com.tenistik.rendering;


public class NativeCalls {
    //ffmpeg
    public static native void initVideo(); //
    public static native int  loadVideo(String fileName); //
    public static native void prepareStorageFrame(); //
    public static native int  getFrame(); //
    public static native void closeVideo();//
    public static native void setSeek(int usec);//
    //opengl
    public static native void initOpenGL(); //
    public static native void drawFrame(); //
    public static native void closeOpenGL(); //
    //getters
    public static native int getVideoHeight();
    public static native int getVideoWidth();
    //setters
    public static native void setScreenPadding(int w,int h); //
    public static native void setDrawDimensions(int drawWidth, int drawHeight); //
    public static native void setScreenDimensions(int w, int h); //
    public static native void setTextureDimensions(int tx, int ty );

    public static native void setSViewPort(int w, int h); //
    public static native void setStopThread(); //
    public static native void videoConvertToMjpeg(String srcfileName, String dstfileName);

    static {
    	/*System.loadLibrary("avutil");
    	System.loadLibrary("swresample");
    	System.loadLibrary("avcodec");
    	System.loadLibrary("avformat");
    	System.loadLibrary("swscale");
    	System.loadLibrary("avfilter");
    	System.loadLibrary("avdevice");*/
        System.loadLibrary("video");
    }

}