package com.vedica.labs.ind.app.docora.ui.screens

enum class Screen(
    val route: String,
) {
    Home("home"),
    Documents("documents"),
    Category("category/{category}"),
    Tools("tools"),
    Scanner("scanner"),
    Search("search"),
    Details("details/{documentId}"),
    Viewer("viewer/{documentId}"),
    PdfEditor("pdfeditor/{documentId}/{page}"),
    PdfTools("pdftools/{documentId}"),
    Settings("settings"),
    ;

    companion object {
        const val ARG_DOCUMENT_ID = "documentId"
        const val ARG_CATEGORY = "category"
        const val ARG_PAGE = "page"

        fun detailsRoute(documentId: String) = "details/$documentId"
        fun viewerRoute(documentId: String) = "viewer/$documentId"
        fun pdfEditorRoute(documentId: String, page: Int) = "pdfeditor/$documentId/$page"
        fun pdfToolsRoute(documentId: String) = "pdftools/$documentId"
        fun categoryRoute(category: String) = "category/$category"
    }
}
