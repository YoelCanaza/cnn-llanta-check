package pe.edu.unap.llantacheck

import android.graphics.Bitmap
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pe.edu.unap.llantacheck.data.Analisis
import pe.edu.unap.llantacheck.data.HistoryRepository
import pe.edu.unap.llantacheck.ml.BitmapUtils
import pe.edu.unap.llantacheck.ml.TyreClassifier
import pe.edu.unap.llantacheck.ui.AboutScreen
import pe.edu.unap.llantacheck.ui.CameraScreen
import pe.edu.unap.llantacheck.ui.HistoryScreen
import pe.edu.unap.llantacheck.ui.HomeScreen
import pe.edu.unap.llantacheck.ui.LlantaCheckTheme
import pe.edu.unap.llantacheck.ui.ResultScreen

sealed interface Pantalla {
    data object Inicio : Pantalla
    data class Camara(val enVivo: Boolean) : Pantalla
    data class Resultado(val analisis: Analisis) : Pantalla
    data object Historial : Pantalla
    data object AcercaDe : Pantalla
}

/** Pantalla visible y profundidad de la pila, para animar hacia adelante o hacia atrás. */
private data class Entrada(val pantalla: Pantalla, val profundidad: Int)

class MainActivity : ComponentActivity() {

    private var classifier: TyreClassifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val error = runCatching { classifier = TyreClassifier(applicationContext) }.exceptionOrNull()
        val repo = HistoryRepository(applicationContext)
        setContent {
            LlantaCheckTheme {
                val c = classifier
                if (c == null) {
                    ErrorScreen("No se pudo cargar el modelo: ${error?.message}")
                } else {
                    LlantaCheckApp(c, repo)
                }
            }
        }
    }

    override fun onDestroy() {
        classifier?.close()
        super.onDestroy()
    }
}

@Composable
private fun LlantaCheckApp(classifier: TyreClassifier, repo: HistoryRepository) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pila = remember { mutableStateListOf<Pantalla>(Pantalla.Inicio) }
    var analizando by remember { mutableStateOf(false) }

    fun ir(p: Pantalla) { pila.add(p) }
    fun atras() { if (pila.size > 1) pila.removeAt(pila.lastIndex) }

    fun analizar(fuente: String, obtener: () -> Bitmap?) {
        if (analizando) return
        analizando = true
        scope.launch {
            val a = runCatching {
                withContext(Dispatchers.Default) {
                    val b = obtener() ?: error("No se pudo leer la imagen")
                    repo.guardar(b, classifier.classify(b), fuente)
                }
            }
            analizando = false
            a.onSuccess {
                if (pila.last() is Pantalla.Camara) atras()
                ir(Pantalla.Resultado(it))
            }.onFailure {
                Toast.makeText(context, it.message ?: "Error al analizar la imagen", Toast.LENGTH_LONG).show()
            }
        }
    }

    val galeria = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) analizar("Galería") { BitmapUtils.decodeUri(context, uri) }
    }
    fun abrirGaleria() = galeria.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    BackHandler(enabled = pila.size > 1) { atras() }

    Box(Modifier.fillMaxSize()) {
        AnimatedContent(
            targetState = Entrada(pila.last(), pila.size),
            transitionSpec = {
                val adelante = targetState.profundidad >= initialState.profundidad
                val d = if (adelante) 1 else -1
                (slideInHorizontally(tween(280)) { d * it / 4 } + fadeIn(tween(280))) togetherWith
                    (slideOutHorizontally(tween(280)) { -d * it / 4 } + fadeOut(tween(200)))
            },
            label = "navegacion",
        ) { entrada ->
        when (val p = entrada.pantalla) {
            Pantalla.Inicio -> {
                // Se relee al volver a Inicio, porque la pantalla entra de nuevo en la composición
                val historial = remember { repo.cargar() }
                HomeScreen(
                    historial = historial,
                    imagenDe = repo::imagen,
                    onFoto = { ir(Pantalla.Camara(enVivo = false)) },
                    onGaleria = { abrirGaleria() },
                    onEnVivo = { ir(Pantalla.Camara(enVivo = true)) },
                    onHistorial = { ir(Pantalla.Historial) },
                    onAcercaDe = { ir(Pantalla.AcercaDe) },
                    onAbrir = { ir(Pantalla.Resultado(it)) },
                )
            }
            is Pantalla.Camara -> CameraScreen(
                enVivoInicial = p.enVivo,
                classifier = classifier,
                onCaptura = { b, fuente -> analizar(fuente) { b } },
                onGaleria = { abrirGaleria() },
                onBack = { atras() },
            )
            is Pantalla.Resultado -> ResultScreen(
                analisis = p.analisis,
                imagen = repo.imagen(p.analisis),
                onBack = { atras() },
                onNueva = {
                    atras()
                    ir(Pantalla.Camara(enVivo = false))
                },
                onHistorial = {
                    atras()
                    if (pila.last() !is Pantalla.Historial) ir(Pantalla.Historial)
                },
            )
            Pantalla.Historial -> HistoryScreen(
                repo = repo,
                onBack = { atras() },
                onAbrir = { ir(Pantalla.Resultado(it)) },
            )
            Pantalla.AcercaDe -> AboutScreen(classifier, onBack = { atras() })
        }
        }

        if (analizando) {
            Box(
                Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    CircularProgressIndicator()
                    Text("Analizando la llanta…", color = Color.White)
                }
            }
        }
    }
}

@Composable
private fun ErrorScreen(mensaje: String) {
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.padding(32.dp), contentAlignment = Alignment.Center) {
            Text(mensaje, textAlign = TextAlign.Center)
        }
    }
}
