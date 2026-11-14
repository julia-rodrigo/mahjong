package mahjong.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import mahjong.engine.Tiles

private val MAN_NUMERALS = "一二三四五六七八九"
private val HONOR_KANJI = "東南西北白發中"
private val HONOR_LABELS = arrayOf("E", "S", "W", "N", "Wh", "Gr", "Rd")

private fun suitColor(kind: Int): Color = when {
    kind < 9 -> Palette.TileRed
    kind < 18 -> Palette.TileBlue
    kind < 27 -> Palette.TileGreen
    kind == Tiles.GREEN -> Palette.TileGreen
    kind == Tiles.RED -> Palette.TileRed
    kind == Tiles.WHITE -> Palette.TileBlue
    else -> Palette.TileInk
}

@Composable
private fun Glyph(text: String, size: Dp, color: Color, bold: Boolean = false) {
    val sp = with(LocalDensity.current) { size.toSp() }
    Text(
        text,
        color = color,
        textAlign = TextAlign.Center,
        maxLines = 1,
        softWrap = false,
        style = TextStyle(
            fontSize = sp,
            lineHeight = sp,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
            platformStyle = PlatformTextStyle(includeFontPadding = false),
            lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
        ),
    )
}

/** Dot positions for the circle suit, as fractions of the drawing area. */
private fun pinDots(n: Int): List<Offset> = when (n) {
    1 -> listOf(Offset(.5f, .5f))
    2 -> listOf(Offset(.5f, .27f), Offset(.5f, .73f))
    3 -> listOf(Offset(.25f, .2f), Offset(.5f, .5f), Offset(.75f, .8f))
    4 -> listOf(Offset(.28f, .27f), Offset(.72f, .27f), Offset(.28f, .73f), Offset(.72f, .73f))
    5 -> listOf(Offset(.25f, .22f), Offset(.75f, .22f), Offset(.5f, .5f), Offset(.25f, .78f), Offset(.75f, .78f))
    6 -> listOf(Offset(.28f, .2f), Offset(.72f, .2f), Offset(.28f, .5f), Offset(.72f, .5f), Offset(.28f, .8f), Offset(.72f, .8f))
    7 -> listOf(Offset(.22f, .14f), Offset(.5f, .25f), Offset(.78f, .36f), Offset(.28f, .62f), Offset(.72f, .62f), Offset(.28f, .86f), Offset(.72f, .86f))
    8 -> listOf(.14f, .38f, .62f, .86f).flatMap { y -> listOf(Offset(.28f, y), Offset(.72f, y)) }
    else -> listOf(.2f, .5f, .8f).flatMap { y -> listOf(Offset(.2f, y), Offset(.5f, y), Offset(.8f, y)) }
}

private fun DrawScope.drawPin(n: Int) {
    val r = when (n) { 1 -> size.minDimension * .34f; 2, 3, 4, 5 -> size.minDimension * .17f; else -> size.minDimension * .135f }
    pinDots(n).forEachIndexed { i, p ->
        val c = Offset(p.x * size.width, p.y * size.height)
        val color = if (n == 1 || (n == 5 && i == 2) || (n == 7 && i < 3) || (n == 9 && i in 3..5) || (n == 6 && i < 2)) Palette.TileRed else Palette.TileBlue
        drawCircle(color, r, c)
        drawCircle(Palette.TileFace, r * .45f, c)
        if (n == 1) drawCircle(Palette.TileBlue, r * .25f, c)
    }
}

/** Stick rows for the bamboo suit: each row is a list of x positions; red sticks marked by a negative x. */
private fun souRows(n: Int): List<List<Float>> = when (n) {
    1 -> listOf(listOf(-.5f))
    2 -> listOf(listOf(.5f), listOf(.5f))
    3 -> listOf(listOf(.5f), listOf(.28f, .72f))
    4 -> listOf(listOf(.28f, .72f), listOf(.28f, .72f))
    5 -> listOf(listOf(.2f, .8f), listOf(-.5f), listOf(.2f, .8f))
    6 -> listOf(listOf(.2f, .5f, .8f), listOf(.2f, .5f, .8f))
    7 -> listOf(listOf(-.5f), listOf(.2f, .5f, .8f), listOf(.2f, .5f, .8f))
    8 -> listOf(listOf(.14f, .38f, .62f, .86f), listOf(.14f, .38f, .62f, .86f))
    else -> listOf(listOf(.2f, -.5f, .8f), listOf(.2f, -.5f, .8f), listOf(.2f, -.5f, .8f))
}

private fun DrawScope.drawSou(n: Int) {
    val rows = souRows(n)
    val rowH = size.height / rows.size
    val w = size.width * (if (n == 1) .3f else .15f)
    rows.forEachIndexed { r, xs ->
        for (x in xs) {
            val red = x < 0
            val cx = kotlin.math.abs(x) * size.width
            val h = rowH * .84f
            val top = r * rowH + (rowH - h) / 2
            drawRoundRect(if (red) Palette.TileRed else Palette.TileGreen, Offset(cx - w / 2, top), Size(w, h), CornerRadius(w / 2.5f))
            drawRect(Palette.TileFace, Offset(cx - w / 2, top + h / 2 - h * .05f), Size(w, h * .1f))
        }
    }
}

private fun DrawScope.drawWhiteDragon() {
    val inset = size.minDimension * .12f
    drawRoundRect(
        Palette.TileBlue, Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
        CornerRadius(inset), style = Stroke(size.minDimension * .08f),
    )
}

/**
 * One mahjong tile. With [labels] on, every tile shows a large Western number or letter with a small
 * picture of its suit underneath, so no Chinese characters have to be read.
 */
@Composable
fun TileView(
    kind: Int,
    width: Dp,
    modifier: Modifier = Modifier,
    labels: Boolean = true,
    faceDown: Boolean = false,
    dim: Boolean = false,
    outline: Color? = null,
    tint: Color? = null,
) {
    val height = width * 1.36f
    val shape = RoundedCornerShape(width * .12f)
    var m = modifier
        .size(width, height)
        .padding(width * .03f)
        .clip(shape)
        .background(if (faceDown) Palette.TileBack else tint ?: Palette.TileFace)
        .border(if (outline != null) width * .09f else 0.6.dp, outline ?: Palette.TileEdge, shape)
    if (dim) m = m.alpha(.38f)
    if (faceDown) { Box(m); return }
    m = m.semantics { contentDescription = Tiles.name(kind) }
    val suit = Tiles.suit(kind)
    val n = Tiles.num(kind)
    if (labels) {
        Column(m.padding(vertical = width * .06f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.SpaceEvenly) {
            if (suit < 3) Glyph(n.toString(), width * .6f, suitColor(kind), bold = true)
            else Glyph(HONOR_LABELS[kind - 27], width * (if (kind >= 31) .44f else .56f), suitColor(kind), bold = true)
            when {
                suit == 0 -> Glyph("萬", width * .4f, Palette.TileRed)
                suit == 1 -> Canvas(Modifier.size(width * .4f)) {
                    drawCircle(Palette.TileBlue, size.minDimension * .5f)
                    drawCircle(Palette.TileFace, size.minDimension * .24f)
                }
                suit == 2 -> Canvas(Modifier.size(width * .22f, width * .42f)) {
                    drawRoundRect(Palette.TileGreen, cornerRadius = CornerRadius(size.width / 2.5f))
                    drawRect(Palette.TileFace, Offset(0f, size.height * .45f), Size(size.width, size.height * .1f))
                }
                kind == Tiles.WHITE -> Canvas(Modifier.size(width * .36f, width * .42f)) { drawWhiteDragon() }
                else -> Glyph(HONOR_KANJI[kind - 27].toString(), width * .4f, suitColor(kind))
            }
        }
    } else {
        Box(m, contentAlignment = Alignment.Center) {
            when {
                suit == 0 -> Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Glyph(MAN_NUMERALS[n - 1].toString(), width * .5f, Palette.TileInk, bold = true)
                    Glyph("萬", width * .5f, Palette.TileRed, bold = true)
                }
                suit == 1 -> Canvas(Modifier.fillMaxSize().padding(width * .1f)) { drawPin(n) }
                suit == 2 -> Canvas(Modifier.fillMaxSize().padding(width * .12f)) { drawSou(n) }
                kind == Tiles.WHITE -> Canvas(Modifier.fillMaxSize().padding(width * .06f)) { drawWhiteDragon() }
                else -> Glyph(HONOR_KANJI[kind - 27].toString(), width * .72f, suitColor(kind), bold = true)
            }
        }
    }
}

/** A row of small tiles, used for melds, results and tutor hints. */
@Composable
fun TileRow(kinds: List<Int>, width: Dp, labels: Boolean, modifier: Modifier = Modifier) {
    Row(modifier) { for (k in kinds) TileView(k, width, labels = labels) }
}
