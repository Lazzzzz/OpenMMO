package de.fiereu.openmmo.server.game.session

import de.fiereu.network.SessionContext
import io.netty.channel.Channel
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SessionRegistry @Inject constructor() {
  private val sessionsByChannel = ConcurrentHashMap<Channel, SessionContext>()
  private val sessionsByCharacter = ConcurrentHashMap<Long, SessionContext>()
  private val sessionsByUser = ConcurrentHashMap<Int, SessionContext>()

  fun register(ctx: SessionContext) {
    sessionsByChannel[ctx.channel] = ctx
    ctx.attributes[PLAYER_STATE]?.let { sessionsByUser[it.userId] = ctx }
  }

  // Removals are identity checked, so a session that has already been replaced does not take its
  // successor's entries with it when it finally disconnects.
  fun unregister(ctx: SessionContext) {
    sessionsByChannel.remove(ctx.channel, ctx)
    val state = ctx.attributes[PLAYER_STATE] ?: return
    state.characterId?.let { sessionsByCharacter.remove(it, ctx) }
    sessionsByUser.remove(state.userId, ctx)
  }

  fun getByUserId(userId: Int): SessionContext? = sessionsByUser[userId]

  fun bindCharacter(ctx: SessionContext, characterId: Long) {
    sessionsByCharacter[characterId] = ctx
  }

  fun unbindCharacter(characterId: Long) {
    sessionsByCharacter.remove(characterId)
  }

  fun getByCharacterId(id: Long): SessionContext? = sessionsByCharacter[id]

  fun onlineCharacterIds(): Set<Long> = sessionsByCharacter.keys
}
