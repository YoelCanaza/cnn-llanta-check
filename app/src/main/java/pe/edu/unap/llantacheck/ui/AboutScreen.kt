package pe.edu.unap.llantacheck.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.edu.unap.llantacheck.data.ModelInfo
import pe.edu.unap.llantacheck.data.Muestras
import pe.edu.unap.llantacheck.ml.BitmapUtils
import pe.edu.unap.llantacheck.ml.Decision
import pe.edu.unap.llantacheck.ml.TyreClassifier
import java.util.Locale
import kotlin.math.abs

/** Tolerancia entre la probabilidad calculada en el teléfono y la calculada en Colab. */
private const val TOLERANCIA = 0.02f

private data class FilaVerificacion(val archivo: String, val real: String, val esperado: Float, val obtenido: Float) {
    val ok get() = abs(esperado - obtenido) <= TOLERANCIA
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AboutScreen(classifier: TyreClassifier, onBack: () -> Unit) {
    val context = LocalContext.current
    val metricas = remember { ModelInfo.metricas(context) }
    val muestras = remember { Muestras.cargar(context) }
    var filas by remember { mutableStateOf<List<FilaVerificacion>?>(null) }
    var verificando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Acerca del modelo") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SectionCard("¿Qué hace LlantaCheck?") {
                Parrafo(
                    "Clasifica la fotografía de una llanta como defectuosa o en buen estado usando una red " +
                        "neuronal convolucional (CNN) que se ejecuta directamente en el teléfono, sin conexión a internet.",
                )
            }

            SectionCard("Modelo") {
                InfoRow("Arquitectura", "MobileNetV2")
                InfoRow("Entrenamiento", "Transfer learning + fine-tuning")
                InfoRow("Preentrenamiento", "ImageNet")
                InfoRow("Entrada", "${classifier.inputSize} × ${classifier.inputSize} RGB")
                InfoRow("Clases", classifier.labels.joinToString(" / "))
                InfoRow("Formato", "TensorFlow Lite (float16)")
                InfoRow("Umbral de decisión", pct(Decision.UMBRAL_CONFIANZA))
            }

            SectionCard("¿Cómo funciona?") {
                Paso("1", "Foto", "Se recorta la zona del recuadro guía y se reduce a 224 × 224 píxeles.")
                Paso("2", "CNN MobileNetV2", "17 bloques convolucionales extraen bordes, texturas y patrones de la llanta.")
                Paso("3", "Clasificador", "Una capa densa con softmax da la probabilidad de cada clase.")
                Paso("4", "Decisión", "Si una clase supera el ${pct(Decision.UMBRAL_CONFIANZA)} se muestra el veredicto; si no, es no concluyente.")
            }

            SectionCard("Resultados en el conjunto de prueba") {
                if (metricas == null) {
                    Parrafo("Modelo provisional: las métricas aparecerán al integrar el modelo entrenado.")
                } else {
                    metricas.destacadas.chunked(2).forEach { fila ->
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            fila.forEach { (k, v) -> MetricTile(v, k, Modifier.weight(1f)) }
                            if (fila.size == 1) Spacer(Modifier.weight(1f))
                        }
                    }
                    metricas.matriz?.let { MatrizConfusion(it, metricas.clases) }
                    metricas.detalle.forEach { (k, v) -> InfoRow(k, v) }
                }
            }

            SectionCard("Verificación del modelo") {
                if (muestras.isEmpty()) {
                    Parrafo("No hay muestras de verificación en esta versión.")
                } else {
                    Parrafo(
                        "Analiza ${muestras.size} imágenes del conjunto de prueba y compara la probabilidad obtenida " +
                            "en el teléfono con la calculada durante el entrenamiento (tolerancia ±${pct(TOLERANCIA)}).",
                    )
                    FilledTonalButton(
                        enabled = !verificando,
                        onClick = {
                            verificando = true
                            scope.launch {
                                filas = withContext(Dispatchers.Default) {
                                    muestras.mapNotNull { m ->
                                        val b = BitmapUtils.decodeAsset(context, "${Muestras.DIR}/${m.archivo}") ?: return@mapNotNull null
                                        FilaVerificacion(m.archivo, m.real, m.pDefectuosaEsperada, classifier.classify(b).pDefectuosa)
                                    }
                                }
                                verificando = false
                            }
                        },
                    ) {
                        if (verificando) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text("Ejecutar verificación")
                    }
                    filas?.let { lista ->
                        val oks = lista.count { it.ok }
                        val maxDif = lista.maxOfOrNull { abs(it.esperado - it.obtenido) } ?: 0f
                        Text(
                            "$oks de ${lista.size} coinciden · diferencia máxima ${String.format(Locale.US, "%.4f", maxDif)}",
                            fontWeight = FontWeight.SemiBold,
                            color = if (oks == lista.size) VerdictColors.buena else VerdictColors.defectuosa,
                        )
                        lista.forEach { f ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (f.ok) Icons.Rounded.CheckCircle else Icons.Rounded.Cancel, contentDescription = null,
                                    tint = if (f.ok) VerdictColors.buena else VerdictColors.defectuosa,
                                    modifier = Modifier.size(18.dp),
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(f.real, style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    "esperado ${String.format(Locale.US, "%.3f", f.esperado)} · app ${String.format(Locale.US, "%.3f", f.obtenido)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
            }

            SectionCard("Dataset") {
                Parrafo(
                    "Pathmanaban P., Abishek C., Sai K. M., Karthick S., Aakash S. (2023). Digital images of defective " +
                        "and good condition tyres (V1). Mendeley Data. doi:10.17632/bn7ch8tvyp.1 — Licencia CC BY 4.0.",
                )
            }

            SectionCard("Limitaciones") {
                Parrafo("• El modelo solo distingue dos clases; no identifica el tipo de defecto ni su gravedad.")
                Parrafo("• No mide la profundidad del dibujo ni la presión de la llanta.")
                Parrafo("• Ante imágenes que no son llantas puede equivocarse; por eso existe el resultado no concluyente.")
                Parrafo("• Es una herramienta de apoyo y no reemplaza la inspección de un profesional.")
            }

            SectionCard("Créditos") {
                InfoRow("Autor", "Canaza Chagua Yoel Nhelio")
                InfoRow("Curso", "Aprendizaje Profundo")
                InfoRow("Universidad", "UNA Puno")
                InfoRow("Versión", "1.0")
            }
            Spacer(Modifier.size(8.dp))
        }
    }
}

@Composable
private fun Parrafo(texto: String) {
    Text(texto, style = MaterialTheme.typography.bodyMedium)
}

@Composable
private fun Paso(n: String, titulo: String, texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(28.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center,
        ) {
            Text(n, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(titulo, fontWeight = FontWeight.SemiBold)
            Text(texto, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private fun nombreClase(c: String) = when (c) {
    "defective" -> "Defectuosa"
    "good" -> "Buen estado"
    else -> c
}

/** Matriz de confusión 2×2: diagonal (aciertos) en verde, errores en rojo. */
@Composable
private fun MatrizConfusion(m: List<List<Int>>, clases: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("Matriz de confusión", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        Row {
            Spacer(Modifier.width(92.dp))
            clases.forEach {
                Text(
                    "Pred. ${nombreClase(it).lowercase()}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.weight(1f),
                )
            }
        }
        m.forEachIndexed { i, fila ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "Real ${nombreClase(clases.getOrElse(i) { "" }).lowercase()}", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(92.dp),
                )
                fila.forEachIndexed { j, v ->
                    val color = if (i == j) VerdictColors.buena else VerdictColors.defectuosa
                    Box(
                        Modifier.weight(1f).padding(3.dp).height(52.dp).clip(RoundedCornerShape(12.dp))
                            .background(color.copy(alpha = if (i == j) 0.28f else 0.16f)),
                        contentAlignment = Alignment.Center,
                    ) { Text("$v", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                }
            }
        }
    }
}
