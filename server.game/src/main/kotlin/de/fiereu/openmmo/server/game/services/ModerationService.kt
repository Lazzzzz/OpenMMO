package de.fiereu.openmmo.server.game.services

import de.fiereu.openmmo.db.game.tables.references.CHARACTERS
import java.time.LocalDateTime
import javax.inject.Inject
import javax.inject.Named
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext
import org.jooq.DSLContext

@Singleton
class ModerationService
@Inject
constructor(
    private val dsl: DSLContext,
    @param:Named("db") private val dispatcher: CoroutineDispatcher,
) {
  suspend fun isMuted(characterId: Long): Boolean =
      withContext(dispatcher) {
        dsl.select(CHARACTERS.MUTED_UNTIL)
            .from(CHARACTERS)
            .where(CHARACTERS.ID.eq(characterId))
            .fetchOne(CHARACTERS.MUTED_UNTIL)
            ?.isAfter(LocalDateTime.now()) == true
      }
}
