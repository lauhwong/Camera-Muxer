[Camera-Muxer](https://github.com/lauhwong/Camera-Muxer/blob/master/demo/CameraMuxer.apk?raw=true)
==============
一个支持Android拍照和视频录制的camera库，Maven-SnapShot依赖.
```groovy
allprojects {
    repositories {
        google()
        jcenter()
        maven{
            url 'https://oss.sonatype.org/content/repositories/snapshots/'
        }
    }
}
```

CameraView：通用的CameraView，支持帧预览，拍照。
------------------------------------

1.添加Module依赖.
```groovy
configurations.all {
    resolutionStrategy.cacheChangingModulesFor 0, 'seconds'
}
dependencies {
    implementation 'com.github.lauhwong:cameraview-muxer:0.8-SNAPSHOT'
}
```
2.开启或者关闭CameraView预览.
```kotlin
   override fun onResume() {
       super.onResume()
       cameraView.start()
   }
    override fun onPause() {
         super.onPause()
         cameraView.stop()
    }
```
3.设置预览frame或者picture的大小,可以自己扩展ChooseSizeStrategy.
```kotlin
cameraView.setCameraSizeStrategy(CameraFunctions.STRATEGY_RECORD_PREVIEW_SIZE,
                ChooseSizeStrategy.NearestStrategy(1920, 1080))
cameraView.setCameraSizeStrategy(CameraFunctions.STRATEGY_PICTURE_SIZE,
               ChooseSizeStrategy.LargestSizeStrategy())
```
4.给recordingFrame或者capturePicture添加回调，非UI线程.扩展CameraView.Callback
```kotlin
cameraView.addCallback(object:CameraView.Callback{})
```
5.设置相机自动对焦，闪光灯，切换摄像头参数
```kotlin
cameraView.set*(*:*)
```

CameraView-Muxer：CameraView的回调实现,支持拍照和Mp4录制(Mp4采用MediaCodec编码)Handler
---------------------------------------------------------------------
1.添加拍照回调
```kotlin
cameraView.addCallback(CapturePictureHandler(path))
```
2.添加MP4录制回调
```kotlin
cameraView.addCallback(object:Mp4MuxerHandler(){
    override fun createMp4Muxer(): Mp4Muxer {
        return newMp4Muxer()
     }
    private fun newMp4Muxer(): Mp4Muxer {
        val path = File(Environment.getExternalStorageDirectory(), "me.mp4").absolutePath
        val mp4Param = Mp4Muxer.Params().apply {
             this.path = path
        }
        val audioParam = AudioDevice.Params()
        val audioDevice = AudioDevice.create(audioParam)
        return Mp4Muxer(ctx, mp4Param, audioDevice)
    }
    override fun onStartRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        super.onStartRecordingFrame(cameraView, timeStampInNs)
     }
    override fun onStopRecordingFrame(cameraView: CameraView, timeStampInNs: Long) {
        super.onStopRecordingFrame(cameraView, timeStampInNs)
    }
})
```
3.设置mp4编码的帧数据和声音采样录制数据
```kotlin
    val mp4Param = Mp4Muxer.Params().apply {
         this.path = path
         this.width=544
         this.height=960
         this.fps=30
         this.videoBitrate=1300000
         ***
    }
     val audioParam = AudioDevice.Params().apply{
          this.audioSampleRate=44100
     }
```
更多细节，请查看demo！