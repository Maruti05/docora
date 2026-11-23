package com.vedica.labs.ind.app.docora.core.pdf

import com.google.common.truth.Truth.assertThat
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * Verifies that the text-run collector used by the page editor produces normalised rectangles
 * that sit inside the page, which is what selection and highlight depend on.
 */
@RunWith(RobolectricTestRunner::class)
class PdfTextExtractorTest {

    @Before
    fun setUp() {
        PDFBoxResourceLoader.init(RuntimeEnvironment.getApplication())
    }

    @Test
    fun `collects word rectangles in displayed page space`() {
        val document = PDDocument()
        try {
            val page = PDPage(PDRectangle(200f, 300f))
            document.addPage(page)
            PDPageContentStream(document, page).use { stream ->
                stream.beginText()
                stream.setFont(PDType1Font.HELVETICA, 14f)
                stream.newLineAtOffset(20f, 260f)
                stream.showText("Hello world")
                stream.endText()
            }

            val stripper = PdfTextExtractor.PositionStripper(
                pageWidth = 200f,
                pageHeight = 300f,
            ).apply {
                startPage = 1
                endPage = 1
                sortByPosition = true
            }
            stripper.getText(document)

            val runs = stripper.runs
            assertThat(runs).isNotEmpty()
            assertThat(runs.joinToString(separator = " ") { it.text }).contains("Hello")
            assertThat(runs.joinToString(separator = " ") { it.text }).contains("world")
            runs.forEach { run ->
                assertThat(run.left).isAtLeast(0f)
                assertThat(run.top).isAtLeast(0f)
                assertThat(run.right).isAtMost(1f)
                assertThat(run.bottom).isAtMost(1f)
                assertThat(run.right).isAtLeast(run.left)
                assertThat(run.bottom).isAtLeast(run.top)
            }
            // "Hello world" is drawn near the top-left of a 200x300 page.
            val hello = runs.first { it.text.contains("Hello") }
            assertThat(hello.top).isLessThan(0.25f)
            assertThat(hello.left).isLessThan(0.25f)
        } finally {
            document.close()
        }
    }
}
