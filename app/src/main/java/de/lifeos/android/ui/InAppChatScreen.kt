package de.lifeos.android.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import de.lifeos.core.social.ChatMessage
import de.lifeos.core.social.InteractiveLifeChatEngine
import de.lifeos.core.social.StagedOutboundIntent
import kotlinx.coroutines.launch

// Chat Color Palette
val ChatBackground = Color(0xFF0D1117)
val ChatSurface = Color(0xFF161B22)
val ChatUserBubble = Color(0xFF1F4068)
val ChatBotBubble = Color(0xFF1C2128)
val ChatAccentCyan = Color(0xFF00E5FF)
val ChatAccentGreen = Color(0xFF00E676)
val ChatAccentYellow = Color(0xFFFFD600)
val ChatAccentRed = Color(0xFFFF1744)
val ChatAccentPurple = Color(0xFFAA00FF)
val ChatTextPrimary = Color(0xFFE6EDF3)
val ChatTextSecondary = Color(0xFF7D8590)
val ChatDivider = Color(0xFF21262D)

@Composable
fun InAppChatScreen(
    chatEngine: InteractiveLifeChatEngine,
    onBackToDashboard: () -> Unit
) {
    var messages by remember { mutableStateOf(chatEngine.getChatHistory()) }
    var inputText by remember { mutableStateOf("") }
    var stagedIntent by remember { mutableStateOf<StagedOutboundIntent?>(null) }
    var isTyping by remember { mutableStateOf(false) }
    var showQuickActions by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Auto-scroll to bottom when new message arrives
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    // Hide quick actions after first message
    LaunchedEffect(messages.size) {
        if (messages.size > 2) showQuickActions = false
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ChatBackground)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Header
            ChatHeader(onBackToDashboard = onBackToDashboard)

            // Messages List
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 8.dp)
            ) {
                // Welcome message
                item {
                    WelcomeBanner()
                }

                // Quick Actions
                if (showQuickActions) {
                    item {
                        QuickActionsRow(
                            onActionSelected = { action ->
                                inputText = action
                            }
                        )
                    }
                }

                // Messages
                items(messages, key = { it.id }) { msg ->
                    ChatBubble(
                        message = msg,
                        onConfirmIntent = { intent ->
                            val confirmMsg = chatEngine.confirmAndDispatchStagedIntent(intent)
                            messages = messages + ChatMessage(isFromUser = false, text = confirmMsg)
                            stagedIntent = null
                        }
                    )
                }

                // Typing indicator
                if (isTyping) {
                    item {
                        TypingIndicator()
                    }
                }

                // Staged Intent Card
                stagedIntent?.let { intent ->
                    item {
                        StagedIntentCard(
                            intent = intent,
                            onConfirm = {
                                val confirmMsg = chatEngine.confirmAndDispatchStagedIntent(intent)
                                messages = messages + ChatMessage(isFromUser = false, text = confirmMsg)
                                stagedIntent = null
                            },
                            onCancel = { stagedIntent = null }
                        )
                    }
                }
            }

            // Input Area - Always visible
            ChatInputArea(
                inputText = inputText,
                onInputChange = { inputText = it },
                onSend = {
                    if (inputText.isNotBlank()) {
                        val userMsg = ChatMessage(isFromUser = true, text = inputText)
                        messages = messages + userMsg
                        val currentInput = inputText
                        inputText = ""
                        isTyping = true

                        chatEngine.processUserMessage(currentInput) { botMsg ->
                            messages = messages + botMsg
                            botMsg.stagedIntent?.let { stagedIntent = it }
                            isTyping = false
                        }
                    }
                }
            )
        }
    }
}

@Composable
fun ChatHeader(onBackToDashboard: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.horizontalGradient(
                    colors = listOf(ChatSurface, Color(0xFF0D1117))
                )
            )
            .padding(horizontal = 12.dp, vertical = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(
                    onClick = onBackToDashboard,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.ArrowBack,
                        contentDescription = "Zurück",
                        tint = ChatAccentCyan
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Column {
                    Text(
                        text = "INTERAKTIVER CHAT",
                        color = ChatTextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ChatAccentGreen)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "System aktiv",
                            color = ChatTextSecondary,
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            IconButton(
                onClick = { /* Clear chat history */ },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Neu",
                    tint = ChatTextSecondary
                )
            }
        }
    }
}

@Composable
fun WelcomeBanner() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ChatSurface)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.Email,
                contentDescription = null,
                tint = ChatAccentCyan,
                modifier = Modifier.size(32.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "LIFE-OS ASSISTANT",
                color = ChatAccentCyan,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Ich analysiere deine Dokumente, erfasse Reibungspunkte und bereite Handlungsoptionen vor.",
                color = ChatTextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
fun QuickActionsRow(onActionSelected: (String) -> Unit) {
    val quickActions = listOf(
        "📊 Status analysieren" to "Analysiere meinen aktuellen Status",
        "📄 Dokumente prüfen" to "Prüfe alle Dokumente im Tresor",
        "⚠️ Reibungspunkte" to "Zeige aktive Reibungspunkte",
        "📱 WhatsApp Vorlage" to "Schreib eine Nachricht an"
    )

    Column {
        Text(
            text = "SCHNELLAKTIONEN",
            color = ChatTextSecondary,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickActions.take(2).forEach { (label, action) ->
                QuickActionChip(
                    label = label,
                    onClick = { onActionSelected(action) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            quickActions.drop(2).forEach { (label, action) ->
                QuickActionChip(
                    label = label,
                    onClick = { onActionSelected(action) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
fun QuickActionChip(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onClick() },
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = ChatSurface)
    ) {
        Text(
            text = label,
            color = ChatAccentCyan,
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun ChatBubble(
    message: ChatMessage,
    onConfirmIntent: (StagedOutboundIntent) -> Unit
) {
    val isUser = message.isFromUser
    val backgroundColor = if (isUser) ChatUserBubble else ChatBotBubble
    val textColor = if (isUser) ChatAccentCyan else ChatTextPrimary
    val timeFormatted = remember(message.timestamp) {
        val sdf = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
        sdf.format(java.util.Date(message.timestamp))
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
    ) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
        ) {
            if (!isUser) {
                // Bot avatar
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ChatAccentCyan.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Person,
                        contentDescription = null,
                        tint = ChatAccentCyan,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
            }

            Card(
                modifier = Modifier.widthIn(max = 280.dp),
                shape = RoundedCornerShape(
                    topStart = 12.dp,
                    topEnd = 12.dp,
                    bottomStart = if (isUser) 12.dp else 4.dp,
                    bottomEnd = if (isUser) 4.dp else 12.dp
                ),
                colors = CardDefaults.cardColors(containerColor = backgroundColor)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = message.text,
                        color = textColor,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 18.sp
                    )

                    if (!isUser && message.stagedIntent != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Button(
                            onClick = { onConfirmIntent(message.stagedIntent) },
                            colors = ButtonDefaults.buttonColors(containerColor = ChatAccentGreen),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                "1-TAP FREIGABE",
                                color = Color.Black,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            if (isUser) {
                Spacer(modifier = Modifier.width(6.dp))
                // User avatar
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(ChatAccentCyan),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "PB",
                        color = Color.Black,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }

        // Timestamp
        Text(
            text = timeFormatted,
            color = ChatTextSecondary,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(top = 2.dp, start = if (!isUser) 34.dp else 0.dp, end = if (isUser) 34.dp else 0.dp)
        )
    }
}

@Composable
fun TypingIndicator() {
    val dotCount = remember { mutableStateOf(1) }

    LaunchedEffect(Unit) {
        while (true) {
            kotlinx.coroutines.delay(500)
            dotCount.value = if (dotCount.value >= 3) 1 else dotCount.value + 1
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 34.dp)
    ) {
        Box(
            modifier = Modifier
                .background(ChatBotBubble, RoundedCornerShape(12.dp))
                .padding(horizontal = 16.dp, vertical = 10.dp)
        ) {
            Text(
                text = ".".repeat(dotCount.value) + " ".repeat(3 - dotCount.value),
                color = ChatTextSecondary,
                fontSize = 16.sp,
                fontFamily = FontFamily.Monospace
            )
        }
    }
}

@Composable
fun StagedIntentCard(
    intent: StagedOutboundIntent,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = ChatSurface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = null,
                    tint = ChatAccentYellow,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "FREIGABE ERFORDERLICH",
                    color = ChatAccentYellow,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Intent details
            Row {
                Text(
                    text = "An: ",
                    color = ChatTextSecondary,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "${intent.recipient} (${intent.channel})",
                    color = ChatAccentCyan,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(6.dp),
                colors = CardDefaults.cardColors(containerColor = ChatBackground)
            ) {
                Text(
                    text = intent.draftPayload,
                    color = ChatTextPrimary,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.padding(10.dp),
                    lineHeight = 16.sp
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = ChatTextSecondary)
                ) {
                    Text("Abbrechen", fontSize = 11.sp)
                }
                Button(
                    onClick = onConfirm,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = ChatAccentGreen),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text("FREIGEBEN", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun ChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    onSend: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(ChatSurface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom
        ) {
            // Text Input
            OutlinedTextField(
                value = inputText,
                onValueChange = onInputChange,
                modifier = Modifier.weight(1f),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = ChatTextPrimary,
                    unfocusedTextColor = ChatTextPrimary,
                    cursorColor = ChatAccentCyan,
                    focusedBorderColor = ChatAccentCyan.copy(alpha = 0.5f),
                    unfocusedBorderColor = ChatDivider,
                    focusedContainerColor = ChatBackground,
                    unfocusedContainerColor = ChatBackground
                ),
                textStyle = androidx.compose.ui.text.TextStyle(
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace
                ),
                placeholder = {
                    Text(
                        "Anweisung oder Frage eingeben...",
                        color = ChatTextSecondary,
                        fontSize = 13.sp
                    )
                },
                shape = RoundedCornerShape(12.dp),
                singleLine = false,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { onSend() })
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Send Button
            FilledIconButton(
                onClick = onSend,
                modifier = Modifier.size(48.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledIconButtonColors(
                    containerColor = if (inputText.isNotBlank()) ChatAccentCyan else ChatDivider
                ),
                enabled = inputText.isNotBlank()
            ) {
                Icon(
                    imageVector = Icons.Filled.Send,
                    contentDescription = "Senden",
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}