package com.example.calcalc.ui.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.calcalc.camera.CameraCaptureDialog
import com.example.calcalc.camera.ImageUtils
import com.example.calcalc.data.model.totalCalories
import com.example.calcalc.nav.ChatKey
import com.example.calcalc.ui.components.ItemsTable
import kotlinx.coroutines.launch
import java.time.format.DateTimeFormatter

private val DISPLAY_DATE = DateTimeFormatter.ofPattern("d MMM")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    args: ChatKey,
    onDone: () -> Unit,
    onOpenProfile: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: ChatViewModel = viewModel(
        key = "chat-${args.entryId}-${args.dateLocal}",
        factory = ChatViewModel.factory(args),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var input by remember { mutableStateOf("") }
    var tableExpanded by remember { mutableStateOf(true) }
    var showAttachSheet by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(args.openCamera) }
    val listState = rememberLazyListState()

    LaunchedEffect(state.finished) { if (state.finished) onDone() }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) listState.animateScrollToItem(state.messages.lastIndex)
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                ImageUtils.fromUri(context, uri)?.let { viewModel.send(text = null, imageBase64 = it) }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Log · ${state.date.format(DISPLAY_DATE)}") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(enabled = state.canFinish, onClick = viewModel::finish) {
                        Text(if (state.saving) "Saving…" else "Done")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
        ) {
            TableSection(
                expanded = tableExpanded,
                onToggle = { tableExpanded = !tableExpanded },
                items = state.items,
                onRemove = viewModel::removeItem,
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.messages) { message -> MessageBubble(message, onOpenProfile, state.needsApiKey) }
                if (state.sending) {
                    item {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text(
                                "  Reading your meal…",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            InputBar(
                value = input,
                onValueChange = { input = it },
                enabled = !state.sending && !state.loading,
                onAttach = { showAttachSheet = true },
                onSend = {
                    // Collapsing on send keeps the newest messages visible on a short screen.
                    tableExpanded = false
                    viewModel.send(input)
                    input = ""
                },
            )
        }
    }

    if (showAttachSheet) {
        ModalBottomSheet(onDismissRequest = { showAttachSheet = false }) {
            ListItem(
                headlineContent = { Text("Take a photo") },
                leadingContent = { Icon(Icons.Default.PhotoCamera, contentDescription = null) },
                modifier = Modifier.clickable { showAttachSheet = false; showCamera = true },
            )
            ListItem(
                headlineContent = { Text("Choose from gallery") },
                leadingContent = { Icon(Icons.Default.PhotoLibrary, contentDescription = null) },
                modifier = Modifier.clickable {
                    showAttachSheet = false
                    galleryLauncher.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                },
            )
            Box(Modifier.navigationBarsPadding())
        }
    }

    if (showCamera) {
        CameraCaptureDialog(
            onDismiss = { showCamera = false },
            onCaptured = { bytes ->
                showCamera = false
                scope.launch {
                    ImageUtils.fromJpegBytes(bytes)?.let { viewModel.send(text = null, imageBase64 = it) }
                }
            },
        )
    }
}

/**
 * The table stays on screen while logging, per the spec. When collapsed it shrinks to a
 * one-line summary so the keyboard and the conversation still fit on a phone.
 */
@Composable
private fun TableSection(
    expanded: Boolean,
    onToggle: () -> Unit,
    items: List<com.example.calcalc.data.model.FoodItem>,
    onRemove: (Int) -> Unit,
) {
    Column(Modifier.padding(horizontal = 12.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                if (items.isEmpty()) "No items yet"
                else "${items.size} item${if (items.size == 1) "" else "s"} · ${items.totalCalories()} kcal",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "Collapse table" else "Expand table",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        AnimatedVisibility(visible = expanded) {
            ItemsTable(items = items, onRemove = onRemove)
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    onOpenProfile: () -> Unit,
    needsApiKey: Boolean,
) {
    val isUser = message.role == ChatRole.USER
    val isSystem = message.role == ChatRole.SYSTEM

    val background = when {
        isSystem -> MaterialTheme.colorScheme.errorContainer
        isUser -> MaterialTheme.colorScheme.primaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainer
    }
    val foreground = when {
        isSystem -> MaterialTheme.colorScheme.onErrorContainer
        isUser -> MaterialTheme.colorScheme.onPrimaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
    ) {
        Column(
            Modifier
                .widthIn(max = 300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(background)
                .padding(12.dp),
        ) {
            message.imageBase64?.let { base64 ->
                val bitmap = remember(base64) { ImageUtils.decodeBase64(base64) }
                bitmap?.let {
                    androidx.compose.foundation.Image(
                        bitmap = it.asImageBitmap(),
                        contentDescription = "Meal photo",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .padding(bottom = if (message.text.isBlank()) 0.dp else 8.dp),
                    )
                }
            }
            if (message.text.isNotBlank()) {
                Text(message.text, color = foreground, style = MaterialTheme.typography.bodyMedium)
            }
            if (isSystem && needsApiKey) {
                TextButton(onClick = onOpenProfile) { Text("Open Profile") }
            }
        }
    }
}

@Composable
private fun InputBar(
    value: String,
    onValueChange: (String) -> Unit,
    enabled: Boolean,
    onAttach: () -> Unit,
    onSend: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            enabled = enabled,
            placeholder = { Text("What did you eat?") },
            maxLines = 4,
            shape = RoundedCornerShape(24.dp),
            leadingIcon = {
                IconButton(onClick = onAttach, enabled = enabled) {
                    Icon(Icons.Default.AddAPhoto, contentDescription = "Add a photo")
                }
            },
            modifier = Modifier.weight(1f),
        )
        IconButton(
            onClick = onSend,
            enabled = enabled && value.isNotBlank(),
        ) {
            Icon(
                Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = if (enabled && value.isNotBlank()) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
