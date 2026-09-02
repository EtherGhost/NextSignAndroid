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
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import se.cloudsite.nextsign.util.SignatureImageEncoder

// A Compose Canvas's backing bitmap is tied to its layout size - unlike the Ubuntu
// Touch app's QML Canvas (which stretches existing pixel content non-uniformly on
// resize), this screen never stores rendered pixels mid-draw, only stroke point data,
// so a resize (e.g. rotating the device) can't distort anything already drawn. What it
// CAN'T do safely is keep drawing in the old coordinate space after a resize, so
// strokes are still cleared on a size change while mid-drawing, matching the ported
// app's own fix for this - just for a different underlying reason.
@Composable
fun SignatureDrawScreen(onSave: (String) -> Unit, onCancel: () -> Unit) {
    var strokes by remember { mutableStateOf<List<List<Offset>>>(emptyList()) }
    var currentStroke by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var clearedByResize by remember { mutableStateOf(false) }

    val strokeWidthPx = with(LocalDensity.current) { 4.dp.toPx() }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Draw your signature", style = MaterialTheme.typography.headlineSmall)

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(320.dp)
                .background(Color.White)
                .onSizeChanged { newSize ->
                    if (canvasSize != IntSize.Zero && canvasSize != newSize && strokes.isNotEmpty()) {
                        strokes = emptyList()
                        currentStroke = emptyList()
                        clearedByResize = true
                    }
                    canvasSize = newSize
                }
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            clearedByResize = false
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
            text = if (clearedByResize) {
                "The drawing area changed size, so it was cleared - please sign again."
            } else {
                "Sign with your finger or a stylus, then tap Save."
            },
            style = MaterialTheme.typography.bodySmall,
            color = if (clearedByResize) Color(0xFFB37A2A) else Color.Unspecified
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                Text("Cancel")
            }
            OutlinedButton(
                onClick = { strokes = emptyList(); currentStroke = emptyList() },
                enabled = strokes.isNotEmpty(),
                modifier = Modifier.weight(1f)
            ) {
                Text("Clear")
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
                Text("Save")
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
