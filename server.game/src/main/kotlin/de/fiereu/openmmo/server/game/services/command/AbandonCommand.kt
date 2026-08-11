package de.fiereu.openmmo.server.game.services.command

import de.fiereu.openmmo.server.game.services.DuelService
import javax.inject.Inject
import javax.inject.Singleton

/** Lets a player leave a PvP battle even while the trainer-shaped client UI hides Run. */
@Singleton
class AbandonCommand @Inject constructor(private val duels: DuelService) : ChatCommand {
  override val name = "abandon"
  override val usage = "/abandon"
  override val description = "abandonne le duel PvP en cours"

  override suspend fun run(ctx: CommandContext) {
    if (!duels.forfeit(ctx.session)) ctx.reply("Tu n'es pas dans un duel PvP.")
  }
}
