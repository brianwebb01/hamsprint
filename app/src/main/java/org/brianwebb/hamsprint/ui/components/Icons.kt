package org.brianwebb.hamsprint.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/** A small, consistent line-icon set on a 24-unit grid (2-unit strokes, round joins). */
object HamIcons {
    val Close = stroke("Close", "M6 6L18 18", "M18 6L6 18")
    val Back = stroke("Back", "M15 5L8 12L15 19")
    val Chevron = stroke("Chevron", "M9 5L16 12L9 19")
    val ChevronDown = stroke("ChevronDown", "M6 9L12 15L18 9")
    val Check = stroke("Check", "M5 12.5L10 17.5L19 7")
    val Settings = stroke(
        "Settings",
        "M12 15.2A3.2 3.2 0 1 0 12 8.8A3.2 3.2 0 1 0 12 15.2Z",
        "M19.4 13.5a7.6 7.6 0 0 0 0-3l2-1.6-2-3.4-2.4 1a7.6 7.6 0 0 0-2.6-1.5L14 2.5h-4l-.4 2.5a7.6 7.6 0 0 0-2.6 1.5l-2.4-1-2 3.4 2 1.6a7.6 7.6 0 0 0 0 3l-2 1.6 2 3.4 2.4-1a7.6 7.6 0 0 0 2.6 1.5l.4 2.5h4l.4-2.5a7.6 7.6 0 0 0 2.6-1.5l2.4 1 2-3.4z",
    )
    val Flag = stroke("Flag", "M5 21V4", "M5 4H17L14.5 8.5L17 13H5")
    val Clock = stroke("Clock", "M12 21A9 9 0 1 0 12 3A9 9 0 1 0 12 21Z", "M12 7V12L15.5 14")
    val Grid = stroke("Grid", "M4 4H10V10H4Z", "M14 4H20V10H14Z", "M4 14H10V20H4Z", "M14 14H20V20H14Z")
    val Target = stroke(
        "Target",
        "M12 21A9 9 0 1 0 12 3A9 9 0 1 0 12 21Z",
        "M12 16.5A4.5 4.5 0 1 0 12 7.5A4.5 4.5 0 1 0 12 16.5Z",
        "M12 12.5A.5 .5 0 1 0 12 11.5A.5 .5 0 1 0 12 12.5Z",
    )
    val Expand = stroke("Expand", "M14 4H20V10", "M20 4L13.5 10.5", "M10 20H4V14", "M4 20L10.5 13.5")
    val Book = stroke("Book", "M4 5.5C6.5 4 9.5 4 12 6C14.5 4 17.5 4 20 5.5V19C17.5 17.5 14.5 17.5 12 19.5C9.5 17.5 6.5 17.5 4 19Z", "M12 6V19.5")
    val Refresh = stroke("Refresh", "M20 11A8 8 0 0 0 5.6 6.4L4 8", "M4 4V8H8", "M4 13A8 8 0 0 0 18.4 17.6L20 16", "M20 20V16H16")
    val Code = stroke("Code", "M8 7L3 12L8 17", "M16 7L21 12L16 17")
    val Bolt = fill("Bolt", "M13.5 2L4.5 13.5H11L10 22L19.5 10H13Z")
    val Sparkle = fill("Sparkle", "M12 2L14.2 9.8L22 12L14.2 14.2L12 22L9.8 14.2L2 12L9.8 9.8Z")
    val Play = fill("Play", "M7 4.5V19.5L19.5 12Z")

    private fun builder(name: String) = ImageVector.Builder(
        name = name, defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f,
    )

    private fun stroke(name: String, vararg paths: String): ImageVector = builder(name).apply {
        paths.forEach { d ->
            addPath(
                pathData = PathParser().parsePathString(d).toNodes(),
                stroke = SolidColor(Color.Black),
                strokeLineWidth = 2f,
                strokeLineCap = StrokeCap.Round,
                strokeLineJoin = StrokeJoin.Round,
            )
        }
    }.build()

    private fun fill(name: String, d: String): ImageVector = builder(name).apply {
        addPath(pathData = PathParser().parsePathString(d).toNodes(), fill = SolidColor(Color.Black))
    }.build()
}
