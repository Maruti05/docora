package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentType

data class CollectionTileData(
    val titleRes: Int,
    val count: Int,
    val icon: ImageVector,
)

fun collectionTiles(state: HomeUiState): List<CollectionTileData> {
    return listOf(
        CollectionTileData(R.string.collection_all, state.stats.totalDocuments, Icons.Filled.Description),
        CollectionTileData(R.string.collection_recent, state.recentDocuments.size, Icons.Filled.History),
        CollectionTileData(R.string.collection_favorites, state.stats.favorites, Icons.Filled.Favorite),
        CollectionTileData(R.string.collection_scans, state.stats.scans, Icons.Filled.CameraAlt),
        CollectionTileData(R.string.collection_pdfs, state.stats.pdfs, Icons.Filled.PictureAsPdf),
        CollectionTileData(R.string.collection_images, state.stats.images, Icons.Filled.Image),
    )
}

fun categoryNameRes(category: DocumentCategory): Int {
    return when (category) {
        DocumentCategory.WORK -> R.string.category_work
        DocumentCategory.FINANCE -> R.string.category_finance
        DocumentCategory.PERSONAL -> R.string.category_personal
        DocumentCategory.IDENTITY -> R.string.category_identity
        DocumentCategory.TRAVEL -> R.string.category_travel
        DocumentCategory.VEHICLE -> R.string.category_vehicle
        DocumentCategory.MEDICAL -> R.string.category_medical
        DocumentCategory.EDUCATION -> R.string.category_education
        DocumentCategory.RECEIPTS -> R.string.category_receipts
        DocumentCategory.IMPORTANT -> R.string.category_important
        DocumentCategory.OTHER -> R.string.category_other
    }
}

fun categoryIcon(category: DocumentCategory): ImageVector {
    return when (category) {
        DocumentCategory.WORK -> Icons.Filled.Description
        DocumentCategory.FINANCE -> Icons.Filled.Star
        DocumentCategory.IDENTITY -> Icons.Filled.Badge
        DocumentCategory.TRAVEL -> Icons.Filled.Schedule
        DocumentCategory.VEHICLE -> Icons.Filled.Star
        DocumentCategory.MEDICAL -> Icons.Filled.Favorite
        DocumentCategory.EDUCATION -> Icons.Filled.Description
        DocumentCategory.RECEIPTS -> Icons.Filled.UploadFile
        DocumentCategory.PERSONAL -> Icons.Filled.Favorite
        DocumentCategory.IMPORTANT -> Icons.Filled.Star
        DocumentCategory.OTHER -> Icons.Filled.Description
    }
}

fun typeIcon(type: DocumentType): ImageVector {
    return when (type) {
        DocumentType.PDF -> Icons.Filled.PictureAsPdf
        DocumentType.IMAGE -> Icons.Filled.Image
        DocumentType.TEXT, DocumentType.WORD_DOCUMENT -> Icons.Filled.Description
        DocumentType.ARCHIVE -> Icons.Filled.UploadFile
        else -> Icons.Filled.Description
    }
}

@Composable
fun typeBackground(type: DocumentType): Color {
    val scheme = androidx.compose.material3.MaterialTheme.colorScheme
    val extended = com.vedica.labs.ind.app.docora.ui.designsystem.LocalDocoraExtendedColors.current
    return when (type) {
        DocumentType.PDF -> extended.pdfBadge
        DocumentType.IMAGE -> extended.imageBadge
        DocumentType.TEXT -> extended.textBadge
        DocumentType.WORD_DOCUMENT, DocumentType.SPREADSHEET, DocumentType.PRESENTATION -> extended.officeBadge
        DocumentType.ARCHIVE -> extended.archiveBadge
        DocumentType.UNKNOWN -> scheme.outline
    }
}

fun heroSubtitle(total: Int): String = "$total"

val UnusedHomeIcon = Icons.Filled.AutoAwesome
