package com.vedica.labs.ind.app.docora.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MergeType
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavHostController
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter
import com.vedica.labs.ind.app.docora.ui.components.DocoraSkeleton
import com.vedica.labs.ind.app.docora.ui.components.LocalBottomBarInset
import kotlinx.coroutines.launch

/** A tool advertised on the hub; opening it guides the user to pick a PDF. */
private data class ToolShortcut(
    val titleRes: Int,
    val icon: ImageVector,
)

private val ToolShortcuts = listOf(
    ToolShortcut(R.string.toolkit_merge, Icons.AutoMirrored.Filled.MergeType),
    ToolShortcut(R.string.toolkit_split, Icons.Filled.ContentCut),
    ToolShortcut(R.string.toolkit_reorder, Icons.Filled.ViewModule),
    ToolShortcut(R.string.toolkit_compress, Icons.Filled.Archive),
    ToolShortcut(R.string.toolkit_metadata, Icons.Filled.Edit),
    ToolShortcut(R.string.toolkit_images_to_pdf, Icons.Filled.PhotoLibrary),
)

/**
 * Top-level Tools tab.
 *
 * The operations themselves are document-scoped, so the hub stays useful by keeping the library's
 * PDFs one tap away. The screen is intentionally plain: a short explanation, six neutral tool
 * rows and the PDFs they can act on. No coloured shortcuts - the label is the differentiator, and
 * the glyphs all share one muted tint.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(
    controller: NavHostController,
    vm: ToolsViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val bottomInset = LocalBottomBarInset.current
    val pdfsHeaderIndex = 3

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.nav_tools)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 4.dp,
                bottom = 24.dp + bottomInset,
            ),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "intro") {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.tools_hero_title),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                    Text(
                        text = stringResource(R.string.tools_hero_body),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            item(key = "toolsHeader") {
                ToolsSectionLabel(stringResource(R.string.tools_section_operations))
            }
            item(key = "tools") {
                ToolShortcutGrid(
                    onShortcut = {
                        scope.launch { listState.animateScrollToItem(pdfsHeaderIndex) }
                    },
                )
            }
            item(key = "pdfHeader") {
                ToolsSectionLabel(stringResource(R.string.tools_section_documents))
            }
            when {
                state.isLoading -> items(count = 3, key = { "pdf-skeleton-$it" }) {
                    PdfRowSkeleton()
                }
                state.pdfs.isEmpty() -> item(key = "empty") {
                    EmptyPdfsCard(
                        onOpenDocuments = { controller.navigateTopLevel(Screen.Documents) },
                    )
                }
                else -> items(state.pdfs, key = { it.id }) { pdf ->
                    PdfToolDocumentCard(
                        pdf = pdf,
                        onClick = { controller.navigate(Screen.pdfToolsRoute(pdf.id)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ToolsSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}

/** Placeholder row while the PDF list is being read from the library. */
@Composable
private fun PdfRowSkeleton() {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            DocoraSkeleton(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(12.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                DocoraSkeleton(Modifier.fillMaxWidth(0.66f).height(13.dp))
                Spacer(Modifier.height(7.dp))
                DocoraSkeleton(Modifier.fillMaxWidth(0.34f).height(10.dp))
            }
        }
    }
}

@Composable
private fun ToolShortcutGrid(onShortcut: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        ToolShortcuts.chunked(2).forEach { rowItems ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                rowItems.forEach { tool ->
                    ToolShortcutTile(
                        tool = tool,
                        onClick = onShortcut,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowItems.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun ToolShortcutTile(
    tool: ToolShortcut,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(10.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = tool.icon,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = stringResource(tool.titleRes),
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun PdfToolDocumentCard(pdf: Document, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .background(scheme.surfaceContainerHigh, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Filled.PictureAsPdf,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = pdf.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val pages = pdf.pageCount ?: 0
                Text(
                    text = "${pluralStringResource(R.plurals.plural_pages, pages, pages)}" +
                        "  ·  ${FileSizeFormatter.format(pdf.sizeBytes)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
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
}

@Composable
private fun EmptyPdfsCard(onOpenDocuments: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = scheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text(
                text = stringResource(R.string.tools_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.tools_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp),
            )
            TextButton(
                onClick = onOpenDocuments,
                modifier = Modifier.padding(top = 6.dp),
            ) {
                Text(stringResource(R.string.nav_documents))
            }
        }
    }
}
