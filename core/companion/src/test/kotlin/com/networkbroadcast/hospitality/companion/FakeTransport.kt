package com.networkbroadcast.hospitality.companion

import java.io.IOException

/** Backend de mentira: guarda los pedidos y contesta lo que el test le cargó, en orden. */
class FakeTransport : HttpTransport {
    val requests = mutableListOf<HttpRequest>()
    private val responses = ArrayDeque<Result<HttpResponse>>()

    fun reply(status: Int, body: String = "") { responses.addLast(Result.success(HttpResponse(status, body))) }
    fun offline() { responses.addLast(Result.failure(IOException("sin red"))) }

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        return (responses.removeFirstOrNull() ?: error("pedido sin respuesta cargada: ${request.method} ${request.path}")).getOrThrow()
    }
}
