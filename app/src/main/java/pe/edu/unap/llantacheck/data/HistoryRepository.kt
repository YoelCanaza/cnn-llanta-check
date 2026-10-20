package pe.edu.unap.llantacheck.data

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import org.json.JSONObject
import pe.edu.unap.llantacheck.ml.BitmapUtils
import pe.edu.unap.llantacheck.ml.Resultado
import pe.edu.unap.llantacheck.ml.Veredicto
import java.io.File

data class Analisis(
    val id: Long,
    val fecha: Long,
    val veredicto: Veredicto,
    val pDefectuosa: Float,
    val pBuena: Float,
    val fuente: String,
    val tiempoMs: Long,
    val brillo: Float = -1f,
)

/** Historial local: un índice JSON y una miniatura JPEG por análisis, en el almacenamiento privado de la app. */
class HistoryRepository(context: Context) {

    private val dir = File(context.filesDir, "historial").apply { mkdirs() }
    private val index = File(dir, "historial.json")

    fun imagen(a: Analisis) = File(dir, "${a.id}.jpg")

    @Synchronized
    fun cargar(): List<Analisis> {
        if (!index.exists()) return emptyList()
        val arr = runCatching { JSONArray(index.readText()) }.getOrElse { return emptyList() }
        return (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            Analisis(
                id = o.getLong("id"),
                fecha = o.getLong("fecha"),
                veredicto = Veredicto.valueOf(o.getString("veredicto")),
                pDefectuosa = o.getDouble("pDefectuosa").toFloat(),
                pBuena = o.getDouble("pBuena").toFloat(),
                fuente = o.getString("fuente"),
                tiempoMs = o.optLong("tiempoMs"),
                brillo = o.optDouble("brillo", -1.0).toFloat(),
            )
        }.sortedByDescending { it.fecha }
    }

    @Synchronized
    fun guardar(imagen: Bitmap, r: Resultado, fuente: String): Analisis {
        val now = System.currentTimeMillis()
        val a = Analisis(now, now, r.veredicto, r.pDefectuosa, r.pBuena, fuente, r.tiempoMs, r.brillo)
        BitmapUtils.saveJpeg(imagen, imagen(a))
        escribir(listOf(a) + cargar())
        return a
    }

    @Synchronized
    fun eliminar(a: Analisis) {
        imagen(a).delete()
        escribir(cargar().filter { it.id != a.id })
    }

    @Synchronized
    fun borrarTodo() {
        dir.listFiles()?.forEach { it.delete() }
    }

    private fun escribir(items: List<Analisis>) {
        val arr = JSONArray()
        items.forEach { a ->
            arr.put(JSONObject().apply {
                put("id", a.id)
                put("fecha", a.fecha)
                put("veredicto", a.veredicto.name)
                put("pDefectuosa", a.pDefectuosa.toDouble())
                put("pBuena", a.pBuena.toDouble())
                put("fuente", a.fuente)
                put("tiempoMs", a.tiempoMs)
                put("brillo", a.brillo.toDouble())
            })
        }
        val tmp = File(dir, "historial.json.tmp")
        tmp.writeText(arr.toString())
        tmp.renameTo(index)
    }
}
