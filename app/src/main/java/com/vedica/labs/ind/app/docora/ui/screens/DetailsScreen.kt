package com.vedica.labs.ind.app.docora.ui.screens

import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.model.TextOrigin
import com.vedica.labs.ind.app.docora.core.util.DateFormatter
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter
import com.vedica.labs.ind.app.docora.feature.print.DocumentPrintAdapter
import com.vedica.labs.ind.app.docora.ui.components.DocumentThumbnail
import com.vedica.labs.ind.app.docora.ui.home.categoryNameRes

/**
 * Document details (PRD §49).
 *
 * The page is built around the one action people come here for: opening the file. The preview
 * and a large primary button sit first, followed by the handful of secondary actions and then
 * the metadata, grouped into a single readable card. Destructive actions are separated at the
 * bottom so they can never be tapped by accident while scanning the information.
 *
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
    var renameOpen by remember { mutableStateOf(false) }

    LaunchedEffect(message) {
        message?.let {
            snacks.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.details_title)) },
                navigationIcon = {
                    IconButton(onClick = { controller.popBackStack() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = vm::toggleFavorite) {
                        val favourite = state.document?.isFavorite == true
                        Icon(
                            imageVector = if (favourite) Icons.Filled.Star else Icons.Filled.StarBorder,
                            contentDescription = stringResource(
                                if (favourite) R.string.action_unfavourite else R.string.action_favourite,
                            ),
                            tint = if (favourite) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snacks) },
    ) { padding ->
        val document = state.document
        when {
            state.isLoading -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
            document == null -> Box(
                Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    stringResource(R.string.error_document_missing),
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(32.dp),
                )
            }
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                DocumentHero(
                    document = document,
                    renderPreview = { _, target -> vm.renderPreview(target) },
                )

                Button(
                    onClick = {
                        vm.markOpened()
                        controller.navigate(Screen.viewerRoute(document.id))
                    },
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.details_open))
                }

                QuickActions(
                    onShare = { vm.share(context) },
                    onRename = { renameOpen = true },
                    onPrint = {
                        DocumentPrintAdapter.print(context, Uri.parse(document.uri), document.displayName)
                    },
                )

                SectionTitle(stringResource(R.string.details_information))
                InfoCard(document = document, folderPath = state.folderPath)

                if (state.tags.isNotEmpty() || document.category != null) {
                    SectionTitle(stringResource(R.string.details_category_tags))
                    TagsAndCategory(
                        category = document.category,
                        tags = state.tags.map { it.name },
                    )
                }

                if (document.type == DocumentType.PDF || document.requiresExternalApp) {
                    SectionTitle(stringResource(R.string.details_more_actions))
                    MoreActionsCard(
                        document = document,
                        onPdfTools = { controller.navigate(Screen.pdfToolsRoute(document.id)) },
                        onOpenWith = { vm.openExternally(context) },
                    )
                }

                DeleteCard(onDelete = { vm.moveToTrash() })
            }
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

/** Preview, name and the three facts that identify a file at a glance. */
@Composable
private fun DocumentHero(
    document: Document,
    renderPreview: suspend (String, Int) -> Bitmap?,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(28.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier.height(210.dp),
                contentAlignment = Alignment.Center,
            ) {
                DocumentThumbnail(
                    uri = document.uri,
                    type = document.type,
                    renderPreview = renderPreview,
                    targetPx = 512,
                    modifier = Modifier
                        .fillMaxHeight()
                        .aspectRatio(0.74f),
                )
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = document.displayName,
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MetaPill(document.type.label)
                document.pageCount?.takeIf { it > 0 }?.let { pages ->
                    MetaPill(pluralStringResource(R.plurals.plural_pages, pages, pages))
                }
                MetaPill(FileSizeFormatter.format(document.sizeBytes))
            }
        }
    }
}

@Composable
private fun MetaPill(text: String) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            maxLines = 1,
        )
    }
}

/** The three everyday actions, shown as equal neutral buttons. */
@Composable
private fun QuickActions(
    onShare: () -> Unit,
    onRename: () -> Unit,
    onPrint: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        QuickAction(
            icon = Icons.Filled.Share,
            label = stringResource(R.string.action_share),
            onClick = onShare,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Filled.Edit,
            label = stringResource(R.string.action_rename),
            onClick = onRename,
            modifier = Modifier.weight(1f),
        )
        QuickAction(
            icon = Icons.Filled.Print,
            label = stringResource(R.string.action_print),
            onClick = onPrint,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun QuickAction(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(scheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 4.dp, top = 6.dp),
    )
}

/** Everything the app knows about the file, in one quiet card. */
@Composable
private fun InfoCard(document: Document, folderPath: String?) {
    val rows = buildList {
        add(stringResource(R.string.details_type) to document.type.label)
        add(stringResource(R.string.details_size) to FileSizeFormatter.format(document.sizeBytes))
        document.pageCount?.let { pages ->
            add(stringResource(R.string.details_pages) to pages.toString())
        }
        if (document.width != null && document.height != null) {
            add(stringResource(R.string.details_dimensions) to "${document.width} × ${document.height}")
        }
        add(
            stringResource(R.string.details_created) to
                DateFormatter.formatDate(document.createdAt),
        )
        add(
            stringResource(R.string.details_modified) to
                DateFormatter.formatDate(document.modifiedAt),
        )
        document.lastOpenedAt?.let {
            add(stringResource(R.string.details_last_opened) to DateFormatter.formatRelative(it))
        }
        add(
            stringResource(R.string.details_location) to
                (folderPath ?: stringResource(R.string.details_location_root)),
        )
        add(
            stringResource(R.string.details_source) to
                stringResource(sourceLabelRes(document.source)),
        )
        if (document.isEncrypted) {
            add(stringResource(R.string.details_protection) to stringResource(R.string.details_protected))
        }
        textSourceLabelRes(document.textOrigin)?.let { sourceRes ->
            add(stringResource(R.string.details_text_source) to stringResource(sourceRes))
        }
        document.checksumSha256?.takeIf { it.isNotBlank() }?.let { checksum ->
            add(stringResource(R.string.details_checksum) to checksum.take(20) + "…")
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            rows.forEachIndexed { index, (label, value) ->
                InfoRow(label = label, value = value)
                if (index != rows.lastIndex) {
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.width(104.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurface,
            textAlign = TextAlign.End,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
    }
}

/** Category and tags, wrapped so a long tag name never pushes the row off screen. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagsAndCategory(
    category: DocumentCategory?,
    tags: List<String>,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
    ) {
        FlowRow(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            category?.let {
                Pill(
                    text = stringResource(categoryNameRes(it)),
                    emphasised = true,
                )
            }
            tags.forEach { tag ->
                Pill(text = tag, emphasised = false)
            }
        }
    }
}

@Composable
private fun Pill(text: String, emphasised: Boolean) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = if (emphasised) scheme.secondaryContainer else scheme.surfaceContainerHigh,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (emphasised) scheme.onSecondaryContainer else scheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
            maxLines = 1,
        )
    }
}

/** Format-specific actions that do not belong in the everyday three. */
@Composable
private fun MoreActionsCard(
    document: Document,
    onPdfTools: () -> Unit,
    onOpenWith: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (document.type == DocumentType.PDF) {
                MoreActionRow(
                    icon = Icons.Filled.PictureAsPdf,
                    title = stringResource(R.string.toolkit_title),
                    subtitle = stringResource(R.string.details_pdf_tools_summary),
                    onClick = onPdfTools,
                )
            }
            if (document.requiresExternalApp) {
                MoreActionRow(
                    icon = Icons.AutoMirrored.Filled.OpenInNew,
                    title = stringResource(R.string.action_open_with),
                    subtitle = stringResource(R.string.details_open_with_summary),
                    onClick = onOpenWith,
                )
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = scheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            imageVector = Icons.Filled.ChevronRight,
            contentDescription = null,
            tint = scheme.outline,
            modifier = Modifier.size(18.dp),
        )
    }
}

/** Destructive action isolated at the very bottom. */
@Composable
private fun DeleteCard(onDelete: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onDelete)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(scheme.errorContainer, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = null,
                    tint = scheme.onErrorContainer,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.action_delete),
                    style = MaterialTheme.typography.titleSmall,
                    color = scheme.error,
                )
                Text(
                    text = stringResource(R.string.details_delete_summary),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun sourceLabelRes(source: DocumentSource): Int = when (source) {
    DocumentSource.IMPORTED -> R.string.details_source_imported
    DocumentSource.SCANNED -> R.string.details_source_scanned
    DocumentSource.CREATED -> R.string.details_source_created
    DocumentSource.SHARED_IN -> R.string.details_source_shared
}

private fun textSourceLabelRes(origin: TextOrigin): Int? = when (origin) {
    TextOrigin.EMBEDDED -> R.string.details_text_embedded
    TextOrigin.RECOGNISED -> R.string.details_text_recognised
    TextOrigin.NONE -> null
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
