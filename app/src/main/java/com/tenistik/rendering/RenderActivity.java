package com.tenistik.rendering;

import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;

public class RenderActivity extends Activity implements SurfaceHolder.Callback{

    // Log tag
    private static final String TAG = RenderActivity.class.getName();

    static{
        System.loadLibrary("avutil-54");
        System.loadLibrary("avcodec-56");
        System.loadLibrary("avformat-56");
        System.loadLibrary("swscale-3");
        System.loadLibrary("swresample-1");
        System.loadLibrary("videoController");
    }

    private String filePath;
    private int width;
    private int height;
    private SurfaceView surface;
    private SurfaceHolder holder;
    static boolean bInit = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_render);

        this.filePath = getIntent().getExtras().get("file").toString();

        this.surface = (SurfaceView) findViewById(R.id.render_view);
        Button start = (Button) findViewById(R.id.button_play);
        Button stop = (Button) findViewById(R.id.button_stop);
        start.bringToFront();
        stop.bringToFront();

        holder = surface.getHolder();
        holder.addCallback(this);

        //naFinish();
        naInit(filePath);
        int[] size = naGetVideoRes();
        this.width = size[0];
        this.height = size[1];
    }

    @Override
    public void surfaceCreated(SurfaceHolder holder) {
        Log.e(TAG,"Create surface");
    }

    @Override
    public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
        naSetSurface(this.holder.getSurface());
        //Bitmap bitmap = Bitmap.createBitmap(height, width, Bitmap.Config.ARGB_8888);
        //naSetup(bitmap, height, width);
        Bitmap bitmap = Bitmap.createBitmap(width,height, Bitmap.Config.ARGB_8888);
        naSetup(bitmap, width, height);
    }

    @Override
    public void surfaceDestroyed(SurfaceHolder holder) {
        Log.e(TAG, "Destroyed surface");
    }

    public void onPlay(View view) {
        naPlay();
    }

    public void onStop(View view) {
        naStop();
    }

    private static native int naInit(String fileName);
    private static native void naSetSurface(Surface surface);
    private static native int[] naGetVideoRes();
    private static native int naSetup(Bitmap bitmap, int width, int height);
    private static native void naPlay();
    private static native void naStop();
    private static native int countFrames();
    private static native void renderFrame(int position);
    private static native void naFinish();

}
