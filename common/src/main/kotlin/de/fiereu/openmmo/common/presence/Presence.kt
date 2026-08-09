package de.fiereu.openmmo.common.presence

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * What a game server reports about an account. The login server cannot know this on its own: its
 * connection to the client is gone by the time a second login arrives, so the game server holding
 * the session is the only authority.
 */
@Serializable
data class Presence(
    val online: Boolean,
    val gameServerId: Int = 0,
    val sessionId: Long = 0,
    val characterId: Long? = null,
) {
  companion object {
    val OFFLINE = Presence(online = false)

    private val json = Json { ignoreUnknownKeys = true }

    fun encode(presence: Presence): String = json.encodeToString(serializer(), presence)

    fun decode(body: String): Presence = json.decodeFromString(serializer(), body)
  }
}

/** Looks up an account's presence. Implementations may be local, remote, or a stub. */
fun interface PresenceLookup {
  suspend fun find(userId: Int): Presence
}
