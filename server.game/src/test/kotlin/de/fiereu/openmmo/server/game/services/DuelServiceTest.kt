package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.openmmo.common.Pokemon
import de.fiereu.openmmo.common.PokemonMove
import de.fiereu.openmmo.common.enums.BattleAction
import de.fiereu.openmmo.common.enums.CharacterGender
import de.fiereu.openmmo.common.enums.EVs
import de.fiereu.openmmo.common.enums.IVs
import de.fiereu.openmmo.common.enums.PokemonContainer
import de.fiereu.openmmo.common.enums.Region
import de.fiereu.openmmo.moves.MoveRegistry
import de.fiereu.openmmo.net.game.packets.DuelChallengePacket
import de.fiereu.openmmo.net.game.packets.battle.BattleActionSelectPacket
import de.fiereu.openmmo.net.game.packets.battle.BattleBulkStatePacket
import de.fiereu.openmmo.net.game.packets.battle.BattleEntityMoveEventPacket
import de.fiereu.openmmo.net.game.packets.battle.BattleFieldStatePacket
import de.fiereu.openmmo.net.game.packets.battle.OpposingSide
import de.fiereu.openmmo.pokemon.SpeciesRegistry
import de.fiereu.openmmo.server.game.battle.BattlePacketEmitter
import de.fiereu.openmmo.server.game.battle.BattleRegistry
import de.fiereu.openmmo.server.game.battle.TurnEngine
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import de.fiereu.openmmo.server.game.storage.EntityIdService
import de.fiereu.openmmo.server.game.testsupport.FakeCharacterRepository
import de.fiereu.openmmo.server.game.testsupport.FakeSession
import de.fiereu.openmmo.server.game.world.interest.InterestManager
import de.fiereu.openmmo.typechart.TypeChart
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.collections.shouldNotBeEmpty
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

private const val TACKLE: Short = 33

private fun partyMon(ownerId: Long, id: Long): Pokemon =
    Pokemon(
        id = id,
        ownerId = ownerId,
        container = PokemonContainer.PARTY,
        containerSlot = 0,
        dexId = 1,
        seed = 0,
        ot = "",
        nickname = "",
        level = 50,
        hp = 999,
        xp = 0,
        eVs = EVs(),
        iVs = IVs(),
        moves =
            listOf(
                PokemonMove(TACKLE, 35),
                PokemonMove(0, 0),
                PokemonMove(0, 0),
                PokemonMove(0, 0),
            ),
        isShiny = false,
        hasHiddenAbility = false,
        isAlpha = false,
        isSecret = false,
        isFatefulEncounter = false,
        isRaidEncounter = false,
        caughtAt = LocalDateTime.now(),
    )

private class DuelFixture(scope: CoroutineScope) {
  val ids = EntityIdService()
  val characters = CharacterStore(FakeCharacterRepository(), ids, scope)
  val sessions = SessionRegistry()
  val battles = BattleRegistry()
  val interests = InterestManager()
  val service =
      DuelService(
          characters,
          sessions,
          battles,
          TurnEngine(MoveRegistry(), TypeChart()),
          BattlePacketEmitter(interests),
          interests,
          SpeciesRegistry(),
      )

  suspend fun player(userId: Int, name: String): FakeSession {
    val stored = characters.createCharacter(userId, name, CharacterGender.MALE, Region.HOENN)
    characters.addPokemon(stored.info.id, partyMon(stored.info.id, ids.newMonsterId()))
    val session = FakeSession(stored.info.id)
    sessions.register(session)
    sessions.bindCharacter(session, stored.info.id)
    return session
  }
}

private fun challenge(name: String) =
    DuelChallengePacket(name, 0, false, 0, 0, 0, 0, null, null, null, null)

private fun move() = BattleActionSelectPacket(0, BattleAction.MOVE, TACKLE, 0, 0)

class DuelServiceTest :
    FunSpec({
      test("a reciprocal challenge accepts and starts both player perspectives") {
        runTest {
          val fx = DuelFixture(this)
          val ash = fx.player(1, "Ash")
          val misty = fx.player(2, "Misty")

          fx.service.onChallenge(PacketEvent(challenge("Misty"), ash))
          ash.sent.filterIsInstance<BattleFieldStatePacket>() shouldBe emptyList()
          misty.sent.filterIsInstance<BattleFieldStatePacket>() shouldBe emptyList()
          fx.service.onChallenge(PacketEvent(challenge("Ash"), misty))

          ash.sent.filterIsInstance<BattleFieldStatePacket>().single().opposing shouldBe
              OpposingSide.WILD
          misty.sent.filterIsInstance<BattleFieldStatePacket>().single().opposing shouldBe
              OpposingSide.WILD
          fx.service.inDuel(ash.state().characterId!!) shouldBe true
          fx.service.inDuel(misty.state().characterId!!) shouldBe true
        }
      }

      test("the turn waits for both choices then broadcasts the same move events") {
        runTest {
          val fx = DuelFixture(this)
          val ash = fx.player(1, "Ash")
          val misty = fx.player(2, "Misty")
          fx.service.onChallenge(PacketEvent(challenge("Misty"), ash))
          fx.service.onChallenge(PacketEvent(challenge("Ash"), misty))
          ash.sent.clear()
          misty.sent.clear()

          fx.service.onBattleAction(PacketEvent(move(), ash)) shouldBe true
          ash.sent.filterIsInstance<BattleEntityMoveEventPacket>() shouldBe emptyList()
          fx.service.onBattleAction(PacketEvent(move(), misty)) shouldBe true

          ash.sent.filterIsInstance<BattleEntityMoveEventPacket>().size shouldBe 2
          misty.sent.filterIsInstance<BattleEntityMoveEventPacket>().size shouldBe 2
        }
      }

      test("the native Run action forfeits and ends the duel for both players") {
        runTest {
          val fx = DuelFixture(this)
          val ash = fx.player(1, "Ash")
          val misty = fx.player(2, "Misty")
          fx.service.onChallenge(PacketEvent(challenge("Misty"), ash))
          fx.service.onChallenge(PacketEvent(challenge("Ash"), misty))
          ash.sent.clear()
          misty.sent.clear()

          val run = BattleActionSelectPacket(0, BattleAction.RUN, 0, 0, 0)
          fx.service.onBattleAction(PacketEvent(run, ash)) shouldBe true

          ash.sent.filterIsInstance<BattleBulkStatePacket>().shouldNotBeEmpty()
          misty.sent.filterIsInstance<BattleBulkStatePacket>().shouldNotBeEmpty()
        }
      }
    })
