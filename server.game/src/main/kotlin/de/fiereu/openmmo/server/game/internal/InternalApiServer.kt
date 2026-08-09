package de.fiereu.openmmo.server.game.internal

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.fiereu.openmmo.common.presence.Presence
import de.fiereu.openmmo.common.presence.PresenceLookup
import de.fiereu.openmmo.server.game.config.InternalApiConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking

private val log = KotlinLogging.logger {}

const val PRESENCE_PATH = "/internal/presence"
const val SECRET_HEADER = "X-Openmmo-Internal"

/**
 * Serves questions only other servers may ask. The login server cannot see game sessions, so it
 * asks here rather than keeping a copy that would go stale.
 *
 * Bound to the configured host, which defaults to loopback, and every request has to carry the
 * shared secret. Nothing here is reachable by a game client.
 */
@Singleton
class InternalApiServer
@Inject
constructor(
    private val config: InternalApiConfig,
    private val presence: PresenceLookup,
) {
  private var server: HttpServer? = null

  val port: Int
    get() = server?.address?.port ?: config.port

  fun start() {
    if (!config.enabled) {
      log.info { "Internal api disabled" }
      return
    }
    val http = HttpServer.create(InetSocketAddress(config.host, config.port), 0)
    http.createContext(PRESENCE_PATH) { exchange -> handlePresence(exchange) }
    http.executor = null
    http.start()
    server = http
    log.info { "Internal api listening on ${config.host}:${http.address.port}" }
  }

  fun stop() {
    server?.stop(0)
    server = null
  }

  private fun handlePresence(exchange: HttpExchange) {
    exchange.use {
      if (exchange.requestMethod != "GET") return respond(exchange, 405, "")
      if (exchange.requestHeaders.getFirst(SECRET_HEADER) != config.secret) {
        log.warn { "Internal api request from ${exchange.remoteAddress} without a valid secret" }
        return respond(exchange, 403, "")
      }
      val userId = exchange.requestURI.query?.let(::userIdOf)
      if (userId == null) return respond(exchange, 400, "")
      val found = runBlocking { presence.find(userId) }
      respond(exchange, 200, Presence.encode(found))
    }
  }

  private fun respond(exchange: HttpExchange, status: Int, body: String) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.add("Content-Type", "application/json")
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    if (bytes.isNotEmpty()) exchange.responseBody.write(bytes)
  }
}

private fun userIdOf(query: String): Int? =
    query
        .split('&')
        .asSequence()
        .map { it.split('=', limit = 2) }
        .firstOrNull { it.size == 2 && it[0] == "userId" }
        ?.get(1)
        ?.toIntOrNull()
