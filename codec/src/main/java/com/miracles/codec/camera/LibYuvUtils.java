package com.miracles.codec.camera;

/**
 * Created by lxw
 */
public class LibYuvUtils {
    public static int FOURCC_I420 = format('I', '4', '2', '0');
    public static int FOURCC_I422 = format('I', '4', '2', '2');
    public static int FOURCC_I444 = format('I', '4', '4', '4');
    public static int FOURCC_I400 = format('I', '4', '0', '0');
    public static int FOURCC_NV21 = format('N', 'V', '2', '1');
    public static int FOURCC_NV12 = format('N', 'V', '1', '2');
    public static int FOURCC_YUY2 = format('Y', 'U', 'Y', '2');
    public static int FOURCC_UYVY = format('U', 'Y', 'V', 'Y');
    public static int FOURCC_H010 = format('H', '0', '1', '0');  // unofficial fourcc. 10 bit lsb
    // 1 Secondary YUV format: row biplanar.
    public static int FOURCC_M420 = format('M', '4', '2', '0');
    // 11 Primary RGB formats: 4 32 bpp, 2 24 bpp, 3 16 bpp, 1 10 bpc
    public static int FOURCC_ARGB = format('A', 'R', 'G', 'B');
    public static int FOURCC_BGRA = format('B', 'G', 'R', 'A');
    public static int FOURCC_ABGR = format('A', 'B', 'G', 'R');
    public static int FOURCC_AR30 = format('A', 'R', '3', '0');  // 10 bit per channel. 2101010.
    public static int FOURCC_AB30 = format('A', 'B', '3', '0');  // ABGR version of 10 bit
    public static int FOURCC_24BG = format('2', '4', 'B', 'G');
    public static int FOURCC_RAW = format('r', 'a', 'w', ' ');
    public static int FOURCC_RGBA = format('R', 'G', 'B', 'A');
    public static int FOURCC_RGBP = format('R', 'G', 'B', 'P');  // rgb565 LE.
    public static int FOURCC_RGBO = format('R', 'G', 'B', 'O');  // argb1555 LE.
    public static int FOURCC_R444 = format('R', '4', '4', '4');  // argb4444 LE.
    // 1 Primary Compressed YUV format.
    public static int FOURCC_MJPG = format('M', 'J', 'P', 'G');
    // 8 Auxiliary YUV variations: 3 with U and V planes are swapped, 1 Alias.
    public static int FOURCC_YV12 = format('Y', 'V', '1', '2');
    public static int FOURCC_YV16 = format('Y', 'V', '1', '6');
    public static int FOURCC_YV24 = format('Y', 'V', '2', '4');
    public static int FOURCC_YU12 = format('Y', 'U', '1', '2');  // Linux version of I420.
    public static int FOURCC_J420 = format('J', '4', '2', '0');
    public static int FOURCC_J400 = format('J', '4', '0', '0');  // unofficial fourcc
    public static int FOURCC_H420 = format('H', '4', '2', '0');  // unofficial fourcc
    public static int FOURCC_H422 = format('H', '4', '2', '2');  // unofficial fourcc
    // 14 Auxiliary aliases.  CanonicalFourCC() maps these to canonical fourcc.
    public static int FOURCC_IYUV = format('I', 'Y', 'U', 'V');  // Alias for I420.
    public static int FOURCC_YU16 = format('Y', 'U', '1', '6');  // Alias for I422.
    public static int FOURCC_YU24 = format('Y', 'U', '2', '4');  // Alias for I444.
    public static int FOURCC_YUYV = format('Y', 'U', 'Y', 'V');  // Alias for YUY2.
    public static int FOURCC_YUVS = format('y', 'u', 'v', 's');  // Alias for YUY2 on Mac.
    public static int FOURCC_HDYC = format('H', 'D', 'Y', 'C');  // Alias for UYVY.
    public static int FOURCC_2VUY = format('2', 'v', 'u', 'y');  // Alias for UYVY on Mac.
    public static int FOURCC_JPEG = format('J', 'P', 'E', 'G');  // Alias for MJPG.
    public static int FOURCC_DMB1 = format('d', 'm', 'b', '1');  // Alias for MJPG on Mac.
    public static int FOURCC_BA81 = format('B', 'A', '8', '1');  // Alias for BGGR.
    public static int FOURCC_RGB3 = format('R', 'G', 'B', '3');  // Alias for RAW.
    public static int FOURCC_BGR3 = format('B', 'G', 'R', '3');  // Alias for 24BG.
    public static int FOURCC_CM32 = format((char) 0, (char) 0, (char) 0, (char) 32);  // Alias for BGRA kCMPixelFormat_32ARGB
    public static int FOURCC_CM24 = format((char) 0, (char) 0, (char) 0, (char) 24);  // Alias for RAW kCMPixelFormat_24RGB
    public static int FOURCC_L555 = format('L', '5', '5', '5');  // Alias for RGBO.
    public static int FOURCC_L565 = format('L', '5', '6', '5');  // Alias for RGBP.
    public static int FOURCC_5551 = format('5', '5', '5', '1');  // Alias for RGBO.
    // deprecated formats.  Not supported, but defined for backward compatibility.
    public static int FOURCC_I411 = format('I', '4', '1', '1');
    public static int FOURCC_Q420 = format('Q', '4', '2', '0');
    public static int FOURCC_RGGB = format('R', 'G', 'G', 'B');
    public static int FOURCC_BGGR = format('B', 'G', 'G', 'R');
    public static int FOURCC_GRBG = format('G', 'R', 'B', 'G');
    public static int FOURCC_GBRG = format('G', 'B', 'R', 'G');
    public static int FOURCC_H264 = format('H', '2', '6', '4');
    // Match any fourcc.
    public static int FOURCC_ANY = -1;

    //rotation
    public static int ROTATION_0 = 0;
    public static int ROTATION_90 = 90;
    public static int ROTATION_180 = 180;
    public static int ROTATION_270 = 270;

    //scale filter
    public static int SCALE_FILTER_NONE = 0;
    public static int SCALE_FILTER_LINEAR = 1;
    public static int SCALE_FILTER_BILINEAR = 2;
    public static int SCALE_FILTER_BOX = 3;

    static {
        System.loadLibrary("libyuv");
        System.loadLibrary("yuv-utils");
    }

    private static int format(char... fourcc) {
        if (fourcc.length != 4) return FOURCC_ANY;
        return fourcc[0] | fourcc[1] << 8 | fourcc[2] << 16 | fourcc[3] << 24;
    }


    /**
     * Convert camera sample to I420 with cropping, rotation and vertical flip.
     *
     * @return result=if(result>=0) 'success' else 'failed'
     */
    public native static int convertToI420(byte[] samples, int sampleSize,
                                           byte[] dstY, int dstStrideY, byte[] dstU, int dstStrideU, byte[] dstV, int dstStrideV,
                                           int cropX, int cropY, int srcWidth, int srcHeight,
                                           int cropWidth, int cropHeight, int rotation, int format);

    /**
     * if(result.length<(I420 formatted size (cropWidth*cropHeight*3/2))) result will be failed.
     *
     * @param scaleMode scale mode
     * @param rotation  rotation degree.
     * @param mirror    mirror or not.
     * @param format    input samples format
     * @return result= if('native success') 'data size of convert len' else <=0
     */
    public native static int scaleRotationAndMirrorToI420(byte[] samples, int sampleSize, byte[] result, int srcWidth,
                                                          int srcHeight, int scaleWidth, int scaleHeight, int scaleMode, int rotation,
                                                          boolean mirror, int format);

    /**
     * @return size of result.
     */
    public native static int i420ToNV12(byte[] samples, int sampleSize, byte[] result, int width, int height);

    public native static int convertToARGB(byte[] samples, int sampleSize, byte[] dst, int dst_stride, int cropX, int cropY, int srcWidth, int srcHeight,
                                           int cropWidth, int cropHeight, int rotation, int format);

    public native static int i420Rotate(byte[] srcY, int srcYStride, byte[] srcU, int srcUStride, byte[] srcV, int srcVStride,
                                        byte[] dstY, int dstYStride, byte[] dstU, int dstUStride, byte[] dstV, int dstVStride,
                                        int srcWidth, int srcHeight, int rotation);

    public native static int argbRotate(byte[] src, int srcStride, byte[] dst, int dstStride, int srcWidth, int srcHeight, int rotation);

    public native static int i420Mirror(byte[] srcY, int srcYStride, byte[] srcU, int srcUStride, byte[] srcV, int srcVStride,
                                        byte[] dstY, int dstYStride, byte[] dstU, int dstUStride, byte[] dstV, int dstVStride,
                                        int srcWidth, int srcHeight);

    public native static int argbMirror(byte[] src, int srcStride, byte[] dst, int dstStride, int srcWidth, int srcHeight);

}
