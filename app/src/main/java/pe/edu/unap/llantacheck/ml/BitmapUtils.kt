package pe.edu.unap.llantacheck.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.camera.core.ImageProxy
import androidx.exifinterface.media.ExifInterface
import java.io.File
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

object BitmapUtils {

    /** Lado mayor de las imágenes con que se entrenó el modelo (copia redimensionada del dataset). */
    const val TRAINING_MAX_SIDE = 320

    /**
     * Lleva la imagen a la misma escala que el dataset de entrenamiento (lado mayor 320 px),
     * reduciendo a la mitad varias veces para evitar aliasing, como hace PIL `thumbnail`.
     */
    fun toTrainingScale(src: Bitmap): Bitmap {
        var b = if (src.config == Bitmap.Config.ARGB_8888) src else src.copy(Bitmap.Config.ARGB_8888, false)
        if (max(b.width, b.height) <= TRAINING_MAX_SIDE) return b
        while (max(b.width, b.height) >= 2 * TRAINING_MAX_SIDE) {
            b = Bitmap.createScaledBitmap(b, b.width / 2, b.height / 2, true)
        }
        val s = TRAINING_MAX_SIDE.toFloat() / max(b.width, b.height)
        return Bitmap.createScaledBitmap(
            b,
            (b.width * s).roundToInt().coerceAtLeast(1),
            (b.height * s).roundToInt().coerceAtLeast(1),
            true,
        )
    }

    fun fromImageProxy(image: ImageProxy): Bitmap = rotate(image.toBitmap(), image.imageInfo.rotationDegrees)

    fun rotate(b: Bitmap, degrees: Int): Bitmap {
        if (degrees % 360 == 0) return b
        val m = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }

    /**
     * Recorta de la imagen de la cámara la zona que el usuario ve dentro del recuadro guía.
     * La vista previa usa FILL_CENTER: la imagen se escala para cubrir la vista y se centra,
     * así que el recuadro (centrado, de lado guideFraction × ancho de la vista) corresponde
     * a un cuadrado centrado en la imagen.
     */
    fun cropToGuide(b: Bitmap, viewW: Int, viewH: Int, guideFraction: Float): Bitmap {
        val side = if (viewW > 0 && viewH > 0) {
            val scale = max(viewW.toFloat() / b.width, viewH.toFloat() / b.height)
            (guideFraction * viewW / scale).roundToInt()
        } else {
            (guideFraction * min(b.width, b.height)).roundToInt()
        }.coerceIn(1, min(b.width, b.height))
        return Bitmap.createBitmap(b, (b.width - side) / 2, (b.height - side) / 2, side, side)
    }

    /** Decodifica una imagen de la galería respetando su orientación EXIF y sin cargarla a resolución completa. */
    fun decodeUri(context: Context, uri: Uri, minSide: Int = 2 * TRAINING_MAX_SIDE): Bitmap? {
        val resolver = context.contentResolver
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= minSide) sample *= 2
        val opts = BitmapFactory.Options().apply {
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
        }
        val bitmap = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) } ?: return null
        val orientation = runCatching {
            resolver.openInputStream(uri)?.use {
                ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
            }
        }.getOrNull() ?: ExifInterface.ORIENTATION_NORMAL
        return applyExif(bitmap, orientation)
    }

    fun decodeAsset(context: Context, path: String): Bitmap? =
        context.assets.open(path).use { BitmapFactory.decodeStream(it) }

    fun saveJpeg(b: Bitmap, file: File, maxSide: Int = 480, quality: Int = 88) {
        val s = maxSide.toFloat() / max(b.width, b.height)
        val out = if (s < 1f) {
            Bitmap.createScaledBitmap(b, (b.width * s).roundToInt(), (b.height * s).roundToInt(), true)
        } else b
        file.outputStream().use { out.compress(Bitmap.CompressFormat.JPEG, quality, it) }
    }

    private fun applyExif(b: Bitmap, orientation: Int): Bitmap {
        val m = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> m.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> { m.postRotate(90f); m.postScale(-1f, 1f) }
            ExifInterface.ORIENTATION_TRANSVERSE -> { m.postRotate(270f); m.postScale(-1f, 1f) }
            else -> return b
        }
        return Bitmap.createBitmap(b, 0, 0, b.width, b.height, m, true)
    }
}
