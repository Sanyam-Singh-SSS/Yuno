package com.yuno

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/** Measured off Apple Music's mini player (~37pt). */
val LineFade = 36.dp

private val FullFade = { 1f }

/**
 * One line that fades out at the end when it overflows, instead of an ellipsis.
 */
@Composable
fun FadingLine(text: String, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    var overflowing by remember { mutableStateOf(false) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        onTextLayout = { overflowing = it.didOverflowWidth },
        modifier = modifier.then(if (overflowing) Modifier.fadingEdges(LineFade, end = FullFade) else Modifier),
    )
}

/**
 * Up to [maxLines] (default 2). When the text is clipped, the last visible line fades
 * out at the end instead of showing an ellipsis; earlier lines stay sharp.
 */
@Composable
fun FadingTitle(
    text: String,
    style: TextStyle,
    color: Color = LocalContentColor.current,
    maxLines: Int = 2,
    modifier: Modifier = Modifier,
) {
    var overflow by remember { mutableStateOf(false) }
    var lineTop by remember { mutableFloatStateOf(0f) }
    var lineBottom by remember { mutableFloatStateOf(0f) }
    Text(
        text = text,
        style = style,
        color = color,
        maxLines = maxLines,
        overflow = TextOverflow.Clip,
        onTextLayout = { layout ->
            overflow = layout.hasVisualOverflow
            if (layout.lineCount > 0) {
                val last = layout.lineCount - 1
                lineTop = layout.getLineTop(last)
                lineBottom = layout.getLineBottom(last)
            }
        },
        modifier = modifier.then(
            if (overflow) Modifier.fadingEndOnLine(LineFade, { lineTop }, { lineBottom }, FullFade)
            else Modifier,
        ),
    )
}
