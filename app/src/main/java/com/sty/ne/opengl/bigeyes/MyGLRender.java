package com.sty.ne.opengl.bigeyes;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLContext;
import android.opengl.GLSurfaceView;
import android.os.Environment;
import android.util.Log;


import com.sty.ne.opengl.bigeyes.filter.CameraFilter;
import com.sty.ne.opengl.bigeyes.filter.ScreenFilter;
import com.sty.ne.opengl.bigeyes.record.MyMediaRecorder;
import com.sty.ne.opengl.bigeyes.util.CameraHelper;

import java.io.File;
import java.io.IOException;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import static android.opengl.GLES20.GL_COLOR_BUFFER_BIT;
import static android.opengl.GLES20.glClear;
import static android.opengl.GLES20.glClearColor;
import static android.opengl.GLES20.glGenTextures;

class MyGLRender implements GLSurfaceView.Renderer, SurfaceTexture.OnFrameAvailableListener {
    private CameraHelper mCameraHelper;
    private MyGLSurfaceView myGLSurfaceView;
    private int[] mTextureID;
    private SurfaceTexture mSurfaceTexture;
    private CameraFilter mCameraFilter;
    private ScreenFilter mScreenFilter;
    private float[] mtx = new float[16];
    private MyMediaRecorder mMediaRecorder;

    public MyGLRender(MyGLSurfaceView myGLSurfaceView) {
        this.myGLSurfaceView = myGLSurfaceView;
    }

    /**
     * Surface创建时回调
     * @param gl10 1.0 api预留参数
     * @param eglConfig
     */
    @Override
    public void onSurfaceCreated(GL10 gl10, EGLConfig eglConfig) {
        mCameraHelper = new CameraHelper((Activity) myGLSurfaceView.getContext());
        //准备画布
        mTextureID = new int[1];
        //第三个参数表示你要使用mTextureID数组中那个ID的索引
        glGenTextures(mTextureID.length, mTextureID, 0);

        mSurfaceTexture = new SurfaceTexture(mTextureID[0]);
        mSurfaceTexture.setOnFrameAvailableListener(this);

        mCameraFilter = new CameraFilter(myGLSurfaceView.getContext());
        mScreenFilter = new ScreenFilter(myGLSurfaceView.getContext());

        EGLContext eglContext = EGL14.eglGetCurrentContext();
        String filePath = Environment.getExternalStorageDirectory().getAbsolutePath() +
                "/sty/screen_record";
        File dirPath = new File(filePath);
        if(!dirPath.exists()) {
            dirPath.mkdirs();
        }
        String fileName = filePath + "/record_" + System.currentTimeMillis() + ".mp4";
        Log.d("sty", fileName);
//        mMediaRecorder = new MyMediaRecorder(800, 480, fileName, eglContext,
        mMediaRecorder = new MyMediaRecorder(480, 800, fileName, eglContext,
                myGLSurfaceView.getContext());
    }

    /**
     * Surface 发生改变时回调
     * @param gl10 1.0 api预留参数
     * @param width
     * @param height
     */
    @Override
    public void onSurfaceChanged(GL10 gl10, int width, int height) {
        mCameraHelper.startPreview(mSurfaceTexture);
        mCameraFilter.onReady(width, height);
        mScreenFilter.onReady(width, height);
    }

    /**
     * 绘制一帧图像时回调
     * 注意：该方法中必须进行绘制操作
     * （返回后，交换渲染缓冲区，如果不绘制，会导致闪屏）
     * @param gl10 1.0 api预留参数
     */
    @Override
    public void onDrawFrame(GL10 gl10) {
        glClearColor(255, 0, 0, 0); //设置清屏颜色
        glClear(GL_COLOR_BUFFER_BIT); //颜色缓冲区

        //绘制相机图像数据
        mSurfaceTexture.updateTexImage();

        mSurfaceTexture.getTransformMatrix(mtx);
        mCameraFilter.setMatrix(mtx);
        //mTextureID[0]: 摄像头的纹理
        int textureId = mCameraFilter.onDrawFrame(mTextureID[0]);//渲染到FBO
        //textureId: FBO的纹理
        //...加滤镜
        //int aTextureId = aaaFilter.onDrawFrame(textureId);//渲染到FBO
        //int bTextureId = bbbFilter.onDrawFrame(aTextureId);//渲染到FBO
        //int cTextureId = cccFilter.onDrawFrame(bTextureId);//渲染到FBO
        //...
        mScreenFilter.onDrawFrame(textureId); //渲染到屏幕 textureId : cTextureId

        //渲染录制
        mMediaRecorder.encodeFrame(textureId, mSurfaceTexture.getTimestamp());
    }

    /**
     * 画布有有效数据时回调
     * @param surfaceTexture
     */
    @Override
    public void onFrameAvailable(SurfaceTexture surfaceTexture) {
        myGLSurfaceView.requestRender();
    }

    public void surfaceDestroyed() {
        mCameraHelper.stopPreview();
    }

    /**
     * 开始录制
     * @param speed
     */
    public void startRecording(float speed) {
        Log.d("sty", "startRecording");
        try {
            mMediaRecorder.start(speed);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 停止录制
     */
    public void stopRecording() {
        Log.d("sty", "stopRecording");
        mMediaRecorder.stop();
    }
}
