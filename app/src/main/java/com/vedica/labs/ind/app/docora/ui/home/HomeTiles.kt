package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.util.DateFormatter
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * Icon and colour mapping for the home screen.
 *
 * Keeping every mapping in one file means the dashboard never invents a glyph or a tint inline:
 * a category looks the same in the collection rail, the category rail and the browser. The
 * [HomeCollection] table is also the single source of truth for the shortcut rail's order, so the
 * composable below stays a pure renderer.
 */

/** The library shortcuts shown on the dashboard, in display order. */
enum class HomeCollection(
    val titleRes: Int,
    val icon: ImageVector,
) {
    ALL(R.string.collection_all, Icons.Filled.Folder),
    FAVOURITES(R.string.collection_favorites, Icons.Filled.Star),
    SCANS(R.string.collection_scans, Icons.Filled.DocumentScanner),
    PDFS(R.string.collection_pdfs, Icons.Filled.PictureAsPdf),
    IMAGES(R.string.collection_images, Icons.Filled.Image),
}

/** The live count behind a shortcut; every value comes from the dashboard aggregate, never a scan. */
fun collectionCount(collection: HomeCollection, state: HomeUiState): Int = when (collection) {
    HomeCollection.ALL -> state.stats.totalDocuments
    HomeCollection.FAVOURITES -> state.stats.favorites
    HomeCollection.SCANS -> state.stats.scans
    HomeCollection.PDFS -> state.stats.pdfs
    HomeCollection.IMAGES -> state.stats.images
}

/**
 * Category accent palette.
 *
 * These are deliberately mid-tone hues: each reads on a light paper background and on the dark
 * surface without being re-tuned, because the tile draws the accent at low alpha as a bubble and at
 * full strength for the glyph. They sit beside the file-type badges in the design system rather
 * than replacing them - a document's type and its category are different signals.
 */
private val AccentBlue = Color(0xFF3C7DBF)
private val AccentGreen = Color(0xFF2C6E49)
private val AccentAmber = Color(0xFF9A7415)
private val AccentPurple = Color(0xFF6A4CA8)
private val AccentTeal = Color(0xFF1F8A80)
private val AccentOrange = Color(0xFFB3541E)
private val AccentRed = Color(0xFFC0392B)
private val AccentSlate = Color(0xFF5B6360)

/** Neutral accent used by the shortcut tiles whose meaning is "everything". */
val HomeNeutralAccent: Color = AccentBlue

/** A distinct, meaningful glyph for every category (PRD §19). */
fun categoryIcon(category: DocumentCategory): ImageVector = when (category) {
    DocumentCategory.WORK -> Icons.Filled.Work
    DocumentCategory.FINANCE -> Icons.Filled.AccountBalance
    DocumentCategory.PERSONAL -> Icons.Filled.Person
    DocumentCategory.IDENTITY -> Icons.Filled.Badge
    DocumentCategory.TRAVEL -> Icons.Filled.FlightTakeoff
    DocumentCategory.VEHICLE -> Icons.Filled.DirectionsCar
    DocumentCategory.MEDICAL -> Icons.Filled.LocalHospital
    DocumentCategory.EDUCATION -> Icons.Filled.School
    DocumentCategory.RECEIPTS -> Icons.AutoMirrored.Filled.ReceiptLong
    DocumentCategory.IMPORTANT -> Icons.Filled.Bookmark
    DocumentCategory.OTHER -> Icons.Filled.Category
}

/** The matching accent for [categoryIcon]. */
fun categoryAccent(category: DocumentCategory): Color = when (category) {
    DocumentCategory.WORK -> AccentBlue
    DocumentCategory.FINANCE -> AccentGreen
    DocumentCategory.PERSONAL -> AccentAmber
    DocumentCategory.IDENTITY -> AccentPurple
    DocumentCategory.TRAVEL -> AccentTeal
    DocumentCategory.VEHICLE -> AccentOrange
    DocumentCategory.MEDICAL -> AccentRed
    DocumentCategory.EDUCATION -> AccentBlue
    DocumentCategory.RECEIPTS -> AccentOrange
    DocumentCategory.IMPORTANT -> AccentPurple
    DocumentCategory.OTHER -> AccentSlate
}

/** Accent paired with a shortcut tile, so the rail is colourful without being noisy. */
fun collectionAccent(collection: HomeCollection): Color = when (collection) {
    HomeCollection.ALL -> HomeNeutralAccent
    HomeCollection.FAVOURITES -> AccentAmber
    HomeCollection.SCANS -> AccentGreen
    HomeCollection.PDFS -> AccentRed
    HomeCollection.IMAGES -> AccentTeal
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

/** One-line meta for a document row: type, size and when it was last modified. */
fun documentMeta(document: Document): String =
    "${document.type.label} · ${FileSizeFormatter.format(document.sizeBytes)} · " +
        DateFormatter.formatRelative(document.modifiedAt)
