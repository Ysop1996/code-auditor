package de.lifeos.core.optical

import android.graphics.Bitmap
import android.graphics.Color
import java.io.File
import java.io.FileOutputStream
import kotlin.math.*

object DeterministicSceneRenderer {

    fun renderSceneToFile(
        outputFile: File,
        epochMs: Long = System.currentTimeMillis(),
        personPos: Vector3 = Vector3(0.7f, 0f, 2.5f),
        carPos: Vector3 = Vector3(-0.5f, 0f, 3.8f),
        width: Int = 512,
        height: Int = 512
    ): File {
        val solar = SolarEphemerisEngine.computeSolarState(epochMs)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val pixels = IntArray(width * height)
        val camPos = Vector3(0f, 1.2f, 0f)

        for (y in 0 until height) {
            val v = (1f - 2f * (y + 0.5f) / height) * 0.5f
            for (x in 0 until width) {
                val u = (2f * (x + 0.5f) / width - 1f) * 0.5f * (width.toFloat() / height)
                val rayDir = Vector3(u, v, 1.0f).normalize()

                pixels[y * width + x] = raymarchPixel(camPos, rayDir, solar, personPos, carPos)
            }
        }

        bitmap.setPixels(pixels, 0, width, 0, 0, width, height)
        FileOutputStream(outputFile).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        return outputFile
    }

    private fun raymarchPixel(
        ro: Vector3,
        rd: Vector3,
        solar: SolarState,
        personPos: Vector3,
        carPos: Vector3
    ): Int {
        var t = 0.1f
        val maxDist = 25.0f

        for (i in 0 until 90) {
            val p = ro + (rd * t)
            val (dist, matId) = DeterministicSceneSDF.mapScene(p, personPos, carPos)
            if (dist < 0.001f) {
                val n = DeterministicSceneSDF.calcNormal(p, personPos, carPos)
                return shadeSpectral(p, n, rd, matId, solar, personPos, carPos)
            }
            t += dist
            if (t > maxDist) break
        }

        // Himmelsstrahlung bei Fehltreffer
        val skyFactor = max(0f, rd.y)
        val r = (solar.skyAmbient[5] * skyFactor * 180).toInt().coerceIn(0, 255)
        val g = (solar.skyAmbient[3] * skyFactor * 210).toInt().coerceIn(0, 255)
        val b = (solar.skyAmbient[1] * skyFactor * 255 + 50).toInt().coerceIn(0, 255)
        return Color.rgb(r, g, b)
    }

    private fun shadeSpectral(
        p: Vector3,
        n: Vector3,
        rd: Vector3,
        matId: Int,
        solar: SolarState,
        personPos: Vector3,
        carPos: Vector3
    ): Int {
        val cosTheta = max(0f, n.dot(solar.sunDirection))

        // Deterministischer harter Schattenwurf
        var shadow = 1.0f
        var st = 0.05f
        for (i in 0 until 30) {
            val sp = p + (solar.sunDirection * st)
            val (sd, _) = DeterministicSceneSDF.mapScene(sp, personPos, carPos)
            if (sd < 0.001f) {
                shadow = 0.15f
                break
            }
            st += sd
            if (st > 10f) break
        }

        // Spektrale Albedo pro Material
        val albedo = when (matId) {
            2 -> floatArrayOf(0.1f, 0.2f, 0.3f, 0.4f, 0.7f, 0.9f) // Lack: Karminrot
            3 -> floatArrayOf(0.3f, 0.4f, 0.5f, 0.6f, 0.7f, 0.8f) // Hautton
            4 -> floatArrayOf(0.6f, 0.6f, 0.5f, 0.4f, 0.2f, 0.1f) // Textil (Marineblau)
            else -> floatArrayOf(0.4f, 0.4f, 0.4f, 0.4f, 0.4f, 0.4f) // Asphalt/Boden
        }

        // CIE-1931 Tristimulus Integration über die 6 Bänder
        var rLin = 0f
        var gLin = 0f
        var bLin = 0f

        for (i in albedo.indices) {
            val directLight = solar.directIrradiance[i] * cosTheta * shadow
            val ambientLight = solar.skyAmbient[i]
            val totalFlux = albedo[i] * (directLight + ambientLight)

            if (i >= 4) rLin += totalFlux * 0.7f
            if (i in 2..4) gLin += totalFlux * 0.6f
            if (i <= 2) bLin += totalFlux * 0.8f
        }

        val r = (rLin.pow(1f / 2.2f) * 255).toInt().coerceIn(0, 255)
        val g = (gLin.pow(1f / 2.2f) * 255).toInt().coerceIn(0, 255)
        val b = (bLin.pow(1f / 2.2f) * 255).toInt().coerceIn(0, 255)

        return Color.rgb(r, g, b)
    }
}
