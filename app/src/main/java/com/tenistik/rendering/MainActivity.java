package com.tenistik.rendering;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Intent;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.CamcorderProfile;
import android.media.MediaRecorder;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import com.tenistik.rendering.util.CameraTexturePreview;
import com.tenistik.rendering.util.CameraWrapper;

import java.io.File;
import java.io.IOException;
import java.util.List;

public class MainActivity extends Activity implements CameraWrapper.CamOpenOverCallback {

    // Log tag
    private static final String TAG = MainActivity.class.getName();
    private String fileName = "/sdcard/capture.mp4";
    private String username = "TESTUSER";
    private boolean isRecording = false;
    private Button captureButton;
    private Button outButton;

    private CameraTexturePreview mCameraTexturePreview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        captureButton = (Button) findViewById(R.id.button_capture);
        outButton = (Button) findViewById(R.id.button_out);
        mCameraTexturePreview = (CameraTexturePreview) findViewById(R.id.surface_view);

        captureButton.bringToFront();
        outButton.bringToFront();
        outButton.setText("Preview");
    }

    public void onCaptureClick(View view) {
        /*
        if (isRecording) {
            isRecording = false;
            CameraWrapper.getInstance().doStopCamera();
            setCaptureButtonText("Capture");
        } else {
            isRecording = true;
            Thread openThread = new Thread() {
                @Override
                public void run() {
                    CameraWrapper.getInstance().doOpenCamera(MainActivity.this);
                }
            };
            openThread.start();
            setCaptureButtonText("Stop");
        }
        */

        if (isRecording){
            isRecording = false;
            CameraWrapper.getInstance().CameraRecordingStop();
            outButton.setText("Play");
            setCaptureButtonText("Capture");
        } else {
            isRecording = true;
            CameraWrapper.getInstance().CameraRecordingStart();
            outButton.setText("Preview");
            setCaptureButtonText("Stop");
        }

    }

    public void onOutClick(View view) {
        if (isRecording)
            return;

        Intent intent = new Intent(MainActivity.this, VideoActivity.class);
        intent.putExtra("file",fileName);
        intent.putExtra("userName",username);
        intent.putExtra("action", "record");

        startActivity(intent);
    }

    private void setCaptureButtonText(String title) {
        captureButton.setText(title);
    }

    @Override
    protected void onPause() {
        super.onPause();
    }

    @Override
    public void cameraHasOpened() {
        SurfaceTexture surface = this.mCameraTexturePreview.getSurfaceTexture();
        CameraWrapper.getInstance().doStartPreview(surface);
    }

    @Override
    protected void onStart() {
        Log.i(TAG, "onStart");
        super.onStart();

        Thread openThread = new Thread() {
            @Override
            public void run() {
                CameraWrapper.getInstance().doOpenCamera(MainActivity.this);
            }
        };
        openThread.start();
    }
}
