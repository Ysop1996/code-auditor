package de.lifeos.android.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lifeos.android.telemetry.BehaviorMetrics
import de.lifeos.core.field.AttractorNode

@Composable
fun WeltformelDashboard(
    metrics: BehaviorMetrics,
    activeAttractors: List<AttractorNode>,
    onExecuteAction: (AttractorNode) -> Unit
) {
    val backgroundColor = if (metrics.isSeinsmodus) Color(0xFF070B0E) else Color(0xFF140A0A)
    val indicatorColor by animateColorAsState(
        targetValue = when {
            metrics.isSeinsmodus -> Color(0xFF00E676)
            metrics.frictionW > 2.0 -> Color(0xFFFF1744)
            else -> Color(0xFFFFD600)
        },
        animationSpec = tween(durationMillis = 400),
        label = "FieldColor"
    )

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIFE-OS // MMSI V3.8",
                    color = Color.White.copy(alpha = 0.6f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = if (metrics.isSeinsmodus) "STATUS: SEINSMODUS" else "STATUS: REIBUNGSREDUKTION",
                    color = indicatorColor,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = "W(t): ${"%.3f".format(metrics.frictionW)}",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "ρ(t): ${"%.3f".format(metrics.backpressureRho)}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(60.dp)
                .background(Color.Black.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
        ) {
            val width = size.width
            val height = size.height
            val midY = height / 2

            drawLine(
                color = Color.DarkGray,
                start = Offset(0f, midY),
                end = Offset(width, midY),
                strokeWidth = 1f
            )

            val amplitude = (metrics.frictionW.toFloat() * 12f).coerceAtMost(midY - 4)
            drawCircle(
                color = indicatorColor,
                radius = 6f,
                center = Offset(width * 0.5f, midY - amplitude)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "DURCHSETZUNGS- & HANDLUNGSTRAJEKTORIEN",
            color = Color.White.copy(alpha = 0.8f),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (activeAttractors.isEmpty()) {
                item {
                    Text(
                        text = "Keine Reibungspunkte aktiv. System konvergiert stabil.",
                        color = Color.Gray,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                }
            } else {
                items(activeAttractors) { node ->
                    AttractorActionCard(node = node, onClick = { onExecuteAction(node) })
                }
            }
        }
    }
}
