#include <jni.h>
#include <cstring>

extern "C"
JNIEXPORT void JNICALL
Java_de_lifeos_android_security_BlackboxMemoryBridge_nativeSecureWipe(
    JNIEnv *env,
    jobject /* this */,
    jobject directBuffer,
    jlong capacity
) {
    if (directBuffer == nullptr || capacity <= 0) return;
    void *address = env->GetDirectBufferAddress(directBuffer);
    if (address != nullptr) {
        volatile unsigned char *volatilePtr = static_cast<volatile unsigned char *>(address);
        for (jlong i = 0; i < capacity; ++i) {
            volatilePtr[i] = 0x00;
        }
    }
}
