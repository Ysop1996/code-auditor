package de.lifeos.core.optical

import kotlin.math.*

object DeterministicSceneSDF {

    private fun sdSphere(p: Vector3, radius: Float) = p.length() - radius

    private fun sdBox(p: Vector3, b: Vector3): Float {
        val qx = abs(p.x) - b.x
        val qy = abs(p.y) - b.y
        val qz = abs(p.z) - b.z
        val outside = Vector3(max(0f, qx), max(0f, qy), max(0f, qz)).length()
        val inside = min(max(qx, max(qy, qz)), 0f)
        return outside + inside
    }

    private fun sdCapsule(p: Vector3, a: Vector3, b: Vector3, r: Float): Float {
        val pa = p - a
        val ba = b - a
        val h = (pa.dot(ba) / ba.dot(ba)).coerceIn(0f, 1f)
        return (pa - (ba * h)).length() - r
    }

    // Liefert (Distanz, Material-ID: 1=Boden, 2=Auto-Lack, 3=Mensch-Haut, 4=Textil)
    fun mapScene(p: Vector3, personOffset: Vector3, carOffset: Vector3): Pair<Float, Int> {
        // 1. Boden
        var dMin = p.y
        var matId = 1

        // 2. Auto (Chassis + Kabine)
        val pCar = p - carOffset
        val chassis = sdBox(pCar - Vector3(0f, 0.45f, 0f), Vector3(0.9f, 0.3f, 2.1f))
        val cabin = sdBox(pCar - Vector3(0f, 0.85f, -0.2f), Vector3(0.75f, 0.25f, 1.1f))
        val carTotal = min(chassis, cabin)
        if (carTotal < dMin) {
            dMin = carTotal
            matId = 2
        }

        // 3. Mensch (Kopf + Torso + Beine)
        val pPerson = p - personOffset
        val head = sdSphere(pPerson - Vector3(0f, 1.70f, 0f), 0.12f)
        val torso = sdCapsule(pPerson, Vector3(0f, 0.9f, 0f), Vector3(0f, 1.55f, 0f), 0.22f)
        val legs = min(
            sdCapsule(pPerson, Vector3(-0.1f, 0.0f, 0f), Vector3(-0.1f, 0.9f, 0f), 0.09f),
            sdCapsule(pPerson, Vector3(0.1f, 0.0f, 0f), Vector3(0.1f, 0.9f, 0f), 0.09f)
        )
        val personTotal = min(head, min(torso, legs))
        if (personTotal < dMin) {
            dMin = personTotal
            matId = if (personTotal == head) 3 else 4
        }

        return Pair(dMin, matId)
    }

    fun calcNormal(p: Vector3, personPos: Vector3, carPos: Vector3): Vector3 {
        val eps = 0.001f
        val d = mapScene(p, personPos, carPos).first
        val nx = mapScene(Vector3(p.x + eps, p.y, p.z), personPos, carPos).first - d
        val ny = mapScene(Vector3(p.x, p.y + eps, p.z), personPos, carPos).first - d
        val nz = mapScene(Vector3(p.x, p.y, p.z + eps), personPos, carPos).first - d
        return Vector3(nx, ny, nz).normalize()
    }
}
