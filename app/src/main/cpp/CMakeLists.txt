cmake_minimum_required(VERSION 3.22)
project(meltype_jni C)
add_library(meltype_jni SHARED meltype_jni.c)
target_link_libraries(meltype_jni dl log)
