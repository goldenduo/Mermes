#include <jni.h>
#include <string.h>
#include <stdlib.h>
#include <unistd.h>
#include <fcntl.h>
#include <termios.h>
#include <sys/ioctl.h>
#include <sys/wait.h>
#include <sys/types.h>
#include <errno.h>
#include <android/log.h>

#define TAG "MermesTerminal"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

/**
 * Create a pseudo-terminal and fork a child process.
 *
 * @param executable Path to the executable
 * @param args Arguments array (args[0] is process name)
 * @param cwd Working directory
 * @param environment Environment variables array ["KEY=VALUE", ...]
 * @param masterFd Output parameter for master PTY fd
 * @return Child process PID
 */
JNIEXPORT jint JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_createSubprocess(
    JNIEnv *env,
    jobject thiz,
    jstring executable,
    jobjectArray args,
    jstring cwd,
    jobjectArray environment,
    jintArray masterFd)
{
    const char *exe_path = (*env)->GetStringUTFChars(env, executable, NULL);
    const char *cwd_path = (*env)->GetStringUTFChars(env, cwd, NULL);

    // Open PTY master
    int ptm = open("/dev/ptmx", O_RDWR | O_CLOEXEC);
    if (ptm < 0) {
        LOGE("Failed to open /dev/ptmx: %s", strerror(errno));
        (*env)->ReleaseStringUTFChars(env, executable, exe_path);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_path);
        return -1;
    }

    // Grant and unlock PTY
    if (grantpt(ptm) || unlockpt(ptm)) {
        LOGE("Failed to grant/unlock PTY: %s", strerror(errno));
        close(ptm);
        (*env)->ReleaseStringUTFChars(env, executable, exe_path);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_path);
        return -1;
    }

    // Get slave device name
    char devname[64];
    if (ptsname_r(ptm, devname, sizeof(devname))) {
        LOGE("Failed to get PTY name: %s", strerror(errno));
        close(ptm);
        (*env)->ReleaseStringUTFChars(env, executable, exe_path);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_path);
        return -1;
    }

    // Configure terminal attributes
    struct termios tios;
    if (tcgetattr(ptm, &tios) == 0) {
        tios.c_iflag |= IUTF8;
        tios.c_iflag &= ~(IXON | IXOFF);
        tcsetattr(ptm, TCSANOW, &tios);
    }

    // Get args array
    int argc = (*env)->GetArrayLength(env, args);
    char **argv = (char **)malloc(sizeof(char *) * (argc + 1));
    for (int i = 0; i < argc; i++) {
        jstring jarg = (jstring)(*env)->GetObjectArrayElement(env, args, i);
        argv[i] = strdup((*env)->GetStringUTFChars(env, jarg, NULL));
        (*env)->DeleteLocalRef(env, jarg);
    }
    argv[argc] = NULL;

    // Get environment array
    int envc = (*env)->GetArrayLength(env, environment);
    char **envp = (char **)malloc(sizeof(char *) * (envc + 1));
    for (int i = 0; i < envc; i++) {
        jstring jenv = (jstring)(*env)->GetObjectArrayElement(env, environment, i);
        envp[i] = strdup((*env)->GetStringUTFChars(env, jenv, NULL));
        (*env)->DeleteLocalRef(env, jenv);
    }
    envp[envc] = NULL;

    // Fork
    pid_t pid = fork();
    if (pid < 0) {
        LOGE("Fork failed: %s", strerror(errno));
        close(ptm);
        // Free allocated memory
        for (int i = 0; i < argc; i++) free(argv[i]);
        free(argv);
        for (int i = 0; i < envc; i++) free(envp[i]);
        free(envp);
        (*env)->ReleaseStringUTFChars(env, executable, exe_path);
        (*env)->ReleaseStringUTFChars(env, cwd, cwd_path);
        return -1;
    }

    if (pid == 0) {
        // Child process

        // Create new session
        setsid();

        // Open slave PTY
        int pts = open(devname, O_RDWR);
        if (pts < 0) {
            _exit(1);
        }

        // Close extra file descriptors (but keep pts for now)
        int max_fd = sysconf(_SC_OPEN_MAX);
        for (int fd = 3; fd < max_fd; fd++) {
            if (fd != pts) close(fd);
        }

        // Redirect stdin/stdout/stderr to slave PTY
        dup2(pts, 0);
        dup2(pts, 1);
        dup2(pts, 2);

        // Close the original pts fd if it's not already 0/1/2
        if (pts > 2) close(pts);

        // Clear environment and set new one
        clearenv();
        for (int i = 0; i < envc; i++) {
            putenv(envp[i]);
        }

        // Change working directory
        if (chdir(cwd_path) != 0) {
            fprintf(stderr, "\r\nchdir failed for %s: %s\r\n", cwd_path, strerror(errno));
            _exit(1);
        }

        // Execute
        execvp(exe_path, argv);

        // execvp only returns on error
        fprintf(stderr, "\r\nexecvp failed for %s: %s\r\n", exe_path, strerror(errno));
        _exit(1);
    }

    // Parent process

    // Free allocated memory
    for (int i = 0; i < argc; i++) free(argv[i]);
    free(argv);
    for (int i = 0; i < envc; i++) free(envp[i]);
    free(envp);

    (*env)->ReleaseStringUTFChars(env, executable, exe_path);
    (*env)->ReleaseStringUTFChars(env, cwd, cwd_path);

    // Return master fd
    jint fd = ptm;
    (*env)->SetIntArrayRegion(env, masterFd, 0, 1, &fd);

    LOGI("Created subprocess: pid=%d, master_fd=%d", pid, ptm);
    return (jint)pid;
}

/**
 * Wait for a child process to exit.
 *
 * @param pid Child process PID
 * @return Exit code
 */
JNIEXPORT jint JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_waitFor(
    JNIEnv *env,
    jobject thiz,
    jint pid)
{
    int status;
    int result = waitpid(pid, &status, 0);
    if (result < 0) {
        LOGE("waitpid failed: %s", strerror(errno));
        return -1;
    }

    if (WIFEXITED(status)) {
        return WEXITSTATUS(status);
    } else if (WIFSIGNALED(status)) {
        return 128 + WTERMSIG(status);
    }
    return -1;
}

/**
 * Send a signal to a child process.
 *
 * @param pid Child process PID
 * @param signal Signal number
 */
JNIEXPORT void JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_sendSignal(
    JNIEnv *env,
    jobject thiz,
    jint pid,
    jint signal)
{
    kill(pid, signal);
}

/**
 * Set PTY window size.
 *
 * @param masterFd Master PTY fd
 * @param rows Number of rows
 * @param cols Number of columns
 */
JNIEXPORT void JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_setPtyWindowSize(
    JNIEnv *env,
    jobject thiz,
    jint masterFd,
    jint rows,
    jint cols)
{
    struct winsize sz = {rows, cols, 0, 0};
    ioctl(masterFd, TIOCSWINSZ, &sz);
}

/**
 * Close a file descriptor.
 *
 * @param masterFd File descriptor to close
 */
JNIEXPORT void JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_closeFd(
    JNIEnv *env,
    jobject thiz,
    jint masterFd)
{
    close(masterFd);
}

/**
 * Read from file descriptor.
 *
 * @param fd File descriptor
 * @param buffer Buffer to read into
 * @return Number of bytes read, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_readFromFd(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jbyteArray buffer)
{
    jsize len = (*env)->GetArrayLength(env, buffer);
    jbyte *buf = (*env)->GetByteArrayElements(env, buffer, NULL);

    ssize_t n = read(fd, buf, len);

    (*env)->ReleaseByteArrayElements(env, buffer, buf, 0);

    if (n < 0) {
        if (errno == EAGAIN) {
            return 0; // No data available
        }
        return -1;
    }
    if (n == 0) {
        return -1; // EOF
    }

    return (jint)n;
}

/**
 * Write to file descriptor.
 *
 * @param fd File descriptor
 * @param data Data to write
 * @return Number of bytes written, or -1 on error
 */
JNIEXPORT jint JNICALL
Java_com_mermes_core_terminal_NativeTerminalLib_writeToFd(
    JNIEnv *env,
    jobject thiz,
    jint fd,
    jbyteArray data)
{
    jsize len = (*env)->GetArrayLength(env, data);
    jbyte *buf = (*env)->GetByteArrayElements(env, data, NULL);

    ssize_t n = write(fd, buf, len);

    (*env)->ReleaseByteArrayElements(env, data, buf, JNI_ABORT);

    if (n < 0) {
        return -1;
    }

    return (jint)n;
}
