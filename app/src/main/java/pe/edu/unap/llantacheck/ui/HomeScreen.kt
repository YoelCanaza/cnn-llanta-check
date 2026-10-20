package pe.edu.unap.llantacheck.ui

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.unap.llantacheck.R
import pe.edu.unap.llantacheck.data.Analisis
import pe.edu.unap.llantacheck.ml.Veredicto
import java.io.File

@Composable
fun HomeScreen(
    historial: List<Analisis>,
    imagenDe: (Analisis) -> File,
    onFoto: () -> Unit,
    onGaleria: () -> Unit,
    onEnVivo: () -> Unit,
    onHistorial: () -> Unit,
    onAcercaDe: () -> Unit,
    onAbrir: (Analisis) -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Surface(Modifier.fillMaxSize(), color = cs.background) {
        Column(Modifier.verticalScroll(rememberScrollState())) {
            // Cabecera con degradado
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(cs.primary.copy(alpha = 0.22f), cs.background))),
            ) {
                Column(
                    Modifier.statusBarsPadding().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(60.dp).clip(RoundedCornerShape(18.dp)).background(Color(0xFF1F2328)),
                        ) {
                            Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text("LlantaCheck", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                            Text("Inspección de llantas con IA", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
                        }
                    }
                    Spacer(Modifier.height(20.dp))
                    Text(
                        "Fotografía de cerca una llanta y una red neuronal convolucional te dirá si parece defectuosa " +
                            "o en buen estado. Todo ocurre en tu teléfono, sin internet.",
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }

            Column(
                Modifier.padding(horizontal = 20.dp).navigationBarsPadding().padding(bottom = 20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                // Acción principal
                Card(
                    onClick = onFoto,
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = cs.primary, contentColor = cs.onPrimary),
                ) {
                    Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(cs.onPrimary.copy(alpha = 0.14f)),
                            contentAlignment = Alignment.Center,
                        ) { Icon(Icons.Rounded.CameraAlt, contentDescription = null, modifier = Modifier.size(30.dp)) }
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Tomar foto", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Acerca la cámara a la superficie", style = MaterialTheme.typography.bodyMedium)
                        }
                        Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
                    }
                }

                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ActionTile(Icons.Rounded.PhotoLibrary, "Galería", "Elegir una foto", onGaleria, Modifier.weight(1f))
                    ActionTile(Icons.Rounded.Videocam, "En vivo", "Análisis continuo", onEnVivo, Modifier.weight(1f))
                }

                if (historial.isNotEmpty()) {
                    val ultimo = historial.first()
                    Text(
                        "Último análisis",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 6.dp),
                    )
                    UltimoCard(ultimo, imagenDe(ultimo)) { onAbrir(ultimo) }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatChip(historial.count { it.veredicto == Veredicto.DEFECTUOSA }, "defectuosas", VerdictColors.defectuosa)
                        StatChip(historial.count { it.veredicto == Veredicto.BUEN_ESTADO }, "buenas", VerdictColors.buena)
                        StatChip(historial.count { it.veredicto == Veredicto.NO_CONCLUYENTE }, "dudosas", VerdictColors.dudosa)
                    }
                }

                Spacer(Modifier.height(2.dp))
                MenuRow(Icons.Rounded.History, "Historial", "${historial.size} análisis guardados en el teléfono", onHistorial)
                MenuRow(Icons.Rounded.Info, "Acerca del modelo", "Cómo funciona, métricas y verificación", onAcercaDe)

                SectionCard("Consejos para una buena foto") {
                    Consejo("1", "Acerca la cámara a la superficie de la llanta: banda de rodadura o flanco.")
                    Consejo("2", "Usa buena luz; si está oscuro, activa la linterna en la cámara.")
                    Consejo("3", "Mantén el teléfono quieto y toca la pantalla para enfocar.")
                }

                Text(
                    "LlantaCheck es una herramienta de apoyo y no reemplaza la inspección de un profesional.",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ActionTile(icon: ImageVector, titulo: String, subtitulo: String, onClick: () -> Unit, modifier: Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(18.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.height(14.dp))
            Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UltimoCard(a: Analisis, imagen: File, onClick: () -> Unit) {
    val e = estilo(a.veredicto)
    val thumb by produceState<ImageBitmap?>(null, a.id) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(imagen.path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
        }
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(72.dp).aspectRatio(1f).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.titulo, fontWeight = FontWeight.SemiBold, color = e.color)
                Text(
                    "Defectuosa ${pct(a.pDefectuosa)} · Buen estado ${pct(a.pBuena)}",
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    fechaLarga(a.fecha) + " · " + a.fuente,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            VerdictBadge(a.veredicto, size = 40)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, titulo: String, subtitulo: String, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(titulo, fontWeight = FontWeight.SemiBold)
                Text(subtitulo, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun Consejo(n: String, texto: String) {
    Row(verticalAlignment = Alignment.Top) {
        Box(
            Modifier.size(22.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(n, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.width(10.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium)
    }
}
