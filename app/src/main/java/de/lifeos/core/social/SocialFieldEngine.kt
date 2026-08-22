package de.lifeos.core.social

import com.mmsi.neuro.engine.core.MmsiCoreEngineV38
import com.mmsi.neuro.engine.core.MmsiFrameOutput
import de.lifeos.core.field.AttractorNode
import de.lifeos.core.field.DeterministicFieldEngine
import de.lifeos.core.field.PhaseVector

data class SocialHealthReport(
    val frictionW: Double,
    val unresolvedMattersCount: Int,
    val urgentFollowUps: List<String>
)

class SocialFieldEngine(
    private val fieldEngine: DeterministicFieldEngine
) {
    private val mmsiEngine = MmsiCoreEngineV38()
    private val frameOutput = MmsiFrameOutput()

    fun evaluateSocialFriction(
        totalContacts: Int,
        unresolvedMessagesCount: Int,
        missedCallsCount: Int,
        vipBusinessInquiries: Int
    ): SocialHealthReport {
        val yLoad = (unresolvedMessagesCount * 1.5) + (missedCallsCount * 3.0) + (vipBusinessInquiries * 5.0)
        val zDamping = (totalContacts * 0.2).coerceIn(5.0, 25.0)

        mmsiEngine.processFrameInPlace(
            af7Alpha = zDamping,
            af8Alpha = zDamping,
            betaHigh = yLoad.coerceIn(1.0, 45.0),
            thetaPost = missedCallsCount * 2.5,
            age = 30.0,
            sex = "M",
            deltaF7 = 10.0,
            deltaF8 = 10.0,
            out = frameOutput
        )

        val actions = mutableListOf<String>()
        if (vipBusinessInquiries > 0) actions.add("Dringende Kundenanfrage ($vipBusinessInquiries offen)")
        if (missedCallsCount > 0) actions.add("Verpasste Anrufe ($missedCallsCount) -> Rückruf priorisieren")

        if (frameOutput.wBounded > 1.0) {
            fieldEngine.registerNode(
                AttractorNode(
                    id = "COMMUNICATION_BACKPRESSURE",
                    payload = "Offene Punkte: $unresolvedMessagesCount Nachrichten, $missedCallsCount Anrufe",
                    position = PhaseVector(FloatArray(32) { 0.25f }),
                    mass = 2.8f,
                    isTerminal = false
                )
            )
        }

        return SocialHealthReport(
            frictionW = frameOutput.wBounded,
            unresolvedMattersCount = unresolvedMessagesCount + missedCallsCount,
            urgentFollowUps = actions
        )
    }
}
