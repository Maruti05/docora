package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.automirrored.filled.Label
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.util.DateFormatter
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter
import com.vedica.labs.ind.app.docora.feature.print.DocumentPrintAdapter
import android.graphics.Bitmap
import com.vedica.labs.ind.app.docora.ui.components.DocumentThumbnail
import com.vedica.labs.ind.app.docora.ui.designsystem.DocoraThemeTokens
import com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors
import android.net.Uri

/**
 * Document details (PRD §49): metadata, tags, and the contextual actions of PRD §27.
 * The document flow is live, so a rename or background re-index is reflected immediately.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetailsScreen(
    controller: NavHostController,
    documentId: String? = null,
    vm: DetailsViewModel = hiltViewModel(),
) {
    val context = LocalContext.current
    val state by vm.state.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    val snacks = remember { SnackbarHostState() }
    val extended = LocalDocoraExtendedColors.current
    var renameOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snacks.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = { controller.popBackStack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        Icon(
                            imageVector = if (state.document?.isFavorite == true) {
                                Icons.Filled.Star
                            } else {
                                Icons.Filled.StarBorder
                            },
                            contentDescription = stringResource(R.string.action_favourite),
                            tint = if (state.document?.isFavorite == true) extended.favourite else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { padding ->
        val document = state.document
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
            return@Scaffold
        }
        if (document == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    stringResource(R.string.error_document_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            return@Scaffold
        }
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            PreviewHeader(document = document, renderPreview = { _, target -> vm.renderPreview(target) })
            MetaCard(document = document, folderPath = state.folderPath)
            if (state.tags.isNotEmpty()) TagsRow(tags = state.tags)
            ActionsCard(
                document = document,
                onRename = { renameOpen = true },
                onShare = { vm.share(context) },
                onPrint = {
                    DocumentPrintAdapter.print(context, Uri.parse(document.uri), document.displayName)
                },
                onOpenWith = { vm.openExternally(context) },
                onDelete = { vm.moveToTrash() },
            )
        }
    }

    if (renameOpen && state.document != null) {
        RenameDialog(
            initial = state.document?.displayName.orEmpty(),
            onDismiss = { renameOpen = false },
            onConfirm = { value ->
                renameOpen = false
                vm.rename(value)
            },
        )
    }
}

/** Hero preview with type-coloured backdrop. */
@Composable
private fun PreviewHeader(
    document: Document,
    renderPreview: suspend (String, Int) -> Bitmap?,
) {
    val extended = LocalDocoraExtendedColors.current
    val backdrop = when (document.type) {
        com.vedica.labs.ind.app.docora.core.model.DocumentType.PDF -> extended.pdfBadge
        com.vedica.labs.ind.app.docora.core.model.DocumentType.IMAGE -> extended.imageBadge
        com.vedica.labs.ind.app.docora.core.model.DocumentType.ARCHIVE -> extended.archiveBadge
        else -> extended.officeBadge
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .clip(MaterialTheme.shapes.extraLarge)
            .background(backdrop.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        DocumentThumbnail(
            uri = document.uri,
            type = document.type,
            renderPreview = renderPreview,
            targetPx = 512,
            modifier = Modifier.fillMaxHeight(0.92f),
        )
    }
}

/** Metadata grid: type, size, pages, dates, location, checksum (PRD §49). */
@Composable
private fun MetaCard(document: Document, folderPath: String?) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(document.title, style = MaterialTheme.typography.titleLarge)
            MetaRow(stringResource(R.string.details_type), document.type.label)
            MetaRow(stringResource(R.string.details_size), FileSizeFormatter.format(document.sizeBytes))
            document.pageCount?.let { MetaRow(stringResource(R.string.details_pages), it.toString()) }
            if (document.width != null && document.height != null) {
                MetaRow(
                    stringResource(R.string.details_dimensions),
                    "${document.width} × ${document.height}",
                )
            }
            MetaRow(stringResource(R.string.details_created), DateFormatter.formatDate(document.createdAt))
            MetaRow(stringResource(R.string.details_modified), DateFormatter.formatDate(document.modifiedAt))
            document.lastOpenedAt?.let {
                MetaRow(stringResource(R.string.details_last_opened), DateFormatter.formatRelative(it))
            }
            MetaRow(
                stringResource(R.string.details_location),
                folderPath ?: stringResource(R.string.details_location_root),
            )
            document.checksumSha256?.takeIf { it.isNotBlank() }?.let {
                MetaRow(stringResource(R.string.details_checksum), it.take(16) + "…")
            }
        }
    }
}

@Composable
private fun MetaRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth()) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(120.dp),
        )
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun TagsRow(tags: List<com.vedica.labs.ind.app.docora.core.model.Tag>) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            Icons.AutoMirrored.Filled.Label,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(18.dp),
        )
        tags.forEach { tag ->
            AssistChip(onClick = {}, label = { Text(tag.name) })
        }
    }
}

/** Contextual actions (PRD §27, §49). */
@Composable
private fun ActionsCard(
    document: Document,
    onRename: () -> Unit,
    onShare: () -> Unit,
    onPrint: () -> Unit,
    onOpenWith: () -> Unit,
    onDelete: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Card(
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = scheme.surfaceContainerLow),
    ) {
        Column(Modifier.fillMaxWidth().padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            ActionRow(Icons.Filled.Edit, stringResource(R.string.action_rename), onRename)
            ActionRow(Icons.Filled.Share, stringResource(R.string.action_share), onShare)
            ActionRow(Icons.Filled.Print, stringResource(R.string.action_print), onPrint)
            if (document.requiresExternalApp) {
                ActionRow(Icons.AutoMirrored.Filled.OpenInNew, stringResource(R.string.action_open_with), onOpenWith)
            }
            ActionRow(
                Icons.Filled.Delete,
                stringResource(R.string.action_delete),
                onDelete,
                tint = scheme.error,
            )
        }
    }
}

@Composable
private fun ActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color? = null,
) {
    val scheme = MaterialTheme.colorScheme
    Row(            Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = tint ?: scheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(14.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge, color = tint ?: scheme.onSurface)
    }
}

@Composable
private fun RenameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var value by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.action_rename)) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = { value = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(value) }) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
