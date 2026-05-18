#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <errno.h>
#include <android/log.h>

#define TAG "MermesUtils"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Set file permissions.
 *
 * @param path File path
 * @param mode Permission mode (e.g., 0700)
 */
JNIEXPORT void JNICALL
Java_com_mermes_core_utils_NativeUtils_chmod(
    JNIEnv *env,
    jobject thiz,
    jstring path,
    jint mode)
{
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    if (chmod(cpath, mode) != 0) {
        LOGE("chmod failed for %s: %s", cpath, strerror(errno));
    }
    (*env)->ReleaseStringUTFChars(env, path, cpath);
}

/**
 * Create a symbolic link.
 *
 * @param target Link target
 * @param linkPath Link path
 */
JNIEXPORT void JNICALL
Java_com_mermes_core_utils_NativeUtils_symlink(
    JNIEnv *env,
    jobject thiz,
    jstring target,
    jstring linkPath)
{
    const char *ctarget = (*env)->GetStringUTFChars(env, target, NULL);
    const char *clinkPath = (*env)->GetStringUTFChars(env, linkPath, NULL);

    if (symlink(ctarget, clinkPath) != 0) {
        LOGE("symlink failed: %s -> %s: %s", clinkPath, ctarget, strerror(errno));
    }

    (*env)->ReleaseStringUTFChars(env, target, ctarget);
    (*env)->ReleaseStringUTFChars(env, linkPath, clinkPath);
}

/**
 * Get current device architecture.
 *
 * @return Architecture name (aarch64, arm, i686, x86_64)
 */
JNIEXPORT jstring JNICALL
Java_com_mermes_core_utils_NativeUtils_getArch(
    JNIEnv *env,
    jobject thiz)
{
#if defined(__aarch64__)
    return (*env)->NewStringUTF(env, "aarch64");
#elif defined(__arm__)
    return (*env)->NewStringUTF(env, "arm");
#elif defined(__i686__)
    return (*env)->NewStringUTF(env, "i686");
#elif defined(__x86_64__)
    return (*env)->NewStringUTF(env, "x86_64");
#else
    return (*env)->NewStringUTF(env, "unknown");
#endif
}

/**
 * Check if a file is an ELF binary.
 *
 * @param path File path
 * @return true if ELF binary
 */
JNIEXPORT jboolean JNICALL
Java_com_mermes_core_utils_NativeUtils_isElfBinary(
    JNIEnv *env,
    jobject thiz,
    jstring path)
{
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    jboolean result = JNI_FALSE;

    int fd = open(cpath, O_RDONLY);
    if (fd >= 0) {
        unsigned char magic[4];
        if (read(fd, magic, 4) == 4) {
            if (magic[0] == 0x7F && magic[1] == 'E' && magic[2] == 'L' && magic[3] == 'F') {
                result = JNI_TRUE;
            }
        }
        close(fd);
    }

    (*env)->ReleaseStringUTFChars(env, path, cpath);
    return result;
}

/**
 * Get the shebang interpreter path from a script file.
 *
 * @param path File path
 * @return Interpreter path, or null if no shebang
 */
JNIEXPORT jstring JNICALL
Java_com_mermes_core_utils_NativeUtils_getShebang(
    JNIEnv *env,
    jobject thiz,
    jstring path)
{
    const char *cpath = (*env)->GetStringUTFChars(env, path, NULL);
    jstring result = NULL;

    int fd = open(cpath, O_RDONLY);
    if (fd >= 0) {
        char buf[256];
        ssize_t n = read(fd, buf, sizeof(buf) - 1);
        close(fd);

        if (n >= 2 && buf[0] == '#' && buf[1] == '!') {
            // Skip #! and whitespace
            int i = 2;
            while (i < n && (buf[i] == ' ' || buf[i] == '\t')) i++;

            // Find end of line
            int start = i;
            while (i < n && buf[i] != '\n' && buf[i] != '\r') i++;

            if (i > start) {
                buf[i] = '\0';
                result = (*env)->NewStringUTF(env, buf + start);
            }
        }
    }

    (*env)->ReleaseStringUTFChars(env, path, cpath);
    return result;
}
