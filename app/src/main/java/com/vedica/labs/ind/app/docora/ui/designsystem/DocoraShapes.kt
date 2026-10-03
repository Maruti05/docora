package com.vedica.labs.ind.app.docora.ui.designsystem

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/**
 * Shape scale.
 *
 * Docora deliberately avoids the "everything is a rounded card" look (PRD §23): corners
 * are modest, and the largest radius is reserved for sheets and dialogs where the
 * platform expects it.
 */
internal val DocoraShapes: Shapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

/** Shape used by document tiles: distinct from cards so tiles read as objects. */
internal val ThumbnailShape = RoundedCornerShape(12.dp)

/** Shape used by the small file-type badge over a thumbnail. */
internal val BadgeShape = RoundedCornerShape(6.dp)
