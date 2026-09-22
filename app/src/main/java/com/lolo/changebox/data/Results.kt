package com.lolo.changebox.data

import android.util.Log
import kotlin.coroutines.cancellation.CancellationException

// Contrato de resultados de los servicios, espejo del ActionResult de la web:
// los fallos de NEGOCIO devuelven Failure con mensaje amigable en español;
// los errores inesperados de infraestructura se propagan como excepción.

sealed interface ActionResult<out T> {
    data class Success<T>(val data: T) : ActionResult<T>
    data class Failure(val error: String) : ActionResult<Nothing>
}

fun <T> ok(data: T): ActionResult<T> = ActionResult.Success(data)
fun fail(error: String): ActionResult.Failure = ActionResult.Failure(error)

/**
 * Error de negocio lanzado DENTRO de una transacción de Room para abortarla y
 * devolver el mensaje al UI (las validaciones de saldo/pendiente se releen ahí
 * dentro — patrón anti doble-envío heredado de la web).
 */
class ActionError(message: String) : Exception(message)

/**
 * Red de seguridad de una operación de escritura, equivalente al `try/catch`
 * que envuelve cada server action de la web: un fallo inesperado (una
 * `SQLiteException` de clave foránea, por ejemplo) se convierte en un
 * `Failure` con mensaje amigable en vez de tumbar la app.
 *
 * `ActionError` NO es inesperado: es el mensaje de negocio que lanzan las
 * validaciones releídas dentro de la transacción, así que pasa tal cual.
 *
 * `fallback` es el texto genérico de la web para esa operación, p. ej.
 * "No se pudo crear la cuenta".
 */
suspend fun <T> guarded(
    fallback: String,
    block: suspend () -> ActionResult<T>,
): ActionResult<T> = try {
    block()
} catch (e: ActionError) {
    fail(e.message ?: fallback)
} catch (e: CancellationException) {
    // Cancelar una corrutina no es un fallo: debe propagarse.
    throw e
} catch (e: Exception) {
    Log.e("Changebox", fallback, e)
    fail(fallback)
}

