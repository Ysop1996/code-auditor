package de.lifeos.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lifeos.android.telemetry.BehaviorMetrics
import de.lifeos.core.field.AttractorNode

// MMSI V3.8 Color Palette
val DarkBackground = Color(0xFF0A0E14)
val CardBackground = Color(0xFF141E2C)
val AccentCyan = Color(0xFF00E5FF)
val AccentGreen = Color(0xFF00E676)
val AccentYellow = Color(0xFFFFD600)
val AccentRed = Color(0xFFFF1744)
val AccentPurple = Color(0xFFAA00FF)
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFF90A4AE)

enum class DashboardTab {
    STATUS, CLOUD, VAULT, CHAT, BROWSER, CALENDAR, DOCUMENTS
}

@Composable
fun LifeOSMainScreen(
    metrics: BehaviorMetrics,
    uiMorphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState,
    activeAttractors: List<AttractorNode>,
    unifiedState: de.lifeos.core.field.FieldDynamicsIntegrator.UnifiedFieldState? = null,
    homoeostasisResult: de.lifeos.core.field.HomoeostasisRegulator.HomoeostasisResult? = null,
    interventions: List<de.lifeos.core.field.HomoeostasisRegulator.Intervention> = emptyList(),
    homoeostasisScore: Float = 1.0f,
    onExecuteAction: (AttractorNode) -> Unit,
    onNavigateToChat: () -> Unit,
    onNavigateToBrowser: () -> Unit,
    vaultDb: net.sqlcipher.database.SQLiteDatabase? = null
) {
    var selectedTab by remember { mutableStateOf(DashboardTab.STATUS) }

    // VRR-gated recomposition: only recompose when morph state changes
    val morphState by rememberUpdatedState(uiMorphState)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                when (morphState) {
                    de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> Color(0xFF05080C)
                    de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> Color(0xFF0A0E14)
                    else -> DarkBackground
                }
            )
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header with morph-state-aware styling
            LifeOSHeader(metrics = metrics, morphState = morphState)

            // Content based on selected tab and morph state
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    DashboardTab.STATUS -> StatusDashboard(
                        metrics = metrics,
                        activeAttractors = activeAttractors,
                        onExecuteAction = onExecuteAction,
                        morphState = morphState,
                        unifiedState = unifiedState,
                        homoeostasisResult = homoeostasisResult,
                        interventions = interventions,
                        homoeostasisScore = homoeostasisScore
                    )
                    DashboardTab.CLOUD -> CloudDiscoveryDashboard(morphState = morphState)
                    DashboardTab.VAULT -> VaultDashboard(morphState = morphState)
                    DashboardTab.CHAT -> {
                        onNavigateToChat()
                        selectedTab = DashboardTab.STATUS
                    }
                    DashboardTab.BROWSER -> {
                        onNavigateToBrowser()
                        selectedTab = DashboardTab.STATUS
                    }
                    DashboardTab.CALENDAR -> CalendarScreen(
                        vaultDb = vaultDb,
                        morphState = morphState
                    )
                    DashboardTab.DOCUMENTS -> DocumentViewerScreen(
                        vaultDb = vaultDb,
                        morphState = morphState
                    )
                }
            }

            // Morph-state-aware bottom navigation
            BottomNavigationBar(
                selectedTab = selectedTab,
                onTabSelected = { selectedTab = it },
                morphState = morphState
            )
        }
    }
}

@Composable
fun LifeOSHeader(metrics: BehaviorMetrics, morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState) {
    val indicatorColor by animateColorAsState(
        targetValue = when {
            metrics.isSeinsmodus -> AccentGreen
            metrics.frictionW > 2.0 -> AccentRed
            else -> AccentYellow
        },
        animationSpec = tween(durationMillis = when (morphState) {
            de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 100
            de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 800
            else -> 400
        }),
        label = "HeaderColor"
    )

    val headerAlpha by animateFloatAsState(
        targetValue = when (morphState) {
            de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 0.6f
            de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 1.0f
            else -> 0.85f
        },
        animationSpec = tween(durationMillis = 300),
        label = "HeaderAlpha"
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF141E2C), DarkBackground)
                )
            )
            .padding(horizontal = 20.dp, vertical = 16.dp)
            .alpha(headerAlpha)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "LIFE-OS",
                    color = AccentCyan,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 2.sp
                )
                Text(
                    text = "MMSI V3.8 HARDENED",
                    color = TextSecondary,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (metrics.isSeinsmodus) "SEINSMODUS" else "AKTIV",
                        color = indicatorColor,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = "W(t): ${"%.3f".format(metrics.frictionW)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun StatusDashboard(
    metrics: BehaviorMetrics,
    activeAttractors: List<AttractorNode>,
    onExecuteAction: (AttractorNode) -> Unit,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState,
    unifiedState: de.lifeos.core.field.FieldDynamicsIntegrator.UnifiedFieldState? = null,
    homoeostasisResult: de.lifeos.core.field.HomoeostasisRegulator.HomoeostasisResult? = null,
    interventions: List<de.lifeos.core.field.HomoeostasisRegulator.Intervention> = emptyList(),
    homoeostasisScore: Float = 1.0f
) {
    val isSeinsmodus = morphState == de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus
    val itemSpacing = when (morphState) {
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 8.dp
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 16.dp
        else -> 12.dp
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(itemSpacing)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Metrics Cards — reduced visibility in Seinsmodus but still present
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "REIBUNG",
                    value = "%.3f".format(metrics.frictionW),
                    color = if (isSeinsmodus) AccentGreen else AccentCyan,
                    modifier = Modifier.weight(1f),
                    morphState = morphState,
                    alpha = if (isSeinsmodus) 0.6f else 1.0f
                )
                MetricCard(
                    title = "STAUDRUCK",
                    value = "%.3f".format(metrics.backpressureRho),
                    color = if (isSeinsmodus) AccentGreen else AccentYellow,
                    modifier = Modifier.weight(1f),
                    morphState = morphState,
                    alpha = if (isSeinsmodus) 0.6f else 1.0f
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MetricCard(
                    title = "KNOTEN",
                    value = "${activeAttractors.size}",
                    color = AccentGreen,
                    modifier = Modifier.weight(1f),
                    morphState = morphState,
                    alpha = if (isSeinsmodus) 0.6f else 1.0f
                )
                MetricCard(
                    title = "MASSE",
                    value = "%.1f".format(activeAttractors.sumOf { it.mass.toDouble() }),
                    color = AccentPurple,
                    modifier = Modifier.weight(1f),
                    morphState = morphState,
                    alpha = if (isSeinsmodus) 0.6f else 1.0f
                )
            }
        }

        // Field Dynamics Section — always visible when data exists
        if (unifiedState != null) {
            item {
                Text(
                    text = "FELD-DYNAMIK",
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "W(t)",
                        value = "%.3f".format(unifiedState.load),
                        color = if (unifiedState.load > 1.0f) AccentRed else AccentCyan,
                        modifier = Modifier.weight(1f),
                        morphState = morphState,
                        alpha = if (isSeinsmodus) 0.6f else 1.0f
                    )
                    MetricCard(
                        title = "Ω(t)",
                        value = "%.0f".format(unifiedState.omega),
                        color = if (unifiedState.omega > 5000f) AccentRed else AccentYellow,
                        modifier = Modifier.weight(1f),
                        morphState = morphState,
                        alpha = if (isSeinsmodus) 0.6f else 1.0f
                    )
                }
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    MetricCard(
                        title = "S_o(t)",
                        value = "%.3f".format(unifiedState.sovereignty),
                        color = if (unifiedState.sovereignty > 1.0f) AccentGreen else AccentPurple,
                        modifier = Modifier.weight(1f),
                        morphState = morphState,
                        alpha = if (isSeinsmodus) 0.6f else 1.0f
                    )
                    MetricCard(
                        title = "HOMÖO",
                        value = "%.0f%%".format(homoeostasisScore * 100),
                        color = if (homoeostasisScore > 0.8f) AccentGreen else AccentYellow,
                        modifier = Modifier.weight(1f),
                        morphState = morphState,
                        alpha = if (isSeinsmodus) 0.6f else 1.0f
                    )
                }
            }

            // Interventions
            if (interventions.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "INTERVENTIONEN",
                        color = AccentRed,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                interventions.take(3).forEach { intervention ->
                    item {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = when (intervention.type) {
                                    de.lifeos.core.field.HomoeostasisRegulator.InterventionType.WARNING -> AccentYellow.copy(alpha = 0.1f)
                                    de.lifeos.core.field.HomoeostasisRegulator.InterventionType.ESCALATION -> AccentRed.copy(alpha = 0.1f)
                                    de.lifeos.core.field.HomoeostasisRegulator.InterventionType.COLLAPSE -> AccentRed.copy(alpha = 0.2f)
                                }
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = intervention.title,
                                    color = TextPrimary,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = intervention.description,
                                    color = TextSecondary,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Trajectory Section
        item {
            Text(
                text = "HANDLUNGSTRAJEKTORIEN",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        if (activeAttractors.isEmpty()) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = CardBackground)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Icon(
                            imageVector = Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = AccentGreen,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "System initialisiert. Keine aktiven Reibungspunkte.",
                            color = TextSecondary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        } else {
            items(activeAttractors) { node ->
                AttractorActionCard(node = node, onClick = { onExecuteAction(node) }, morphState = morphState)
            }
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun CloudDiscoveryDashboard(morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState) {
    val showDetails = morphState != de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = "CLOUD BACKUP DISCOVERY",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Google Accounts
        item {
            DiscoveryCard(
                title = "GOOGLE ACCOUNTS",
                count = 10,
                icon = Icons.Filled.AccountCircle,
                color = AccentCyan,
                items = listOf(
                    "patrickbuckreus@gmail.com",
                    "b12505807@gmail.com",
                    "flo899576@gmail.com",
                    "buckreus2@gmail.com",
                    "flopat665@gmail.com"
                ),
                expanded = showDetails
            )
        }

        // Takeout Backups
        item {
            DiscoveryCard(
                title = "TAKEOUT BACKUPS",
                count = 19,
                icon = Icons.Filled.List,
                color = AccentGreen,
                items = listOf(
                    "takeout-20260721T102628Z (461MB)",
                    "takeout-20260721T102816Z (421MB)",
                    "takeout-20251125T070210Z (1.2MB)"
                ),
                expanded = showDetails
            )
        }

        // Password Files
        item {
            DiscoveryCard(
                title = "PASSWORD EXPORTS",
                count = 2,
                icon = Icons.Filled.Lock,
                color = AccentYellow,
                items = listOf(
                    "Google Passwords.csv (54KB)",
                    "Google Passwords (1).csv (206KB)"
                ),
                expanded = showDetails
            )
        }

        // WhatsApp
        item {
            DiscoveryCard(
                title = "WHATSAPP DATA",
                count = 1,
                icon = Icons.Filled.Email,
                color = AccentPurple,
                items = listOf(
                    "msgstore.db.crypt14 (268MB)",
                    "Media (1.4GB)"
                ),
                expanded = showDetails
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun VaultDashboard(morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState) {
    val showDetails = morphState != de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        item {
            Text(
                text = "BLACKBOX VAULT STATUS",
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace
            )
        }

        // Vault Info Cards
        item {
            VaultInfoCard(
                title = "VAULT DATEI",
                value = "lifeos_blackbox.db",
                subtitle = "80KB verschlüsselt (SQLCipher AES-256)",
                morphState = morphState
            )
        }

        item {
            VaultInfoCard(
                title = "SPEICHERORT",
                value = "/sdcard/Android/data/de.lifeos.android/files/.lifeos_vault/",
                subtitle = "Persistent - übersteht Neuinstallation",
                morphState = morphState
            )
        }

        item {
            VaultInfoCard(
                title = "VERSCHLÜSSELUNG",
                value = "AES-256-GCM",
                subtitle = "Hardware-bound KDF (256000 iterations)",
                morphState = morphState
            )
        }

        item {
            VaultInfoCard(
                title = "TABELLEN",
                value = "12",
                subtitle = "semantic_nodes, cloud_accounts, cloud_backups, password_vault, github_data...",
                morphState = morphState
            )
        }

        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    color: Color,
    modifier: Modifier = Modifier,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState = de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Homoeostase,
    alpha: Float = 1.0f
) {
    val cardHeight = when (morphState) {
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 60.dp
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 100.dp
        else -> 80.dp
    }

    Card(
        modifier = modifier.height(cardHeight).alpha(alpha),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Text(
                text = value,
                color = color,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun DiscoveryCard(
    title: String,
    count: Int,
    icon: ImageVector,
    color: Color,
    items: List<String>,
    expanded: Boolean = false
) {
    var isExpanded by remember { mutableStateOf(expanded) }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { isExpanded = !isExpanded },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = title,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Box(
                    modifier = Modifier
                        .background(color.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = "$count",
                        color = color,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            AnimatedVisibility(visible = isExpanded) {
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    items.forEach { item ->
                        Text(
                            text = "• $item",
                            color = TextSecondary,
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun VaultInfoCard(
    title: String,
    value: String,
    subtitle: String,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState = de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Homoeostase
) {
    val showSubtitle = morphState != de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = title,
                color = TextSecondary,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = value,
                color = AccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            if (showSubtitle) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = subtitle,
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}

@Composable
fun EmptyStateCard(
    message: String,
    icon: ImageVector,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState = de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Homoeostase
) {
    val iconSize = when (morphState) {
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 20.dp
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 32.dp
        else -> 24.dp
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = AccentGreen,
                modifier = Modifier.size(iconSize)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = message,
                color = TextSecondary,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun BottomNavigationBar(
    selectedTab: DashboardTab,
    onTabSelected: (DashboardTab) -> Unit,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState = de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Homoeostase
) {
    // In Seinsmodus: nur essentielle Tabs anzeigen
    val visibleTabs = when (morphState) {
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus ->
            listOf(DashboardTab.STATUS, DashboardTab.CHAT)
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.LegalFocus ->
            listOf(DashboardTab.STATUS, DashboardTab.VAULT, DashboardTab.CHAT)
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.FinancialFocus ->
            listOf(DashboardTab.STATUS, DashboardTab.CLOUD, DashboardTab.CHAT)
        else -> DashboardTab.entries
    }

    val tabs = listOf(
        Triple(DashboardTab.STATUS, Icons.Filled.Home, "STATUS"),
        Triple(DashboardTab.CLOUD, Icons.Filled.List, "CLOUD"),
        Triple(DashboardTab.VAULT, Icons.Filled.Lock, "VAULT"),
        Triple(DashboardTab.CALENDAR, Icons.Filled.Event, "KALENDER"),
        Triple(DashboardTab.DOCUMENTS, Icons.Filled.Description, "DOKS"),
        Triple(DashboardTab.CHAT, Icons.Filled.Email, "CHAT"),
        Triple(DashboardTab.BROWSER, Icons.Filled.AccountCircle, "PORTAL")
    ).filter { it.first in visibleTabs }

    val navHeight = when (morphState) {
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus -> 56.dp
        de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch -> 80.dp
        else -> 72.dp
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF141E2C))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            tabs.forEach { (tab, icon, label) ->
                val isSelected = selectedTab == tab
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .clickable { onTabSelected(tab) }
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        tint = if (isSelected) AccentCyan else TextSecondary,
                        modifier = Modifier.size(if (morphState == de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Kritisch) 28.dp else 24.dp)
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = label,
                        color = if (isSelected) AccentCyan else TextSecondary,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}

@Composable
fun AttractorActionCard(
    node: AttractorNode,
    onClick: () -> Unit,
    morphState: de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState = de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Homoeostase
) {
    val showFullDetails = morphState != de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer.UiMorphState.Seinsmodus

    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = node.id,
                    color = AccentCyan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "M: ${"%.2f".format(node.mass)}",
                    color = TextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            if (showFullDetails) {
                Text(
                    text = node.payload,
                    color = TextSecondary,
                    fontSize = 12.sp,
                    maxLines = 3
                )
                Spacer(modifier = Modifier.height(12.dp))
            }
            Box(
                modifier = Modifier
                    .background(AccentGreen.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text(
                    text = "1-TAP FREIGABE [F = -∇U]",
                    color = AccentGreen,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }
}