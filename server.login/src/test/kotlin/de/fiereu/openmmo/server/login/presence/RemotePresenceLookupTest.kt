package de.fiereu.openmmo.server.login.presence

import com.sun.net.httpserver.HttpServer
import de.fiereu.openmmo.common.presence.Presence
import de.fiereu.openmmo.server.login.config.PresenceConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.InetSocketAddress
import java.nio.charset.StandardCharsets
import java.time.Duration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

private fun stub(status: Int, body: String, capture: MutableList<String>): HttpServer {
  val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
  server.createContext("/internal/presence") { exchange ->
    capture += (exchange.requestHeaders.getFirst("X-Openmmo-Internal") ?: "")
    val bytes = body.toByteArray(StandardCharsets.UTF_8)
    exchange.sendResponseHeaders(status, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }
  server.start()
  return server
}

class RemotePresenceLookupTest :
    FunSpec({
      fun configFor(port: Int, enabled: Boolean = true) =
          PresenceConfig(
              enabled = enabled,
              baseUrl = "http://127.0.0.1:$port",
              secret = "test-secret",
              timeout = Duration.ofSeconds(2),
          )

      test("reads the answer the game server gives") {
        val online = Presence(online = true, gameServerId = 2, sessionId = 42L, characterId = 5L)
        val seen = mutableListOf<String>()
        val server = stub(200, Presence.encode(online), seen)
        try {
          val lookup = RemotePresenceLookup(configFor(server.address.port), Dispatchers.IO)

          runBlocking { lookup.find(1) } shouldBe online
          seen.single() shouldBe "test-secret"
        } finally {
          server.stop(0)
        }
      }

      test("treats an unreachable game server as offline rather than failing the login") {
        // Port 1 is not listening, so the request cannot connect.
        val lookup = RemotePresenceLookup(configFor(1), Dispatchers.IO)

        runBlocking { lookup.find(1) } shouldBe Presence.OFFLINE
      }

      test("treats a refused or broken answer as offline") {
        val server = stub(403, "", mutableListOf())
        try {
          val lookup = RemotePresenceLookup(configFor(server.address.port), Dispatchers.IO)

          runBlocking { lookup.find(1) } shouldBe Presence.OFFLINE
        } finally {
          server.stop(0)
        }
      }

      test("asks nothing when presence is turned off") {
        val seen = mutableListOf<String>()
        val server = stub(200, Presence.encode(Presence(online = true)), seen)
        try {
          val lookup =
              RemotePresenceLookup(configFor(server.address.port, enabled = false), Dispatchers.IO)

          runBlocking { lookup.find(1) } shouldBe Presence.OFFLINE
          seen.isEmpty() shouldBe true
        } finally {
          server.stop(0)
        }
      }
    })
