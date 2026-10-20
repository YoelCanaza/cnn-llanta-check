package pe.edu.unap.llantacheck.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.SystemClock
import android.provider.Settings
import android.util.Size
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import pe.edu.unap.llantacheck.ml.BitmapUtils
import pe.edu.unap.llantacheck.ml.Decision
import pe.edu.unap.llantacheck.ml.Resultado
import pe.edu.unap.llantacheck.ml.TyreClassifier
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

/** Fracción del ancho de la vista que ocupa el recuadro guía. */
private const val GUIA = 0.82f

@Composable
fun CameraScreen(
    enVivoInicial: Boolean,
    classifier: TyreClassifier,
    onCaptura: (Bitmap, String) -> Unit,
    onGaleria: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    var permiso by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var preguntado by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        permiso = it
        preguntado = true
    }
    LaunchedEffect(Unit) { if (!permiso) launcher.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (permiso) {
            CameraContent(enVivoInicial, classifier, onCaptura, onGaleria, onBack)
        } else {
            if (preguntado) {
                Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = Color.White, modifier = Modifier.size(48.dp))
                    Text(
                        "LlantaCheck necesita la cámara para fotografiar la llanta. También puedes elegir una foto de la galería.",
                        color = Color.White, textAlign = TextAlign.Center,
                    )
                    Button(onClick = { launcher.launch(Manifest.permission.CAMERA) }) { Text("Permitir cámara") }
                    Button(onClick = {
                        context.startActivity(
                            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)),
                        )
                    }) { Text("Abrir ajustes") }
                    Button(onClick = onGaleria) { Text("Usar la galería") }
                }
            }
            BotonRedondo(onBack, Modifier.safeDrawingPadding().padding(12.dp)) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
            }
        }
    }
}

@Composable
private fun CameraContent(
    enVivoInicial: Boolean,
    classifier: TyreClassifier,
    onCaptura: (Bitmap, String) -> Unit,
    onGaleria: () -> Unit,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    var enVivo by remember { mutableStateOf(enVivoInicial) }
    var enVivoResultado by remember { mutableStateOf<Resultado?>(null) }
    var ultimoFrame by remember { mutableStateOf<Bitmap?>(null) }
    var capturando by remember { mutableStateOf(false) }
    var camara by remember { mutableStateOf<Camera?>(null) }
    var linterna by remember { mutableStateOf(false) }
    var puntoFoco by remember { mutableStateOf<Offset?>(null) }
    var zoom by remember { mutableStateOf(1f) }
    val destello = remember { Animatable(0f) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val executor = remember { Executors.newSingleThreadExecutor() }
    val selector4x3 = remember {
        ResolutionSelector.Builder()
            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
            .build()
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                    .setResolutionStrategy(
                        ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER),
                    )
                    .build(),
            )
            .build()
    }

    DisposableEffect(Unit) { onDispose { executor.shutdown() } }

    // Se vuelve a enlazar la cámara al cambiar de modo: Foto usa ImageCapture, En vivo usa ImageAnalysis.
    DisposableEffect(enVivo, lifecycleOwner) {
        val future = ProcessCameraProvider.getInstance(context)
        var analysis: ImageAnalysis? = null
        enVivoResultado = null
        ultimoFrame = null
        future.addListener({
            val provider = future.get()
            val preview = Preview.Builder().setResolutionSelector(selector4x3).build()
            preview.setSurfaceProvider(previewView.surfaceProvider)
            val segundo = if (enVivo) {
                var ultimo = 0L
                var suavizado = -1f
                ImageAnalysis.Builder()
                    .setResolutionSelector(selector4x3)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                    .build()
                    .also { a ->
                        analysis = a
                        a.setAnalyzer(executor) { proxy: ImageProxy ->
                            proxy.use {
                                val ahora = SystemClock.elapsedRealtime()
                                if (ahora - ultimo < 200) return@use
                                ultimo = ahora
                                val frame = BitmapUtils.cropToGuide(
                                    BitmapUtils.fromImageProxy(it), previewView.width, previewView.height, GUIA,
                                )
                                val r = classifier.classify(frame)
                                // Media móvil exponencial para que la etiqueta no parpadee entre cuadros
                                suavizado = if (suavizado < 0) r.pDefectuosa else 0.6f * suavizado + 0.4f * r.pDefectuosa
                                val pBuena = 1f - suavizado
                                enVivoResultado = r.copy(
                                    pDefectuosa = suavizado, pBuena = pBuena,
                                    veredicto = Decision.decidir(suavizado, pBuena),
                                )
                                ultimoFrame = frame
                            }
                        }
                    }
            } else imageCapture
            try {
                provider.unbindAll()
                camara = provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, segundo).also {
                    if (linterna) it.cameraControl.enableTorch(true)
                    zoom = 1f
                }
            } catch (e: Exception) {
                Toast.makeText(context, "No se pudo abrir la cámara: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }, ContextCompat.getMainExecutor(context))
        onDispose {
            analysis?.clearAnalyzer()
            camara = null
            if (future.isDone) future.get().unbindAll()
        }
    }

    LaunchedEffect(puntoFoco) {
        if (puntoFoco != null) {
            delay(1200)
            puntoFoco = null
        }
    }

    fun capturar() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            destello.snapTo(0.85f)
            destello.animateTo(0f, tween(320))
        }
        if (enVivo) {
            ultimoFrame?.let { onCaptura(it, "En vivo") }
            return
        }
        capturando = true
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                val b = image.use { BitmapUtils.fromImageProxy(it) }
                val recorte = BitmapUtils.cropToGuide(b, previewView.width, previewView.height, GUIA)
                ContextCompat.getMainExecutor(context).execute {
                    capturando = false
                    onCaptura(recorte, "Cámara")
                }
            }

            override fun onError(exception: ImageCaptureException) {
                ContextCompat.getMainExecutor(context).execute {
                    capturando = false
                    Toast.makeText(context, "Error al capturar: ${exception.message}", Toast.LENGTH_SHORT).show()
                }
            }
        })
    }

    val colorGuia by animateColorAsState(
        when {
            !enVivo -> MaterialTheme.colorScheme.primary
            enVivoResultado == null -> Color.White
            else -> estilo(enVivoResultado!!.veredicto).color
        },
        label = "colorGuia",
    )

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())

        // Gestos: tocar para enfocar, pellizcar para hacer zoom
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures { p ->
                        val c = camara ?: return@detectTapGestures
                        val punto = previewView.meteringPointFactory.createPoint(p.x, p.y)
                        c.cameraControl.startFocusAndMetering(
                            FocusMeteringAction.Builder(punto).setAutoCancelDuration(3, TimeUnit.SECONDS).build(),
                        )
                        puntoFoco = p
                    }
                }
                .pointerInput(Unit) {
                    detectTransformGestures { _, _, factor, _ ->
                        val c = camara ?: return@detectTransformGestures
                        val estado = c.cameraInfo.zoomState.value ?: return@detectTransformGestures
                        val nuevo = (estado.zoomRatio * factor).coerceIn(estado.minZoomRatio, estado.maxZoomRatio)
                        c.cameraControl.setZoomRatio(nuevo)
                        zoom = nuevo
                    }
                },
        ) {
            GuideOverlay(colorGuia, puntoFoco)
        }

        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = destello.value)))

        // Barra superior
        Row(
            Modifier.fillMaxWidth().safeDrawingPadding().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BotonRedondo(onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
            Spacer(Modifier.weight(1f))
            if (zoom > 1.05f) {
                Text(
                    String.format(Locale.US, "%.1f×", zoom),
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.clip(RoundedCornerShape(50)).background(Color.Black.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Spacer(Modifier.width(10.dp))
            }
            if (camara?.cameraInfo?.hasFlashUnit() == true) {
                BotonRedondo(onClick = {
                    linterna = !linterna
                    camara?.cameraControl?.enableTorch(linterna)
                }, activo = linterna) {
                    Icon(
                        if (linterna) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                        contentDescription = if (linterna) "Apagar linterna" else "Encender linterna",
                    )
                }
            }
        }

        // Controles inferiores
        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().safeDrawingPadding().padding(bottom = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            if (enVivo) {
                LiveChip(enVivoResultado)
            } else {
                Pastilla("Acércate a la superficie de la llanta y toca para enfocar")
            }

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 36.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BotonRedondo(onGaleria, tamano = 52) {
                    Icon(Icons.Rounded.PhotoLibrary, contentDescription = "Elegir de la galería")
                }
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier.size(80.dp).border(4.dp, Color.White, CircleShape).padding(7.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    IconButton(
                        enabled = !capturando && (!enVivo || ultimoFrame != null),
                        onClick = { capturar() },
                        modifier = Modifier.fillMaxSize(),
                        colors = IconButtonDefaults.iconButtonColors(
                            containerColor = Color.White, contentColor = Color.Black,
                            disabledContainerColor = Color.White.copy(alpha = 0.6f), disabledContentColor = Color.Black,
                        ),
                    ) {
                        if (capturando) {
                            CircularProgressIndicator(Modifier.size(26.dp), color = Color.Black, strokeWidth = 3.dp)
                        } else {
                            Icon(Icons.Rounded.CameraAlt, contentDescription = if (enVivo) "Guardar este resultado" else "Tomar foto")
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                Spacer(Modifier.size(52.dp))
            }

            SingleChoiceSegmentedButtonRow(Modifier.width(260.dp)) {
                val colores = SegmentedButtonDefaults.colors(
                    activeContainerColor = MaterialTheme.colorScheme.primary,
                    activeContentColor = MaterialTheme.colorScheme.onPrimary,
                    activeBorderColor = MaterialTheme.colorScheme.primary,
                    inactiveContainerColor = Color.Black.copy(alpha = 0.5f),
                    inactiveContentColor = Color.White,
                    inactiveBorderColor = Color.White.copy(alpha = 0.4f),
                )
                SegmentedButton(
                    selected = !enVivo, onClick = { enVivo = false },
                    shape = SegmentedButtonDefaults.itemShape(0, 2), colors = colores, icon = {},
                ) { Text("Foto") }
                SegmentedButton(
                    selected = enVivo, onClick = { enVivo = true },
                    shape = SegmentedButtonDefaults.itemShape(1, 2), colors = colores, icon = {},
                ) { Text("En vivo") }
            }
        }
    }
}

@Composable
private fun BotonRedondo(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tamano: Int = 44,
    activo: Boolean = false,
    content: @Composable () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier.size(tamano.dp),
        colors = IconButtonDefaults.iconButtonColors(
            containerColor = if (activo) MaterialTheme.colorScheme.primary else Color.Black.copy(alpha = 0.5f),
            contentColor = if (activo) MaterialTheme.colorScheme.onPrimary else Color.White,
        ),
        content = content,
    )
}

@Composable
private fun Pastilla(texto: String) {
    Text(
        texto,
        color = Color.White,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.55f))
            .padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun LiveChip(r: Resultado?) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (r == null) {
            CircularProgressIndicator(Modifier.size(14.dp), color = Color.White, strokeWidth = 2.dp)
            Spacer(Modifier.width(10.dp))
            Text("Analizando…", color = Color.White)
        } else {
            val e = estilo(r.veredicto)
            Box(Modifier.size(12.dp).clip(CircleShape).background(e.color))
            Spacer(Modifier.width(10.dp))
            Text(e.corto, color = Color.White, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.width(10.dp))
            Text(
                "defectuosa ${pct(r.pDefectuosa)} · ${r.tiempoMs} ms",
                color = Color.White.copy(alpha = 0.75f),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun GuideOverlay(color: Color, puntoFoco: Offset?) {
    Canvas(Modifier.fillMaxSize()) {
        val lado = size.width * GUIA
        val left = (size.width - lado) / 2
        val top = (size.height - lado) / 2
        val radio = 28.dp.toPx()
        val scrim = Path().apply {
            fillType = PathFillType.EvenOdd
            addRect(Rect(0f, 0f, size.width, size.height))
            addRoundRect(RoundRect(left, top, left + lado, top + lado, CornerRadius(radio)))
        }
        drawPath(scrim, Color.Black.copy(alpha = 0.45f))

        // Esquinas del recuadro guía
        val l = lado * 0.16f
        val r = left + lado
        val b = top + lado
        val trazo = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round)
        listOf(
            Path().apply { moveTo(left, top + l); lineTo(left, top + radio); quadraticTo(left, top, left + radio, top); lineTo(left + l, top) },
            Path().apply { moveTo(r - l, top); lineTo(r - radio, top); quadraticTo(r, top, r, top + radio); lineTo(r, top + l) },
            Path().apply { moveTo(left, b - l); lineTo(left, b - radio); quadraticTo(left, b, left + radio, b); lineTo(left + l, b) },
            Path().apply { moveTo(r - l, b); lineTo(r - radio, b); quadraticTo(r, b, r, b - radio); lineTo(r, b - l) },
        ).forEach { drawPath(it, color, style = trazo) }

        puntoFoco?.let {
            drawCircle(Color.White, 34.dp.toPx(), it, style = Stroke(2.dp.toPx()))
            drawCircle(Color.White.copy(alpha = 0.8f), 4.dp.toPx(), it)
        }
    }
}
