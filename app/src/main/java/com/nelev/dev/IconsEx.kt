package com.nelev.dev

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

object IconsEx {
    private val _Mic by lazy { mic() }
    val Mic: ImageVector get() = _Mic

    private val _GraphicEq by lazy { graphicEq() }
    val GraphicEq: ImageVector get() = _GraphicEq

    private val _QrCodeScanner by lazy { qrCodeScanner() }
    val QrCodeScanner: ImageVector get() = _QrCodeScanner

    private val _History by lazy { history() }
    val History: ImageVector get() = _History

    private val _Lightbulb by lazy { lightbulb() }
    val Lightbulb: ImageVector get() = _Lightbulb

    private val _SystemUpdate by lazy { systemUpdate() }
    val SystemUpdate: ImageVector get() = _SystemUpdate

    private fun mic(): ImageVector = ImageVector.Builder(
        name = "Mic", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(12f, 2.6f)
            curveTo(10.12f, 2.6f, 8.6f, 4.12f, 8.6f, 6f)
            verticalLineTo(11.2f)
            curveTo(8.6f, 13.08f, 10.12f, 14.6f, 12f, 14.6f)
            curveTo(13.88f, 14.6f, 15.4f, 13.08f, 15.4f, 11.2f)
            verticalLineTo(6f)
            curveTo(15.4f, 4.12f, 13.88f, 2.6f, 12f, 2.6f)
            close()
            moveTo(6.2f, 11.05f)
            curveTo(5.79f, 11.05f, 5.45f, 11.39f, 5.45f, 11.8f)
            curveTo(5.45f, 15.18f, 7.95f, 18.02f, 11.2f, 18.48f)
            verticalLineTo(20.35f)
            horizontalLineTo(9.15f)
            curveTo(8.74f, 20.35f, 8.4f, 20.69f, 8.4f, 21.1f)
            curveTo(8.4f, 21.51f, 8.74f, 21.85f, 9.15f, 21.85f)
            horizontalLineTo(14.85f)
            curveTo(15.26f, 21.85f, 15.6f, 21.51f, 15.6f, 21.1f)
            curveTo(15.6f, 20.69f, 15.26f, 20.35f, 14.85f, 20.35f)
            horizontalLineTo(12.8f)
            verticalLineTo(18.48f)
            curveTo(16.05f, 18.02f, 18.55f, 15.18f, 18.55f, 11.8f)
            curveTo(18.55f, 11.39f, 18.21f, 11.05f, 17.8f, 11.05f)
            curveTo(17.39f, 11.05f, 17.05f, 11.39f, 17.05f, 11.8f)
            curveTo(17.05f, 14.58f, 14.78f, 16.85f, 12f, 16.85f)
            curveTo(9.22f, 16.85f, 6.95f, 14.58f, 6.95f, 11.8f)
            curveTo(6.95f, 11.39f, 6.61f, 11.05f, 6.2f, 11.05f)
            close()
        }
    }.build()

    private fun graphicEq(): ImageVector = ImageVector.Builder(
        name = "GraphicEq", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        // 5 vertical bars: short → medium → tall → medium → short
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            // Bar 1 (left): short, x=3..5, y=14..22
            moveTo(3f, 22f); horizontalLineToRelative(2f); verticalLineTo(14f); horizontalLineTo(3f); close()
            // Bar 2 (mid-left): medium, x=7..9, y=8..22
            moveTo(7f, 22f); horizontalLineToRelative(2f); verticalLineTo(8f); horizontalLineTo(7f); close()
            // Bar 3 (center): tallest, x=11..13, y=2..22
            moveTo(11f, 22f); horizontalLineToRelative(2f); verticalLineTo(2f); horizontalLineTo(11f); close()
            // Bar 4 (mid-right): medium, x=15..17, y=8..22
            moveTo(15f, 22f); horizontalLineToRelative(2f); verticalLineTo(8f); horizontalLineTo(15f); close()
            // Bar 5 (right): short, x=19..21, y=14..22
            moveTo(19f, 22f); horizontalLineToRelative(2f); verticalLineTo(14f); horizontalLineTo(19f); close()
        }
    }.build()

    private fun qrCodeScanner(): ImageVector = ImageVector.Builder(
        name = "QrCodeScanner", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(9.5f, 6.5f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-3f)
            horizontalLineTo(9.5f)
            close()
            moveTo(11f, 5f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            close()
            moveTo(9.5f, 14.5f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-3f)
            horizontalLineTo(9.5f)
            close()
            moveTo(11f, 13f)
            horizontalLineTo(5f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            verticalLineTo(13f)
            close()
            moveTo(17.5f, 6.5f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(-3f)
            horizontalLineTo(17.5f)
            close()
            moveTo(19f, 5f)
            horizontalLineToRelative(-6f)
            verticalLineToRelative(6f)
            horizontalLineToRelative(6f)
            verticalLineTo(5f)
            close()
            moveTo(13f, 13f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(1.5f)
            horizontalLineTo(13f)
            verticalLineTo(13f)
            close()
            moveTo(14.5f, 14.5f)
            horizontalLineTo(16f)
            verticalLineTo(16f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(14.5f)
            close()
            moveTo(16f, 13f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(1.5f)
            horizontalLineTo(16f)
            verticalLineTo(13f)
            close()
            moveTo(13f, 16f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(1.5f)
            horizontalLineTo(13f)
            verticalLineTo(16f)
            close()
            moveTo(16f, 16f)
            horizontalLineToRelative(1.5f)
            verticalLineToRelative(1.5f)
            horizontalLineTo(16f)
            verticalLineTo(16f)
            close()
            moveTo(17.5f, 14.5f)
            horizontalLineTo(19f)
            verticalLineTo(16f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(14.5f)
            close()
            moveTo(17.5f, 17.5f)
            horizontalLineTo(19f)
            verticalLineTo(19f)
            horizontalLineToRelative(-1.5f)
            verticalLineTo(17.5f)
            close()
            moveTo(22f, 7f)
            horizontalLineToRelative(-2f)
            verticalLineTo(4f)
            horizontalLineToRelative(-3f)
            verticalLineTo(2f)
            horizontalLineToRelative(5f)
            verticalLineTo(7f)
            close()
            moveTo(22f, 22f)
            verticalLineToRelative(-5f)
            horizontalLineToRelative(-2f)
            verticalLineToRelative(3f)
            horizontalLineToRelative(-3f)
            verticalLineToRelative(2f)
            horizontalLineTo(22f)
            close()
            moveTo(2f, 22f)
            horizontalLineToRelative(5f)
            verticalLineToRelative(-2f)
            horizontalLineTo(4f)
            verticalLineToRelative(-3f)
            horizontalLineTo(2f)
            verticalLineTo(22f)
            close()
            moveTo(2f, 2f)
            verticalLineToRelative(5f)
            horizontalLineToRelative(2f)
            verticalLineTo(4f)
            horizontalLineToRelative(3f)
            verticalLineTo(2f)
            horizontalLineTo(2f)
            close()
        }
    }.build()

    private fun history(): ImageVector = ImageVector.Builder(
        name = "History", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(13f, 3f)
            curveToRelative(-4.97f, 0f, -9f, 4.03f, -9f, 9f)
            horizontalLineTo(1f)
            lineToRelative(3.89f, 3.89f)
            lineToRelative(0.07f, 0.14f)
            lineTo(9f, 12f)
            horizontalLineTo(6f)
            curveToRelative(0f, -3.87f, 3.13f, -7f, 7f, -7f)
            reflectiveCurveToRelative(7f, 3.13f, 7f, 7f)
            reflectiveCurveToRelative(-3.13f, 7f, -7f, 7f)
            curveToRelative(-1.93f, 0f, -3.68f, -0.79f, -4.94f, -2.06f)
            lineToRelative(-1.42f, 1.42f)
            curveTo(8.27f, 19.99f, 10.51f, 21f, 13f, 21f)
            curveToRelative(4.97f, 0f, 9f, -4.03f, 9f, -9f)
            reflectiveCurveTo(17.97f, 3f, 13f, 3f)
            close()
            moveTo(12f, 8f)
            verticalLineToRelative(5f)
            lineToRelative(4.28f, 2.54f)
            lineToRelative(0.72f, -1.21f)
            lineToRelative(-3.5f, -2.08f)
            verticalLineTo(8f)
            horizontalLineTo(12f)
            close()
        }
    }.build()

    private fun lightbulb(): ImageVector = ImageVector.Builder(
        name = "Lightbulb", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(9f, 21f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(4f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-1f)
            horizontalLineTo(9f)
            verticalLineTo(21f)
            close()
            moveTo(12f, 2f)
            curveTo(8.14f, 2f, 5f, 5.14f, 5f, 9f)
            curveToRelative(0f, 2.38f, 1.19f, 4.47f, 3f, 5.74f)
            verticalLineTo(17f)
            curveToRelative(0f, 0.55f, 0.45f, 1f, 1f, 1f)
            horizontalLineToRelative(6f)
            curveToRelative(0.55f, 0f, 1f, -0.45f, 1f, -1f)
            verticalLineToRelative(-2.26f)
            curveToRelative(1.81f, -1.27f, 3f, -3.36f, 3f, -5.74f)
            curveTo(19f, 5.14f, 15.86f, 2f, 12f, 2f)
            close()
            moveTo(14f, 14.7f)
            verticalLineTo(16f)
            horizontalLineToRelative(-4f)
            verticalLineToRelative(-1.3f)
            curveTo(8.48f, 14.22f, 7f, 12.7f, 7f, 11f)
            curveToRelative(0f, -2.76f, 2.24f, -5f, 5f, -5f)
            reflectiveCurveToRelative(5f, 2.24f, 5f, 5f)
            curveTo(17f, 12.7f, 15.52f, 14.22f, 14f, 14.7f)
            close()
        }
    }.build()

    // Обновление приложения: телефон + стрелка вниз (Material "system update")
    private fun systemUpdate(): ImageVector = ImageVector.Builder(
        name = "SystemUpdate", defaultWidth = 24.dp, defaultHeight = 24.dp,
        viewportWidth = 24f, viewportHeight = 24f
    ).apply {
        path(fill = SolidColor(Color.Black), pathFillType = PathFillType.NonZero) {
            moveTo(17f, 1.01f)
            lineTo(7f, 1f)
            curveTo(5.9f, 1f, 5f, 1.9f, 5f, 3f)
            verticalLineTo(21f)
            curveToRelative(0f, 1.1f, 0.9f, 2f, 2f, 2f)
            horizontalLineTo(17f)
            curveToRelative(1.1f, 0f, 2f, -0.9f, 2f, -2f)
            verticalLineTo(3f)
            curveToRelative(0f, -1.1f, -0.9f, -1.99f, -2f, -1.99f)
            close()
            moveTo(17f, 19f)
            horizontalLineTo(7f)
            verticalLineTo(5f)
            horizontalLineTo(17f)
            verticalLineTo(19f)
            close()
            moveTo(16f, 13f)
            horizontalLineTo(13f)
            verticalLineTo(8f)
            horizontalLineTo(11f)
            verticalLineTo(13f)
            horizontalLineTo(8f)
            lineTo(12f, 17f)
            lineTo(16f, 13f)
            close()
        }
    }.build()
}
