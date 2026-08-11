package de.fiereu.openmmo.server.game.services.command

import de.fiereu.openmmo.items.ItemRegistry
import de.fiereu.openmmo.items.generated.Items
import de.fiereu.openmmo.server.game.services.itemStackUpdatePacket
import de.fiereu.openmmo.server.game.storage.CharacterStore
import javax.inject.Inject
import javax.inject.Singleton

/** Gives and equips the test Lucky Egg, which doubles XP for its holder. */
@Singleton
class LuckyEggCommand
@Inject
constructor(
    private val characters: CharacterStore,
    private val items: ItemRegistry,
) : ChatCommand {
  override val name = "oeufchance"
  override val usage = "/oeufchance <1-6>"
  override val description = "donne et equipe un Oeuf Chance (EXP x2) au Pokemon choisi"

  override suspend fun run(ctx: CommandContext) {
    val slot = ctx.args.singleOrNull()?.toIntOrNull()?.minus(1)
    if (slot == null || slot !in 0..5) {
      ctx.reply("Utilise /oeufchance <1-6>, selon la place du Pokemon dans ton equipe.")
      return
    }
    val pokemon = characters.getCharacter(ctx.characterId)?.pokemon?.getOrNull(slot)
    if (pokemon == null) {
      ctx.reply("Il n'y a aucun Pokemon a la place ${slot + 1}.")
      return
    }
    val itemId = items.idOf(Items.LUCKY_EGG)
    if (pokemon.heldItemId == itemId) {
      ctx.reply("${pokemon.nickname.ifBlank { "Ce Pokemon" }} tient deja l'Oeuf Chance.")
      return
    }
    val stored = characters.getCharacter(ctx.characterId) ?: return
    if ((stored.items[itemId] ?: 0) == 0 && !characters.addItem(ctx.characterId, itemId, 1)) {
      ctx.reply("Impossible de creer l'Oeuf Chance.")
      return
    }
    if (!characters.equipHeldItem(ctx.characterId, pokemon.id, itemId)) {
      ctx.reply("Impossible d'equiper l'Oeuf Chance.")
      return
    }
    val after = characters.getCharacter(ctx.characterId) ?: return
    ctx.session.send(itemStackUpdatePacket(itemId, after.items[itemId] ?: 0))
    ctx.reply(
        "Oeuf Chance equipe sur ${pokemon.nickname.ifBlank { "le Pokemon ${slot + 1}" }} : EXP x2.")
  }
}
