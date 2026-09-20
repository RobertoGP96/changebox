package com.lolo.changebox.data

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

