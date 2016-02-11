package com.tenistik.rendering.util;

import java.io.IOException;
import java.util.List;
import android.annotation.SuppressLint;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.Camera;
import android.media.MediaRecorder;
import android.util.Log;
import android.view.SurfaceHolder;
import android.widget.Toast;

@SuppressLint("NewApi")
public class CameraWrapper {
	private static final String TAG = "CameraWrapper";
	private Camera mCamera;
	private Camera.Parameters mCameraParamters;
	private static CameraWrapper mCameraWrapper;
	private boolean mIsPreviewing = false;
	private float mPreviewRate = -1.0f;
	public static int IMAGE_HEIGHT = 480;
	public static int IMAGE_WIDTH = 720;
	private CameraPreviewCallback mCameraPreviewCallback;
	private byte[] mImageCallbackBuffer = null;

	private MediaRecorder mMediaAudioRecorder = null;
	private String audiofileName = "/sdcard/audio.mp3";

	public interface CamOpenOverCallback {
		public void cameraHasOpened();
	}

	private CameraWrapper() {
	}

	public static synchronized CameraWrapper getInstance() {
		if (mCameraWrapper == null) {
			mCameraWrapper = new CameraWrapper();
		}
		return mCameraWrapper;
	}

	public void doOpenCamera(CamOpenOverCallback callback) {
		Log.i(TAG, "Camera open....");
		int numCameras = Camera.getNumberOfCameras();
		Camera.CameraInfo info = new Camera.CameraInfo();
		for (int i = 0; i < numCameras; i++) {
			Camera.getCameraInfo(i, info);
			if (info.facing == Camera.CameraInfo.CAMERA_FACING_BACK) {
				mCamera = Camera.open(i);
				break;
			}
		}
		if (mCamera == null) {
			Log.d(TAG, "No front-facing camera found; opening default");
			mCamera = Camera.open();    // opens first back-facing camera
		}
		if (mCamera == null) {
			throw new RuntimeException("Unable to open camera");
		}
		Log.i(TAG, "Camera open over....");
		callback.cameraHasOpened();
	}

	public void doStartPreview(SurfaceHolder holder, float previewRate) {
		Log.i(TAG, "doStartPreview...");
		if (mIsPreviewing) {
			this.mCamera.stopPreview();
			return;
		}

		try {
			this.mCamera.setPreviewDisplay(holder);
		} catch (IOException e) {
			e.printStackTrace();
		}
		initCamera();
	}

	public void doStartPreview(SurfaceTexture surface) {
		Log.i(TAG, "doStartPreview()");
		if (mIsPreviewing) {
			this.mCamera.stopPreview();
			return;
		}

		try {
			this.mCamera.setPreviewTexture(surface);
		} catch (IOException e) {
			e.printStackTrace();
		}
		initCamera();
	}

	public void doStopCamera() {
		Log.i(TAG, "doStopCamera");
        mCameraPreviewCallback.close();

		if (this.mCamera != null) {
			this.mCamera.setPreviewCallback(null);
			this.mCamera.stopPreview();
			this.mIsPreviewing = false;
			this.mPreviewRate = -1f;
			this.mCamera.release();
			this.mCamera = null;

            /* audio record stop */
			if (mMediaAudioRecorder != null)
			{
				Log.i(TAG, "release AudioRecorder");
				mMediaAudioRecorder.stop();
				mMediaAudioRecorder.reset();
				mMediaAudioRecorder.release();
				mMediaAudioRecorder = null;
			}
		}
	}

	private void initCamera() {
		if (this.mCamera != null) {
			this.mCameraParamters = this.mCamera.getParameters();
			this.mCameraParamters.setPreviewFormat(ImageFormat.NV21);
			//this.mCameraParamters.setFlashMode("off");
			this.mCameraParamters.set("cam_mode", 1);
			this.mCameraParamters.setWhiteBalance(Camera.Parameters.WHITE_BALANCE_AUTO);
			this.mCameraParamters.setSceneMode(Camera.Parameters.SCENE_MODE_AUTO);

			Camera.Size resolution = this.mCameraParamters.getSupportedPreviewSizes().get(0);
			CameraWrapper.IMAGE_WIDTH = resolution.width;
			CameraWrapper.IMAGE_HEIGHT = resolution.height;
			this.mImageCallbackBuffer = new byte[CameraWrapper.IMAGE_WIDTH * CameraWrapper.IMAGE_HEIGHT * 3 / 2];

			Log.i("Resolution: ", String.valueOf(CameraWrapper.IMAGE_WIDTH));
			Log.i("Resolution: ", String.valueOf(CameraWrapper.IMAGE_HEIGHT));

			//this.mCameraParamters.setPreviewSize(IMAGE_WIDTH, IMAGE_HEIGHT);
			this.mCameraParamters.setPreviewSize(CameraWrapper.IMAGE_WIDTH, CameraWrapper.IMAGE_HEIGHT);
			//this.mCameraParamters.setPreviewFpsRange(VideoEncoderFromBuffer.FRAME_RATE, VideoEncoderFromBuffer.FRAME_RATE);
			this.mCameraParamters.setPreviewFrameRate(this.mCameraParamters.getSupportedPreviewFrameRates().get(0));
			Log.i("FPS: ", String.valueOf(this.mCameraParamters.getSupportedPreviewFrameRates().get(0)));

			this.mCamera.setDisplayOrientation(90);
			mCameraPreviewCallback = new CameraPreviewCallback();
			mCamera.setPreviewCallbackWithBuffer(mCameraPreviewCallback);

			this.mCameraParamters
					.setFocusMode(Camera.Parameters.FOCUS_MODE_FIXED);
			List<String> focusModes = this.mCameraParamters.getSupportedFocusModes();
			if (focusModes.contains("continuous-video")) {
				this.mCameraParamters
						.setFocusMode(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO);
			}
			this.mCamera.setParameters(this.mCameraParamters);
			this.mCamera.startPreview();

			this.mIsPreviewing = true;
		}
	}

    public void CameraRecordingStart()
    {
        mCamera.addCallbackBuffer(mImageCallbackBuffer);
        mCameraPreviewCallback.start();

        /* audio record start */
        mMediaAudioRecorder = new MediaRecorder();
        mMediaAudioRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        mMediaAudioRecorder.setOutputFormat(MediaRecorder.OutputFormat.DEFAULT);
        mMediaAudioRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.DEFAULT);
        mMediaAudioRecorder.setOutputFile(audiofileName);
        try {
            mMediaAudioRecorder.prepare();
        } catch (IllegalStateException e) {
            e.printStackTrace();
            doStopCamera();
            return;
        } catch (IOException e) {
            e.printStackTrace();
            doStopCamera();
            return;
        }
        mMediaAudioRecorder.start();
    }

    public void CameraRecordingStop()
    {
        mCameraPreviewCallback.close();
        /* audio record stop */
        if (mMediaAudioRecorder != null)
        {
            Log.i(TAG, "release AudioRecorder");
            mMediaAudioRecorder.stop();
            mMediaAudioRecorder.reset();
            mMediaAudioRecorder.release();
            mMediaAudioRecorder = null;
        }
    }

	long startTime;
	long endTime;

	class CameraPreviewCallback implements Camera.PreviewCallback {
		private static final String TAG = "CameraPreviewCallback";
		private VideoEncoderFromBuffer videoEncoder = null;
        private boolean bRecording = false;

		private CameraPreviewCallback() {

		}

        void start(){
            videoEncoder = new VideoEncoderFromBuffer(CameraWrapper.IMAGE_WIDTH, CameraWrapper.IMAGE_HEIGHT);
            startTime = System.currentTimeMillis();
            bRecording = true;
        }

		void close() {
            if (bRecording) {
                bRecording = false;
                videoEncoder.close();
                endTime = System.currentTimeMillis();
                Log.i(TAG, "onPreviewFrame : " + Integer.toString((int) (endTime - startTime)) + "ms");
            }
		}

		@Override
		public void onPreviewFrame(byte[] data, Camera camera) {
            if (bRecording != true)
                return;

			videoEncoder.encodeFrame(data/*, encodeData*/);
            if (bRecording)
			    camera.addCallbackBuffer(data);
		}
	}
}
