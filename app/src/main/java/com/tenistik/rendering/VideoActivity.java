package com.tenistik.rendering;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.media.MediaPlayer;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.res.AssetManager;
import android.util.Log;
import android.view.Menu;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import static com.tenistik.rendering.R.id.gl_surface_view;

public class VideoActivity extends Activity{

    public static final String TAG = "VideoActivity";

    public int videoWidth,videoHeight;
    public boolean videoWideScreen=false;

    VideoRender mRenderer = null;
    private GLSurfaceView mGLSurfaceView = null;

    private boolean isContinue = false;
    public int m_videoduration = 0;

    private String videofilePath;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video);

        this.videofilePath = getIntent().getExtras().get("file").toString();

        Log.d(TAG,"onCreate()");
        initPlay();
    }

    public void initPlay()
    {
        Log.d(TAG,"initPlay()");

        Log.d(TAG,"native -> closeVideo");
        NativeCalls.setStopThread();
        NativeCalls.closeVideo();

        Log.d(TAG,"native -> initVideo");
        NativeCalls.initVideo();

        Log.d(TAG,"transferring video asset to sdcard");
        copyVideoToCard();

        Log.d(TAG,"native -> loadVideo");
        int duration = NativeCalls.loadVideo(this.videofilePath);
        if (duration > 0)
            m_videoduration = duration;

        videoWidth = NativeCalls.getVideoWidth();
        videoHeight = NativeCalls.getVideoHeight();
        videoWideScreen = ( videoWidth > videoHeight ) ? true : false;

        Log.d(TAG,"Video Asset : " + videoWidth + "x" + videoHeight);

		/* init renderer */
        InitGLRender();
        mGLSurfaceView = (GLSurfaceView) findViewById(gl_surface_view);
        mGLSurfaceView.setRenderer(mRenderer);

        //setContentView(mGLSurfaceView);

        Log.d(TAG,"Finish Init");

        isContinue = true;
        new Thread(new Runnable() {
            @Override
            public void run() {
                while(isContinue)
                {
					/* proc */
                    refresh();

                    try {
                        Thread.sleep(25);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public void refresh()
    {
        runOnUiThread(new Runnable() {

            @SuppressLint("NewApi") @Override
            public void run() {
                if (mRenderer.mSeekBarThumb != null)
                    mRenderer.mThumb.drawThumb(mRenderer.mSeekBarThumb);
            }
        });
    }

    public void InitGLRender()
    {
        mRenderer = new VideoRender(this);
    }

    private void copyVideoToCard() {

        return;
        /*
        String dstname = "file:/"+"sdcard/" +VideoActivity.videoConvertName;
        Log.e(TAG, String.valueOf(System.currentTimeMillis()));
        NativeCalls.videoConvertToMjpeg(this.videofilePath, dstname);
        Log.e(TAG, String.valueOf(System.currentTimeMillis()));
        */
    }

    @Override
    protected void onDestroy() {
        // TODO Auto-generated method stub
        super.onDestroy();
        Log.d(TAG,"onDestroy()");
        mRenderer.releaseRender();
        isContinue = false;
    }
}