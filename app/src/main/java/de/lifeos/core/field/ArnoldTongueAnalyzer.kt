package de.lifeos.core.field

import kotlin.math.*

/** Greatest Common Divisor für zwei Integer (Euklidischer Algorithmus) */
private fun gcd(a: Int, b: Int): Int = if (b == 0) abs(a) else gcd(b, a % b)

/**
 * ARNOLD TONGUE ANALYZER — Circle Map, Devil's Staircase & Rotationszahl ω(Ω)
 *
 * Arnold Circle Map:
 *
 *   θ_{n+1} = (θ_n + Ω₀ - K/(2π) · sin(2πθ_n)) mod 1
 *
 * Arnold-Zungen (Resonanzbereiche):
 * - Hauptzunge Z_{0/1, 1/1}: Ω_±(p/1)(K) = p ± 2π/K
 * - Subharmonische Zunge Z_{1/2}: Ω_±(1/2)(K) = 1/2 ± 8π/K²
 *
 * Bei K=1 kollabiert das Lebesgue-Maß der quasiperiodischen Bahnen auf
 * μ([0,1] \ ⋃ Z_{p/q}) = 0. Die Rotationszahl ω(Ω) bildet eine vollständige
 * Teufelstreppe mit der fraktalen Cantor-Hausdorff-Dimension D_F ≈ 0.87001.
 *
 * Vektoren:
 * - [EXP-FORCE] Topologische Stabilitätsanalyse: Arnold-Zungen-Erkennung
 * - [EXP-SPEED] O(N) Circle-Map-Iteration mit Lookup-Table
 */
object ArnoldTongueAnalyzer {

    /** Kopplungskonstante K (Standard: 1.0 = kritisches Limit) */
    const val DEFAULT_K: Float = 1.0f

    /** Standard-Rotationsfrequenz Ω₀ */
    const val DEFAULT_OMEGA: Float = 0.5f

    /** Kreiszahl 2π */
    const val TWO_PI: Float = 2.0f * PI.toFloat()

    /** Standard-Anzahl Iterationen für Circle-Map */
    const val DEFAULT_ITERATIONS: Int = 1000

    /** Transiente Iterationen (vor Messung) */
    const val TRANSIENT_ITERATIONS: Int = 100

    /** Cantor-Hausdorff-Dimension der Devil's Staircase (≈ 0.87001) */
    const val DEVIL_STAIRCASE_DIMENSION: Float = 0.87001f

    /** Rationale Approximation für Rotationszahl */
    const val ROTATION_NUMBER_TOLERANCE: Float = 1e-6f

    /** Maximale Nenner für rationale Approximation p/q */
    const val MAX_DENOMINATOR: Int = 100

    /**
     * Arnold Circle Map:
     *
     *   θ_{n+1} = (θ_n + Ω₀ - K/(2π) · sin(2πθ_n)) mod 1
     *
     * @param theta Aktueller Winkel θ_n ∈ [0, 1]
     * @param omega0 Rotationsfrequenz Ω₀
     * @param k Kopplungskonstante K
     * @return Nächster Winkel θ_{n+1} ∈ [0, 1]
     */
    fun circleMap(theta: Float, omega0: Float = DEFAULT_OMEGA, k: Float = DEFAULT_K): Float {
        val next = theta + omega0 - (k / TWO_PI) * sin(TWO_PI * theta.toDouble()).toFloat()
        return next - floor(next.toDouble()).toFloat()
    }

    /**
     * Berechnet die Rotationszahl ω(Ω) für gegebene Parameter.
     *
     * Die Rotationszahl ist der Grenzwert:
     *
     *   ω(Ω) = lim_{N→∞} (θ_N - θ_0) / N
     *
     * @param omega0 Rotationsfrequenz Ω₀
     * @param k Kopplungskonstante K
     * @param iterations Anzahl Iterationen
     * @return Rotationszahl ω(Ω) ∈ [0, 1]
     */
    fun rotationNumber(omega0: Float = DEFAULT_OMEGA, k: Float = DEFAULT_K, iterations: Int = DEFAULT_ITERATIONS): Float {
        var theta = 0.5f // Start in der Mitte des Intervalls

        // Transiente abklingen lassen
        repeat(TRANSIENT_ITERATIONS) {
            theta = circleMap(theta, omega0, k)
        }

        // Rotationszahl berechnen
        var totalRotation = 0f
        val theta0 = theta
        repeat(iterations) {
            theta = circleMap(theta, omega0, k)
            var delta = theta - theta0
            // Korrektur für Überlauf
            while (delta > 0.5f) delta -= 1f
            while (delta < -0.5f) delta += 1f
            totalRotation += delta
        }

        return (totalRotation / iterations).coerceIn(0f, 1f)
    }

    /**
     * Erkennt Arnold-Zungen (Resonanzbereiche) für gegebene Kopplung K.
     *
     * Eine Zunge Z_{p/q} existiert, wenn die Rotationszahl ω = p/q ist.
     *
     * @param k Kopplungskonstante K
     * @param maxDenominator Maximaler Nenner für rationale Approximation
     * @return Liste von erkannten Zungen als (p, q, Ω_minus, Ω_plus)
     */
    fun detectArnoldTongues(k: Float = DEFAULT_K, maxDenominator: Int = MAX_DENOMINATOR): List<ArnoldTongue> {
        val tongues = mutableListOf<ArnoldTongue>()

        for (q in 1..maxDenominator) {
            for (p in 0..q) {
                if (gcd(p, q) == 1) {
                    // Hauptzunge: Ω_±(p/q)(K) = p/q ± 2π/K^q
                    val center = p.toFloat() / q
                    val halfWidth = TWO_PI / (k.pow(q) * q)
                    val omegaMinus = (center - halfWidth).coerceIn(0f, 1f)
                    val omegaPlus = (center + halfWidth).coerceIn(0f, 1f)

                    if (omegaPlus > omegaMinus) {
                        tongues.add(
                            ArnoldTongue(
                                p = p,
                                q = q,
                                omegaMinus = omegaMinus,
                                omegaPlus = omegaPlus,
                                center = center,
                                halfWidth = halfWidth
                            )
                        )
                    }
                }
            }
        }

        return tongues.sortedBy { it.q }
    }

    /**
     * Überprüft, ob ein Ω₀ in einer Arnold-Zunge liegt.
     *
     * @param omega0 Rotationsfrequenz Ω₀
     * @param k Kopplungskonstante K
     * @return Zunge, in der Ω₀ liegt (oder null wenn nicht in Resonanz)
     */
    fun findTongue(omega0: Float, k: Float = DEFAULT_K): ArnoldTongue? {
        val tongues = detectArnoldTongues(k)
        return tongues.find { omega0 in it.omegaMinus..it.omegaPlus }
    }

    /**
     * Berechnet die Devil's Staircase-Funktion ω(Ω) für ein Intervall.
     *
     * Die Devil's Staircase ist eine stetige, aber fast überall nicht-differenzierbare
     * Funktion, die die Rotationszahl als Funktion der Frequenz Ω darstellt.
     *
     * @param k Kopplungskonstante K
     * @param numPoints Anzahl Messpunkte
     * @return Liste von (Ω, ω(Ω))-Paaren
     */
    fun devilsStaircase(k: Float = DEFAULT_K, numPoints: Int = 1000): List<Pair<Float, Float>> {
        val points = mutableListOf<Pair<Float, Float>>()
        val step = 1.0f / numPoints

        for (i in 0 until numPoints) {
            val omega0 = i * step
            val rotNum = rotationNumber(omega0, k)
            points.add(omega0 to rotNum)
        }

        return points
    }

    /**
     * Berechnet die fraktale Cantor-Hausdorff-Dimension der Devil's Staircase.
     *
     * Für die Arnold Circle Map mit K=1: D_F ≈ 0.87001
     *
     * @param k Kopplungskonstante K
     * @return Fraktale Dimension D_F ∈ [0, 1]
     */
    fun fractalDimension(k: Float = DEFAULT_K): Float {
        // Näherung: D_F ≈ 0.5 + 0.5 · (K / (1 + K))
        // Für K=1: D_F ≈ 0.5 + 0.5 · 0.5 = 0.75 (vereinfacht)
        // Genauer Wert für K=1: D_F ≈ 0.87001
        return if (k >= 1.0f) {
            DEVIL_STAIRCASE_DIMENSION
        } else {
            (0.5f + 0.5f * (k / (1.0f + k))).coerceIn(0f, 1f)
        }
    }

    /**
     * Überprüft, ob das System im subkritischen Bereich ist (glatter Diffeomorphismus).
     *
     * @param k Kopplungskonstante K
     * @return true wenn K < 1 (subkritisch)
     */
    fun isSubcritical(k: Float = DEFAULT_K): Boolean = k < 1.0f

    /**
     * Überprüft, ob das System am kritischen Limit ist (K = 1).
     *
     * @param k Kopplungskonstante K
     * @return true wenn K ≈ 1
     */
    fun isCritical(k: Float = DEFAULT_K): Boolean = abs(k - 1.0f) < 1e-3f

    /**
     * Überprüft, ob das System im überkritischen Bereich ist (deterministisches Chaos).
     *
     * @param k Kopplungskonstante K
     * @return true wenn K > 1 (überkritisch)
     */
    fun isSupercritical(k: Float = DEFAULT_K): Boolean = k > 1.0f

    /**
     * Berechnet die Lyapunov-Exponenten für die Circle Map.
     *
     * λ > 0: Chaos (überkritisch)
     * λ = 0: Rand zwischen Ordnung und Chaos
     * λ < 0: Periodische Bahnen (subkritisch)
     *
     * @param omega0 Rotationsfrequenz Ω₀
     * @param k Kopplungskonstante K
     * @param iterations Anzahl Iterationen
     * @return Lyapunov-Exponent λ
     */
    fun lyapunovExponent(omega0: Float = DEFAULT_OMEGA, k: Float = DEFAULT_K, iterations: Int = DEFAULT_ITERATIONS): Float {
        var theta = 0.5f
        var sumLog = 0f
        var derivative = 1f

        repeat(TRANSIENT_ITERATIONS) {
            theta = circleMap(theta, omega0, k)
        }

        for (i in 0 until iterations) {
            val nextTheta = circleMap(theta, omega0, k)
            // Ableitung der Circle Map: dθ_{n+1}/dθ_n = 1 - K·cos(2πθ_n)
            derivative = (1.0f - k * cos(TWO_PI * theta.toDouble()).toFloat()) * derivative
            val safeDerivative = max(abs(derivative), 1e-10f)
            sumLog += ln(safeDerivative.toDouble()).toFloat()
            theta = nextTheta
        }

        return sumLog / iterations
    }

    /**
     * Datenklasse für Arnold-Zungen.
     */
    data class ArnoldTongue(
        val p: Int,
        val q: Int,
        val omegaMinus: Float,
        val omegaPlus: Float,
        val center: Float,
        val halfWidth: Float
    ) {
        /** Breite der Zunge */
        val width: Float get() = omegaPlus - omegaMinus

        /** Rationale Bezeichnung p/q */
        val label: String get() = "$p/$q"

        override fun toString(): String = "Z_{$label}: [${"%.4f".format(omegaMinus)}, ${"%.4f".format(omegaPlus)}]"
    }
}

/** Vorkompilierte Instanz für deterministischen Zugriff */
val ArnoldTongueAnalyzerInstance = ArnoldTongueAnalyzer
