package pe.edu.unap.llantacheck.ml

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Compara el redimensionado de la app con `tf.image.resize(..., "bilinear", antialias=False)`
 * de TensorFlow 2.21. Los valores de referencia se generaron en Python con la misma imagen sintética.
 */
class ImagePreprocessorTest {

    private class Caso(val w: Int, val h: Int, val suma: Double, val valores: FloatArray)

    private val indices = intArrayOf(0, 1, 2, 3, 671, 1000, 33333, 75000, 100001, 150527, 12345, 98765, 140000, 2244, 50000)

    private val casos = listOf(
        Caso(320, 240, 19213114.28, floatArrayOf(1.9643f, 50.8214f, 0.2500f, 11.9643f, 102.2527f, 11.3214f, 242.4643f, 92.0356f, 166.5357f, 140.0329f, 58.6786f, 23.8224f, 198.1070f, 35.7500f, 80.1071f)),
        Caso(240, 320, 19110477.89, floatArrayOf(3.0357f, 51.1786f, 0.2500f, 10.5357f, 34.4645f, 152.6786f, 133.5357f, 26.1782f, 210.9634f, 97.0360f, 191.8214f, 128.7482f, 107.1788f, 116.7500f, 76.6790f)),
        Caso(200, 150, 19025228.76, floatArrayOf(0.0000f, 50.0000f, 0.0000f, 5.8750f, 177.0000f, 88.3259f, 243.7902f, 21.7723f, 201.0314f, 70.0000f, 160.9241f, 139.5494f, 140.9247f, 242.5938f, 98.2637f)),
        Caso(320, 213, 19173625.07, floatArrayOf(1.5000f, 50.6429f, 0.2143f, 11.5000f, 102.2170f, 10.4174f, 164.8996f, 173.3192f, 148.6361f, 113.0686f, 29.6898f, 207.3045f, 172.9754f, 30.2656f, 71.1272f)),
    )

    private fun imagenSintetica(w: Int, h: Int) = IntArray(w * h) { i ->
        val x = i % w
        val y = i / w
        val r = (x * 7 + y * 13) % 256
        val g = (x * 3 + y * 5 + 50) % 256
        val b = (x * x + y) % 256
        (0xFF shl 24) or (r shl 16) or (g shl 8) or b
    }

    @Test
    fun coincideConTensorFlow() {
        for (c in casos) {
            val out = ImagePreprocessor.resizeBilinearRgb(imagenSintetica(c.w, c.h), c.w, c.h, 224)
            indices.forEachIndexed { k, i -> assertEquals("caso ${c.w}x${c.h}, índice $i", c.valores[k], out[i], 1e-3f) }
            assertEquals("suma ${c.w}x${c.h}", c.suma, out.sumOf { it.toDouble() }, c.suma * 1e-6)
        }
    }

    @Test
    fun decisionConUmbral() {
        assertEquals(Veredicto.DEFECTUOSA, Decision.decidir(0.9f, 0.1f))
        assertEquals(Veredicto.BUEN_ESTADO, Decision.decidir(0.2f, 0.8f))
        assertEquals(Veredicto.NO_CONCLUYENTE, Decision.decidir(0.6f, 0.4f))
    }
}
