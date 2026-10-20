package pe.edu.unap.llantacheck.data

import android.content.Context
import org.json.JSONObject
import java.util.Locale

data class Metricas(
    /** Métricas principales, mostradas como tarjetas grandes. */
    val destacadas: List<Pair<String, String>>,
    /** Resto de datos del entrenamiento. */
    val detalle: List<Pair<String, String>>,
    /** Matriz de confusión del conjunto de prueba (filas = real, columnas = predicción). */
    val matriz: List<List<Int>>?,
    val clases: List<String>,
)

/** Métricas del modelo leídas de model_info.json (generado por el cuaderno). Null si es el modelo provisional. */
object ModelInfo {

    fun metricas(context: Context): Metricas? {
        val json = runCatching {
            JSONObject(context.assets.open("model_info.json").bufferedReader().readText())
        }.getOrNull() ?: return null

        fun pct(v: Double) = String.format(Locale.US, "%.1f %%", v * 100)
        val destacadas = mutableListOf<Pair<String, String>>()
        val detalle = mutableListOf<Pair<String, String>>()

        json.optJSONObject("test")?.let { t ->
            fun add(lista: MutableList<Pair<String, String>>, etiqueta: String, key: String) {
                t.optDouble(key).takeIf { !it.isNaN() }?.let { lista += etiqueta to pct(it) }
            }
            add(destacadas, "Accuracy", "accuracy")
            t.optDouble("auc_roc").takeIf { !it.isNaN() }?.let { destacadas += "AUC-ROC" to String.format(Locale.US, "%.3f", it) }
            add(destacadas, "Recall defectuosa", "recall_defective")
            add(destacadas, "Precisión defectuosa", "precision_defective")
            add(detalle, "F1 defectuosa", "f1_defective")
            add(detalle, "Recall buen estado", "recall_good")
            add(detalle, "Precisión buen estado", "precision_good")
            add(detalle, "F1 buen estado", "f1_good")
        }
        json.optJSONObject("particion")?.let { p ->
            detalle += "Imágenes train / val / test" to "${p.optInt("train")} / ${p.optInt("val")} / ${p.optInt("test")}"
        }
        json.optJSONObject("hiperparametros")?.let { h ->
            detalle += "Épocas (fase 1 + fine-tuning)" to "${h.optInt("epocas_fase1")} + ${h.optInt("epocas_fase2")}"
        }
        json.optDouble("tiempo_entrenamiento_min").takeIf { !it.isNaN() }?.let {
            detalle += "Tiempo de entrenamiento" to String.format(Locale.US, "%.1f min (GPU T4)", it)
        }
        json.optJSONObject("tflite")?.optJSONObject("fp16")?.let { f ->
            detalle += "Accuracy TFLite fp16" to pct(f.optDouble("accuracy"))
        }

        val matriz = json.optJSONArray("matriz_confusion")?.let { m ->
            (0 until m.length()).map { i -> m.getJSONArray(i).let { r -> (0 until r.length()).map { r.getInt(it) } } }
        }
        val clases = json.optJSONObject("salida")?.optJSONArray("clases")?.let { c ->
            (0 until c.length()).map { c.getString(it) }
        } ?: listOf("defective", "good")
        return Metricas(destacadas, detalle, matriz, clases)
    }
}

data class Muestra(val archivo: String, val real: String, val pDefectuosaEsperada: Float)

object Muestras {
    const val DIR = "muestras"

    fun cargar(context: Context): List<Muestra> {
        val json = runCatching {
            JSONObject(context.assets.open("$DIR/muestras.json").bufferedReader().readText())
        }.getOrNull() ?: return emptyList()
        val clases = json.getJSONArray("clases")
        val idxDef = (0 until clases.length()).firstOrNull { clases.getString(it) == "defective" } ?: return emptyList()
        val arr = json.getJSONArray("muestras")
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Muestra(o.getString("archivo"), o.getString("real"), o.getJSONArray("prob_tflite_fp16").getDouble(idxDef).toFloat())
        }
    }
}
