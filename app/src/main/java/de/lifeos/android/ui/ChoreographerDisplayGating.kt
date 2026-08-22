package de.lifeos.android.ui

import android.view.Choreographer
import de.lifeos.android.telemetry.BehaviorMetrics

class ChoreographerDisplayGating(
    private val onRenderTick: () -> Unit
) : Choreographer.FrameCallback {

    private val choreographer = Choreographer.getInstance()
    private var isRunning = false
    private var targetFps = 1
    private var lastFrameTimeNanos = 0L

    fun start() {
        if (!isRunning) {
            isRunning = true
            choreographer.postFrameCallback(this)
        }
    }

    fun stop() {
        isRunning = false
        choreographer.removeFrameCallback(this)
    }

    fun updateGating(metrics: BehaviorMetrics) {
        targetFps = when {
            metrics.frictionW > 2.0 -> 120
            metrics.frictionW > 1.0 -> 60
            else -> 1
        }
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isRunning) return
        val frameIntervalNanos = 1_000_000_000L / targetFps
        if (frameTimeNanos - lastFrameTimeNanos >= frameIntervalNanos) {
            lastFrameTimeNanos = frameTimeNanos
            onRenderTick()
        }
        choreographer.postFrameCallback(this)
    }
}
