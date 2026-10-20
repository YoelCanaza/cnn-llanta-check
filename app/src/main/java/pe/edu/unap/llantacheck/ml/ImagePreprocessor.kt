package pe.edu.unap.llantacheck.ml

import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Reproduce el redimensionado usado en el entrenamiento (`image_dataset_from_directory`,
 * que llama a `tf.image.resize` bilineal con half-pixel centers y sin antialias):
 * la imagen completa se lleva a size×size SIN conservar la proporción.
 *
 * Trabaja sobre píxeles ARGB empaquetados para poder probarse en la JVM sin Android.
 * La salida es RGB intercalado (HWC) en float con valores 0–255; la normalización
 * a [-1, 1] la hace el propio modelo.
 */
object ImagePreprocessor {

    fun resizeBilinearRgb(
        argb: IntArray,
        width: Int,
        height: Int,
        size: Int,
        out: FloatArray = FloatArray(size * size * 3),
    ): FloatArray {
        require(argb.size == width * height) { "Se esperaban ${width * height} píxeles" }
        require(out.size == size * size * 3) { "Buffer de salida de tamaño incorrecto" }

        val scaleX = width.toFloat() / size
        val scaleY = height.toFloat() / size

        val x0 = IntArray(size)
        val x1 = IntArray(size)
        val lerpX = FloatArray(size)
        for (x in 0 until size) {
            val inX = (x + 0.5f) * scaleX - 0.5f
            val f = floor(inX)
            x0[x] = max(f.toInt(), 0)
            x1[x] = min(ceil(inX).toInt(), width - 1).coerceAtLeast(0)
            lerpX[x] = inX - f
        }

        var o = 0
        for (y in 0 until size) {
            val inY = (y + 0.5f) * scaleY - 0.5f
            val f = floor(inY)
            val row0 = max(f.toInt(), 0) * width
            val row1 = min(ceil(inY).toInt(), height - 1).coerceAtLeast(0) * width
            val ly = inY - f
            for (x in 0 until size) {
                val tl = argb[row0 + x0[x]]
                val tr = argb[row0 + x1[x]]
                val bl = argb[row1 + x0[x]]
                val br = argb[row1 + x1[x]]
                val lx = lerpX[x]
                for (shift in CHANNEL_SHIFTS) {
                    val a = ((tl shr shift) and 0xFF).toFloat()
                    val b = ((tr shr shift) and 0xFF).toFloat()
                    val c = ((bl shr shift) and 0xFF).toFloat()
                    val d = ((br shr shift) and 0xFF).toFloat()
                    val top = a + (b - a) * lx
                    val bottom = c + (d - c) * lx
                    out[o++] = top + (bottom - top) * ly
                }
            }
        }
        return out
    }

    private val CHANNEL_SHIFTS = intArrayOf(16, 8, 0) // R, G, B
}
