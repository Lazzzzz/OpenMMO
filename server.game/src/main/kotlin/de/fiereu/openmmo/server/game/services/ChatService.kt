package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.network.SessionContext
import de.fiereu.openmmo.common.enums.ChatType
import de.fiereu.openmmo.common.enums.Language
import de.fiereu.openmmo.net.game.packets.ChatMessagePacket
import de.fiereu.openmmo.net.game.packets.ChatMessageSendPacket
import de.fiereu.openmmo.server.game.services.command.ChatCommandService
import de.fiereu.openmmo.server.game.session.PLAYER_STATE
import de.fiereu.openmmo.server.game.storage.CharacterStore
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import javax.inject.Singleton

private val log = KotlinLogging.logger {}

/** Turns the client's chat input packet into the message packet displayed by every player. */
@Singleton
class ChatService
@Inject
constructor(
    private val characterStore: CharacterStore,
    private val multiplayerService: MultiplayerService,
    private val chatCommandService: ChatCommandService,
) {

  suspend fun onMessageSend(event: PacketEvent<ChatMessageSendPacket>) {
    val packet = event.packet
    val text = packet.message ?: packet.target
    if (text.isBlank() || chatCommandService.tryHandle(event.session, text)) return

    val type = ChatType.entries.getOrNull(packet.mode.toInt() and 0xff) ?: ChatType.NORMAL
    // In whisper mode `target` is the recipient and `message` is the private text. Broadcasting
    // that packet would leak a private message, so keep it disabled until recipient lookup exists.
    if (type == ChatType.WHISPER) {
      event.session.send(notice("Private messages are not available yet."))
      return
    }

    broadcast(event.session, type, Language.EN, text)
  }

  suspend fun onMessage(event: PacketEvent<ChatMessagePacket>) {
    val message = event.packet
    broadcast(event.session, message.type, message.language, message.message)
  }

  private fun broadcast(
      session: SessionContext,
      type: ChatType,
      language: Language?,
      message: String,
  ) {
    val state = session.attributes[PLAYER_STATE]
    if (state == null) {
      log.warn { "Chat message from session without PlayerState" }
      return
    }
    val charId = state.characterId ?: return
    val sender = characterStore.getCharacter(charId)?.info?.name ?: "Unknown"
    log.info { "Chat [$type] $sender: $message" }

    multiplayerService.broadcastMessage(
        if (type == ChatType.TEAM) {
          ChatMessagePacket(type = type, language = null, message = message, sender = null)
        } else {
          ChatMessagePacket(type = type, language = language ?: Language.EN, message, sender)
        })
  }
}
