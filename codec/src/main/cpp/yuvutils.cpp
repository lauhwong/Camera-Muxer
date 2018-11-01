#include <jni.h>
#include <libyuv/video_common.h>
#include <libyuv/convert.h>
#include "include/com_miracles_codec_camera_LibYuvUtils.h"
#include <libyuv/rotate.h>
#include <libyuv/rotate_argb.h>
#include <libyuv/scale.h>

libyuv::RotationMode ToRotationMode(jint rotation) {
    libyuv::RotationMode rm = libyuv::kRotate0;
    if (rotation == 90) {
        rm = libyuv::kRotate90;
    } else if (rotation == 180) {
        rm = libyuv::kRotate180;
    } else if (rotation == 270) {
        rm = libyuv::kRotate270;
    }
    return rm;
}

libyuv::FilterMode ToFilterMode(jint jFilter) {
    libyuv::FilterMode fm = libyuv::kFilterNone;
    if (jFilter == 1) {
        fm = libyuv::kFilterLinear;
    } else if (jFilter == 2) {
        fm = libyuv::kFilterBilinear;
    } else if (jFilter == 3) {
        fm = libyuv::kFilterBox;
    }
    return fm;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_convertToI420
        (JNIEnv *env, jclass jClz, jbyteArray jSamples,
         jint jSampleSize, jbyteArray jDstY, jint jDstYStride,
         jbyteArray jDstU, jint jDstUStride, jbyteArray jDstV, jint jDstVStride, jint jCropX,
         jint jCropY,
         jint jSrcWidth, jint jSrcHeight, jint jCropWidth, jint jCropHeight, jint jRotation,
         jint jFormat) {

    jboolean isCopy = 0;
    uint8_t *sample_buf = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSamples, &isCopy));
    uint8_t *dst_y = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstY, &isCopy));
    uint8_t *dst_u = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstU, &isCopy));
    uint8_t *dst_v = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstV, &isCopy));

    jint res = libyuv::ConvertToI420(sample_buf, static_cast<size_t>(jSampleSize), dst_y,
                                     jDstYStride, dst_u, jDstUStride, dst_v,
                                     jDstVStride, jCropX, jCropY, jSrcWidth, jSrcHeight, jCropWidth,
                                     jCropHeight, ToRotationMode(jRotation),
                                     static_cast<uint32_t>(jFormat));
    env->ReleaseByteArrayElements(jSamples, reinterpret_cast<jbyte *>(sample_buf), 0);
    env->ReleaseByteArrayElements(jDstY, reinterpret_cast<jbyte *>(dst_y), 0);
    env->ReleaseByteArrayElements(jDstU, reinterpret_cast<jbyte *>(dst_u), 0);
    env->ReleaseByteArrayElements(jDstV, reinterpret_cast<jbyte *>(dst_v), 0);
    return res;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_scaleRotationAndMirrorToI420
        (JNIEnv *env, jclass jClz, jbyteArray jSamples,
         jint jSampleSize, jbyteArray jResultBuf, jint jSrcWidth, jint jSrcHeight, jint jScaleWidth,
         jint jScaleHeight, jint jScaleMode, jint jRotation, jboolean jMirror, jint jFormat) {
    jsize predict_size = jScaleWidth * jScaleHeight * 3 / 2;
    jsize result_buf_size = env->GetArrayLength(jResultBuf);
    if (result_buf_size < predict_size) {
        return 0;
    }
    //copy.
    jboolean isCopy = 0;
    uint8_t *sample_buf = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSamples, &isCopy));
    uint8_t *result_buf = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jResultBuf,
                                                                                &isCopy));
    //temp-src-buf
    uint8_t *temp_src_buf = new uint8_t[jSrcWidth * jSrcHeight * 3 / 2];
    uint8_t *temp_src_y = temp_src_buf;
    uint8_t *temp_src_u = temp_src_y + jSrcWidth * jSrcHeight;
    uint8_t *temp_src_v = temp_src_u + jSrcWidth * jSrcHeight / 4;
    //convert info
    jint convertDstWidth = jSrcWidth, convertDstHeight = jSrcHeight;
    jint yStride = jSrcWidth;
    jint scaleYStride = jScaleWidth;
    jint scaleWidth = jScaleWidth, scaleHeight = jScaleHeight;
    libyuv::RotationMode mode = ToRotationMode(jRotation);
    if (mode == libyuv::kRotate90 || mode == libyuv::kRotate270) {
        yStride = jSrcHeight;
        scaleYStride = jScaleHeight;
        convertDstWidth = jSrcHeight;
        convertDstHeight = jSrcWidth;
        scaleWidth = jScaleHeight;
        scaleHeight = jScaleWidth;
    }
    jint uStride = yStride / 2, vStride = yStride / 2;
    //ConvertToI420
    jint res = libyuv::ConvertToI420(sample_buf, static_cast<size_t>(jSampleSize), temp_src_y,
                                     yStride, temp_src_u, uStride, temp_src_v,
                                     vStride, 0, 0, jSrcWidth, jSrcHeight, jSrcWidth,
                                     jSrcHeight, mode, static_cast<uint32_t>(jFormat));

    //I420Scale
    if (res == 0) {
        jint scaleUStride = scaleYStride / 2, scaleVStride = scaleYStride / 2;
        uint8_t *result_y = result_buf;
        uint8_t *result_u = result_y + scaleWidth * scaleHeight;
        uint8_t *result_v = result_u + scaleWidth * scaleHeight / 4;
        if (jMirror > 0) {
            uint8_t *temp_scale_buf = new uint8_t[scaleWidth * scaleHeight * 3 / 2];
            uint8_t *temp_scale_y = temp_scale_buf;
            uint8_t *temp_scale_u = temp_src_y + scaleWidth * scaleHeight;
            uint8_t *temp_scale_v = temp_src_u + scaleWidth * scaleHeight / 4;
            res = libyuv::I420Scale(temp_src_y, yStride, temp_src_u, uStride, temp_src_v, vStride,
                                    convertDstWidth, convertDstHeight,
                                    temp_scale_y, scaleYStride, temp_scale_u, scaleUStride,
                                    temp_scale_v,
                                    scaleVStride, scaleWidth, scaleHeight,
                                    ToFilterMode(jScaleMode));
            if (res == 0) {
                res = libyuv::I420Mirror(temp_scale_y, scaleYStride, temp_scale_u, scaleUStride,
                                         temp_scale_v, scaleVStride,
                                         result_y, scaleYStride, result_u, scaleUStride, result_v,
                                         scaleVStride, scaleWidth, scaleHeight);
            }
            delete[]temp_scale_buf;
        } else {
            res = libyuv::I420Scale(temp_src_y, yStride, temp_src_u, uStride, temp_src_v, vStride,
                                    convertDstWidth, convertDstHeight,
                                    result_y, scaleYStride, result_u, scaleUStride, result_v,
                                    scaleVStride, scaleWidth, scaleHeight,
                                    ToFilterMode(jScaleMode));
        }
    }
    delete[]temp_src_buf;
    env->ReleaseByteArrayElements(jSamples, reinterpret_cast<jbyte *>(sample_buf), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->ReleaseByteArrayElements(jResultBuf, reinterpret_cast<jbyte *>(result_buf), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (res >= 0)return predict_size; else return 0;
}

JNIEXPORT
JNICALL
jint
Java_com_miracles_codec_camera_LibYuvUtils_i420ToNV12(JNIEnv *env, jclass jClz, jbyteArray jSamples,
                                                      jint jSampleSize, jbyteArray jDst,
                                                      jint jWidth, jint jHeight) {
    jsize predict_size = jWidth * jHeight * 3 / 2;
    jsize result_buf_size = env->GetArrayLength(jDst);
    if (result_buf_size < predict_size) {
        return 0;
    }
    jboolean isCopy = 0;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSamples, &isCopy));
    uint8_t *dst = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDst, &isCopy));
    uint8_t *src_y = src;
    uint8_t *src_u = src_y + jWidth * jHeight;
    uint8_t *src_v = src_u + jWidth * jHeight / 4;
    jint res = libyuv::I420ToNV12(src_y, jWidth, src_u, jWidth / 2, src_v, jWidth / 2, dst, jWidth,
                                  dst + jWidth * jHeight, jWidth / 1, jWidth, jHeight);
    env->ReleaseByteArrayElements(jSamples, reinterpret_cast<jbyte *>(src), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->ReleaseByteArrayElements(jDst, reinterpret_cast<jbyte *>(dst), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (res >= 0)return predict_size; else return 0;
}

JNIEXPORT
JNICALL
jint
Java_com_miracles_codec_camera_LibYuvUtils_i420ToNV21(JNIEnv *env, jclass jClz, jbyteArray jSamples,
                                                      jint jSampleSize, jbyteArray jDst,
                                                      jint jWidth, jint jHeight) {
    jsize predict_size = jWidth * jHeight * 3 / 2;
    jsize result_buf_size = env->GetArrayLength(jDst);
    if (result_buf_size < predict_size) {
        return 0;
    }
    jboolean isCopy = 0;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSamples, &isCopy));
    uint8_t *dst = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDst, &isCopy));
    uint8_t *src_y = src;
    uint8_t *src_u = src_y + jWidth * jHeight;
    uint8_t *src_v = src_u + jWidth * jHeight / 4;
    jint res = libyuv::I420ToNV21(src_y, jWidth, src_u, jWidth / 2, src_v, jWidth / 2, dst, jWidth,
                                  dst + jWidth * jHeight, jWidth, jWidth, jHeight);
    env->ReleaseByteArrayElements(jSamples, reinterpret_cast<jbyte *>(src), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    env->ReleaseByteArrayElements(jDst, reinterpret_cast<jbyte *>(dst), 0);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
    }
    if (res >= 0)return predict_size; else return 0;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_convertToARGB
        (JNIEnv *env, jclass jClz, jbyteArray jSamples, jint jSampleSize, jbyteArray jDst,
         jint jDstStride,
         jint jCropX, jint jCropY,
         jint jSrcWidth, jint jSrcHeight, jint jCropWidth, jint jCropHeight, jint jRotation,
         jint jFormat) {
    jboolean isCopy = 0;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSamples, &isCopy));
    uint8_t *dst = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDst, &isCopy));
    jint res = libyuv::ConvertToARGB(src, static_cast<size_t>(jSampleSize), dst, jDstStride, jCropX,
                                     jCropY, jSrcWidth, jSrcHeight,
                                     jCropWidth, jCropHeight, ToRotationMode(jRotation),
                                     static_cast<uint32_t>(jFormat));
    env->ReleaseByteArrayElements(jSamples, reinterpret_cast<jbyte *>(src), 0);
    env->ReleaseByteArrayElements(jDst, reinterpret_cast<jbyte *>(dst), 0);
    return res;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_i420Rotate
        (JNIEnv *env, jclass jClz, jbyteArray jSrcY, jint jSrcYStride, jbyteArray jSrcU,
         jint jSrcUStride,
         jbyteArray jSrcV, jint jSrcVStride, jbyteArray jDstY, jint jDstYStride, jbyteArray jDstU,
         jint jDstUStride, jbyteArray jDstV, jint jDstVStride, jint srcWidth, jint srcHeight,
         jint jRotation) {
    jboolean isCopy = 0;
    uint8_t *src_y = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcY, &isCopy));
    uint8_t *src_u = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcU, &isCopy));
    uint8_t *src_v = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcV, &isCopy));

    uint8_t *dst_y = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstY, &isCopy));
    uint8_t *dst_u = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstU, &isCopy));
    uint8_t *dst_v = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstV, &isCopy));

    jint res = libyuv::I420Rotate(src_y, jSrcYStride, src_u, jSrcUStride, src_v, jSrcVStride,
                                  dst_y, jDstYStride, dst_u, jDstUStride, dst_v, jDstVStride,
                                  srcWidth, srcHeight, ToRotationMode(jRotation));
    env->ReleaseByteArrayElements(jSrcY, reinterpret_cast<jbyte *>(src_y), 0);
    env->ReleaseByteArrayElements(jSrcU, reinterpret_cast<jbyte *>(src_u), 0);
    env->ReleaseByteArrayElements(jSrcV, reinterpret_cast<jbyte *>(src_v), 0);

    env->ReleaseByteArrayElements(jDstY, reinterpret_cast<jbyte *>(dst_y), 0);
    env->ReleaseByteArrayElements(jDstU, reinterpret_cast<jbyte *>(dst_u), 0);
    env->ReleaseByteArrayElements(jDstV, reinterpret_cast<jbyte *>(dst_v), 0);
    return res;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_argbRotate
        (JNIEnv *env, jclass jClz, jbyteArray jSrc, jint jSrcStride, jbyteArray jDst,
         jint jDstStride,
         jint jSrcWidth, jint jSrcHeight, jint jRotation) {
    jboolean isCopy = 0;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrc, &isCopy));
    uint8_t *dst = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDst, &isCopy));
    jint res = libyuv::ARGBRotate(src, jSrcStride, dst, jDstStride, jSrcWidth, jSrcHeight,
                                  ToRotationMode(jRotation));
    env->ReleaseByteArrayElements(jSrc, reinterpret_cast<jbyte *>(src), 0);
    env->ReleaseByteArrayElements(jDst, reinterpret_cast<jbyte *>(dst), 0);
    return res;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_i420Mirror
        (JNIEnv *env, jclass jClz, jbyteArray jSrcY, jint jSrcYStride, jbyteArray jSrcU,
         jint jSrcUStride,
         jbyteArray jSrcV, jint jSrcVStride, jbyteArray jDstY, jint jDstYStride, jbyteArray jDstU,
         jint jDstUStride, jbyteArray jDstV, jint jDstVStride, jint srcWidth, jint srcHeight) {
    jboolean isCopy = 0;
    uint8_t *src_y = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcY, &isCopy));
    uint8_t *src_u = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcU, &isCopy));
    uint8_t *src_v = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrcV, &isCopy));

    uint8_t *dst_y = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstY, &isCopy));
    uint8_t *dst_u = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstU, &isCopy));
    uint8_t *dst_v = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDstV, &isCopy));

    jint res = libyuv::I420Mirror(src_y, jSrcYStride, src_u, jSrcUStride, src_v, jSrcVStride,
                                  dst_y, jDstYStride, dst_u, jDstUStride, dst_v, jDstVStride,
                                  srcWidth, srcHeight);
    env->ReleaseByteArrayElements(jSrcY, reinterpret_cast<jbyte *>(src_y), 0);
    env->ReleaseByteArrayElements(jSrcU, reinterpret_cast<jbyte *>(src_u), 0);
    env->ReleaseByteArrayElements(jSrcV, reinterpret_cast<jbyte *>(src_v), 0);

    env->ReleaseByteArrayElements(jDstY, reinterpret_cast<jbyte *>(dst_y), 0);
    env->ReleaseByteArrayElements(jDstU, reinterpret_cast<jbyte *>(dst_u), 0);
    env->ReleaseByteArrayElements(jDstV, reinterpret_cast<jbyte *>(dst_v), 0);
    return res;
}

JNIEXPORT
JNICALL
jint Java_com_miracles_codec_camera_LibYuvUtils_argbMirror
        (JNIEnv *env, jclass jClz, jbyteArray jSrc, jint jSrcStride, jbyteArray jDst,
         jint jDstStride,
         jint jSrcWidth, jint jSrcHeight) {
    jboolean isCopy = 0;
    uint8_t *src = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jSrc, &isCopy));
    uint8_t *dst = reinterpret_cast<uint8_t *>(env->GetByteArrayElements(jDst, &isCopy));
    jint res = libyuv::ARGBMirror(src, jSrcStride, dst, jDstStride, jSrcWidth, jSrcHeight);
    env->ReleaseByteArrayElements(jSrc, reinterpret_cast<jbyte *>(src), 0);
    env->ReleaseByteArrayElements(jDst, reinterpret_cast<jbyte *>(dst), 0);
    return res;
}

