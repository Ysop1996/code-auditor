#include <jni.h>
#include <cstring>
#include <unistd.h>
#include <signal.h>
#include <sys/ptrace.h>

extern "C" {

JNIEXPORT jboolean JNICALL
Java_de_lifeos_android_security_TeeIntegrityGuard_nativeCheckIntegrity(JNIEnv * /* env */, jclass /* clazz */) {
    if (ptrace(PTRACE_TRACEME, 0, 1, 0) < 0) {
        return JNI_FALSE;
    }
    ptrace(PTRACE_DETACH, 0, 1, 0);
    return JNI_TRUE;
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
