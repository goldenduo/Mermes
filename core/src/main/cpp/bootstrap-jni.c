#include <jni.h>
#include <string.h>
#include <android/log.h>

#define TAG "MermesBootstrap"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Defined in bootstrap-zip.S */
extern unsigned char blob[];
extern int blob_size;

/**
 * Get the embedded bootstrap zip as a byte array.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_mermes_core_bootstrap_NativeBootstrapLib_getZip(
    JNIEnv *env,
    jobject thiz)
{
    LOGI("getZip: blob_size=%d", blob_size);

    jbyteArray result = (*env)->NewByteArray(env, blob_size);
    if (result == NULL) {
        LOGE("getZip: Failed to allocate byte array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, result, 0, blob_size, (jbyte *)blob);
    return result;
}
