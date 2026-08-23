package de.lifeos.core.field

import kotlin.math.*

/**
 * FIELD VISUALIZATION ENGINE — Feld-Visualisierung
 *
 * Die FieldVisualizationEngine erzeugt deterministische Visualisierungen
 * der Feld-Dynamik. Sie rendert Trajektorien, Vektorfelder, Heatmaps und
 * Spektraldarstellungen ohne neuronale Netze oder Diffusionsmodelle.
 *
 * Rendering-Methoden:
 * 1. Trajectory: 2D/3D-Trajektorien des Zustandsvektors I(t)
 * 2. Vector Field: Vektorfeld der Feldkräfte F = -∇U
 * 3. Heatmap: 2D-Darstellung des Potenzials U(x, t)
 * 4. Spectral: Frequenzspektrum der kognitiven Zustände
 * 5. Phase Space: Phasenraum-Darstellung der Attraktoren
 *
 * Vektoren:
 * - [EXP-FORCE] Visuelle Klarheit: Maximale Interpretierbarkeit
 * - [EXP-AUTO] Autopoietische Darstellung: Selbst-erklärende Visualisierung
 * - [EXP-SPEED] O(n) Rendering, O(1) Color-Lookup
 */
object FieldVisualizationEngine {

    // =========================================================================
    // VISUALIZATION PARAMETERS
    // =========================================================================

    /** Standard-Canvas-Breite */
    const val DEFAULT_WIDTH: Int = 800

    /** Standard-Canvas-Höhe */
    const val DEFAULT_HEIGHT: Int = 600

    /** Standard-Farbtiefe */
    const val COLOR_DEPTH: Int = 256

    /** Standard-Abtastrate für Visualisierungen */
    const val DEFAULT_SAMPLE_RATE: Int = 100

    // =========================================================================
    // COLOR SYSTEM
    // =========================================================================

    /**
     * Farbraum für Visualisierungen.
     */
    enum class ColorSpace {
        /** RGB */
        RGB,

        /** HSV */
        HSV,

        /** Grayscale */
        GRAYSCALE,

        /** Thermodynamisch (Blau→Rot) */
        THERMODYNAMIC
    }

    /**
     * Farbe im RGBA-Format.
     *
     * @param r Rot [0, 255]
     * @param g Grün [0, 255]
     * @param b Blau [0, 255]
     * @param a Alpha [0, 255]
     */
    data class Color(val r: Int, val g: Int, val b: Int, val a: Int = 255) {
        init {
            require(r in 0..255) { "Red must be in [0, 255]" }
            require(g in 0..255) { "Green must be in [0, 255]" }
            require(b in 0..255) { "Blue must be in [0, 255]" }
            require(a in 0..255) { "Alpha must be in [0, 255]" }
        }

        fun toHex(): String {
            return String.format("#%02X%02X%02X%02X", r, g, b, a)
        }

        fun toRGBA(): Int {
            return (a shl 24) or (r shl 16) or (g shl 8) or b
        }

        companion object {
            val BLACK = Color(0, 0, 0)
            val WHITE = Color(255, 255, 255)
            val RED = Color(255, 0, 0)
            val GREEN = Color(0, 255, 0)
            val BLUE = Color(0, 0, 255)
            val YELLOW = Color(255, 255, 0)
            val CYAN = Color(0, 255, 255)
            val MAGENTA = Color(255, 0, 255)
            val TRANSPARENT = Color(0, 0, 0, 0)
        }
    }

    /**
     * Farb-Mapper für Feldwerte.
     */
    object ColorMapper {

        /**
         * Mappt einen Wert [0, 1] auf eine Farbe im thermodynamischen Farbraum.
         *
         * @param value Wert [0, 1]
         * @return Farbe
         */
        fun thermodynamic(value: Float): Color {
            val v = value.coerceIn(0.0f, 1.0f)
            // Blau (kalt) → Cyan → Grün → Gelb → Rot (heiß)
            val hue = (1.0f - v) * 240.0f // 240 (blau) → 0 (rot)
            return hsvToRgb(hue, 1.0f, 1.0f)
        }

        /**
         * Mappt einen Wert [0, 1] auf eine Graustufe.
         *
         * @param value Wert [0, 1]
         * @return Farbe
         */
        fun grayscale(value: Float): Color {
            val v = (value.coerceIn(0.0f, 1.0f) * 255).toInt()
            return Color(v, v, v)
        }

        /**
         * Mappt einen Wert [0, 1] auf eine Farbe im HSV-Farbraum.
         *
         * @param value Wert [0, 1]
         * @param saturation Sättigung [0, 1]
         * @param value Helligkeit [0, 1]
         * @return Farbe
         */
        fun hsv(value: Float, saturation: Float = 1.0f, brightness: Float = 1.0f): Color {
            val hue = (1.0f - value.coerceIn(0.0f, 1.0f)) * 360.0f
            return hsvToRgb(hue, saturation.coerceIn(0.0f, 1.0f), brightness.coerceIn(0.0f, 1.0f))
        }

        /**
         * Konvertiert HSV zu RGB.
         */
        private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Color {
            val c = value * saturation
            val x = c * (1.0f - abs((hue / 60.0f) % 2.0f - 1.0f))
            val m = value - c

            val (r1, g1, b1) = when {
                hue < 60.0f -> Triple(c, x, 0.0f)
                hue < 120.0f -> Triple(x, c, 0.0f)
                hue < 180.0f -> Triple(0.0f, c, x)
                hue < 240.0f -> Triple(0.0f, x, c)
                hue < 300.0f -> Triple(x, 0.0f, c)
                else -> Triple(c, 0.0f, x)
            }

            val r = ((r1 + m) * 255).toInt().coerceIn(0, 255)
            val g = ((g1 + m) * 255).toInt().coerceIn(0, 255)
            val b = ((b1 + m) * 255).toInt().coerceIn(0, 255)

            return Color(r, g, b)
        }
    }

    // =========================================================================
    // TRAJECTORY RENDERER
    // =========================================================================

    /**
     * Rendert eine Trajektorie als 2D-Canvas.
     *
     * @param trajectory Trajektorie-Punkte
     * @param width Canvas-Breite
     * @param height Canvas-Höhe
     * @param color Linienfarbe
     * @param lineWidth Linienbreite
     * @return Canvas als 2D-Array (RGBA)
     */
    fun renderTrajectory(
        trajectory: List<CognitiveStateVector>,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT,
        color: Color = Color.GREEN,
        lineWidth: Int = 2
    ): Array<IntArray> {
        val canvas = Array(height) { IntArray(width) { Color.BLACK.toRGBA() } }

        if (trajectory.size < 2) return canvas

        // Normalisiere auf Canvas
        val normPoints = trajectory.map { state ->
            val x = (state.past / 2.0f * width).toInt().coerceIn(0, width - 1)
            val y = (state.present / 2.0f * height).toInt().coerceIn(0, height - 1)
            Pair(x, y)
        }

        // Zeichne Linien zwischen Punkten
        for (i in 1 until normPoints.size) {
            val (x0, y0) = normPoints[i - 1]
            val (x1, y1) = normPoints[i]
            drawLine(canvas, x0, y0, x1, y1, color, lineWidth)
        }

        return canvas
    }

    /**
     * Zeichnet eine Linie (Bresenham-Algorithmus).
     */
    private fun drawLine(canvas: Array<IntArray>, x0: Int, y0: Int, x1: Int, y1: Int, color: Color, width: Int) {
        var x = x0
        var y = y0
        val dx = abs(x1 - x0)
        val dy = abs(y1 - y0)
        val sx = if (x0 < x1) 1 else -1
        val sy = if (y0 < y1) 1 else -1
        var err = dx - dy

        while (true) {
            drawPixel(canvas, x, y, color, width)

            if (x == x1 && y == y1) break
            val e2 = 2 * err
            if (e2 > -dy) {
                err -= dy
                x += sx
            }
            if (e2 < dx) {
                err += dx
                y += sy
            }
        }
    }

    /**
     * Zeichnet ein Pixel mit Breite.
     */
    private fun drawPixel(canvas: Array<IntArray>, x: Int, y: Int, color: Color, width: Int) {
        val halfWidth = width / 2
        for (dy in -halfWidth until width - halfWidth) {
            for (dx in -halfWidth until width - halfWidth) {
                val px = x + dx
                val py = y + dy
                if (px in canvas[0].indices && py in canvas.indices) {
                    canvas[py][px] = color.toRGBA()
                }
            }
        }
    }

    // =========================================================================
    // HEATMAP GENERATOR
    // =========================================================================

    /**
     * Generiert eine Heatmap eines 2D-Feldes.
     *
     * @param fieldValues 2D-Feldwerte
     * @param width Breite
     * @param height Höhe
     * @param colorSpace Farbraum
     * @return Heatmap-Canvas
     */
    fun generateHeatmap(
        fieldValues: Array<FloatArray>,
        width: Int,
        height: Int,
        colorSpace: ColorSpace = ColorSpace.THERMODYNAMIC
    ): Array<IntArray> {
        val canvas = Array(height) { IntArray(width) }

        // Finde Min/Max für Normalisierung
        var minVal = Float.MAX_VALUE
        var maxVal = Float.MIN_VALUE
        for (y in 0 until height) {
            for (x in 0 until width) {
                val v = fieldValues[y][x]
                if (v < minVal) minVal = v
                if (v > maxVal) maxVal = v
            }
        }

        val range = if (maxVal > minVal) maxVal - minVal else 1.0f

        for (y in 0 until height) {
            for (x in 0 until width) {
                val normalized = (fieldValues[y][x] - minVal) / range
                val color = when (colorSpace) {
                    ColorSpace.THERMODYNAMIC -> ColorMapper.thermodynamic(normalized)
                    ColorSpace.GRAYSCALE -> ColorMapper.grayscale(normalized)
                    ColorSpace.HSV -> ColorMapper.hsv(normalized)
                    ColorSpace.RGB -> ColorMapper.thermodynamic(normalized)
                }
                canvas[y][x] = color.toRGBA()
            }
        }

        return canvas
    }

    // =========================================================================
    // VECTOR FIELD RENDERER
    // =========================================================================

    /**
     * Rendert ein Vektorfeld.
     *
     * @param vectorField 2D-Vektorfeld (pro Zelle: (Fx, Fy))
     * @param width Breite
     * @param height Höhe
     * @param arrowColor Pfeilfarbe
     * @param arrowLength Skalierung der Pfeillänge
     * @return Canvas
     */
    fun renderVectorField(
        vectorField: Array<Pair<Float, Float>>,
        width: Int,
        height: Int,
        arrowColor: Color = Color.WHITE,
        arrowLength: Float = 10.0f
    ): Array<IntArray> {
        val canvas = Array(height) { IntArray(width) { Color.BLACK.toRGBA() } }

        for (y in 0 until height) {
            for (x in 0 until width) {
                val index = y * width + x
                if (index < vectorField.size) {
                    val (fx, fy) = vectorField[index]
                    drawArrow(canvas, x, y, fx, fy, arrowColor, arrowLength)
                }
            }
        }

        return canvas
    }

    /**
     * Zeichnet einen Pfeil.
     */
    private fun drawArrow(canvas: Array<IntArray>, x: Int, y: Int, fx: Float, fy: Float, color: Color, length: Float) {
        val magnitude = sqrt(fx * fx + fy * fy)
        if (magnitude < 0.001f) return

        val nx = fx / magnitude
        val ny = fy / magnitude

        val endX = (x + nx * length).toInt().coerceIn(0, canvas[0].size - 1)
        val endY = (y + ny * length).toInt().coerceIn(0, canvas.size - 1)

        drawLine(canvas, x, y, endX, endY, color, 1)
    }

    // =========================================================================
    // PHASE SPACE RENDERER
    // =========================================================================

    /**
     * Rendert eine Phasenraum-Darstellung.
     *
     * @param attractors Liste von Attraktoren
     * @param trajectories Liste von Trajektorien
     * @param width Breite
     * @param height Höhe
     * @return Canvas
     */
    fun renderPhaseSpace(
        attractors: List<AttractorNode>,
        trajectories: List<List<CognitiveStateVector>>,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Array<IntArray> {
        val canvas = Array(height) { IntArray(width) { Color.BLACK.toRGBA() } }

        // Zeichne Attraktoren
        for (attractor in attractors) {
            val ax = (attractor.position.dim.getOrElse(0) { 0f } / 2.0f * width).toInt().coerceIn(0, width - 1)
            val ay = (attractor.position.dim.getOrElse(1) { 0f } / 2.0f * height).toInt().coerceIn(0, height - 1)
            drawCircle(canvas, ax, ay, 5, Color.RED)
        }

        // Zeichne Trajektorien
        val colors = listOf(Color.GREEN, Color.BLUE, Color.YELLOW, Color.CYAN, Color.MAGENTA)
        for ((index, trajectory) in trajectories.withIndex()) {
            val color = colors[index % colors.size]
            val points = trajectory.map { state ->
                val px = (state.past / 2.0f * width).toInt().coerceIn(0, width - 1)
                val py = (state.present / 2.0f * height).toInt().coerceIn(0, height - 1)
                Pair(px, py)
            }

            for (i in 1 until points.size) {
                drawLine(canvas, points[i - 1].first, points[i - 1].second, points[i].first, points[i].second, color, 2)
            }
        }

        return canvas
    }

    /**
     * Zeichnet einen Kreis.
     */
    private fun drawCircle(canvas: Array<IntArray>, cx: Int, cy: Int, radius: Int, color: Color) {
        for (dy in -radius..radius) {
            for (dx in -radius..radius) {
                if (dx * dx + dy * dy <= radius * radius) {
                    val px = cx + dx
                    val py = cy + dy
                    if (px in canvas[0].indices && py in canvas.indices) {
                        canvas[py][px] = color.toRGBA()
                    }
                }
            }
        }
    }

    // =========================================================================
    // SPECTRAL VISUALIZATION
    // =========================================================================

    /**
     * Rendert ein Spektrum als Balkendiagramm.
     *
     * @param spectrum Spektrum
     * @param width Breite
     * @param height Höhe
     * @return Canvas
     */
    fun renderSpectrum(
        spectrum: List<SpectralFieldAnalyzer.SpectralPoint>,
        width: Int = DEFAULT_WIDTH,
        height: Int = DEFAULT_HEIGHT
    ): Array<IntArray> {
        val canvas = Array(height) { IntArray(width) { Color.BLACK.toRGBA() } }

        if (spectrum.isEmpty()) return canvas

        val maxPower = spectrum.maxOfOrNull { it.power } ?: 1.0f
        val barWidth = (width / spectrum.size).coerceAtLeast(1)

        for ((index, point) in spectrum.withIndex()) {
            val barHeight = ((point.power / maxPower) * height * 0.9).toInt()
            val x = index * barWidth
            val color = ColorMapper.thermodynamic(point.power / maxPower)

            for (y in height - barHeight until height) {
                for (dx in 0 until barWidth) {
                    val px = x + dx
                    if (px < width) {
                        canvas[y][px] = color.toRGBA()
                    }
                }
            }
        }

        return canvas
    }

    // =========================================================================
    // EXPORT UTILITIES
    // =========================================================================

    /**
     * Exportiert ein Canvas als PPM-Bild (einfaches Format).
     *
     * @param canvas Canvas
     * @return PPM-String
     */
    fun exportToPPM(canvas: Array<IntArray>): String {
        val height = canvas.size
        val width = if (canvas.isNotEmpty()) canvas[0].size else 0

        val sb = StringBuilder()
        sb.appendLine("P3")
        sb.appendLine("# Generated by FieldVisualizationEngine")
        sb.appendLine("$width $height")
        sb.appendLine("255")

        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgba = canvas[y][x]
                val r = rgba and 0xFF
                val g = (rgba shr 8) and 0xFF
                val b = (rgba shr 16) and 0xFF
                sb.append("$r $g $b ")
            }
            sb.appendLine()
        }

        return sb.toString()
    }

    /**
     * Exportiert ein Canvas als ASCII-Art.
     *
     * @param canvas Canvas
     * @return ASCII-String
     */
    fun exportToASCII(canvas: Array<IntArray>, chars: String = " .:-=+*#%@"): String {
        val height = canvas.size
        val width = if (canvas.isNotEmpty()) canvas[0].size else 0

        val sb = StringBuilder()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val rgba = canvas[y][x]
                val r = rgba and 0xFF
                val g = (rgba shr 8) and 0xFF
                val b = (rgba shr 16) and 0xFF
                val brightness = (0.299f * r + 0.587f * g + 0.114f * b) / 255.0f
                val charIndex = (brightness * (chars.length - 1)).toInt().coerceIn(0, chars.length - 1)
                sb.append(chars[charIndex])
            }
            sb.appendLine()
        }

        return sb.toString()
    }
}
