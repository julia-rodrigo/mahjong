package mahjong.app

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

object Palette {
    val Felt = Color(0xFF0E3B2A)
    val FeltLight = Color(0xFF175239)
    val FeltDark = Color(0xFF0A2A1E)
    val Panel = Color(0xFF12241C)
    val Ink = Color(0xFFF3F1E7)
    val InkDim = Color(0xFFB5C2B8)
    val Gold = Color(0xFFF2C14E)
    val Good = Color(0xFF6FE08A)
    val Warn = Color(0xFFFFB454)
    val Bad = Color(0xFFFF7B6B)
    val Blue = Color(0xFF7DB7FF)
    val TileFace = Color(0xFFFBF7EA)
    val TileEdge = Color(0xFFBFA76F)
    val TileBack = Color(0xFF2F7D5B)
    val TileRed = Color(0xFFC62828)
    val TileGreen = Color(0xFF1B7A3A)
    val TileBlue = Color(0xFF1F4FA3)
    val TileInk = Color(0xFF1E1E1E)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.Gold,
            onPrimary = Color(0xFF2A2000),
            secondary = Palette.Good,
            background = Palette.Felt,
            surface = Palette.Panel,
            onBackground = Palette.Ink,
            onSurface = Palette.Ink,
            surfaceVariant = Palette.FeltLight,
            onSurfaceVariant = Palette.InkDim,
        ),
        content = content,
    )
}
