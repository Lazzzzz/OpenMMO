package de.fiereu.openmmo.server.game

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import de.fiereu.openmmo.server.game.config.GameServerConfig
import de.fiereu.openmmo.server.game.services.MultiplayerService
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.concurrent.Executors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

private val adminLog = KotlinLogging.logger {}

/** Small private API. It is never published outside the Docker network. */
@Singleton
class AdminControlServer
@Inject
constructor(
    private val config: GameServerConfig,
    private val sessions: SessionRegistry,
    private val characters: CharacterStore,
    private val multiplayer: MultiplayerService,
) {
  private var server: HttpServer? = null

  fun start() {
    val http = HttpServer.create(InetSocketAddress(config.adminHost, config.adminPort), 0)
    http.executor = Executors.newVirtualThreadPerTaskExecutor()
    http.createContext("/health") { exchange -> handle(exchange) { respond(exchange, 200, "ok") } }
    http.createContext("/players") { exchange ->
      handle(exchange) {
        when (exchange.requestMethod) {
          "GET" -> onlinePlayers(exchange)
          "DELETE" -> disconnect(exchange)
          else -> respond(exchange, 405, "method not allowed")
        }
      }
    }
    http.createContext("/announce") { exchange ->
      handle(exchange) {
        if (exchange.requestMethod != "POST") {
          respond(exchange, 405, "method not allowed")
        } else {
          announce(exchange)
        }
      }
    }
    http.createContext("/reset-character") { exchange ->
      handle(exchange) {
        if (exchange.requestMethod != "POST") {
          respond(exchange, 405, "method not allowed")
        } else {
          resetCharacter(exchange)
        }
      }
    }
    http.start()
    server = http
    adminLog.info { "Private admin control listening on ${config.adminHost}:${config.adminPort}" }
  }

  fun stop() {
    server?.stop(1)
  }

  private fun handle(exchange: HttpExchange, block: () -> Unit) {
    try {
      val supplied = exchange.requestHeaders.getFirst("Authorization")?.removePrefix("Bearer ")
      val valid =
          supplied != null &&
              MessageDigest.isEqual(
                  supplied.toByteArray(StandardCharsets.UTF_8),
                  config.adminToken.toByteArray(StandardCharsets.UTF_8),
              )
      if (!valid) {
        respond(exchange, 401, "unauthorized")
        return
      }
      block()
    } catch (error: Exception) {
      adminLog.warn(error) { "Admin control request failed" }
      respond(exchange, 500, "internal error")
    } finally {
      exchange.close()
    }
  }

  private fun onlinePlayers(exchange: HttpExchange) {
    val payload = buildJsonObject {
      put(
          "players",
          buildJsonArray {
            sessions.onlineCharacterIds().sorted().forEach { id ->
              characters.getCharacter(id)?.info?.let { info ->
                add(
                    buildJsonObject {
                      put("id", id)
                      put("name", info.name)
                    })
              }
            }
          })
    }
    respond(exchange, 200, payload.toString(), "application/json")
  }

  private fun disconnect(exchange: HttpExchange) {
    val id = query(exchange, "id")?.toLongOrNull()
    if (id == null) {
      respond(exchange, 422, "invalid character id")
      return
    }
    respond(exchange, if (sessions.disconnectCharacter(id)) 204 else 404, "")
  }

  private fun announce(exchange: HttpExchange) {
    val message = exchange.requestBody.bufferedReader().readText().trim()
    if (message.isEmpty() || message.length > 300) {
      respond(exchange, 422, "message must contain 1 to 300 characters")
      return
    }
    multiplayer.broadcastNotice("[Server] $message")
    respond(exchange, 204, "")
  }

  private fun resetCharacter(exchange: HttpExchange) {
    val id = query(exchange, "id")?.toLongOrNull()
    if (id == null) {
      respond(exchange, 422, "invalid character id")
      return
    }
    if (id in sessions.onlineCharacterIds()) {
      respond(exchange, 409, "character is online")
      return
    }
    val reset = runBlocking { characters.resetToNewGame(id) }
    respond(exchange, if (reset) 204 else 404, "")
  }

  private fun query(exchange: HttpExchange, name: String): String? =
      exchange.requestURI.rawQuery
          ?.split('&')
          ?.mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 } }
          ?.firstOrNull { it[0] == name }
          ?.get(1)

  private fun respond(
      exchange: HttpExchange,
      status: Int,
      body: String,
      contentType: String = "text/plain; charset=utf-8",
  ) {
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.responseHeaders.set("Content-Type", contentType)
    exchange.sendResponseHeaders(status, if (status == 204) -1 else bytes.size.toLong())
    if (status != 204) exchange.responseBody.write(bytes)
  }
}
