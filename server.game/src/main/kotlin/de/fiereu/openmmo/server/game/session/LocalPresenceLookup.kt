package de.fiereu.openmmo.server.game.session

import de.fiereu.openmmo.common.presence.Presence
import de.fiereu.openmmo.common.presence.PresenceLookup
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton

/** Answers from the live registry, so the result is the truth rather than a cached view of it. */
@Singleton
class LocalPresenceLookup
@Inject
constructor(
    private val sessionRegistry: SessionRegistry,
    @param:Named("gameServerId") private val gameServerId: Int,
) : PresenceLookup {

  override suspend fun find(userId: Int): Presence {
    val state =
        sessionRegistry.getByUserId(userId)?.attributes?.get(PLAYER_STATE)
            ?: return Presence.OFFLINE
    return Presence(
        online = true,
        gameServerId = gameServerId,
        sessionId = state.sessionId,
        characterId = state.characterId,
    )
  }
}
