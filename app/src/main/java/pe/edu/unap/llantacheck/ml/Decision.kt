package pe.edu.unap.llantacheck.ml

enum class Veredicto { DEFECTUOSA, BUEN_ESTADO, NO_CONCLUYENTE }

/**
 * Regla de decisión de la app. El modelo solo conoce dos clases, así que ante una imagen
 * que no es una llanta (o una foto borrosa) igual reparte la probabilidad entre ambas.
 * Si ninguna clase alcanza el umbral de confianza, la app no adivina: responde
 * "no concluyente".
 */
object Decision {
    const val UMBRAL_CONFIANZA = 0.75f

    fun decidir(pDefectuosa: Float, pBuena: Float): Veredicto = when {
        pDefectuosa >= UMBRAL_CONFIANZA -> Veredicto.DEFECTUOSA
        pBuena >= UMBRAL_CONFIANZA -> Veredicto.BUEN_ESTADO
        else -> Veredicto.NO_CONCLUYENTE
    }
}

data class Resultado(
    val pDefectuosa: Float,
    val pBuena: Float,
    val veredicto: Veredicto,
    val tiempoMs: Long,
    /** Luminancia media (0–255) de la imagen que entró al modelo. */
    val brillo: Float,
)

enum class AvisoCalidad { OSCURA, SOBREEXPUESTA }

/** Fotos muy oscuras o quemadas suelen dar resultados poco fiables; la app lo advierte. */
fun avisoCalidad(brillo: Float): AvisoCalidad? = when {
    brillo < 0f -> null
    brillo < 45f -> AvisoCalidad.OSCURA
    brillo > 220f -> AvisoCalidad.SOBREEXPUESTA
    else -> null
}
