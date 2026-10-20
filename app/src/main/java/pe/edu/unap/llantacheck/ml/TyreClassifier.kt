package pe.edu.unap.llantacheck.ml

import android.content.Context
import android.graphics.Bitmap
import android.os.SystemClock
import org.tensorflow.lite.Interpreter
import java.io.Closeable
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

/**
 * Ejecuta en el teléfono el modelo exportado por el cuaderno de entrenamiento.
 *
 * Contrato (ver model_info.json): entrada float32 [1, 224, 224, 3] RGB 0–255,
 * salida softmax [1, N] en el orden de labels.txt.
 */
class TyreClassifier(context: Context) : Closeable {

    val labels: List<String> = context.assets.open(LABELS_FILE).bufferedReader()
        .readLines().map { it.trim() }.filter { it.isNotEmpty() }

    private val interpreter = Interpreter(
        loadModel(context),
        Interpreter.Options().setNumThreads(4),
    )

    val inputSize: Int = interpreter.getInputTensor(0).shape()[1]
    private val idxDefectuosa = labels.indexOf("defective")
    private val idxBuena = labels.indexOf("good")

    private val pixels = FloatArray(inputSize * inputSize * 3)
    private val input = ByteBuffer.allocateDirect(pixels.size * 4).order(ByteOrder.nativeOrder())
    private val output = Array(1) { FloatArray(labels.size) }

    init {
        val outClasses = interpreter.getOutputTensor(0).shape().last()
        require(outClasses == labels.size) { "El modelo tiene $outClasses salidas pero labels.txt ${labels.size}" }
        require(idxDefectuosa >= 0 && idxBuena >= 0) { "labels.txt debe contener 'defective' y 'good'" }
    }

    @Synchronized
    fun classify(bitmap: Bitmap): Resultado {
        val img = BitmapUtils.toTrainingScale(bitmap)
        val argb = IntArray(img.width * img.height)
        img.getPixels(argb, 0, img.width, 0, 0, img.width, img.height)
        ImagePreprocessor.resizeBilinearRgb(argb, img.width, img.height, inputSize, pixels)

        input.rewind()
        input.asFloatBuffer().put(pixels)
        input.rewind()

        val t0 = SystemClock.elapsedRealtime()
        interpreter.run(input, output)
        val ms = SystemClock.elapsedRealtime() - t0

        val pDef = output[0][idxDefectuosa]
        val pBuena = output[0][idxBuena]
        return Resultado(pDef, pBuena, Decision.decidir(pDef, pBuena), ms, brilloMedio())
    }

    private fun brilloMedio(): Float {
        var suma = 0.0
        for (i in pixels.indices step 3) suma += 0.299 * pixels[i] + 0.587 * pixels[i + 1] + 0.114 * pixels[i + 2]
        return (suma / (pixels.size / 3)).toFloat()
    }

    override fun close() = interpreter.close()

    companion object {
        const val MODEL_FILE = "tyre_classifier_fp16.tflite"
        const val LABELS_FILE = "labels.txt"

        private fun loadModel(context: Context): MappedByteBuffer {
            context.assets.openFd(MODEL_FILE).use { fd ->
                FileInputStream(fd.fileDescriptor).use { stream ->
                    return stream.channel.map(FileChannel.MapMode.READ_ONLY, fd.startOffset, fd.declaredLength)
                }
            }
        }
    }
}
