package com.sarahpena.rutasmart.domain.casosdeuso

/** Validación de los datos de la cuenta antes de enviarlos a Firebase. Devuelve el error o null. */
object ValidadorCuenta {
    private val CORREO = Regex("^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
    const val MIN_CLAVE = 6

    fun correo(valor: String?): String? = when {
        valor.isNullOrBlank() -> "Escribe tu correo."
        !CORREO.matches(valor.trim()) -> "El correo no tiene un formato válido."
        else -> null
    }

    fun clave(valor: String?): String? = when {
        valor.isNullOrEmpty() -> "Escribe tu contraseña."
        valor.length < MIN_CLAVE -> "La contraseña debe tener al menos $MIN_CLAVE caracteres."
        else -> null
    }

    fun registro(correo: String?, clave: String?, confirmacion: String?): String? =
        correo(correo) ?: clave(clave) ?: if (clave != confirmacion) "Las contraseñas no coinciden." else null
}
