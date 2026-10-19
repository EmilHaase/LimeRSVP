package com.limersvp.ui

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The app's own line icons: one stroke weight, round ends. */
object Glyphs {
    private fun icon(name: String, stroke: Float = 2f, block: PathBuilder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).path(
            stroke = SolidColor(Color.Black), strokeLineWidth = stroke,
            strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round, pathBuilder = block,
        ).build()

    private fun PathBuilder.circle(cx: Float, cy: Float, r: Float) {
        moveTo(cx - r, cy)
        arcToRelative(r, r, 0f, true, true, 2 * r, 0f)
        arcToRelative(r, r, 0f, true, true, -2 * r, 0f)
    }

    private fun PathBuilder.dot(x: Float, y: Float) {
        moveTo(x, y); lineTo(x + 0.01f, y)
    }

    val Back = icon("back") { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }

    val Close = icon("close") {
        moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f)
    }

    val Plus = icon("plus") { moveTo(12f, 5f); lineTo(12f, 19f); moveTo(5f, 12f); lineTo(19f, 12f) }

    val Check = icon("check") { moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f) }

    val Library = icon("library") {
        moveTo(4f, 4.5f); lineTo(4f, 19.5f)
        moveTo(8.5f, 4.5f); lineTo(8.5f, 19.5f)
        moveTo(13f, 6.5f); lineTo(16.6f, 5.6f); lineTo(20f, 18.6f); lineTo(16.4f, 19.5f); close()
    }

    val Stats = icon("stats", 2.4f) {
        moveTo(5f, 19.5f); lineTo(5f, 12f)
        moveTo(12f, 19.5f); lineTo(12f, 5f)
        moveTo(19f, 19.5f); lineTo(19f, 9.5f)
    }

    val Tune = icon("tune") {
        moveTo(4f, 7f); lineTo(6.5f, 7f); circle(9f, 7f, 2.5f); moveTo(11.5f, 7f); lineTo(20f, 7f)
        moveTo(4f, 17f); lineTo(12.5f, 17f); circle(15f, 17f, 2.5f); moveTo(17.5f, 17f); lineTo(20f, 17f)
    }

    val Chapters = icon("chapters") {
        moveTo(9f, 6f); lineTo(20f, 6f); moveTo(9f, 12f); lineTo(20f, 12f); moveTo(9f, 18f); lineTo(20f, 18f)
        dot(4.5f, 6f); dot(4.5f, 12f); dot(4.5f, 18f)
    }

    val Page = icon("page") {
        moveTo(12f, 6.5f)
        curveTo(10f, 5f, 7f, 4.5f, 3f, 5f); lineTo(3f, 19f)
        curveTo(7f, 18.5f, 10f, 19f, 12f, 20.5f)
        curveTo(14f, 19f, 17f, 18.5f, 21f, 19f); lineTo(21f, 5f)
        curveTo(17f, 4.5f, 14f, 5f, 12f, 6.5f); close()
        moveTo(12f, 6.5f); lineTo(12f, 20.5f)
    }

    val More = icon("more", 2.8f) { dot(5f, 12f); dot(12f, 12f); dot(19f, 12f) }

    val Undo = icon("undo") {
        moveTo(9f, 7f); lineTo(4.5f, 11.5f); lineTo(9f, 16f)
        moveTo(4.5f, 11.5f); lineTo(14f, 11.5f); curveTo(17.3f, 11.5f, 20f, 14.2f, 20f, 17.5f)
    }

    val Restart = icon("restart") {
        moveTo(4f, 12f); arcToRelative(8f, 8f, 0f, true, false, 2.4f, -5.7f)
        moveTo(4f, 4.5f); lineTo(4f, 9f); lineTo(8.5f, 9f)
    }

    val Trash = icon("trash") {
        moveTo(4.5f, 7f); lineTo(19.5f, 7f)
        moveTo(9.5f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(14.5f, 7f)
        moveTo(6.5f, 7f); lineTo(7.5f, 19.5f); lineTo(16.5f, 19.5f); lineTo(17.5f, 7f)
    }

    val Flag = icon("flag") {
        moveTo(6f, 20.5f); lineTo(6f, 4f)
        moveTo(6f, 4.5f); lineTo(18f, 4.5f); lineTo(15f, 8.5f); lineTo(18f, 12.5f); lineTo(6f, 12.5f)
    }
}
