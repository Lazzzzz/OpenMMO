package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.openmmo.common.Pokemon
import de.fiereu.openmmo.common.PokemonMove
import de.fiereu.openmmo.common.enums.CharacterGender
import de.fiereu.openmmo.common.enums.EVs
import de.fiereu.openmmo.common.enums.IVs
import de.fiereu.openmmo.common.enums.PokemonContainer
import de.fiereu.openmmo.common.enums.Region
import de.fiereu.openmmo.net.game.packets.ClientOpcode09Packet
import de.fiereu.openmmo.net.game.packets.DuelInviteOutcomePacket
import de.fiereu.openmmo.net.game.packets.DuelInvitePacket
import de.fiereu.openmmo.net.game.packets.ExchangeItemRequestPacket
import de.fiereu.openmmo.net.game.packets.StringCommandPacket
import de.fiereu.openmmo.net.game.packets.TradeActionPacket
import de.fiereu.openmmo.net.game.packets.TradeListEntryPacket
import de.fiereu.openmmo.net.game.packets.TradeSelectMonPacket
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import de.fiereu.openmmo.server.game.storage.EntityIdService
import de.fiereu.openmmo.server.game.testsupport.FakeCharacterRepository
import de.fiereu.openmmo.server.game.testsupport.FakeSession
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private fun tradeMon(ownerId: Long, id: Long, dexId: Int) =
    Pokemon(
        id = id,
        ownerId = ownerId,
        container = PokemonContainer.PARTY,
        containerSlot = 0,
        dexId = dexId,
        seed = 0,
        ot = "OT",
        nickname = "",
        level = 5,
        hp = 20,
        xp = 0,
        eVs = EVs(),
        iVs = IVs(),
        moves = listOf(PokemonMove(33, 35)),
        isShiny = false,
        hasHiddenAbility = false,
        isAlpha = false,
        isSecret = false,
        isFatefulEncounter = false,
        isRaidEncounter = false,
        caughtAt = LocalDateTime.now(),
    )

private class TradeFixture(scope: CoroutineScope) {
  val repository = FakeCharacterRepository()
  val ids = EntityIdService()
  val characters = CharacterStore(repository, ids, scope)
  val sessions = SessionRegistry()
  val service = TradeService(characters, sessions, WorldStateService())

  suspend fun player(userId: Int, name: String, dexId: Int): FakeSession {
    val stored = characters.createCharacter(userId, name, CharacterGender.MALE, Region.HOENN)
    characters.addPokemon(stored.info.id, tradeMon(stored.info.id, ids.newMonsterId(), dexId))
    val session = FakeSession(stored.info.id)
    sessions.register(session)
    sessions.bindCharacter(session, stored.info.id)
    return session
  }

  suspend fun open(left: FakeSession, right: FakeSession) {
    left.state().interactionTargetId = right.state().characterId
    right.state().interactionTargetId = left.state().characterId
    service.onAction(PacketEvent(TradeActionPacket(9), left))
    service.onAction(PacketEvent(TradeActionPacket(9), right))
  }
}

class TradeServiceTest :
    FunSpec({
      test("a reciprocal request opens the exchange for both players") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)

          fx.open(ash, misty)

          ash.sent.filterIsInstance<DuelInvitePacket>().single().name shouldBe "Misty"
          misty.sent.filterIsInstance<DuelInvitePacket>().single().name shouldBe "Ash"
          ash.sent.filterIsInstance<DuelInvitePacket>().single().flags shouldBe 0x07.toByte()
          misty.sent.filterIsInstance<DuelInvitePacket>().single().flags shouldBe 0x07.toByte()
          fx.service.inTrade(ash.state().characterId!!) shouldBe true
        }
      }

      test("the generic exchange command starts a reciprocal invitation") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)

          fx.service.onCommand(PacketEvent(StringCommandPacket("trade Misty"), ash))
          fx.service.onCommand(PacketEvent(StringCommandPacket("trade Ash"), misty))

          ash.sent.filterIsInstance<DuelInvitePacket>().single().name shouldBe "Misty"
          misty.sent.filterIsInstance<DuelInvitePacket>().single().name shouldBe "Ash"
          fx.service.inTrade(ash.state().characterId!!) shouldBe true
        }
      }

      test("pokemon and money move only after both players lock and confirm") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          fx.open(ash, misty)
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), misty))
          fx.service.onMoney(PacketEvent(StringCommandPacket("1000"), ash)) shouldBe true

          fx.service.onAction(PacketEvent(TradeActionPacket(1), ash))
          fx.service.onAction(PacketEvent(TradeActionPacket(1), misty))
          fx.service.onAction(PacketEvent(TradeActionPacket(2), ash))
          fx.service.inTrade(ash.state().characterId!!) shouldBe true
          fx.service.onAction(PacketEvent(TradeActionPacket(2), misty))

          val ashAfter = fx.characters.getCharacter(ash.state().characterId!!)!!
          val mistyAfter = fx.characters.getCharacter(misty.state().characterId!!)!!
          ashAfter.pokemon.map { it.dexId } shouldContainExactly listOf(7)
          mistyAfter.pokemon.map { it.dexId } shouldContainExactly listOf(1)
          ashAfter.pokemon.single().ownerId shouldBe ashAfter.info.id
          mistyAfter.pokemon.single().ownerId shouldBe mistyAfter.info.id
          ashAfter.info.money shouldBe 29000
          mistyAfter.info.money shouldBe 31000
          fx.service.inTrade(ashAfter.info.id) shouldBe false
        }
      }

      test("pokemon, items and money are committed together") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          val ashId = ash.state().characterId!!
          val mistyId = misty.state().characterId!!
          fx.characters.addItem(ashId, itemId = 17, amount = 4)
          fx.characters.addItem(mistyId, itemId = 42, amount = 3)
          fx.open(ash, misty)
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), misty))
          fx.service.onSelectItem(PacketEvent(ExchangeItemRequestPacket(17, 2, 0), ash)) shouldBe
              true
          fx.service.onSelectItem(PacketEvent(ExchangeItemRequestPacket(42, 1, 0), misty)) shouldBe
              true
          fx.service.onMoney(PacketEvent(StringCommandPacket("1250"), ash)) shouldBe true

          lockAndConfirm(fx, ash, misty)

          val ashAfter = fx.characters.getCharacter(ashId)!!
          val mistyAfter = fx.characters.getCharacter(mistyId)!!
          ashAfter.pokemon.single().dexId shouldBe 7
          mistyAfter.pokemon.single().dexId shouldBe 1
          ashAfter.items[17] shouldBe 2
          ashAfter.items[42] shouldBe 1
          mistyAfter.items[17] shouldBe 2
          mistyAfter.items[42] shouldBe 2
          ashAfter.info.money shouldBe 28750
          mistyAfter.info.money shouldBe 31250
          fx.repository.saved[ashId] shouldBe ashAfter
          fx.repository.saved[mistyId] shouldBe mistyAfter
        }
      }

      test("a player cannot trade away the last party pokemon for nothing") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          val ashId = ash.state().characterId!!
          val mistyId = misty.state().characterId!!
          fx.open(ash, misty)
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))

          lockAndConfirm(fx, ash, misty)

          fx.service.inTrade(ashId) shouldBe true
          fx.characters.getCharacter(ashId)!!.pokemon.single().dexId shouldBe 1
          fx.characters.getCharacter(mistyId)!!.pokemon.single().dexId shouldBe 7
          ash.sent.filterIsInstance<DuelInviteOutcomePacket>().shouldNotBeEmpty()
        }
      }

      test("a database failure rolls the complete exchange back for both players") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          val ashId = ash.state().characterId!!
          val mistyId = misty.state().characterId!!
          fx.open(ash, misty)
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), misty))
          fx.repository.failNextSave = true

          lockAndConfirm(fx, ash, misty)

          fx.service.inTrade(ashId) shouldBe true
          fx.characters.getCharacter(ashId)!!.pokemon.single().dexId shouldBe 1
          fx.characters.getCharacter(mistyId)!!.pokemon.single().dexId shouldBe 7
        }
      }

      test("cancelling leaves both inventories unchanged") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          fx.open(ash, misty)
          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))

          fx.service.onAction(PacketEvent(TradeActionPacket(0), misty))

          fx.characters.getCharacter(ash.state().characterId!!)!!.pokemon.single().dexId shouldBe 1
          fx.characters.getCharacter(misty.state().characterId!!)!!.pokemon.single().dexId shouldBe
              7
          fx.service.inTrade(ash.state().characterId!!) shouldBe false
        }
      }

      test("the compact party picker is idempotent and only updates the other client") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          fx.open(ash, misty)

          fx.service.onClientOpcode09(
              PacketEvent(ClientOpcode09Packet(byteArrayOf(1, 1, 0, 0, 2, 0, 0)), ash))

          ash.sent.filterIsInstance<TradeListEntryPacket>().size shouldBe 0
          misty.sent.filterIsInstance<TradeListEntryPacket>().single().pokemon.dexId shouldBe 1

          fx.service.onClientOpcode09(
              PacketEvent(ClientOpcode09Packet(byteArrayOf(1, 1, 0, 0, 2, 0, 0)), ash))

          ash.sent.filterIsInstance<TradeListEntryPacket>().size shouldBe 0
          misty.sent.filterIsInstance<TradeListEntryPacket>().size shouldBe 1

          fx.service.onSelectPokemon(PacketEvent(TradeSelectMonPacket(0), ash))
          misty.sent.filterIsInstance<TradeListEntryPacket>().size shouldBe 1
        }
      }

      test("each player selection is rendered only in the other player's remote offer") {
        runTest {
          val fx = TradeFixture(this)
          val ash = fx.player(1, "Ash", 1)
          val misty = fx.player(2, "Misty", 7)
          fx.open(ash, misty)

          fx.service.onClientOpcode09(
              PacketEvent(ClientOpcode09Packet(byteArrayOf(1, 1, 0, 0, 2, 0, 0)), ash))
          fx.service.onClientOpcode09(
              PacketEvent(ClientOpcode09Packet(byteArrayOf(1, 1, 0, 0, 2, 0, 0)), misty))

          ash.sent.filterIsInstance<TradeListEntryPacket>().single().pokemon.dexId shouldBe 7
          misty.sent.filterIsInstance<TradeListEntryPacket>().single().pokemon.dexId shouldBe 1
        }
      }
    })

private suspend fun lockAndConfirm(fx: TradeFixture, left: FakeSession, right: FakeSession) {
  fx.service.onAction(PacketEvent(TradeActionPacket(1), left))
  fx.service.onAction(PacketEvent(TradeActionPacket(1), right))
  fx.service.onAction(PacketEvent(TradeActionPacket(2), left))
  fx.service.onAction(PacketEvent(TradeActionPacket(2), right))
}
