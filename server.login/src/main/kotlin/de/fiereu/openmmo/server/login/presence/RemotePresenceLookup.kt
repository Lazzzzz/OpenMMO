package de.fiereu.openmmo.server.login.presence

import de.fiereu.openmmo.common.presence.Presence
import de.fiereu.openmmo.common.presence.PresenceLookup
import de.fiereu.openmmo.server.login.config.PresenceConfig
import io.github.oshai.kotlinlogging.KotlinLogging
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

private val log = KotlinLogging.logger {}

private const val SECRET_HEADER = "X-Openmmo-Internal"

/**
 * Asks the game server whether an account is playing. A game server that cannot be reached is
 * reported as offline, because the prompt this feeds is a convenience and must never keep somebody
 * from logging in.
 */
@Singleton
class RemotePresenceLookup
@Inject
constructor(
    private val config: PresenceConfig,
    @param:Named("db") private val dispatcher: CoroutineDispatcher,
) : PresenceLookup {

  private val client =
      HttpClient.newBuilder()
          .connectTimeout(config.timeout)
          .followRedirects(HttpClient.Redirect.NEVER)
          .build()

  override suspend fun find(userId: Int): Presence {
    if (!config.enabled) return Presence.OFFLINE
    val request =
        HttpRequest.newBuilder(URI.create("${config.baseUrl}/internal/presence?userId=$userId"))
            .header(SECRET_HEADER, config.secret)
            .timeout(config.timeout)
            .GET()
            .build()
    return withContext(dispatcher) {
      runCatching { client.send(request, HttpResponse.BodyHandlers.ofString()) }
          .mapCatching { response ->
            if (response.statusCode() != 200) {
              log.warn { "Presence lookup for $userId answered ${response.statusCode()}" }
              Presence.OFFLINE
            } else {
              Presence.decode(response.body())
            }
          }
          .getOrElse {
            log.warn(it) { "Presence lookup for $userId failed, treating the account as offline" }
            Presence.OFFLINE
          }
    }
  }
}
