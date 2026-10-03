package com.vedica.labs.ind.app.docora.core.model

/**
 * Rule-based document categories (PRD §19).
 *
 * Classification is deliberately deterministic: a keyword table plus the file type decides
 * the category. No model, no network and no account is involved, so the feature works
 * offline, is unit testable and can be replaced later by an optional on-device classifier
 * behind the same field on [Document].
 *
 * [titleKey] maps to the `category_*` string resources so the UI never hardcodes labels.
 */
enum class DocumentCategory(val titleKey: String, val keywords: List<String>) {
    WORK(
        titleKey = "category_work",
        keywords = listOf(
            "contract", "agreement", "report", "proposal", "meeting", "resume", "cv",
            "offer letter", "appraisal", "timesheet", "nda", "quotation",
        ),
    ),
    FINANCE(
        titleKey = "category_finance",
        keywords = listOf(
            "invoice", "receipt of payment", "salary", "payslip", "pay slip", "bank",
            "statement", "tax", "gst", "vat", "balance sheet", "ledger", "budget",
            "credit card", "loan", "emi", "mutual fund", "share", "portfolio",
        ),
    ),
    IDENTITY(
        titleKey = "category_identity",
        keywords = listOf(
            "passport", "aadhaar", "aadhar", "pan card", "pancard", "driving", "licence",
            "license", "voter", "social security", "ssn", "id card", "identity",
            "birth certificate", "marriage certificate", "visa",
        ),
    ),
    VEHICLE(
        titleKey = "category_vehicle",
        keywords = listOf(
            "insurance policy", "car", "bike", "vehicle", "rc book", "registration",
            "pollution", "puc", "fitness certificate", "service", "fuel",
        ),
    ),
    MEDICAL(
        titleKey = "category_medical",
        keywords = listOf(
            "prescription", "medical", "hospital", "discharge", "lab report", "pathology",
            "blood test", "x-ray", "mri", "ct scan", "insurance claim", "policy",
            "doctor", "diagnosis", "vaccination",
        ),
    ),
    EDUCATION(
        titleKey = "category_education",
        keywords = listOf(
            "certificate", "marksheet", "mark sheet", "transcript", "degree", "diploma",
            "admission", "syllabus", "assignment", "thesis", "project report", "result",
            "tuition", "fee",
        ),
    ),
    TRAVEL(
        titleKey = "category_travel",
        keywords = listOf(
            "ticket", "boarding", "flight", "train", "bus", "hotel", "booking",
            "itinerary", "reservation", "book my show", "purchase order trip", "visa",
        ),
    ),
    RECEIPTS(
        titleKey = "category_receipts",
        keywords = listOf(
            "receipt", "bill", "invoice copy", "order", "purchase", "warranty",
            "cash memo", "payment confirmation",
        ),
    ),
    PERSONAL(
        titleKey = "category_personal",
        keywords = listOf(
            "letter", "notes", "personal", "diary", "journal", "family", "photo",
            "menu", "recipe", "invitation", "greeting",
        ),
    ),
    IMPORTANT(
        titleKey = "category_important",
        keywords = listOf("urgent", "important", "final", "original", "do not delete"),
    ),
    OTHER(titleKey = "category_other", keywords = emptyList()),
    ;

    companion object {
        /**
         * Categories represented by a chip on the dashboard, in display order.
         *
         * [OTHER] is the catch-all and is deliberately left out: it is reachable from the
         * "all categories" sheet rather than occupying dashboard space.
         */
        val dashboard: List<DocumentCategory> = listOf(
            WORK,
            FINANCE,
            PERSONAL,
            IDENTITY,
            TRAVEL,
            VEHICLE,
            MEDICAL,
            EDUCATION,
            RECEIPTS,
            IMPORTANT,
        )

        val All: List<DocumentCategory> = entries

        /** Resolves a stored category name, defaulting to [OTHER] for unknown values. */
        fun fromName(name: String?): DocumentCategory = when (name) {
            null -> OTHER
            else -> entries.firstOrNull { it.name == name } ?: OTHER
        }
    }
}
