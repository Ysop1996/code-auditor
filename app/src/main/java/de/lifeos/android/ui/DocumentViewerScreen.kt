package de.lifeos.android.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.annotation.RequiresApi
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lifeos.core.workshop.AdaptiveWorkshopSynthesizer
import net.sqlcipher.database.SQLiteDatabase
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

// MMSI V3.8 Document Viewer Module
// Deterministic document viewer for generated documents, frontend previews, and coding outputs

data class DocumentEntry(
    val docId: String,
    val title: String,
    val docType: String,
    val contentUri: String?,
    val contentText: String?,
    val mimeType: String?,
    val fileSize: Long,
    val sourceEngine: String?,
    val metadata: String?,
    val createdAt: Long
) {
    val formattedDate: String
        get() {
            val date = Instant.ofEpochMilli(createdAt).atZone(ZoneId.systemDefault()).toLocalDateTime()
            return date.format(DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", Locale.GERMAN))
        }
    
    val formattedSize: String
        get() {
            return when {
                fileSize < 1024 -> "$fileSize B"
                fileSize < 1024 * 1024 -> "${fileSize / 1024} KB"
                else -> "${fileSize / (1024 * 1024)} MB"
            }
        }
    
    val docTypeLabel: String
        get() = when (docType) {
            "GENERATED" -> "Generiert"
            "FRONTEND" -> "Frontend"
            "CODE" -> "Code"
            "REPORT" -> "Bericht"
            "LEGAL" -> "Rechtsdokument"
            "FINANCE" -> "Finanzdokument"
            else -> docType
        }
}

@Composable
fun DocumentViewerScreen(
    vaultDb: SQLiteDatabase?,
    morphState: AdaptiveWorkshopSynthesizer.UiMorphState = AdaptiveWorkshopSynthesizer.UiMorphState.Default
) {
    var documents by remember { mutableStateOf<List<DocumentEntry>>(emptyList()) }
    var selectedDocument by remember { mutableStateOf<DocumentEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var filterType by remember { mutableStateOf<String?>(null) }

    // Load documents
    LaunchedEffect(Unit) {
        vaultDb?.let { db ->
            documents = loadDocuments(db)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(8.dp))

        // Header
        Text(
            text = "DOKUMENTE & FRONTEND",
            color = TextPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Search and Filter
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Suche...", color = TextSecondary, fontFamily = FontFamily.Monospace) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = TextSecondary) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    focusedBorderColor = AccentCyan,
                    unfocusedBorderColor = TextSecondary
                ),
                modifier = Modifier.weight(1f)
            )
            
            var showFilterMenu by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { showFilterMenu = true }) {
                    Icon(Icons.Filled.Search, contentDescription = "Filter", tint = AccentCyan)
                }
                DropdownMenu(
                    expanded = showFilterMenu,
                    onDismissRequest = { showFilterMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Alle", fontFamily = FontFamily.Monospace) },
                        onClick = { filterType = null; showFilterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Generiert", fontFamily = FontFamily.Monospace) },
                        onClick = { filterType = "GENERATED"; showFilterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Frontend", fontFamily = FontFamily.Monospace) },
                        onClick = { filterType = "FRONTEND"; showFilterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Code", fontFamily = FontFamily.Monospace) },
                        onClick = { filterType = "CODE"; showFilterMenu = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Berichte", fontFamily = FontFamily.Monospace) },
                        onClick = { filterType = "REPORT"; showFilterMenu = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Filter chips
        if (filterType != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = { filterType = null },
                    label = { 
                        Text("Filter: ${when(filterType) { "GENERATED" -> "Generiert"; "FRONTEND" -> "Frontend"; "CODE" -> "Code"; "REPORT" -> "Bericht"; else -> filterType }}", 
                        fontFamily = FontFamily.Monospace, fontSize = 11.sp) 
                    },
                    trailingIcon = { Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    colors = AssistChipDefaults.assistChipColors(containerColor = AccentCyan.copy(alpha = 0.2f))
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Document List
        val filteredDocuments = documents.filter { doc ->
            val matchesSearch = searchQuery.isEmpty() || 
                doc.title.contains(searchQuery, ignoreCase = true) ||
                doc.contentText?.contains(searchQuery, ignoreCase = true) == true
            val matchesFilter = filterType == null || doc.docType == filterType
            matchesSearch && matchesFilter
        }

        if (filteredDocuments.isEmpty()) {
            EmptyStateCard(
                message = if (documents.isEmpty()) "Keine Dokumente vorhanden." else "Keine Dokumente gefunden.",
                icon = Icons.Filled.Description,
                morphState = morphState
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(filteredDocuments) { doc ->
                    DocumentCard(
                        document = doc,
                        onClick = { selectedDocument = doc },
                        morphState = morphState
                    )
                }
                item { Spacer(modifier = Modifier.height(80.dp)) }
            }
        }
    }

    // Document Detail View
    selectedDocument?.let { doc ->
        DocumentDetailDialog(
            document = doc,
            onDismiss = { selectedDocument = null },
            vaultDb = vaultDb
        )
    }
}

@Composable
fun DocumentCard(
    document: DocumentEntry,
    onClick: () -> Unit,
    morphState: AdaptiveWorkshopSynthesizer.UiMorphState
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardBackground)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        when (document.docType) {
                            "FRONTEND" -> AccentPurple.copy(alpha = 0.2f)
                            "CODE" -> AccentGreen.copy(alpha = 0.2f)
                            "REPORT" -> AccentCyan.copy(alpha = 0.2f)
                            "LEGAL" -> AccentYellow.copy(alpha = 0.2f)
                            "FINANCE" -> AccentRed.copy(alpha = 0.2f)
                            else -> AccentCyan.copy(alpha = 0.2f)
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (document.docType) {
                        "FRONTEND" -> Icons.Filled.Language
                        "CODE" -> Icons.Filled.Code
                        "REPORT" -> Icons.Filled.ShowChart
                        "LEGAL" -> Icons.Filled.Balance
                        "FINANCE" -> Icons.Filled.Euro
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = when (document.docType) {
                        "FRONTEND" -> AccentPurple
                        "CODE" -> AccentGreen
                        "REPORT" -> AccentCyan
                        "LEGAL" -> AccentYellow
                        "FINANCE" -> AccentRed
                        else -> AccentCyan
                    },
                    modifier = Modifier.size(20.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = document.title,
                    color = TextPrimary,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    Text(
                        text = document.docTypeLabel,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = document.formattedSize,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
                Text(
                    text = document.formattedDate,
                    color = TextSecondary.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace
                )
            }
            
            Icon(
                imageVector = Icons.Filled.ArrowForward,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DocumentDetailDialog(
    document: DocumentEntry,
    onDismiss: () -> Unit,
    vaultDb: SQLiteDatabase?
) {
    val context = LocalContext.current
    var contentText by remember { mutableStateOf(document.contentText) }
    
    // If no content text but has URI, try to load it
    LaunchedEffect(document.docId) {
        if (contentText == null && document.contentUri != null) {
            // In a real implementation, load content from URI
            contentText = "[Inhalt aus Datei: ${document.contentUri}]"
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when (document.docType) {
                        "FRONTEND" -> Icons.Filled.Language
                        "CODE" -> Icons.Filled.Code
                        "REPORT" -> Icons.Filled.ShowChart
                        else -> Icons.Filled.Description
                    },
                    contentDescription = null,
                    tint = when (document.docType) {
                        "FRONTEND" -> AccentPurple
                        "CODE" -> AccentGreen
                        "REPORT" -> AccentCyan
                        else -> AccentCyan
                    },
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    text = document.title,
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace
                )
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Metadata
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    InfoChip(label = "Typ", value = document.docTypeLabel, color = AccentCyan)
                    InfoChip(label = "Größe", value = document.formattedSize, color = AccentGreen)
                }
                Spacer(modifier = Modifier.height(8.dp))
                InfoChip(label = "Erstellt", value = document.formattedDate, color = TextSecondary)
                
                if (!document.sourceEngine.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    InfoChip(label = "Quelle", value = document.sourceEngine, color = AccentPurple)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Content
                val currentContent = contentText
                if (!currentContent.isNullOrEmpty()) {
                    Text(
                        text = "INHALT",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E14))
                    ) {
                        Text(
                            text = currentContent,
                            color = TextPrimary,
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else if (document.contentUri != null) {
                    Text(
                        text = "DATEI",
                        color = TextSecondary,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0A0E14))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.Description, contentDescription = null, tint = AccentCyan)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = document.contentUri,
                                color = TextPrimary,
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            try {
                                val uri = Uri.parse(document.contentUri)
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, document.mimeType ?: "*/*")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Keine App zum Öffnen gefunden", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = AccentCyan.copy(alpha = 0.2f)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(Icons.Filled.Launch, contentDescription = null, modifier = Modifier.size(16.dp), tint = AccentCyan)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ÖFFNEN", color = AccentCyan, fontFamily = FontFamily.Monospace)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("SCHLIESSEN", color = TextSecondary, fontFamily = FontFamily.Monospace)
            }
        },
        containerColor = CardBackground,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary
    )
}

@Composable
fun InfoChip(label: String, value: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "$label:",
            color = TextSecondary,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = value,
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            fontFamily = FontFamily.Monospace
        )
    }
}

// Data loading helper
private fun loadDocuments(db: SQLiteDatabase): List<DocumentEntry> {
    val cursor = db.rawQuery(
        """SELECT doc_id, title, doc_type, content_uri, content_text, mime_type, 
                  file_size, source_engine, metadata, created_at 
           FROM documents 
           ORDER BY created_at DESC""",
        null
    )
    val results = mutableListOf<DocumentEntry>()
    cursor.use {
        while (it.moveToNext()) {
            results.add(
                DocumentEntry(
                    docId = it.getString(0),
                    title = it.getString(1),
                    docType = it.getString(2),
                    contentUri = it.getString(3),
                    contentText = it.getString(4),
                    mimeType = it.getString(5),
                    fileSize = it.getLong(6),
                    sourceEngine = it.getString(7),
                    metadata = it.getString(8),
                    createdAt = it.getLong(9)
                )
            )
        }
    }
    return results
}
