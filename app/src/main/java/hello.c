#include <string.h>
#include <jni.h>

Java_com_example_testme_audiotest_MainActivity_HelloWorld(JNIEnv* pEnv, jobject pObj)
{
    return (*pEnv)->NewStringUTF(pEnv, "Hello NDK!");
}

