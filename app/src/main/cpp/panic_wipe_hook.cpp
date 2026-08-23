#include <jni.h>
#include <cstring>
#include <unistd.h>
#include <signal.h>
#include <sys/stat.h>
#include <fcntl.h>

extern "C" {

/**
 * Integritätsprüfung ohne ptrace (SELinux-kompatibel).
 * Prüft ob /proc/self/status den TracerPid-Eintrag enthält.
 */
JNIEXPORT jboolean JNICALL
Java_de_lifeos_android_security_TeeIntegrityGuard_nativeCheckIntegrity(JNIEnv * /* env */, jclass /* clazz */) {
    char buf[512];
    int fd = open("/proc/self/status", O_RDONLY);
    if (fd < 0) {
        return JNI_TRUE; // Kann nicht prüfen -> als integer betrachten
    }

    ssize_t len = read(fd, buf, sizeof(buf) - 1);
    close(fd);

    if (len <= 0) {
        return JNI_TRUE;
    }
    buf[len] = '\0';

    // Suche nach TracerPid Zeile
    char *tracer = strstr(buf, "TracerPid:");
    if (tracer == nullptr) {
        return JNI_TRUE;
    }

    // Extrahiere PID
    tracer += 10; // "TracerPid:" überspringen
    while (*tracer == ' ' || *tracer == '\t') tracer++;

    // Wenn TracerPid != 0, wird der Prozess debuggt
    if (*tracer != '0') {
        return JNI_FALSE; // Debugger erkannt
    }

    return JNI_TRUE; // Kein Debugger
}

JNIEXPORT void JNICALL
Java_de_lifeos_android_security_TeeIntegrityGuard_nativeEmergencyPanicWipe(
    JNIEnv *env,
    jclass /* clazz */,
    jobject directBuffer,
    jlong capacity
) {
    if (directBuffer != nullptr && capacity > 0) {
        void *address = env->GetDirectBufferAddress(directBuffer);
        if (address != nullptr) {
            volatile unsigned char *ptr = static_cast<volatile unsigned char *>(address);
            for (jlong i = 0; i < capacity; ++i) {
                ptr[i] = 0x00;
            }
        }
    }
    kill(getpid(), SIGKILL);
}

}
