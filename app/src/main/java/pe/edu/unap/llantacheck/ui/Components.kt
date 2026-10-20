package pe.edu.unap.llantacheck.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.QuestionMark
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import pe.edu.unap.llantacheck.ml.AvisoCalidad
import pe.edu.unap.llantacheck.ml.Decision
import pe.edu.unap.llantacheck.ml.Veredicto
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class EstiloVeredicto(val color: Color, val icono: ImageVector, val titulo: String, val corto: String, val consejo: String)

fun estilo(v: Veredicto): EstiloVeredicto = when (v) {
    Veredicto.DEFECTUOSA -> EstiloVeredicto(
        VerdictColors.defectuosa, Icons.Rounded.Warning,
        "Posible llanta defectuosa", "Defectuosa",
        "El modelo detectó signos de daño (grietas, cortes, deformaciones o desgaste). " +
            "Haz revisar la llanta por un especialista antes de seguir usándola.",
    )
    Veredicto.BUEN_ESTADO -> EstiloVeredicto(
        VerdictColors.buena, Icons.Rounded.CheckCircle,
        "Llanta en buen estado", "Buen estado",
        "No se detectaron defectos visibles. Aun así, revisa periódicamente la presión " +
            "y la profundidad del dibujo.",
    )
    Veredicto.NO_CONCLUYENTE -> EstiloVeredicto(
        VerdictColors.dudosa, Icons.Rounded.QuestionMark,
        "Resultado no concluyente", "No concluyente",
        "El modelo no está seguro. Puede que la imagen no sea una llanta o que esté borrosa, " +
            "oscura o muy lejos. Toma otra foto más cerca de la superficie de la llanta.",
    )
}

fun textoAviso(a: AvisoCalidad): String = when (a) {
    AvisoCalidad.OSCURA -> "La foto está muy oscura. Con más luz el resultado será más fiable."
    AvisoCalidad.SOBREEXPUESTA -> "La foto tiene demasiada luz o reflejos. Evita el sol directo o el flash."
}

fun pct(p: Float): String = "${(p * 100).roundToInt()} %"

fun fechaLarga(ms: Long): String =
    SimpleDateFormat("d MMM yyyy, HH:mm", Locale.forLanguageTag("es-PE")).format(Date(ms))

@Composable
fun VerdictBadge(v: Veredicto, modifier: Modifier = Modifier, size: Int = 44) {
    val e = estilo(v)
    Box(
        modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(e.color.copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(e.icono, contentDescription = null, tint = e.color, modifier = Modifier.size((size * 0.56f).dp))
    }
}

/** Anillo animado con el porcentaje de confianza de la clase ganadora. */
@Composable
fun ConfidenceRing(p: Float, color: Color, modifier: Modifier = Modifier, diametro: Dp = 104.dp, etiqueta: String = "confianza") {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(p) { anim.animateTo(p, tween(900, easing = FastOutSlowInEasing)) }
    val pista = MaterialTheme.colorScheme.surfaceVariant
    Box(modifier.size(diametro), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val grosor = 10.dp.toPx()
            val tl = Offset(grosor / 2, grosor / 2)
            val sz = Size(size.width - grosor, size.height - grosor)
            drawArc(pista, -90f, 360f, false, tl, sz, style = Stroke(grosor))
            drawArc(color, -90f, 360f * anim.value, false, tl, sz, style = Stroke(grosor, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(pct(anim.value), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Text(etiqueta, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Escala de P(defectuosa) con las tres zonas de decisión y un marcador en el valor obtenido:
 * [0, 1-u] buen estado · (1-u, u) no concluyente · [u, 1] defectuosa.
 */
@Composable
fun DecisionScale(pDefectuosa: Float) {
    val u = Decision.UMBRAL_CONFIANZA
    val anim = remember { Animatable(0.5f) }
    LaunchedEffect(pDefectuosa) { anim.animateTo(pDefectuosa, tween(900, easing = FastOutSlowInEasing)) }
    val borde = MaterialTheme.colorScheme.onSurface
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Canvas(Modifier.fillMaxWidth().height(30.dp)) {
            val h = 12.dp.toPx()
            val y = (size.height - h) / 2
            val w = size.width
            val barra = Path().apply {
                addRoundRect(androidx.compose.ui.geometry.RoundRect(0f, y, w, y + h, CornerRadius(h / 2)))
            }
            clipPath(barra) {
                drawRect(VerdictColors.buena, Offset(0f, y), Size(w * (1 - u), h))
                drawRect(VerdictColors.dudosa, Offset(w * (1 - u), y), Size(w * (2 * u - 1), h))
                drawRect(VerdictColors.defectuosa, Offset(w * u, y), Size(w * (1 - u), h))
            }
            val x = (w * anim.value).coerceIn(9.dp.toPx(), w - 9.dp.toPx())
            drawCircle(Color.White, 10.dp.toPx(), Offset(x, size.height / 2))
            drawCircle(borde, 10.dp.toPx(), Offset(x, size.height / 2), style = Stroke(3.dp.toPx()))
        }
        Row(Modifier.fillMaxWidth()) {
            Text("Buen estado", style = MaterialTheme.typography.labelSmall, color = VerdictColors.buena, modifier = Modifier.weight(1f))
            Text("No concluyente", style = MaterialTheme.typography.labelSmall, color = VerdictColors.dudosa,
                textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
            Text("Defectuosa", style = MaterialTheme.typography.labelSmall, color = VerdictColors.defectuosa,
                textAlign = TextAlign.End, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
fun ProbabilityRow(etiqueta: String, p: Float, color: Color) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(p) { anim.animateTo(p, tween(900, easing = FastOutSlowInEasing)) }
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(etiqueta, style = MaterialTheme.typography.bodyMedium)
            Text(pct(p), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { anim.value.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp)),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            strokeCap = StrokeCap.Round,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
    }
}

@Composable
fun SectionCard(titulo: String? = null, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (titulo != null) {
                Text(titulo, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            }
            content()
        }
    }
}

@Composable
fun InfoRow(clave: String, valor: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(clave, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.size(12.dp))
        Text(valor, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, textAlign = TextAlign.End)
    }
}

/** Tarjeta con un número grande y su etiqueta. */
@Composable
fun MetricTile(valor: String, etiqueta: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(valor, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color)
        Text(etiqueta, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** Contador pequeño con punto de color, para resúmenes del historial. */
@Composable
fun StatChip(numero: Int, etiqueta: String, color: Color, modifier: Modifier = Modifier) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.14f))
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(Modifier.width(6.dp))
        Text("$numero $etiqueta", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
fun AvisoCard(texto: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.width(12.dp))
        Text(texto, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
