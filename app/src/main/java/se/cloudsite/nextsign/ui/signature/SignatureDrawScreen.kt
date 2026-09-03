package se.cloudsite.nextsign.ui.signature

import android.graphics.Bitmap
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.R
import se.cloudsite.nextsign.util.SignatureImageEncoder

// A Compose Canvas's backing bitmap is tied to its layout size, so a resize (e.g.
// rotating the device) invalidates the old coordinate space. Unlike the Ubuntu Touch
// app's QML Canvas (which stretches its existing rendered pixels non-uniformly on
// resize, distorting the signature) or an earlier version of this screen (which just
// cleared the drawing on resize), this rescales the stored stroke points
// proportionally to the new size on every resize - safe to do because only vector
// point data is stored while drawing, never a rasterized bitmap, so the signature
// survives rotation intact instead of being stretched or lost.
@Composable
fun SignatureDrawScreen(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }

    val strokeWidthPx = with(LocalDensity.current) { 4.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.draw_signature_title), style = MaterialTheme.typography.headlineSmall)

        Box(
            // weight(1f), not a fixed height - a fixed 320.dp canvas left no room for
            // the button row below it in landscape, where the screen is much shorter.
            // Not fixed with a scroll container instead: a vertical scroll gesture
            // would compete with the canvas's own drag-to-draw gesture on the same
            // area. Letting the canvas just shrink to fill whatever space remains
            // keeps the buttons always visible with no gesture conflict.
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .background(Color.White)
                .onSizeChanged { newSize ->
                    if (canvasSize != IntSize.Zero && canvasSize != newSize &&
                        canvasSize.width > 0 && canvasSize.height > 0
                    ) {
                        val scaleX = newSize.width.toFloat() / canvasSize.width
                        val scaleY = newSize.height.toFloat() / canvasSize.height
                        strokes = strokes.map { stroke -> stroke.map { Offset(it.x * scaleX, it.y * scaleY) } }
                        currentStroke = currentStroke.map { Offset(it.x * scaleX, it.y * scaleY) }
                    }
                    canvasSize = newSize
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            currentStroke = listOf(offset)
                        },
                        onDrag = { change, _ ->
                            currentStroke = currentStroke + change.position
                            change.consume()
                        },
                        onDragEnd = {
                            if (currentStroke.size > 1) {
                                strokes = strokes + listOf(currentStroke)
                            }
                            currentStroke = emptyList()
                        }
                    )
                }
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val allStrokes = if (currentStroke.size > 1) strokes + listOf(currentStroke) else strokes
                allStrokes.forEach { points ->
                    if (points.size < 2) return@forEach
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        for (i in 1 until points.size) lineTo(points[i].x, points[i].y)
                    }
                    drawPath(
                        path = path,
                        color = Color.Black,
                        style = Stroke(width = strokeWidthPx, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
                    )
                }
            }
        }

        Text(
            text = stringResource(R.string.draw_signature_hint),
            style = MaterialTheme.typography.bodySmall
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.cancel_button))
            }
            OutlinedButton(
                onClick = { strokes = emptyList(); currentStroke = emptyList() },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.clear_button))
            }
            Button(
                onClick = {
                    val bitmap = renderToBitmap(strokes, canvasSize, strokeWidthPx) ?: return@Button
                    val dataUri = SignatureImageEncoder.bitmapToBase64DataUri(bitmap)
                    bitmap.recycle()
                    if (dataUri != null) {
                        onSave(dataUri)
                    }
                },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.save_button))
            }
        }
    }
}

// Replays the drawn strokes onto a transparent ARGB_8888 bitmap at the canvas's own
// pixel size - the exported PNG's transparent background (everywhere except the ink)
// is a direct consequence of starting from a blank bitmap and only ever drawing
// strokes onto it, same reasoning as the Ubuntu Touch app's Canvas-based export.
private fun renderToBitmap(strokes: List<List<Offset>>, size: IntSize, strokeWidthPx: Float): Bitmap? {
    if (size.width <= 0 || size.height <= 0) {
        return null
    }
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val canvas = android.graphics.Canvas(bitmap)
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.BLACK
        style = android.graphics.Paint.Style.STROKE
        strokeWidth = strokeWidthPx
        strokeCap = android.graphics.Paint.Cap.ROUND
        strokeJoin = android.graphics.Paint.Join.ROUND
        isAntiAlias = true
    }
    strokes.forEach { points ->
        if (points.size < 2) return@forEach
        val path = android.graphics.Path()
        path.moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size) path.lineTo(points[i].x, points[i].y)
        canvas.drawPath(path, paint)
    }
    return bitmap
}
