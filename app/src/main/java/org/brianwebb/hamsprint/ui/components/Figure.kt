package org.brianwebb.hamsprint.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.brianwebb.hamsprint.ui.theme.HamTheme
import org.brianwebb.hamsprint.ui.theme.Radius
import org.brianwebb.hamsprint.ui.theme.Space

@Composable
private fun rememberFigure(name: String): ImageBitmap? {
    val context = LocalContext.current
    val bitmap by produceState<ImageBitmap?>(null, name) {
        value = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("figures/$name.png").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    return bitmap
}

/** Inline diagram for a question; tap to open a pinch-zoomable full-screen view. */
@Composable
fun FigureCard(name: String, modifier: Modifier = Modifier) {
    val c = HamTheme.colors
    val bitmap = rememberFigure(name)
    var zoomed by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .pressable({ zoomed = true }, onClickLabel = "Enlarge figure $name")
            .clip(RoundedCornerShape(Radius.md))
            .background(c.figureBackground)
            .padding(Space.md),
    ) {
        if (bitmap != null) {
            Image(
                bitmap,
                contentDescription = "Figure $name",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxWidth().aspectRatio(bitmap.width.toFloat() / bitmap.height),
            )
        } else {
            Box(Modifier.fillMaxWidth().height(180.dp))
        }
        Icon(
            HamIcons.Expand, contentDescription = null, tint = c.textTertiary,
            modifier = Modifier.align(Alignment.TopEnd),
        )
    }
    if (zoomed && bitmap != null) {
        ZoomDialog(name, bitmap) { zoomed = false }
    }
}

@Composable
private fun ZoomDialog(name: String, bitmap: ImageBitmap, onDismiss: () -> Unit) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val state = rememberTransformableState { zoom, pan, _ ->
        scale = (scale * zoom).coerceIn(1f, 6f)
        offset = if (scale == 1f) Offset.Zero else offset + pan
    }
    LaunchedEffect(name) { scale = 1f; offset = Offset.Zero }
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(Modifier.fillMaxSize().background(HamTheme.colors.figureBackground)) {
            Image(
                bitmap,
                contentDescription = "Figure $name, zoomable",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(Space.lg)
                    .transformable(state)
                    .graphicsLayer {
                        scaleX = scale; scaleY = scale
                        translationX = offset.x; translationY = offset.y
                    },
            )
            Text(
                "Figure $name",
                style = MaterialTheme.typography.titleMedium,
                color = HamTheme.colors.onFigure,
                modifier = Modifier.align(Alignment.TopStart).statusBarsPadding().padding(Space.xl),
            )
            CircleIconButton(
                HamIcons.Close, "Close figure", onDismiss,
                modifier = Modifier.align(Alignment.TopEnd).statusBarsPadding().padding(Space.lg),
                tint = HamTheme.colors.onFigure,
                background = HamTheme.colors.onFigureScrim,
            )
        }
    }
}
