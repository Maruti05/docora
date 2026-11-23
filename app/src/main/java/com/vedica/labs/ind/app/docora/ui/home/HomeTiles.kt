package com.vedica.labs.ind.app.docora.ui.home

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Badge
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.FlightTakeoff
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocalHospital
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Work
import androidx.compose.ui.graphics.vector.ImageVector
import com.vedica.labs.ind.app.docora.R
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentCategory
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.util.DateFormatter
import com.vedica.labs.ind.app.docora.core.util.FileSizeFormatter

/**
 * Icon and label mappings shared by the dashboard and the browser.
 *
 * The mappings live in one file so a category never gets a different glyph on different screens.
 * They are plain glyphs by design: the visual system uses one neutral tint everywhere, so the
 * meaning comes from the shape and the label, not from a per-item colour.
 */

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

fun categoryNameRes(category: DocumentCategory): Int = when (category) {
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

fun typeIcon(type: DocumentType): ImageVector = when (type) {
    DocumentType.PDF -> Icons.Filled.PictureAsPdf
    DocumentType.IMAGE -> Icons.Filled.Image
    DocumentType.TEXT, DocumentType.WORD_DOCUMENT -> Icons.Filled.Description
    DocumentType.ARCHIVE -> Icons.Filled.UploadFile
    else -> Icons.Filled.Description
}

/** One-line meta for a document row: type, size and when it was last modified. */
fun documentMeta(document: Document): String =
    "${document.type.label} · ${FileSizeFormatter.format(document.sizeBytes)} · " +
        DateFormatter.formatRelative(document.modifiedAt)
