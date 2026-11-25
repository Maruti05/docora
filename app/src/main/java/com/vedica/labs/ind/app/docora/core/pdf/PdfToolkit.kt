package com.vedica.labs.ind.app.docora.core.pdf

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Matrix
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import com.tom_roush.pdfbox.cos.COSName
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDColor
import com.tom_roush.pdfbox.pdmodel.graphics.color.PDDeviceRGB
import com.tom_roush.pdfbox.pdmodel.graphics.image.JPEGFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.LosslessFactory
import com.tom_roush.pdfbox.pdmodel.graphics.image.PDImageXObject
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationText
import com.tom_roush.pdfbox.pdmodel.interactive.annotation.PDAnnotationTextMarkup
import com.vedica.labs.ind.app.docora.core.common.DocoraError
import com.vedica.labs.ind.app.docora.core.common.DocoraResult
import com.vedica.labs.ind.app.docora.core.common.DispatcherProvider
import com.vedica.labs.ind.app.docora.core.common.docoraRunCatching
import com.vedica.labs.ind.app.docora.core.files.AppStorage
import com.vedica.labs.ind.app.docora.core.model.Document
import com.vedica.labs.ind.app.docora.core.model.DocumentImportRequest
import com.vedica.labs.ind.app.docora.core.model.DocumentSource
import com.vedica.labs.ind.app.docora.core.model.DocumentType
import com.vedica.labs.ind.app.docora.core.repository.DocumentRepository
import com.vedica.labs.ind.app.docora.core.storage.SafGateway
import com.vedica.labs.ind.app.docora.core.util.FileNameGenerator
import com.vedica.labs.ind.app.docora.core.util.MimeTypes
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.IdentityHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.math.max
import kotlin.math.min
import kotlin.math.atan2

/**
 * On-device PDF editing built on PDFBox-Android (PRD A�11, A�46).
 *
 * Design rules:
 *  * The selected document is **never modified in place**. Every operation writes a new
 *    file into the app sandbox and registers it in the library, which works for read-only
 *    SAF providers and matches the PRD's "do not destroy the original" rule.
 *  * Page rebuilding always imports pages into a fresh [PDDocument]. That copies only the
 *    objects a page actually references, so deleting pages or compressing images genuinely
 *    shrinks the output instead of leaving orphaned streams behind.
 *  * Large inputs are opened through [MemoryUsageSetting.setupMixed] so PDFBox spills to a
 *    scratch file instead of filling the heap on a low-memory phone.
 */
@Singleton
class PdfToolkit @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context,
    private val safGateway: SafGateway,
    private val appStorage: AppStorage,
    private val documentRepository: DocumentRepository,
    private val unlockStore: PdfUnlockStore,
    private val dispatchers: DispatcherProvider,
) {

    suspend fun readInfo(uri: String): DocoraResult<PdfDocumentInfo> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            openDocument(Uri.parse(uri)).use { document ->
                PdfDocumentInfo(
                    pageCount = document.numberOfPages,
                    metadata = document.documentInformation.toMetadata(),
                )
            }
        }
    }

    /**
     * Persists a page arrangement: reorder, delete, duplicate, rotate and extract all share
     * this operation because they are all "which source pages, in what order, with what
     * extra rotation".
     */
    suspend fun organize(
        document: Document,
        pages: List<PdfPageSpec>,
        suffix: String,
        outputName: String? = null,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            require(pages.isNotEmpty()) { "Select at least one page" }
            openDocument(Uri.parse(document.uri)).use { source ->
                val output = PDDocument()
                try {
                    pages.forEach { spec ->
                        val page = source.getPage(spec.sourceIndex)
                        val imported = output.importPage(page)
                        imported.rotation = ((imported.rotation + spec.rotationDegrees) % 360 + 360) % 360
                    }
                    copyMetadata(source, output)
                    output.documentInformation.title = outputTitle(document, suffix)
                    importResult(output, document, suffix, outputName)
                } finally {
                    runCatching { output.close() }
                }
            }
        }
    }

    /** Splits into one output per supplied (1-based) page range. */
    suspend fun split(
        document: Document,
        ranges: List<IntRange>,
        outputName: String? = null,
    ): DocoraResult<List<Document>> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            require(ranges.isNotEmpty()) { "Select at least one range" }
            openDocument(Uri.parse(document.uri)).use { source ->
                val outputs = ArrayList<Document>(ranges.size)
                ranges.forEachIndexed { index, range ->
                    val output = PDDocument()
                    try {
                        val first = range.first.coerceIn(1, source.numberOfPages)
                        val last = range.last.coerceIn(first, source.numberOfPages)
                        for (pageNumber in first..last) {
                            output.importPage(source.getPage(pageNumber - 1))
                        }
                        copyMetadata(source, output)
                        val suffix = "split-${index + 1}"
                        output.documentInformation.title = outputTitle(document, suffix)
                        outputs.add(
                            importResult(output, document, suffix, outputName?.let { appendCounter(it, index) }),
                        )
                    } finally {
                        runCatching { output.close() }
                    }
                }
                outputs
            }
        }
    }

    /** Merges PDFs in the order supplied. */
    suspend fun merge(
        documents: List<Document>,
        outputName: String? = null,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            require(documents.isNotEmpty()) { "Select at least one PDF" }
            val sources = documents.map { openDocument(Uri.parse(it.uri)) }
            val output = PDDocument()
            try {
                sources.forEach { source ->
                    source.pages.forEach { page -> output.importPage(page) }
                }
                copyMetadata(sources.first(), output)
                output.documentInformation.title = outputTitle(documents.first(), "merged")
                importResult(output, documents.first(), "merged", outputName)
            } finally {
                runCatching { output.close() }
                sources.forEach { runCatching { it.close() } }
            }
        }
    }

    /** Compresses embedded images while leaving the text layer untouched (PRD A�46). */
    suspend fun compress(
        document: Document,
        preset: PdfCompressionPreset,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            openDocument(Uri.parse(document.uri)).use { source ->
                if (preset != PdfCompressionPreset.ORIGINAL) {
                    downscaleImages(source, preset)
                }
                val output = PDDocument()
                try {
                    source.pages.forEach { page -> output.importPage(page) }
                    copyMetadata(source, output)
                    output.documentInformation.title = outputTitle(document, "compressed")
                    importResult(output, document, "compressed")
                } finally {
                    runCatching { output.close() }
                }
            }
        }
    }

    suspend fun updateMetadata(
        document: Document,
        metadata: PdfMetadata,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            openDocument(Uri.parse(document.uri)).use { source ->
                val output = PDDocument()
                try {
                    source.pages.forEach { page -> output.importPage(page) }
                    output.documentInformation.apply {
                        title = metadata.title.ifBlank { document.title }
                        author = metadata.author
                        subject = metadata.subject
                        keywords = metadata.keywords
                        creator = "Docora"
                        producer = "Docora"
                    }
                    importResult(output, document, "metadata")
                } finally {
                    runCatching { output.close() }
                }
            }
        }
    }

    /** Turns picked images into a multi-page PDF (PRD A�11). */
    suspend fun imagesToPdf(
        images: List<Uri>,
        options: PdfImageOptions,
        outputName: String? = null,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            require(images.isNotEmpty()) { "Pick at least one image" }
            val output = PDDocument()
            try {
                images.forEach { imageUri ->
                    val bitmap = decodeImage(imageUri, options.maxImageEdgePx)
                        ?: throw DocoraError.DocumentMissing()
                    try {
                        val (pageWidth, pageHeight) = resolvePageSize(options, bitmap)
                        val page = PDPage(PDRectangle(pageWidth, pageHeight))
                        output.addPage(page)
                        val image = if (bitmap.hasAlpha()) {
                            LosslessFactory.createFromImage(output, bitmap)
                        } else {
                            JPEGFactory.createFromImage(
                                output,
                                bitmap,
                                (options.jpegQuality.coerceIn(30, 100)) / 100f,
                            )
                        }
                        val contentWidth = (pageWidth - options.marginPoints * 2f).coerceAtLeast(1f)
                        val contentHeight = (pageHeight - options.marginPoints * 2f).coerceAtLeast(1f)
                        val scale = min(contentWidth / bitmap.width, contentHeight / bitmap.height)
                        val drawWidth = bitmap.width * scale
                        val drawHeight = bitmap.height * scale
                        val x = (pageWidth - drawWidth) / 2f
                        val y = (pageHeight - drawHeight) / 2f
                        PDPageContentStream(
                            output,
                            page,
                            PDPageContentStream.AppendMode.OVERWRITE,
                            true,
                            true,
                        ).use { stream ->
                            stream.drawImage(image, x, y, drawWidth, drawHeight)
                        }
                    } finally {
                        bitmap.recycle()
                    }
                }
                output.documentInformation.title = outputName?.substringBeforeLast('.') ?: "Images"
                val stem = outputName ?: FileNameGenerator.forScan(System.currentTimeMillis(), "pdf", null, "Images")
                val dummy = Document(
                    id = "images",
                    uri = "",
                    displayName = stem,
                    mimeType = MimeTypes.PDF,
                    extension = "pdf",
                    sizeBytes = 0,
                    type = DocumentType.PDF,
                    createdAt = System.currentTimeMillis(),
                    modifiedAt = System.currentTimeMillis(),
                    lastOpenedAt = null,
                    pageCount = images.size,
                    width = null,
                    height = null,
                    folderId = null,
                    isFavorite = false,
                    isTrashed = false,
                    trashedAt = null,
                    isEncrypted = false,
                    source = DocumentSource.CREATED,
                    textIndexStatus = com.vedica.labs.ind.app.docora.core.model.TextIndexStatus.NOT_PROCESSED,
                    ocrStatus = com.vedica.labs.ind.app.docora.core.model.OcrStatus.NOT_APPLICABLE,
                    textOrigin = com.vedica.labs.ind.app.docora.core.model.TextOrigin.NONE,
                    category = null,
                    checksumSha256 = null,
                    thumbnailPath = null,
                    providerAuthority = null,
                    indexedAt = null,
                )
                importResult(output, dummy, "images", outputName)
            } finally {
                runCatching { output.close() }
            }
        }
    }

    /**
     * Writes viewer marks into a copy of the PDF.
     *
     * Text markup becomes real PDF annotations, sticky notes become note annotations, and
     * everything that has to look identical in every viewer - freehand ink, text boxes, shapes
     * and blackout rectangles - is appended to the page content stream as vector content.
     * Embedding that content avoids depending on an annotation appearance handler that older
     * PDF viewers may not render.
     */
    suspend fun applyAnnotations(
        document: Document,
        marks: List<PdfMark>,
    ): DocoraResult<Document> = io {
        docoraRunCatching({
            DocoraError.CorruptedDocument(it)
        }) {
            require(marks.isNotEmpty()) { "Draw something first" }
            openDocument(Uri.parse(document.uri)).use { source ->
                marks.filter { it.kind.isAnnotation() }.forEach { mark ->
                    if (mark.pageIndex !in 0 until source.numberOfPages) return@forEach
                    val page = source.getPage(mark.pageIndex)
                    when (mark.kind) {
                        PdfMarkKind.HIGHLIGHT,
                        PdfMarkKind.UNDERLINE,
                        PdfMarkKind.STRIKEOUT,
                        PdfMarkKind.SQUIGGLY,
                        -> addTextMarkup(source, page, mark)
                        PdfMarkKind.NOTE -> addNote(page, mark)
                        else -> Unit
                    }
                }
                marks
                    .filterNot { it.kind.isAnnotation() }
                    .groupBy { it.pageIndex }
                    .forEach { (pageIndex, pageMarks) ->
                        if (pageIndex in 0 until source.numberOfPages) {
                            drawContent(source, source.getPage(pageIndex), pageMarks)
                        }
                    }
                val output = PDDocument()
                try {
                    source.pages.forEach { page -> output.importPage(page) }
                    copyMetadata(source, output)
                    output.documentInformation.title = outputTitle(document, "annotated")
                    importResult(output, document, "annotated")
                } finally {
                    runCatching { output.close() }
                }
            }
        }
    }

    private fun PdfMarkKind.isAnnotation(): Boolean = when (this) {
        PdfMarkKind.HIGHLIGHT,
        PdfMarkKind.UNDERLINE,
        PdfMarkKind.STRIKEOUT,
        PdfMarkKind.SQUIGGLY,
        PdfMarkKind.NOTE,
        -> true
        else -> false
    }

    // ------------------------------------------------------------------ internals

    private fun openDocument(uri: Uri): PDDocument {
        // A document unlocked for reading is transparently read from its decrypted copy, so tools
        // and the page editor keep working on password-protected files for the rest of the session.
        val source = unlockStore.resolve(uri.toString()) ?: uri.toString()
        val stream = safGateway.openInputStream(Uri.parse(source)) ?: throw DocoraError.DocumentMissing()
        return stream.use { input ->
            PDDocument.load(input, MemoryUsageSetting.setupMixed(MAX_HEAP_BUFFER_BYTES))
        }
    }

    private suspend fun importResult(
        output: PDDocument,
        source: Document,
        suffix: String,
        requestedName: String? = null,
    ): Document {
        val fileName = requestedName
            ?.takeIf { it.isNotBlank() }
            ?.let { if (it.endsWith(".pdf", ignoreCase = true)) it else "$it.pdf" }
            ?: FileNameGenerator.forDerived(source.displayName, suffix, "pdf")
        val file = appStorage.newDocumentPath(fileName)
        try {
            FileOutputStream(file).use { stream -> output.save(stream) }
        } catch (error: Throwable) {
            file.delete()
            throw error
        }
        val request = DocumentImportRequest(
            uri = appStorage.uriFor(file),
            displayName = file.name,
            mimeType = MimeTypes.PDF,
            extension = "pdf",
            sizeBytes = file.length(),
            type = DocumentType.PDF,
            source = DocumentSource.CREATED,
            createdAt = System.currentTimeMillis(),
            modifiedAt = System.currentTimeMillis(),
            folderId = source.folderId,
            pageCount = output.numberOfPages,
        )
        return documentRepository.import(request)
    }

    private fun outputTitle(document: Document, suffix: String): String =
        FileNameGenerator.forDerived(document.displayName, suffix, "pdf").substringBeforeLast('.')

    private fun copyMetadata(source: PDDocument, target: PDDocument) {
        val from = source.documentInformation
        target.documentInformation.apply {
            title = from.title
            author = from.author
            subject = from.subject
            keywords = from.keywords
            creator = from.creator ?: "Docora"
            producer = from.producer ?: "Docora"
        }
    }

    private fun com.tom_roush.pdfbox.pdmodel.PDDocumentInformation.toMetadata(): PdfMetadata =
        PdfMetadata(
            title = title.orEmpty(),
            author = author.orEmpty(),
            subject = subject.orEmpty(),
            keywords = keywords.orEmpty(),
        )

    private fun downscaleImages(document: PDDocument, preset: PdfCompressionPreset) {
        val alreadyHandled = IdentityHashMap<Any, PDImageXObject>()
        document.pages.forEach { page ->
            val resources = page.resources ?: return@forEach
            val xObjects = resources.cosObject.getCOSDictionary(COSName.XOBJECT) ?: return@forEach
            for (name in resources.xObjectNames) {
                val xObject = runCatching { resources.getXObject(name) }.getOrNull() as? PDImageXObject
                    ?: continue
                if (xObject.isStencil) continue
                if (xObject.cosObject.getDictionaryObject(COSName.SMASK) != null) continue
                if (xObject.cosObject.getDictionaryObject(COSName.MASK) != null) continue
                val existing = alreadyHandled[xObject.cosObject]
                if (existing != null) {
                    xObjects.setItem(name, existing.cosObject)
                    continue
                }
                val bitmap = runCatching { xObject.image }.getOrNull() ?: continue
                val scaled = scaleDown(bitmap, preset.maxImageEdgePx)
                val replacement = runCatching {
                    if (scaled.hasAlpha()) {
                        LosslessFactory.createFromImage(document, scaled)
                    } else {
                        JPEGFactory.createFromImage(document, scaled, preset.jpegQuality)
                    }
                }.getOrNull()
                if (scaled !== bitmap) scaled.recycle()
                bitmap.recycle()
                if (replacement != null) {
                    xObjects.setItem(name, replacement.cosObject)
                    alreadyHandled[xObject.cosObject] = replacement
                }
            }
        }
    }

    private fun scaleDown(bitmap: Bitmap, maxEdge: Int): Bitmap {
        if (maxEdge <= 0) return bitmap
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdge) return bitmap
        val ratio = maxEdge.toFloat() / longest
        val width = (bitmap.width * ratio).toInt().coerceAtLeast(1)
        val height = (bitmap.height * ratio).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }

    private fun decodeImage(uri: Uri, maxEdge: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        safGateway.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        val safeMaxEdge = maxEdge.coerceAtLeast(400)
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= safeMaxEdge) {
            sample *= 2
        }
        val options = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val decoded = safGateway.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        } ?: return null
        return applyExifOrientation(uri, decoded)
    }

    private fun applyExifOrientation(uri: Uri, bitmap: Bitmap): Bitmap {
        val orientation = runCatching {
            safGateway.openInputStream(uri)?.use { input ->
                ExifInterface(input).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL,
                )
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            else -> return bitmap
        }
        return runCatching {
            val rotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            if (rotated !== bitmap) bitmap.recycle()
            rotated
        }.getOrDefault(bitmap)
    }

    private fun resolvePageSize(options: PdfImageOptions, bitmap: Bitmap): Pair<Float, Float> {
        var width: Float
        var height: Float
        when (options.pageSize) {
            PdfPageSize.A4 -> {
                width = A4_WIDTH
                height = A4_HEIGHT
            }
            PdfPageSize.LETTER -> {
                width = LETTER_WIDTH
                height = LETTER_HEIGHT
            }
            PdfPageSize.FIT -> {
                val scale = 72f / FIT_DPI
                width = bitmap.width * scale
                height = bitmap.height * scale
                val longest = max(width, height)
                if (longest > MAX_FIT_EDGE) {
                    val shrink = MAX_FIT_EDGE / longest
                    width *= shrink
                    height *= shrink
                }
            }
        }
        val landscape = when (options.orientation) {
            PdfOrientation.LANDSCAPE -> true
            PdfOrientation.PORTRAIT -> false
            PdfOrientation.AUTO -> bitmap.width > bitmap.height
        }
        if (options.pageSize == PdfPageSize.FIT) {
            if ((landscape && width < height) || (!landscape && width > height)) {
                val swap = width
                width = height
                height = swap
            }
        } else if (landscape && width < height) {
            val swap = width
            width = height
            height = swap
        } else if (!landscape && width > height) {
            val swap = width
            width = height
            height = swap
        }
        return width to height
    }

    private fun addTextMarkup(document: PDDocument, page: PDPage, mark: PdfMark) {
        val start = mark.start ?: return
        val end = mark.end ?: return
        val box = page.cropBox
        val rotation = ((page.rotation + mark.displayRotation) % 360 + 360) % 360
        val left = min(start.x, end.x)
        val right = max(start.x, end.x)
        var top = min(start.y, end.y)
        var bottom = max(start.y, end.y)
        // Underline and strikeout are thin bands, so the annotation's quad has to be narrowed:
        // PDFBox paints the line at the bottom edge of whatever rectangle it is given.
        when (mark.kind) {
            PdfMarkKind.UNDERLINE -> top = (bottom - 0.012f).coerceAtLeast(top)
            PdfMarkKind.STRIKEOUT -> {
                val middle = (top + bottom) / 2f
                top = middle - 0.008f
                bottom = middle + 0.008f
            }
            else -> Unit
        }
        val topLeft = toPdfPoint(left, top, rotation, box)
        val topRight = toPdfPoint(right, top, rotation, box)
        val bottomLeft = toPdfPoint(left, bottom, rotation, box)
        val bottomRight = toPdfPoint(right, bottom, rotation, box)
        val xs = floatArrayOf(topLeft[0], topRight[0], bottomLeft[0], bottomRight[0])
        val ys = floatArrayOf(topLeft[1], topRight[1], bottomLeft[1], bottomRight[1])
        val minX = xs.min()
        val maxX = xs.max()
        val minY = ys.min()
        val maxY = ys.max()
        if (maxX - minX < 0.5f || maxY - minY < 0.5f) return
        val subtype = when (mark.kind) {
            PdfMarkKind.UNDERLINE -> PDAnnotationTextMarkup.SUB_TYPE_UNDERLINE
            PdfMarkKind.STRIKEOUT -> PDAnnotationTextMarkup.SUB_TYPE_STRIKEOUT
            PdfMarkKind.SQUIGGLY -> PDAnnotationTextMarkup.SUB_TYPE_SQUIGGLY
            else -> PDAnnotationTextMarkup.SUB_TYPE_HIGHLIGHT
        }
        val annotation = PDAnnotationTextMarkup(subtype).apply {
            setQuadPoints(
                floatArrayOf(
                    topLeft[0], topLeft[1],
                    topRight[0], topRight[1],
                    bottomLeft[0], bottomLeft[1],
                    bottomRight[0], bottomRight[1],
                ),
            )
            setRectangle(PDRectangle(minX, minY, maxX - minX, maxY - minY))
            setColor(mark.colorArgb.toPdColor())
            setConstantOpacity(if (mark.kind == PdfMarkKind.HIGHLIGHT) 0.38f else 0.9f)
            setContents("Docora ${mark.kind.name.lowercase()}")
            setPrinted(true)
        }
        page.annotations.add(annotation)
        runCatching { annotation.constructAppearances(document) }
    }

    private fun addNote(page: PDPage, mark: PdfMark) {
        val start = mark.start ?: return
        val box = page.cropBox
        val rotation = ((page.rotation + mark.displayRotation) % 360 + 360) % 360
        val point = toPdfPoint(start.x, start.y, rotation, box)
        val annotation = PDAnnotationText().apply {
            setRectangle(PDRectangle(point[0] - 9f, point[1] - 9f, 18f, 18f))
            setContents(mark.note.orEmpty().ifBlank { "Note" })
            setName(PDAnnotationText.NAME_COMMENT)
            setColor(mark.colorArgb.toPdColor())
            setOpen(false)
            setPrinted(true)
        }
        page.annotations.add(annotation)
        runCatching { annotation.constructAppearances() }
    }

    /**
     * Paints the marks that must look identical in every viewer into the page content stream:
     * freehand ink, signature strokes, text boxes, shapes and blackout rectangles.
     *
     * They share one stream per page, which keeps a page with dozens of marks to a single
     * appended content section instead of one per mark.
     */
    private fun drawContent(document: PDDocument, page: PDPage, marks: List<PdfMark>) {
        if (marks.isEmpty()) return
        val box = page.cropBox
        PDPageContentStream(
            document,
            page,
            PDPageContentStream.AppendMode.APPEND,
            true,
            true,
        ).use { stream ->
            stream.setLineCapStyle(1)
            stream.setLineJoinStyle(1)
            marks.forEach { mark ->
                val rotation = ((page.rotation + mark.displayRotation) % 360 + 360) % 360
                when (mark.kind) {
                    PdfMarkKind.INK -> drawInk(stream, mark, box, rotation)
                    PdfMarkKind.TEXT -> drawTextBox(stream, mark, box, rotation)
                    PdfMarkKind.RECTANGLE,
                    PdfMarkKind.ELLIPSE,
                    PdfMarkKind.LINE,
                    PdfMarkKind.ARROW,
                    PdfMarkKind.REDACT,
                    -> drawShape(stream, mark, box, rotation)
                    else -> Unit
                }
            }
        }
    }

    private fun drawInk(
        stream: PDPageContentStream,
        mark: PdfMark,
        box: PDRectangle,
        rotation: Int,
    ) {
        if (mark.points.size < 2) return
        applyStroke(stream, mark.colorArgb, mark.strokeWidth)
        var started = false
        mark.points.forEach { point ->
            val mapped = toPdfPoint(point.x, point.y, rotation, box)
            if (!started) {
                stream.moveTo(mapped[0], mapped[1])
                started = true
            } else {
                stream.lineTo(mapped[0], mapped[1])
            }
        }
        if (started) stream.stroke()
    }

    private fun drawTextBox(
        stream: PDPageContentStream,
        mark: PdfMark,
        box: PDRectangle,
        rotation: Int,
    ) {
        val start = mark.start ?: return
        val text = mark.note.orEmpty().trim()
        if (text.isEmpty()) return
        val point = toPdfPoint(start.x, start.y, rotation, box)
        val fontSize = mark.fontSize.coerceIn(6f, 72f)
        val color = mark.colorArgb
        val opened = runCatching { stream.beginText() }.isSuccess
        if (!opened) return
        try {
            stream.setNonStrokingColor(Color.red(color), Color.green(color), Color.blue(color))
            stream.setFont(PDType1Font.HELVETICA, fontSize)
            stream.setLeading(fontSize * 1.3f)
            // The tap marks the top-left of the box, while PDF text is laid out from a baseline.
            stream.newLineAtOffset(point[0], point[1] - fontSize)
            text.lines().take(MAX_TEXT_LINES).forEachIndexed { index, line ->
                if (index > 0) stream.newLine()
                stream.showText(line)
            }
        } catch (_: Throwable) {
            // Standard fonts cover Latin text only; an unsupported glyph skips that text box
            // rather than failing the whole save.
        } finally {
            runCatching { stream.endText() }
        }
    }

    private fun drawShape(
        stream: PDPageContentStream,
        mark: PdfMark,
        box: PDRectangle,
        rotation: Int,
    ) {
        val start = mark.start ?: return
        val end = mark.end ?: return
        val first = toPdfPoint(start.x, start.y, rotation, box)
        val second = toPdfPoint(end.x, end.y, rotation, box)
        val left = min(first[0], second[0])
        val right = max(first[0], second[0])
        val bottom = min(first[1], second[1])
        val top = max(first[1], second[1])
        val width = right - left
        val height = top - bottom
        val color = mark.colorArgb
        when (mark.kind) {
            PdfMarkKind.RECTANGLE -> {
                if (width < 0.5f || height < 0.5f) return
                applyStroke(stream, color, mark.strokeWidth)
                stream.addRect(left, bottom, width, height)
                stream.stroke()
            }
            PdfMarkKind.ELLIPSE -> {
                if (width < 1f || height < 1f) return
                applyStroke(stream, color, mark.strokeWidth)
                addEllipse(stream, left, bottom, width, height)
                stream.stroke()
            }
            PdfMarkKind.LINE -> {
                if (width < 0.5f && height < 0.5f) return
                applyStroke(stream, color, mark.strokeWidth)
                stream.moveTo(first[0], first[1])
                stream.lineTo(second[0], second[1])
                stream.stroke()
            }
            PdfMarkKind.ARROW -> {
                if (width < 0.5f && height < 0.5f) return
                applyStroke(stream, color, mark.strokeWidth)
                stream.moveTo(first[0], first[1])
                stream.lineTo(second[0], second[1])
                stream.stroke()
                val angle = atan2(
                    (second[1] - first[1]).toDouble(),
                    (second[0] - first[0]).toDouble(),
                )
                val head = max(7f, mark.strokeWidth * 4f)
                val spread = Math.toRadians(24.0)
                val headX1 = (second[0] - head * kotlin.math.cos(angle - spread)).toFloat()
                val headY1 = (second[1] - head * kotlin.math.sin(angle - spread)).toFloat()
                val headX2 = (second[0] - head * kotlin.math.cos(angle + spread)).toFloat()
                val headY2 = (second[1] - head * kotlin.math.sin(angle + spread)).toFloat()
                stream.moveTo(second[0], second[1])
                stream.lineTo(headX1, headY1)
                stream.moveTo(second[0], second[1])
                stream.lineTo(headX2, headY2)
                stream.stroke()
            }
            PdfMarkKind.REDACT -> {
                if (width < 0.5f || height < 0.5f) return
                stream.setNonStrokingColor(
                    Color.red(color),
                    Color.green(color),
                    Color.blue(color),
                )
                stream.addRect(left, bottom, width, height)
                stream.fill()
            }
            else -> Unit
        }
    }

    private fun applyStroke(stream: PDPageContentStream, colorArgb: Int, strokeWidth: Float) {
        stream.setStrokingColor(
            Color.red(colorArgb),
            Color.green(colorArgb),
            Color.blue(colorArgb),
        )
        stream.setLineWidth(strokeWidth.coerceIn(0.5f, 16f))
    }

    /** Four cubic Béziers, the standard approximation of an ellipse in PDF. */
    private fun addEllipse(
        stream: PDPageContentStream,
        left: Float,
        bottom: Float,
        width: Float,
        height: Float,
    ) {
        val k = 0.5522848f
        val cx = left + width / 2f
        val cy = bottom + height / 2f
        val rx = width / 2f
        val ry = height / 2f
        stream.moveTo(cx + rx, cy)
        stream.curveTo(cx + rx, cy + k * ry, cx + k * rx, cy + ry, cx, cy + ry)
        stream.curveTo(cx - k * rx, cy + ry, cx - rx, cy + k * ry, cx - rx, cy)
        stream.curveTo(cx - rx, cy - k * ry, cx - k * rx, cy - ry, cx, cy - ry)
        stream.curveTo(cx + k * rx, cy - ry, cx + rx, cy - k * ry, cx + rx, cy)
        stream.closePath()
    }

    /**
     * Maps a normalised, displayed point back to unrotated PDF user space.
     *
     * PDF's origin is the bottom-left corner while the viewer's is the top-left, so the
     * vertical coordinate is flipped first. Rotation is then undone one quadrant at a time.
     */
    private fun toPdfPoint(
        x: Float,
        y: Float,
        rotation: Int,
        box: PDRectangle,
    ): FloatArray {
        val width = box.width
        val height = box.height
        val mapped = when (rotation) {
            90 -> floatArrayOf(y * width, x * height)
            180 -> floatArrayOf((1f - x) * width, (1f - y) * height)
            270 -> floatArrayOf((1f - y) * width, (1f - x) * height)
            else -> floatArrayOf(x * width, (1f - y) * height)
        }
        return floatArrayOf(mapped[0] + box.lowerLeftX, mapped[1] + box.lowerLeftY)
    }

    private fun Int.toPdColor(): PDColor = PDColor(
        floatArrayOf(
            Color.red(this) / 255f,
            Color.green(this) / 255f,
            Color.blue(this) / 255f,
        ),
        PDDeviceRGB.INSTANCE,
    )

    private fun appendCounter(name: String, index: Int): String {
        val base = name.substringBeforeLast('.', name)
        return "$base-${index + 1}.pdf"
    }

    private suspend fun <T> io(block: suspend () -> T): T = withContext(dispatchers.io) { block() }

    private companion object {
        const val MAX_HEAP_BUFFER_BYTES = 32L * 1024 * 1024
        const val MAX_TEXT_LINES = 12
        const val A4_WIDTH = 595.28f
        const val A4_HEIGHT = 841.89f
        const val LETTER_WIDTH = 612f
        const val LETTER_HEIGHT = 792f
        const val FIT_DPI = 150f
        const val MAX_FIT_EDGE = 1684f
    }
}
