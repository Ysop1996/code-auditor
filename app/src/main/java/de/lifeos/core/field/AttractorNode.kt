package de.lifeos.core.field

data class AttractorNode(
    val id: String,
    val payload: String,
    var position: PhaseVector,
    var mass: Float = 1.0f,
    val isTerminal: Boolean = false
)
