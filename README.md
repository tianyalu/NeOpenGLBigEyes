# 大眼滤镜

[TOC]

## 一、前言

本文基于[`OpenGL` 视频录制处理--变速录制](https://github.com/tianyalu/NeOpenGLScreenRecord)  

本文通过`OpenGL`实现了大眼滤镜效果，效果如下图所示：  

![image](https://github.com/tianyalu/NeOpenGLBigEyes/raw/master/show/show.gif) 

## 二、实现思路

总体分为三步：首先通过`OpenCV`实现人脸检测跟踪，拿到人脸框；然后通过中科院计算所的[`SeetaFace`](https://github.com/seetaface/SeetaFaceEngine) [开源人脸识别引擎](https://zhuanlan.zhihu.com/p/22451474)来实现人脸关键点定位，该引擎主要包含人脸检测、人脸关键点定位和人脸识别三大功能，本文仅使用了其第二项功能；最后通过对拿到的人眼关键点进行局部放大算法运算，最终实现了大眼特效效果。

## 三、实现步骤
### 3.1 人脸检测跟踪
### 3.1.1 集成`OpenCV`
`openCV`集成 步骤可参考：[OpenCV 人脸检测 Android 实现](https://github.com/tianyalu/OpenCVFaceDetectionAndroid)  
### 3.1.2 代码实现人脸检测
`CameraHelper`中`onPreviewFrame()`方法不对原始数据进行选择操作，直接将摄像头原始数据传给`MyGLRender`的`onPreviewFrame()`方法：  
```java
@Override
public void onPreviewFrame(byte[] data, Camera camera) {
  if (mPreviewCallback != null) {
    mPreviewCallback.onPreviewFrame(data, camera);
  }
  camera.addCallbackBuffer(cameraBuffer);
}
```
`MyGLRender`的`onPreviewFrame()`方法直接调用`FaceTrack`的`C++`层人脸检测方法进行人脸检测，在该方法中会首先对图像数据进行旋转、灰度化以及均衡化甚至**人脸关键点定位**等操作：  

```c++
extern "C"
JNIEXPORT jobject JNICALL
Java_com_sty_ne_opengl_bigeyes_face_FaceTrack_native_1detector(JNIEnv *env, jobject thiz,
                                                               jlong self, jbyteArray data_,
                                                               jint camera_id, jint width,
                                                               jint height) {
    if(self == 0) {
        return NULL;
    }
    jbyte* data = env->GetByteArrayElements(data_, 0);
    FaceTrack* faceTrack = reinterpret_cast<FaceTrack *>(self);
    //摄像头数据data转成 OpenCV的 Mat
    Mat src(height + height / 2, width, CV_8UC1, data);

    imwrite("/storage/emulated/0/sty/big_eyes/camera.jpg", src); //摄像头原始图像
    cvtColor(src, src, CV_YUV2RGBA_NV21);
    if(camera_id == 1) { //前置
        rotate(src, src, ROTATE_90_COUNTERCLOCKWISE); //逆时针90度
        flip(src, src, 1); //y轴翻转
    }else { //后摄
        rotate(src, src, ROTATE_90_CLOCKWISE);
    }
    //灰度化
    cvtColor(src, src, COLOR_RGBA2GRAY);

    //均衡化处理
    equalizeHist(src, src);

    vector<Rect2f> rects;
    faceTrack->detector(src, rects);

    env->ReleaseByteArrayElements(data_, data, 0);

    int imgWidth = src.cols;
    int imgHeight = src.rows;
    int ret = rects.size();
    if(ret) {
        jclass clazz = env->FindClass("com/sty/ne/opengl/bigeyes/face/Face");
        jmethodID construct = env->GetMethodID(clazz, "<init>", "(IIII[F)V");
        //int width, int height, int imgWidth, int imgHeight, float[] landmark
        int size = ret * 2;
        jfloatArray floatArray = env->NewFloatArray(size);
        for (int i = 0, j = 0; i < size; ++j) {
            float f[2] = {rects[j].x, rects[j].y};
            env->SetFloatArrayRegion(floatArray, i, 2, f);
            i += 2;
        }
        Rect2f faceRect = rects[0];
        int faceWidth = faceRect.width;
        int faceHeight = faceRect.height;

        jobject face = env->NewObject(clazz, construct, faceWidth, faceHeight, imgWidth, imgHeight,
                floatArray);
        //画人脸矩形
//        rectangle(src, faceRect, Scalar(255, 255, 255));
        rectangle(src, faceRect, Scalar(255, 0, 0));
        for (int i = 1; i < ret; ++i) {
            circle(src, Point2f(rects[i].x, rects[i].y), 5, Scalar(0, 255, 0));
        }

        imwrite("/storage/emulated/0/sty/big_eyes/face.jpg", src); //画了人脸的图像
        return face;
    }
    src.release();
    return NULL;
}
```
### 3.2 人脸关键点定位 
#### 3.2.1 集成`SeetaFace`
`SeetaFace`下载地址：  
* [`SeetaFace GitHub` 地址](https://github.com/seetaface/SeetaFaceEngine)  

* `SeetaFace`库也可以从我的 [网盘](https://pan.baidu.com/s/1iPZm65KwAZBIaF2gDXrqNg) (mssh )获取   

引入`FaceAlignment`库，其`CMakeListsx.txt`文件内容如下：  

```Cma
# Use C++11
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -std=c++11")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -O2")
set(CMAKE_CXX_FLAGS "${CMAKE_CXX_FLAGS} -msse4.1")
include_directories(include)

set(src_files 
    src/cfan.cpp
    src/face_alignment.cpp
    src/sift.cpp
    )

add_library(seeta_fa_lib SHARED ${src_files})
```

#### 3.2.2 代码实现人脸关键点定位

实际在3.1.2的第二部分代码中已经调用了`C++`部分人脸关键点定位方法`faceTrack->detector(src, rects)`方法，该方法实现如下：  

```c++
/**
 * 人脸追踪检测
 * @param src  待检测图像
 * @param rects  检测结果（人脸矩形框+5个特征点矩形）
 */
void FaceTrack::detector(Mat src, vector<Rect2f> &rects) {
    vector<Rect> faces;
    //src: 灰度图
    tracker->process(src);
    tracker->getObjects(faces);
    if(faces.size()) {
        Rect face = faces[0];
        rects.push_back(Rect2f(face.x, face.y, face.width, face.height));

        seeta::ImageData image_data(src.cols, src.rows);
        image_data.data = src.data;

        seeta::FaceInfo faceInfo;
        seeta::Rect bbox;
        bbox.x = face.x;
        bbox.y = face.y;
        bbox.width = face.width;
        bbox.height = face.height;
        faceInfo.bbox = bbox;

        seeta::FacialLandmark points[5];

        faceAlignment->PointDetectLandmarks(image_data, faceInfo, points);

        //统一把5个特征点也转成rect
        for (int i = 0; i < 5; ++i) {
            rects.push_back(Rect2f(points[i].x, points[i].y, 0, 0));
        }
    }
}
```

### 3.3 大眼特效实现

#### 3.3.1 实现原理

大眼特效算法实现依据论文：[Interactive Image Warping](https://github.com/tianyalu/NeOpenGLBigEyes/tree/master/resources/warping-thesis.pdf)  

特效算法公式：  

![image](https://github.com/tianyalu/NeOpenGLBigEyes/raw/master/show/big_eyes_scale_formula.png)  

特效实现示意图：  

![image](https://github.com/tianyalu/NeOpenGLBigEyes/raw/master/show/big_eyes_scale_theory.png)  

#### 3.3.2 代码实现大眼特效

`bigeye_fragment.glsl`文件：  

```glsl
//大眼特效的算法处理（针对纹理数据进行处理：局部放大算法）

//中等精度
precision mediump float;
//从顶点着色器传过来的
varying vec2 aCoord;
//采样器
uniform sampler2D vTexture;

//左眼
uniform vec2 left_eye;
//右眼
uniform vec2 right_eye;

//参考：resources/warping-thesis.pdf  page47 4.4.2的公式  或 show/big_eyes_scale_formula.png
//rmax: 局部放大最大作用半径
//return: fsr--> 放大后的半径
float fs(float r, float rmax) {
    float a = 0.4; //放大系数

//    return (1.0 - pow(r / rmax - 1.0, 2.0) * a) * r;
    return (1.0 - pow(r / rmax - 1.0, 2.0) * a);
}

//参考：show/big_eyes.scale_theory.png
//oldCoord: 旧的采样点坐标
//eye: 眼睛坐标
//rmax: 局部放大最大作用半径
//在部分机型上，变量名和函数名同名的话会导致编译报错！
vec2 calcNewCoord(vec2 oldCoord, vec2 eye, float rmax) {
    vec2 newCoord = oldCoord;
    float r = distance(oldCoord, eye);
    float fsr = fs(r, rmax);

    if(r > 0.0f && r < rmax) {
        //(新点 - 眼睛) / (老点 - 眼睛) = 新距离 / 老距离
        //(newCoord - eye) / (oldCoord - eye) = fsr / r
//        newCoord = (fsr / r) * (oldCoord - eye) + eye;  //vec可以直接相减求距离

        newCoord = fsr * (oldCoord - eye) + eye;  //化简写法//vec可以直接相减求距离
    }
    return newCoord;
}

void main() {
    //两眼距离的一半
    float rmax = distance(left_eye, right_eye) / 2.0;
    vec2 newCoord = calcNewCoord(aCoord, left_eye, rmax); //左眼放大位置的采样点
    newCoord = calcNewCoord(newCoord, right_eye, rmax); //右眼放大位置的采样点

    gl_FragColor = texture2D(vTexture, newCoord);
}
```

`MyGLRender`的`onDrawFrame()`方法中实现叠加大眼滤镜，并绘制：  

```java
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
  if(null != mBigEyeFilter){
    mBigEyeFilter.setFace(mFaceTrack.getFace());
    textureId = mBigEyeFilter.onDrawFrame(textureId);
  }
  mScreenFilter.onDrawFrame(textureId); //渲染到屏幕 textureId : cTextureId

  //渲染录制
  mMediaRecorder.encodeFrame(textureId, mSurfaceTexture.getTimestamp());
}
```


## 四、参考资料
[人脸68关键特征点分布]( https://blog.csdn.net/zj360202/article/details/78674700?utm_source=blogxgwz2)  
[`SeetaFace`开源人脸识别引擎介绍](https://zhuanlan.zhihu.com/p/22451474)  


## 五、遗留的疑问
~~`BaseFilter`类中`TEXTURE`改成原始未旋转状态，大眼滤镜效果才反而是正常的，百思不得其解。~~

其实是在`BaseFilter`中保留原始图像样式，需要旋转的话在子类中复写`changeTextureData()`方法实现。