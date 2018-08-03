# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.4.1)

file(GLOB_RECURSE ly_source_files src/libyuv/source/*.cc)

include_directories(src/libyuv/include src/main/cpp/include)

add_library(yuv-utils SHARED ${ly_source_files} src/main/cpp/yuvutils.cpp)