package com.tenistik.rendering;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.annotation.SuppressLint;
import android.media.MediaPlayer;
import android.opengl.GLSurfaceView;
import android.os.SystemClock;
import android.util.Log;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.SeekBar;
import android.widget.ToggleButton;

import com.tenistik.rendering.util.CustomThumbSeekBar;

import java.io.IOException;

public class VideoRender implements GLSurfaceView.Renderer, SeekBar.OnSeekBarChangeListener, OnCheckedChangeListener{

    public static final String TAG = "VideoRender";
    //lock
    static public Object lock = new Object();
    //screen variables
    int screenWidth=50,screenHeight=50;
    int drawWidth, drawHeight; //dimensions of fit-to-screen video
    int paddingX, paddingY; //padding for fit-to-screen-video
    //texture variables
    int powWidth,powHeight;
    public float fpsRate;

    private SeekBar mSeekBar = null;
    public SeekBar mSeekBarThumb = null;
    static boolean runOnce = false;
    static public int mSeekPos;

    public int mSeekMaxValue;
    private ToggleButton mPlayStopBtn = null;

    private boolean mSeekTouch = false;
    private boolean bSeekFrame = false;

    private boolean bPlayButton = true;
    long mDrawPrevTime = 0;
    public CustomThumbSeekBar mThumb = null;

    final long MAXDRAWDURATION = 100;

    private String audiofileName = "/sdcard/audio.mp3";
    private MediaPlayer mPlayer;
    private int mAudioStartPos = 0;

    //pointers
    VideoActivity mParent;

    public float getFPS() {
        return fpsRate;
    }

    public VideoRender() {
        super();
        Log.d(TAG,"VideoRender()");
        runOnce = false;
    }

    public VideoRender(VideoActivity p) {
        super();
        mParent = p;
        runOnce = false;
        Log.d(TAG,"constructor()");

        fpsRate = 0;
        mSeekPos = 0;
        mPreSeekPos = 0;
    }

    int mPreSeekMaxValue = 0;
    int mPreSeekPos = 0;
    @SuppressLint({ "ShowToast", "NewApi" }) @Override
    public void onDrawFrame(GL10 arg0) {
        // TODO Auto-generated method stub

        if (mPreSeekMaxValue != mParent.m_videoduration) {
            mSeekBar.setMax(mParent.m_videoduration);
            mSeekBarThumb.setMax(mParent.m_videoduration);
            mSeekMaxValue = mParent.m_videoduration;
            mPreSeekMaxValue = mSeekMaxValue;
        }

        synchronized(lock) {
            if (!runOnce)
                return;

            if (bSeekFrame == true) {
                int pos = NativeCalls.getFrame(); // from video
                if (pos >= 0)
                    mSeekPos = pos;
                else
                    mSeekPos = mSeekMaxValue;
                if (bSeekFrame == true)
                    bSeekFrame = false;
            }
            else if (mSeekTouch != true && bPlayButton == true)
            {
                int pos = NativeCalls.getFrame(); // from video
                if (pos >= 0)
                    mSeekPos = pos;
                else {
                    mSeekPos = mSeekMaxValue;
                }
                if (bSeekFrame == true)
                    bSeekFrame = false;

                if (mPreSeekPos > 0)
                {
                    long seekdiff = mSeekPos - mPreSeekPos;
                    if (seekdiff > 100)
                        seekdiff = seekdiff/2;
                    if (seekdiff > 0) {
                        long drawDuration = SystemClock.uptimeMillis() - mDrawPrevTime;
                        long delaytime = seekdiff - drawDuration;
                        if (seekdiff > 0 && seekdiff > drawDuration && delaytime > 0 && delaytime < MAXDRAWDURATION) {
                            try {
                                Thread.sleep(delaytime);
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                    }
                }
            }

            NativeCalls.drawFrame(); // using openGL
            mDrawPrevTime = SystemClock.uptimeMillis();
        }

        if (mPreSeekPos != mSeekPos) {
            if (!mSeekTouch && (mSeekPos > mPreSeekPos)) {
                mSeekBar.setProgress(mSeekPos);
                mSeekBarThumb.setProgress(mSeekPos);
            }
        }
        mPreSeekPos = mSeekPos;
    }

    @SuppressLint("NewApi") public void setSeek(int pos)
    {
        mSeekBar.setProgress(mSeekPos);
        mSeekBarThumb.setProgress(mSeekPos);
        mPlayer.seekTo(mAudioStartPos + pos);

        synchronized(lock) {
            Log.d(TAG,"Render API setSeek : " + String.valueOf(pos));
            NativeCalls.setSeek(pos);
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onSurfaceChanged() " + width + "x" + height);

        synchronized(lock)
        {
            process(width, height);
        }
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // TODO Auto-generated method stub
        Log.d(TAG, "onSurfaceCreated()");
        mSeekBar = (SeekBar)mParent.findViewById(R.id.seekBarFrame);
        mSeekBarThumb = (SeekBar)mParent.findViewById(R.id.seekBarThumb);
        mSeekBar.setOnSeekBarChangeListener(this);
        mSeekBarThumb.setOnSeekBarChangeListener(this);

        mThumb = new CustomThumbSeekBar(mParent);

        mPlayStopBtn = (ToggleButton) mParent.findViewById(R.id.playpausebtn);
        mPlayStopBtn.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked == true) {
                    bPlayButton = true;
                    mPlayer.start();
                }
                else {
                    bPlayButton = false;
                    mPlayer.pause();
                }
            }
        });

        mPlayer = new MediaPlayer();
        mPlayer.reset();
        try {
            mPlayer.setDataSource(audiofileName);
            mPlayer.prepare();
        } catch (IOException e) {
            e.printStackTrace();
        }
        if (mParent.m_videoduration < mPlayer.getDuration())
            mAudioStartPos = mPlayer.getDuration() - mParent.m_videoduration;
        else
            mAudioStartPos = 0;
        Log.d(TAG,"onSurfaceCreated video duration : " + mParent.m_videoduration + " start Pos : " + mAudioStartPos);
        mPlayer.seekTo(mAudioStartPos);
        mPlayer.start();
    }

    void process(int width, int height) {
        setScreenDimensions( width, height );
        Log.d(TAG,"Killing texture");
        NativeCalls.closeOpenGL();
        setFitToScreenDimensions( mParent.videoWidth, mParent.videoHeight );
        setTextureDimensions( screenWidth, screenHeight );
        if ( !runOnce ) {
            Log.d(TAG,"Preparing frame");
            NativeCalls.prepareStorageFrame();
        }
        NativeCalls.initOpenGL();
        runOnce = true;
    }

    public void setTextureDimensions(int w, int h) {
        int s = Math.max( w, h );
        powWidth = getNextHighestPO2( s ) / 2;
        powHeight = getNextHighestPO2( s ) / 2;
        NativeCalls.setTextureDimensions( powWidth,
                powHeight );
        Log.d(TAG,"New texture dimensions:"+powWidth+"x"+powHeight);
    }

    public void setScreenDimensions(int w, int h) {
        screenWidth = w;
        screenHeight = h;
        NativeCalls.setScreenDimensions( screenWidth,
                screenHeight );
        Log.d(TAG,"New screen dimensions:"+screenWidth+"x"+screenHeight);
    }

    public void releaseRender() {
        Log.d(TAG, "releaseRender()");
        synchronized(lock){
            Log.d(TAG, "setStopThread() start");
            NativeCalls.setStopThread();
            try {
                Thread.sleep(150);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            Log.d(TAG, "setStopThread() end");
            NativeCalls.closeVideo();
            NativeCalls.closeOpenGL();
            runOnce = false;
            mPlayer.stop();
        }
    }

    public static int[] scaleBasedOnScreen(int iw, int ih, int sw, int sh) {
        int[] newdims = new int[2];
        //if ( videoWideScreen ) { int t=sw; sw=sh; sh=t; }
        float sf = (float) iw / (float) ih;
        newdims[0] = sw;
        newdims[1] = (int) ((float) sw / sf);
        if ( newdims[1] > sh ) { // new dims too big
            newdims[0] = (int) ((float) sh * sf);
            newdims[1] = sh;
        }
        return newdims;
    }

    public static int getNextHighestPO2( int n ) {
        n -= 1;
        n = n | (n >> 1);
        n = n | (n >> 2);
        n = n | (n >> 4);
        n = n | (n >> 8);
        n = n | (n >> 16);
        n = n | (n >> 32);
        return n + 1;
    }

    public void setFitToScreenDimensions(int w, int h) {
        int[] newdims = scaleBasedOnScreen(w, h,screenWidth, screenHeight);
        drawWidth = newdims[0];
        drawHeight = newdims[1];
        NativeCalls.setDrawDimensions(drawWidth,drawHeight);
        Log.d(TAG,"setupVideoParameters: fit-to-screen video:"+drawWidth+"x"+drawHeight);
        //set video padding
        paddingX = (int) ((float) (screenWidth - drawWidth) / 2.0f);
        paddingY = (int) ((float) (screenHeight - drawHeight) / 2.0f);
        NativeCalls.setScreenPadding(paddingX,paddingY);
    }

    @Override
    public void onCheckedChanged(CompoundButton arg0, boolean arg1) {
        // TODO Auto-generated method stub

    }

    @Override
    public void onProgressChanged(SeekBar seekBar, int progress,
                                  boolean fromUser) {
        // TODO Auto-generated method stub
        if ( fromUser && (seekBar == mSeekBar || seekBar == mSeekBarThumb))
        {
            bSeekFrame = true;
        }

        if ( fromUser && seekBar == mSeekBarThumb)
        {
            mThumb.drawThumb(seekBar);
        }

        mSeekPos = progress;
        if (mSeekPos != mPreSeekPos) {
            mPreSeekPos = mSeekPos;
            setSeek(mSeekPos);
        }
    }

    @Override
    public void onStartTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
        mSeekTouch = true;
        if (bPlayButton == true)
            mPlayer.pause();
    }

    @Override
    public void onStopTrackingTouch(SeekBar seekBar) {
        // TODO Auto-generated method stub
        mSeekTouch = false;
        if (bPlayButton == true)
            mPlayer.start();
    }
}