#include <jni.h>
#include <string.h>
#include <android/log.h>

#define TAG "MermesDeb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/* Defined in deb-zip.S */
extern unsigned char deb_blob[];
extern int deb_blob_size;

/**
 * Get the embedded deb packages zip as a byte array.
 */
JNIEXPORT jbyteArray JNICALL
Java_com_mermes_core_deb_NativeDebLib_getZip(
    JNIEnv *env,
    jobject thiz)
{
    LOGI("getZip: deb_blob_size=%d", deb_blob_size);

    jbyteArray result = (*env)->NewByteArray(env, deb_blob_size);
    if (result == NULL) {
        LOGE("getZip: Failed to allocate byte array");
        return NULL;
    }

    (*env)->SetByteArrayRegion(env, result, 0, deb_blob_size, (jbyte *)deb_blob);
    return result;
}
