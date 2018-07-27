# For more information about using CMake with Android Studio, read the
# documentation: https://d.android.com/studio/projects/add-native-code.html

cmake_minimum_required(VERSION 3.4.1)

add_library(yuv-utils SHARED src/main/cpp/yuvutils.cpp )

file(GLOB_RECURSE ly_source_files src/libyuv/source/*.cc)
add_library(libyuv SHARED ${ly_source_files})
include_directories(src/libyuv/include)

target_link_libraries(yuv-utils libyuv)