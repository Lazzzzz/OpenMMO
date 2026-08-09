package de.fiereu.openmmo.server.game.internal

import de.fiereu.openmmo.common.presence.Presence
import de.fiereu.openmmo.common.presence.PresenceLookup
import de.fiereu.openmmo.server.game.config.InternalApiConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse

private fun get(port: Int, query: String, secret: String?): HttpResponse<String> {
  val builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:$port$PRESENCE_PATH?$query"))
  secret?.let { builder.header(SECRET_HEADER, it) }
  return HttpClient.newHttpClient()
      .send(builder.GET().build(), HttpResponse.BodyHandlers.ofString())
}

class InternalApiServerTest :
    FunSpec({
      val secret = "test-secret"

      fun serving(answer: Presence, block: (Int) -> Unit) {
        val config = InternalApiConfig(host = "127.0.0.1", port = 0, secret = secret)
        val server = InternalApiServer(config, PresenceLookup { answer })
        server.start()
        try {
          block(server.port)
        } finally {
          server.stop()
        }
      }

      test("answers a presence query for an online account") {
        val online = Presence(online = true, gameServerId = 3, sessionId = 99L, characterId = 7L)
        serving(online) { port ->
          val response = get(port, "userId=1", secret)

          response.statusCode() shouldBe 200
          Presence.decode(response.body()) shouldBe online
        }
      }

      test("answers offline when nobody holds the account") {
        serving(Presence.OFFLINE) { port ->
          Presence.decode(get(port, "userId=1", secret).body()).online shouldBe false
        }
      }

      test("refuses a request without the shared secret") {
        serving(Presence.OFFLINE) { port ->
          get(port, "userId=1", null).statusCode() shouldBe 403
          get(port, "userId=1", "wrong").statusCode() shouldBe 403
        }
      }

      test("rejects a query that names no user") {
        serving(Presence.OFFLINE) { port ->
          get(port, "somethingElse=1", secret).statusCode() shouldBe 400
          get(port, "userId=notanumber", secret).statusCode() shouldBe 400
        }
      }

      test("stays quiet when disabled") {
        val config = InternalApiConfig(enabled = false, port = 0, secret = secret)
        val server = InternalApiServer(config, PresenceLookup { Presence.OFFLINE })
        server.start()
        try {
          runCatching { get(config.port, "userId=1", secret) }.isFailure shouldBe true
        } finally {
          server.stop()
        }
      }
    })
