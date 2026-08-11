package de.fiereu.openmmo.server.game.storage

import de.fiereu.openmmo.common.Pokemon
import de.fiereu.openmmo.common.enums.CharacterGender
import de.fiereu.openmmo.common.enums.EVs
import de.fiereu.openmmo.common.enums.IVs
import de.fiereu.openmmo.common.enums.PokemonContainer
import de.fiereu.openmmo.common.enums.Region
import de.fiereu.openmmo.server.game.testsupport.FakeCharacterRepository
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest

/**
 * Anything a player can trade or spend has to be in the database before the call returns. Position
 * and hp may wait for a checkpoint, because replaying a few seconds of walking costs nothing, while
 * an item the client has already been told about must not disappear in a crash.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class CharacterStoreDurabilityTest :
    FunSpec({
      test("granting an item persists it without waiting for a flush") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id

          store.addItem(id, itemId = 17, amount = 3)

          repo.saved[id]!!.items[17] shouldBe 3
        }
      }

      test("spending money persists before the call returns") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id

          store.addMoney(id, -500)

          repo.saved[id]!!.info.money shouldBe 29500
        }
      }

      test("acquiring a monster persists it before the call returns") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)

          store.addPokemon(created.info.id, caughtMonster(created.info.id))

          repo.saved[created.info.id]!!.pokemon.size shouldBe 1
        }
      }

      test("giving a monster to the PC persists it in PC storage") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)
          val monster =
              caughtMonster(created.info.id)
                  .copy(
                      container = PokemonContainer.PC,
                      containerSlot = 4,
                  )

          store.addPokemon(created.info.id, monster) shouldBe true

          repo.saved[created.info.id]!!.pokemon.size shouldBe 0
          repo.saved[created.info.id]!!.pcStorage.single().id shouldBe monster.id
        }
      }

      test("removing a monster deletes it durably from the character") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)
          val monster = caughtMonster(created.info.id)
          store.addPokemon(created.info.id, monster)

          store.removePokemon(created.info.id, monster.id) shouldBe true

          repo.saved[created.info.id]!!.pokemon.size shouldBe 0
          store.getCharacter(created.info.id)!!.pokemon.size shouldBe 0
        }
      }

      test("equipping a held item removes it from the bag and persists it on the monster") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)
          val monster = caughtMonster(created.info.id)
          store.addPokemon(created.info.id, monster)
          store.addItem(created.info.id, itemId = 5231, amount = 1)

          store.equipHeldItem(created.info.id, monster.id, itemId = 5231) shouldBe true

          repo.saved[created.info.id]!!.items[5231] shouldBe null
          repo.saved[created.info.id]!!.pokemon.single().heldItemId shouldBe 5231
        }
      }

      test("a failed held item write restores the bag and the monster") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)
          val monster = caughtMonster(created.info.id)
          store.addPokemon(created.info.id, monster)
          store.addItem(created.info.id, itemId = 5231, amount = 1)
          repo.failNextSave = true

          store.equipHeldItem(created.info.id, monster.id, itemId = 5231) shouldBe false

          store.getCharacter(created.info.id)!!.items[5231] shouldBe 1
          store.getCharacter(created.info.id)!!.pokemon.single().heldItemId shouldBe 0
        }
      }

      test("walking does not pay for a write, it waits for the checkpoint") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val created = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN)
          val before = repo.saveCount.get()

          store.updateCharacter(created.info.copy(positionX = 42))

          repo.saveCount.get() shouldBe before
          store.flushAll()
          repo.saved[created.info.id]!!.info.positionX shouldBe 42
        }
      }

      test("granting an item waits for a flush already in flight rather than riding on it") {
        runTest {
          val entered = CompletableDeferred<Unit>()
          val release = CompletableDeferred<Unit>()
          val repo = GatedRepository(entered, release)
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id

          // A checkpoint is mid write when the grant arrives.
          store.updateCharacter(store.getCharacter(id)!!.info.copy(positionX = 9))
          store.flushCharacterAsync(id)
          entered.await()

          val grant = async { store.addItem(id, itemId = 17, amount = 1) }
          advanceUntilIdle()
          grant.isCompleted shouldBe false

          release.complete(Unit)
          grant.await() shouldBe true
          repo.saved[id]!!.items[17] shouldBe 1
        }
      }

      test("a write that fails grants nothing and says so") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id
          repo.failNextSave = true

          store.addItem(id, itemId = 17, amount = 3) shouldBe false

          // Neither the caller nor a later checkpoint may resurrect the refused grant.
          store.getCharacter(id)!!.items[17] shouldBe null
          store.flushAll()
          repo.saved[id]!!.items[17] shouldBe null
        }
      }

      test("a failed payment leaves the balance alone") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id
          repo.failNextSave = true

          store.addMoney(id, 5000) shouldBe false

          store.getCharacter(id)!!.info.money shouldBe 30000
        }
      }

      test("granting an item does not evict the character its caller is still using") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id
          // The disconnect path has already asked for this character to be dropped.
          store.unloadCharacterAsync(id)

          store.addItem(id, itemId = 17, amount = 1) shouldBe true

          store.getCharacter(id).shouldNotBeNull().items[17] shouldBe 1
        }
      }

      test("a refused item change neither mutates nor writes") {
        runTest {
          val repo = FakeCharacterRepository()
          val store = CharacterStore(repo, EntityIdService(), backgroundScope)
          val id = store.createCharacter(1, "Ash", CharacterGender.MALE, Region.HOENN).info.id
          store.addItem(id, itemId = 17, amount = 1)
          val writes = repo.saveCount.get()

          store.addItem(id, itemId = 17, amount = -5) shouldBe false

          repo.saveCount.get() shouldBe writes
          store.getCharacter(id).shouldNotBeNull().items[17] shouldBe 1
        }
      }
    })

private fun caughtMonster(ownerId: Long): Pokemon =
    Pokemon(
        id = EntityIdService().newMonsterId(),
        ownerId = ownerId,
        container = PokemonContainer.PARTY,
        containerSlot = 0,
        dexId = 19,
        seed = 0,
        ot = "Ash",
        nickname = "",
        level = 3,
        hp = 14,
        xp = 27,
        eVs = EVs(),
        iVs = IVs(),
        moves = listOf(),
        isShiny = false,
        hasHiddenAbility = false,
        isAlpha = false,
        isSecret = false,
        isFatefulEncounter = false,
        isRaidEncounter = false,
        caughtAt = LocalDateTime.now(),
    )

/** Holds the first write open so a grant can be raced against a checkpoint already in flight. */
private class GatedRepository(
    private val entered: CompletableDeferred<Unit>,
    private val release: CompletableDeferred<Unit>,
) : CharacterRepository {
  private val delegate = FakeCharacterRepository()
  private val writes = AtomicInteger(0)

  val saved
    get() = delegate.saved

  override suspend fun loadByUser(userId: Int) = delegate.loadByUser(userId)

  override suspend fun loadById(id: Long) = delegate.loadById(id)

  override suspend fun insertAggregate(stored: StoredCharacter) = delegate.insertAggregate(stored)

  override suspend fun deleteById(userId: Int, id: Long) = delegate.deleteById(userId, id)

  override suspend fun saveChanges(previous: StoredCharacter?, current: StoredCharacter) {
    if (writes.incrementAndGet() == 1) {
      entered.complete(Unit)
      release.await()
    }
    delegate.saveChanges(previous, current)
  }

  override suspend fun saveExchange(
      previousLeft: StoredCharacter,
      currentLeft: StoredCharacter,
      previousRight: StoredCharacter,
      currentRight: StoredCharacter,
  ) {
    delegate.saveExchange(previousLeft, currentLeft, previousRight, currentRight)
  }
}
