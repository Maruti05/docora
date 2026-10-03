package com.vedica.labs.ind.app.docora.ui.screens

enum class Screen(
    val route: String,
) {
    Home("home"),
    Documents("documents"),
    Scanner("scanner"),
    Search("search"),
    Details("details/{documentId}"),
    Viewer("viewer/{documentId}"),
    Settings("settings"),
    ;

    companion object {
        const val ARG_DOCUMENT_ID = "documentId"

        fun detailsRoute(documentId: String) = "details/$documentId"
        fun viewerRoute(documentId: String) = "viewer/$documentId"
    }
}
