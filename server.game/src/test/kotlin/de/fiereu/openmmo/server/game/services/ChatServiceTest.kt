package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.openmmo.common.enums.CharacterGender
import de.fiereu.openmmo.common.enums.ChatType
import de.fiereu.openmmo.common.enums.Language
import de.fiereu.openmmo.common.enums.Region
import de.fiereu.openmmo.net.game.packets.ChatMessagePacket
import de.fiereu.openmmo.net.game.packets.ChatMessageSendPacket
import de.fiereu.openmmo.server.game.services.command.ChatCommandService
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import de.fiereu.openmmo.server.game.storage.EntityIdService
import de.fiereu.openmmo.server.game.testsupport.FakeCharacterRepository
import de.fiereu.openmmo.server.game.testsupport.FakeSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest

@OptIn(ExperimentalCoroutinesApi::class)
class ChatServiceTest :
    FunSpec({
      test("ordinary client chat is broadcast with the character name") {
        runTest {
          val store = CharacterStore(FakeCharacterRepository(), EntityIdService(), backgroundScope)
          val red = store.createCharacter(1, "Red", CharacterGender.MALE, Region.KANTO)
          val blue = store.createCharacter(2, "Blue", CharacterGender.MALE, Region.KANTO)
          val redSession = FakeSession(characterId = red.info.id)
          val blueSession = FakeSession(characterId = blue.info.id)
          val sessions = SessionRegistry()
          sessions.bindCharacter(redSession, red.info.id)
          sessions.bindCharacter(blueSession, blue.info.id)
          val service =
              ChatService(
                  store,
                  MultiplayerService(sessions),
                  ChatCommandService(store, emptySet()),
              )

          service.onMessageSend(
              PacketEvent(
                  ChatMessageSendPacket(mode = 0, target = "bonjour", message = null), redSession))

          val expected =
              ChatMessagePacket(
                  type = ChatType.NORMAL,
                  language = Language.EN,
                  message = "bonjour",
                  sender = "Red",
              )
          redSession.sent.single() shouldBe expected
          blueSession.sent.single() shouldBe expected
        }
      }

      test("a private chat packet is never broadcast to other players") {
        runTest {
          val store = CharacterStore(FakeCharacterRepository(), EntityIdService(), backgroundScope)
          val red = store.createCharacter(1, "Red", CharacterGender.MALE, Region.KANTO)
          val blue = store.createCharacter(2, "Blue", CharacterGender.MALE, Region.KANTO)
          val redSession = FakeSession(characterId = red.info.id)
          val blueSession = FakeSession(characterId = blue.info.id)
          val sessions = SessionRegistry()
          sessions.bindCharacter(redSession, red.info.id)
          sessions.bindCharacter(blueSession, blue.info.id)
          val service =
              ChatService(
                  store,
                  MultiplayerService(sessions),
                  ChatCommandService(store, emptySet()),
              )

          service.onMessageSend(
              PacketEvent(
                  ChatMessageSendPacket(mode = 4, target = "Blue", message = "secret"),
                  redSession,
              ))

          redSession.sent.filterIsInstance<ChatMessagePacket>().single().message shouldBe
              "Private messages are not available yet."
          blueSession.sent shouldBe emptyList()
        }
      }
    })
