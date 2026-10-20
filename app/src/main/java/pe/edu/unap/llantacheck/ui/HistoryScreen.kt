package pe.edu.unap.llantacheck.ui

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import pe.edu.unap.llantacheck.data.Analisis
import pe.edu.unap.llantacheck.data.HistoryRepository
import pe.edu.unap.llantacheck.ml.Veredicto

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(repo: HistoryRepository, onBack: () -> Unit, onAbrir: (Analisis) -> Unit) {
    var items by remember { mutableStateOf(repo.cargar()) }
    var confirmarBorrado by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Historial (${items.size})") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver") }
                },
                actions = {
                    if (items.isNotEmpty()) {
                        IconButton(onClick = { confirmarBorrado = true }) {
                            Icon(Icons.Rounded.DeleteSweep, contentDescription = "Borrar todo")
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (items.isEmpty()) {
            Column(
                Modifier.padding(padding).fillMaxSize().padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Icon(Icons.Rounded.History, contentDescription = null, modifier = Modifier.size(56.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.size(12.dp))
                Text("Aún no hay análisis", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Los resultados de cada foto analizada aparecerán aquí.",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(
                Modifier.padding(padding),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(bottom = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        StatChip(items.count { it.veredicto == Veredicto.DEFECTUOSA }, "defectuosas", VerdictColors.defectuosa)
                        StatChip(items.count { it.veredicto == Veredicto.BUEN_ESTADO }, "buenas", VerdictColors.buena)
                        StatChip(items.count { it.veredicto == Veredicto.NO_CONCLUYENTE }, "dudosas", VerdictColors.dudosa)
                    }
                }
                items(items, key = { it.id }) { a ->
                    HistoryItem(a, repo, onClick = { onAbrir(a) }, onDelete = {
                        repo.eliminar(a)
                        items = repo.cargar()
                    })
                }
            }
        }
    }

    if (confirmarBorrado) {
        AlertDialog(
            onDismissRequest = { confirmarBorrado = false },
            title = { Text("¿Borrar todo el historial?") },
            text = { Text("Se eliminarán ${items.size} análisis y sus imágenes. Esta acción no se puede deshacer.") },
            confirmButton = {
                TextButton(onClick = {
                    repo.borrarTodo()
                    items = emptyList()
                    confirmarBorrado = false
                }) { Text("Borrar") }
            },
            dismissButton = { TextButton(onClick = { confirmarBorrado = false }) { Text("Cancelar") } },
        )
    }
}

@Composable
private fun HistoryItem(a: Analisis, repo: HistoryRepository, onClick: () -> Unit, onDelete: () -> Unit) {
    val e = estilo(a.veredicto)
    val thumb by produceState<ImageBitmap?>(null, a.id) {
        value = withContext(Dispatchers.IO) {
            BitmapFactory.decodeFile(repo.imagen(a).path, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
        }
    }
    Card(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)) {
                thumb?.let { Image(it, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize()) }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(10.dp).clip(RoundedCornerShape(5.dp)).background(e.color))
                    Spacer(Modifier.width(8.dp))
                    Text(e.corto, fontWeight = FontWeight.SemiBold, color = e.color)
                }
                Text("Defectuosa ${pct(a.pDefectuosa)} · Buen estado ${pct(a.pBuena)}", style = MaterialTheme.typography.bodySmall)
                Text(
                    fechaLarga(a.fecha) + " · " + a.fuente,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Rounded.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
