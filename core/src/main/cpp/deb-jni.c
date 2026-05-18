#include <jni.h>
#include <string.h>
#include <android/log.h>

#define TAG "MermesDeb"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/*
 * Deb package embedding structure.
 * Each deb package is stored as a separate .so with embedded data.
 * This is a placeholder implementation - actual deb packages will be
 * embedded via separate assembly files for each package.
 */

/**
 * Get the number of embedded deb packages.
 *
 * @return Number of deb packages
 */
JNIEXPORT jint JNICALL
Java_com_mermes_core_deb_NativeDebLib_getDebCount(
    JNIEnv *env,
    jobject thiz)
{
    // This will be implemented based on actual embedded packages
    // For now, return 0 as placeholder
    return 0;
}

/**
 * Get deb package data by index.
 *
 * @param index Package index
 * @return Deb file byte array
 */
JNIEXPORT jbyteArray JNICALL
Java_com_mermes_core_deb_NativeDebLib_getDebByIndex(
    JNIEnv *env,
    jobject thiz,
    jint index)
{
    // Placeholder - actual implementation will access embedded data
    LOGE("getDebByIndex not implemented yet");
    return NULL;
}

/**
 * Get deb package data by architecture and name.
 *
 * @param arch Architecture name (aarch64, arm, i686, x86_64)
 * @param packageName Package name
 * @return Deb file byte array, or null if not found
 */
JNIEXPORT jbyteArray JNICALL
Java_com_mermes_core_deb_NativeDebLib_getDebByArchAndName(
    JNIEnv *env,
    jobject thiz,
    jstring arch,
    jstring packageName)
{
    // Placeholder - actual implementation will access embedded data
    LOGE("getDebByArchAndName not implemented yet");
    return NULL;
}

/**
 * Get all embedded deb package names.
 *
 * @return Array of package names
 */
JNIEXPORT jobjectArray JNICALL
Java_com_mermes_core_deb_NativeDebLib_getDebNames(
    JNIEnv *env,
    jobject thiz)
{
    // Placeholder - actual implementation will return embedded package names
    jobjectArray result = (*env)->NewObjectArray(env, 0,
        (*env)->FindClass(env, "java/lang/String"), NULL);
    return result;
}
