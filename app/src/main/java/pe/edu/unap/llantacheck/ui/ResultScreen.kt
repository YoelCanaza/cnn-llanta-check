package pe.edu.unap.llantacheck.ui

import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import pe.edu.unap.llantacheck.data.Analisis
import pe.edu.unap.llantacheck.ml.Decision
import pe.edu.unap.llantacheck.ml.Veredicto
import pe.edu.unap.llantacheck.ml.avisoCalidad
import java.io.File
import kotlin.math.max

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultScreen(
    analisis: Analisis,
    imagen: File,
    onBack: () -> Unit,
    onNueva: () -> Unit,
    onHistorial: () -> Unit,
) {
    val context = LocalContext.current
    val e = estilo(analisis.veredicto)
    val bitmap = remember(analisis.id) { BitmapFactory.decodeFile(imagen.path)?.asImageBitmap() }
    val confianza = max(analisis.pDefectuosa, analisis.pBuena)
    val aviso = avisoCalidad(analisis.brillo)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Resultado") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    IconButton(onClick = { compartir(context, analisis, imagen) }) {
                        Icon(Icons.Rounded.Share, contentDescription = "Compartir")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
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
            // Imagen con el veredicto superpuesto
            Box(Modifier.fillMaxWidth().aspectRatio(1f).clip(RoundedCornerShape(28.dp))) {
                if (bitmap != null) {
                    Image(bitmap, contentDescription = "Imagen analizada", contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                }
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.75f))))
                        .padding(18.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.clip(RoundedCornerShape(50)).background(e.color).padding(horizontal = 14.dp, vertical = 6.dp),
                        ) {
                            Text(e.corto, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.weight(1f))
                        Text(analisis.fuente, color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodySmall)
                    }
                }
            }

            // Veredicto
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = e.color.copy(alpha = 0.13f)),
            ) {
                Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                    ConfidenceRing(
                        confianza, e.color,
                        etiqueta = if (analisis.veredicto == Veredicto.NO_CONCLUYENTE) "máxima" else "confianza",
                    )
                    Spacer(Modifier.width(18.dp))
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(e.icono, contentDescription = null, tint = e.color, modifier = Modifier.size(22.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(e.titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = e.color)
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(e.consejo, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }

            aviso?.let { AvisoCard(textoAviso(it)) }

            SectionCard("Probabilidades del modelo") {
                ProbabilityRow("Defectuosa", analisis.pDefectuosa, VerdictColors.defectuosa)
                ProbabilityRow("Buen estado", analisis.pBuena, VerdictColors.buena)
            }

            SectionCard("¿Cómo se decide?") {
                DecisionScale(analisis.pDefectuosa)
                Text(
                    "El marcador indica la probabilidad de que la llanta sea defectuosa. La app solo da un " +
                        "veredicto si una clase alcanza el ${pct(Decision.UMBRAL_CONFIANZA)}; en la zona gris " +
                        "el modelo no está seguro y prefiere no adivinar.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionCard("Detalles") {
                InfoRow("Origen", analisis.fuente)
                InfoRow("Fecha", fechaLarga(analisis.fecha))
                InfoRow("Tiempo de inferencia", "${analisis.tiempoMs} ms")
                InfoRow("Modelo", "MobileNetV2 · TFLite fp16")
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = onHistorial, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.History, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Historial")
                }
                Button(onClick = onNueva, modifier = Modifier.weight(1f).height(52.dp)) {
                    Icon(Icons.Rounded.CameraAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Analizar otra")
                }
            }

            Text(
                "Herramienta de apoyo: no reemplaza la inspección de un profesional.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 16.dp),
            )
        }
    }
}

private fun compartir(context: Context, a: Analisis, imagen: File) {
    val e = estilo(a.veredicto)
    val texto = "LlantaCheck: ${e.titulo}\n" +
        "Probabilidad defectuosa: ${pct(a.pDefectuosa)} · buen estado: ${pct(a.pBuena)}\n" +
        "${fechaLarga(a.fecha)} — análisis con IA (MobileNetV2). No reemplaza la inspección de un profesional."
    val intent = Intent(Intent.ACTION_SEND).apply {
        putExtra(Intent.EXTRA_TEXT, texto)
        if (imagen.exists()) {
            type = "image/jpeg"
            putExtra(Intent.EXTRA_STREAM, FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", imagen))
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        } else {
            type = "text/plain"
        }
    }
    context.startActivity(Intent.createChooser(intent, "Compartir resultado"))
}
