#include <jni.h>
#include <arm_neon.h>
#include <cmath>

extern "C" {

JNIEXPORT jfloat JNICALL
Java_de_lifeos_core_field_NeonPhaseBridge_nativeDistSq32(
    JNIEnv *env,
    jclass /* clazz */,
    jfloatArray vecA,
    jfloatArray vecB
) {
    jfloat *a = env->GetFloatArrayElements(vecA, nullptr);
    jfloat *b = env->GetFloatArrayElements(vecB, nullptr);
    float32x4_t acc = vdupq_n_f32(0.0f);

    for (int i = 0; i < 32; i += 4) {
        float32x4_t va = vld1q_f32(a + i);
        float32x4_t vb = vld1q_f32(b + i);
        float32x4_t diff = vsubq_f32(va, vb);
        acc = vfmaq_f32(acc, diff, diff);
    }

    env->ReleaseFloatArrayElements(vecA, a, JNI_ABORT);
    env->ReleaseFloatArrayElements(vecB, b, JNI_ABORT);

    float32x2_t sum2 = vadd_f32(vget_low_f32(acc), vget_high_f32(acc));
    return vget_lane_f32(vpadd_f32(sum2, sum2), 0);
}

JNIEXPORT void JNICALL
Java_de_lifeos_core_field_NeonPhaseBridge_nativeComputeForce32(
    JNIEnv *env,
    jclass /* clazz */,
    jfloatArray currentPos,
    jfloatArray targetPos,
    jfloat rhoFuture,
    jfloat phi,
    jfloatArray outForce
) {
    jfloat *curr = env->GetFloatArrayElements(currentPos, nullptr);
    jfloat *tgt = env->GetFloatArrayElements(targetPos, nullptr);
    jfloat *out = env->GetFloatArrayElements(outForce, nullptr);

    float32x4_t accDist = vdupq_n_f32(0.0f);

    for (int i = 0; i < 32; i += 4) {
        float32x4_t vc = vld1q_f32(curr + i);
        float32x4_t vt = vld1q_f32(tgt + i);
        float32x4_t diff = vsubq_f32(vc, vt);
        accDist = vfmaq_f32(accDist, diff, diff);
    }

    float32x2_t sum2 = vadd_f32(vget_low_f32(accDist), vget_high_f32(accDist));
    float distSq = vget_lane_f32(vpadd_f32(sum2, sum2), 0);
    float distance = std::sqrt(distSq);

    if (distance < 1e-6f) {
        for (int i = 0; i < 32; ++i) out[i] = 0.0f;
    } else {
        float sign = (distance >= 0.1f) ? 1.0f : -1.0f;
        float factor = (-sign * phi * (rhoFuture + 1.0f)) / distance;
        float32x4_t vFactor = vdupq_n_f32(factor);

        for (int i = 0; i < 32; i += 4) {
            float32x4_t vc = vld1q_f32(curr + i);
            float32x4_t vt = vld1q_f32(tgt + i);
            float32x4_t diff = vsubq_f32(vc, vt);
            float32x4_t res = vmulq_f32(diff, vFactor);
            vst1q_f32(out + i, res);
        }
    }

    env->ReleaseFloatArrayElements(currentPos, curr, JNI_ABORT);
    env->ReleaseFloatArrayElements(targetPos, tgt, JNI_ABORT);
    env->ReleaseFloatArrayElements(outForce, out, 0);
}

}
