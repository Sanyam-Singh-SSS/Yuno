package com.yuno

import android.content.Context
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Size as CoilSize
import kotlinx.coroutines.CoroutineScope
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

/** Storyboard sheets kept decoded at once; the rest reload from Coil's disk cache. */
private const val SheetsKept = 24

internal class StoryboardSheets(private val scope: CoroutineScope, private val context: Context) {
    val loaded = mutableStateMapOf<String, ImageBitmap>()
    private val asked = LinkedHashSet<String>()

    fun request(url: String) {
        if (url in asked) return
        asked += url
        while (asked.size > SheetsKept) asked.first().let { asked.remove(it); loaded.remove(it) }
        scope.launch {
            try {
                val request = ImageRequest.Builder(context).data(url).size(CoilSize.ORIGINAL).build()
                val bitmap = ((context.imageLoader.execute(request) as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
                if (bitmap != null && url in asked) loaded[url] = bitmap.asImageBitmap()
            } finally {
                if (url !in loaded) asked.remove(url)
            }
        }
    }
}

@Composable
internal fun rememberStoryboardSheets(player: PlayerState): StoryboardSheets {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    return remember(player.details?.item?.id) { StoryboardSheets(scope, context) }
}

private fun DrawScope.drawStoryboardFrame(image: ImageBitmap, frame: StoryboardFrame) {
    val x = frame.x.coerceIn(0, (image.width - 1).coerceAtLeast(0))
    val y = frame.y.coerceIn(0, (image.height - 1).coerceAtLeast(0))
    drawImage(
        image,
        srcOffset = IntOffset(x, y),
        srcSize = IntSize(frame.width.coerceAtMost(image.width - x), frame.height.coerceAtMost(image.height - y)),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        filterQuality = FilterQuality.Medium,
    )
}

@Composable
internal fun StoryboardThumb(
    sheets: StoryboardSheets,
    boards: List<Storyboard>,
    ms: Long,
    durationMs: Long,
    modifier: Modifier,
) {
    val frame = boards.lastOrNull()?.frameAt(ms, durationMs.coerceAtLeast(1L))
    LaunchedEffect(frame?.url) { frame?.url?.let(sheets::request) }
    val image = frame?.let { sheets.loaded[it.url] }
    Box(modifier.drawBehind { if (image != null && frame != null) drawStoryboardFrame(image, frame) })
}
