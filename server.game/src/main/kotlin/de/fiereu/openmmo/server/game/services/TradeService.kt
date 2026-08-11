package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.network.SessionContext
import de.fiereu.openmmo.common.MAX_PARTY_SIZE
import de.fiereu.openmmo.common.Pokemon
import de.fiereu.openmmo.common.enums.PokemonContainer
import de.fiereu.openmmo.net.game.packets.ClientOpcode09Packet
import de.fiereu.openmmo.net.game.packets.DuelChallengePacket
import de.fiereu.openmmo.net.game.packets.DuelInviteOutcomePacket
import de.fiereu.openmmo.net.game.packets.DuelInvitePacket
import de.fiereu.openmmo.net.game.packets.ExchangeItemRequestPacket
import de.fiereu.openmmo.net.game.packets.StringCommandPacket
import de.fiereu.openmmo.net.game.packets.TradeActionPacket
import de.fiereu.openmmo.net.game.packets.TradeListEntryPacket
import de.fiereu.openmmo.net.game.packets.TradeSelectMonPacket
import de.fiereu.openmmo.server.game.session.PLAYER_STATE
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import de.fiereu.openmmo.server.game.storage.StoredCharacter
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val log = KotlinLogging.logger {}

private const val TRADE_CANCEL: Byte = 0
private const val TRADE_LOCK: Byte = 1
private const val TRADE_CONFIRM: Byte = 2
private const val MAX_TRADE_SLOTS = 6
private const val DUPLICATE_SELECTION_WINDOW_NANOS = 1_500_000_000L
// S2C trade-open capability mask: Pokemon, items and money. Sending zero opens the window in
// read-only mode; PC-box access is intentionally not enabled outside a PC terminal.
private const val TRADE_CAPABILITIES: Byte = 0x07

private data class TradeInvitation(val senderId: Long, val targetId: Long)

private data class TradeOffer(
    val pokemonIds: MutableList<Long> = mutableListOf(),
    val items: MutableMap<Int, Int> = mutableMapOf(),
    var money: Int = 0,
    var locked: Boolean = false,
    var confirmed: Boolean = false,
    var lastCompactPokemonId: Long? = null,
    var lastCompactSelectionAt: Long = 0,
)

private data class PlayerTrade(
    val leftId: Long,
    val rightId: Long,
    val leftSession: SessionContext,
    val rightSession: SessionContext,
) {
  val offers = mutableMapOf(leftId to TradeOffer(), rightId to TradeOffer())
  var closed = false

  fun otherId(id: Long): Long = if (id == leftId) rightId else leftId

  fun session(id: Long): SessionContext = if (id == leftId) leftSession else rightSession
}

/** Server-authoritative direct trades. No asset moves before both players lock and confirm. */
@Singleton
class TradeService
@Inject
constructor(
    private val characters: CharacterStore,
    private val sessions: SessionRegistry,
    private val worldState: WorldStateService,
) {
  private val invitationsByTarget = ConcurrentHashMap<Long, TradeInvitation>()
  private val tradesByCharacter = ConcurrentHashMap<Long, PlayerTrade>()

  /** The context-menu Exchange entry uses the generic challenge packet with a non-duel subtype. */
  fun onChallenge(event: PacketEvent<DuelChallengePacket>) {
    val senderId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val targetId =
        sessions.onlineCharacterIds().firstOrNull { id ->
          characters
              .getCharacter(id)
              ?.info
              ?.name
              ?.equals(event.packet.targetPlayerName, ignoreCase = true) == true
        }
    log.info {
      "Trade request char=$senderId target='${event.packet.targetPlayerName}' subtype=${event.packet.battleTypeId}"
    }
    if (targetId == null) {
      event.session.send(notice("Ce joueur n'est plus connecté."))
      return
    }
    request(event.session, senderId, targetId)
  }

  suspend fun onAction(event: PacketEvent<TradeActionPacket>) {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val trade = tradesByCharacter[charId]
    log.info { "Trade action char=$charId action=${event.packet.action}" }
    if (trade == null) {
      request(event.session, charId, event.session.attributes[PLAYER_STATE]?.interactionTargetId)
      return
    }
    synchronized(trade) {
      if (trade.closed) return
      when (event.packet.action) {
        TRADE_CANCEL -> close(trade, "L'échange a été annulé.")
        TRADE_LOCK -> {
          val offer = trade.offers.getValue(charId)
          offer.locked = !offer.locked
          offer.confirmed = false
          trade.offers.getValue(trade.otherId(charId)).confirmed = false
          broadcastState(trade)
        }
        TRADE_CONFIRM -> {
          if (trade.offers.values.all { it.locked }) {
            trade.offers.getValue(charId).confirmed = true
            broadcastState(trade)
          }
        }
        else -> log.warn { "Unknown trade action ${event.packet.action} from $charId" }
      }
    }
    if (trade.offers.values.all { it.locked && it.confirmed }) complete(trade)
  }

  fun onSelectPokemon(event: PacketEvent<TradeSelectMonPacket>) {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val trade = tradesByCharacter[charId] ?: return
    synchronized(trade) {
      if (trade.closed) return
      val stored = characters.getCharacter(charId) ?: return
      val owned = stored.pokemon + stored.pcStorage
      val selected = owned.getOrNull(event.packet.slotIndex) ?: return
      val offer = trade.offers.getValue(charId)
      val duplicateOfCompactPicker =
          offer.lastCompactPokemonId == selected.id &&
              System.nanoTime() - offer.lastCompactSelectionAt < DUPLICATE_SELECTION_WINDOW_NANOS
      if (duplicateOfCompactPicker) {
        log.info { "Ignored duplicate trade pokemon packet char=$charId id=${selected.id}" }
        return
      }
      selectPokemon(trade, charId, selected, toggleExisting = true)
    }
  }

  fun onClientOpcode09(event: PacketEvent<ClientOpcode09Packet>) {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val trade = tradesByCharacter[charId] ?: return
    val hex = event.packet.payload.joinToString("") { "%02x".format(it.toInt() and 0xff) }
    synchronized(trade) {
      if (trade.closed) return
      val stored = characters.getCharacter(charId) ?: return
      val containerSlot = decodeCompactPartySlot(event.packet.payload)
      val selected =
          containerSlot?.let { slot ->
            stored.pokemon.firstOrNull { (it.containerSlot.toInt() and 0xffff) == slot }
                ?: stored.pokemon.getOrNull(slot)
          }
      if (selected == null) {
        log.warn { "Unsupported trade pokemon selection char=$charId bytes=$hex" }
        return
      }
      val offer = trade.offers.getValue(charId)
      offer.lastCompactPokemonId = selected.id
      offer.lastCompactSelectionAt = System.nanoTime()
      // The party picker can emit the same compact selection more than once while it closes. A
      // repeated picker result means "ensure selected", never "remove from the offer".
      selectPokemon(trade, charId, selected, toggleExisting = false)
      log.info { "Compact trade pokemon selection char=$charId slot=$containerSlot bytes=$hex" }
    }
  }

  /** Returns true when an item selector belonged to a trade rather than an open shop. */
  fun onSelectItem(event: PacketEvent<ExchangeItemRequestPacket>): Boolean {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return false
    val trade = tradesByCharacter[charId] ?: return false
    synchronized(trade) {
      val offer = trade.offers.getValue(charId)
      if (trade.closed || offer.locked) return true
      val itemId = event.packet.itemEntityRef.toInt() and 0xffff
      val quantity = event.packet.quantity.toInt()
      val held = characters.getCharacter(charId)?.items?.get(itemId) ?: 0
      if (quantity <= 0) offer.items.remove(itemId)
      else if (quantity <= held && (itemId in offer.items || offer.items.size < MAX_TRADE_SLOTS))
          offer.items[itemId] = quantity
      resetApprovals(trade)
      log.info {
        "Trade item char=$charId item=$itemId quantity=$quantity type=${event.packet.exchangeTypeIndex}"
      }
    }
    return true
  }

  /** Returns true when the trade window consumed this string as its money amount. */
  fun onMoney(event: PacketEvent<StringCommandPacket>): Boolean {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return false
    val trade = tradesByCharacter[charId] ?: return false
    synchronized(trade) {
      val offer = trade.offers.getValue(charId)
      if (trade.closed || offer.locked) return true
      val amount = Regex("-?\\d+").find(event.packet.command)?.value?.toLongOrNull() ?: return true
      val balance = characters.getCharacter(charId)?.info?.money ?: return true
      offer.money = amount.coerceIn(0, balance.toLong()).toInt()
      resetApprovals(trade)
      log.info { "Trade money char=$charId amount=${offer.money} raw='${event.packet.command}'" }
    }
    return true
  }

  /**
   * Some client revisions send the context-menu Exchange click as a generic string command. Resolve
   * the named online player when present, otherwise use the last selected player.
   */
  fun onCommand(event: PacketEvent<StringCommandPacket>) {
    val senderId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val command = event.packet.command.trim()
    val namedTargetId =
        sessions.onlineCharacterIds().firstOrNull { id ->
          if (id == senderId) return@firstOrNull false
          val name = characters.getCharacter(id)?.info?.name ?: return@firstOrNull false
          command.equals(name, ignoreCase = true) ||
              Regex(
                      "(^|[^\\p{L}\\p{N}_])${Regex.escape(name)}([^\\p{L}\\p{N}_]|$)",
                      RegexOption.IGNORE_CASE)
                  .containsMatchIn(command)
        }
    val selectedTargetId = event.session.attributes[PLAYER_STATE]?.interactionTargetId
    log.info {
      "Trade command char=$senderId raw='$command' namedTarget=$namedTargetId selectedTarget=$selectedTargetId"
    }
    request(event.session, senderId, namedTargetId ?: selectedTargetId)
  }

  fun onDisconnect(session: SessionContext) {
    val charId = session.attributes[PLAYER_STATE]?.characterId ?: return
    invitationsByTarget.remove(charId)
    invitationsByTarget.entries.removeIf { it.value.senderId == charId }
    tradesByCharacter[charId]?.let { trade ->
      synchronized(trade) { if (!trade.closed) close(trade, "L'autre joueur s'est déconnecté.") }
    }
  }

  fun inTrade(charId: Long): Boolean = tradesByCharacter.containsKey(charId)

  private fun request(session: SessionContext, senderId: Long, requestedTargetId: Long?) {
    val state = session.attributes[PLAYER_STATE] ?: return
    val targetId = requestedTargetId
    if (targetId == null || targetId == senderId) {
      session.send(notice("Clique d'abord sur le joueur avec qui tu veux échanger."))
      return
    }
    val sender = characters.getCharacter(senderId) ?: return
    val target = characters.getCharacter(targetId)
    val targetSession = sessions.getByCharacterId(targetId)
    if (target == null || targetSession == null) {
      session.send(notice("Ce joueur n'est plus connecté."))
      return
    }
    val targetState = targetSession.attributes[PLAYER_STATE] ?: return
    if (state.regionId != targetState.regionId ||
        state.bankId != targetState.bankId ||
        state.mapId != targetState.mapId) {
      session.send(notice("Vous devez être sur la même carte pour échanger."))
      return
    }
    if (tradesByCharacter.containsKey(targetId)) {
      session.send(notice("${target.info.name} échange déjà avec quelqu'un."))
      return
    }

    val reverse = invitationsByTarget[senderId]
    if (reverse?.senderId == targetId) {
      invitationsByTarget.remove(senderId, reverse)
      val trade = PlayerTrade(targetId, senderId, targetSession, session)
      tradesByCharacter[targetId] = trade
      tradesByCharacter[senderId] = trade
      targetSession.send(DuelInvitePacket(TRADE_CAPABILITIES, 0, sender.info.name))
      session.send(DuelInvitePacket(TRADE_CAPABILITIES, 0, target.info.name))
      targetSession.send(notice("Échange commencé avec ${sender.info.name}."))
      session.send(notice("Échange commencé avec ${target.info.name}."))
      log.info { "Trade started ${target.info.name} <-> ${sender.info.name}" }
      return
    }

    val invitation = TradeInvitation(senderId, targetId)
    val existing = invitationsByTarget.putIfAbsent(targetId, invitation)
    if (existing != null && existing.senderId != senderId) {
      session.send(notice("${target.info.name} a dÃ©jÃ  une demande d'Ã©change en attente."))
      return
    }
    session.send(notice("Demande d'échange envoyée à ${target.info.name}."))
    targetSession.send(
        notice(
            "${sender.info.name} veut échanger. Clique sur ${sender.info.name}, puis choisis Échange pour accepter."))
  }

  private suspend fun complete(trade: PlayerTrade) {
    synchronized(trade) { if (trade.closed) return }
    val leftOffer = snapshot(trade.offers.getValue(trade.leftId))
    val rightOffer = snapshot(trade.offers.getValue(trade.rightId))
    val success =
        characters.exchangeDurably(trade.leftId, trade.rightId) { left, right ->
          buildExchange(left, right, leftOffer, rightOffer)
        }
    synchronized(trade) {
      if (trade.closed) return
      if (!success) {
        trade.offers.values.forEach {
          it.locked = false
          it.confirmed = false
        }
        broadcastState(trade)
        trade.leftSession.send(notice("Échange refusé : l'offre n'est plus valide."))
        trade.rightSession.send(notice("Échange refusé : l'offre n'est plus valide."))
        return
      }
      trade.closed = true
      tradesByCharacter.remove(trade.leftId, trade)
      tradesByCharacter.remove(trade.rightId, trade)
      trade.leftSession.send(DuelInviteOutcomePacket(0))
      trade.rightSession.send(DuelInviteOutcomePacket(0))
      characters.getCharacter(trade.leftId)?.let { worldState.send(trade.leftSession, it, true) }
      characters.getCharacter(trade.rightId)?.let { worldState.send(trade.rightSession, it, true) }
      trade.leftSession.send(notice("Échange terminé."))
      trade.rightSession.send(notice("Échange terminé."))
      log.info { "Trade completed ${trade.leftId} <-> ${trade.rightId}" }
    }
  }

  private fun buildExchange(
      left: StoredCharacter,
      right: StoredCharacter,
      leftOffer: TradeOffer,
      rightOffer: TradeOffer,
  ): Pair<StoredCharacter, StoredCharacter>? {
    if (!validOffer(left, leftOffer) || !validOffer(right, rightOffer)) return null
    val leftSelected = allPokemon(left).filter { it.id in leftOffer.pokemonIds }
    val rightSelected = allPokemon(right).filter { it.id in rightOffer.pokemonIds }
    val newLeftMoney = left.info.money.toLong() - leftOffer.money + rightOffer.money
    val newRightMoney = right.info.money.toLong() - rightOffer.money + leftOffer.money
    if (newLeftMoney !in 0..Int.MAX_VALUE.toLong() || newRightMoney !in 0..Int.MAX_VALUE.toLong())
        return null

    val newLeft =
        rebuildPokemon(
                left,
                leftSelected.map { it.id }.toSet(),
                rightSelected,
                tradeItems(left.items, right.items, leftOffer.items, rightOffer.items)
                    ?: return null,
            )
            .copy(info = left.info.copy(money = newLeftMoney.toInt()))
    val newRight =
        rebuildPokemon(
                right,
                rightSelected.map { it.id }.toSet(),
                leftSelected,
                tradeItems(right.items, left.items, rightOffer.items, leftOffer.items)
                    ?: return null,
            )
            .copy(info = right.info.copy(money = newRightMoney.toInt()))
    if (newLeft.pokemon.isEmpty() || newRight.pokemon.isEmpty()) return null
    return newLeft to newRight
  }

  private fun validOffer(stored: StoredCharacter, offer: TradeOffer): Boolean {
    val ownedIds = allPokemon(stored).map { it.id }.toSet()
    if (!ownedIds.containsAll(offer.pokemonIds) ||
        offer.pokemonIds.distinct().size != offer.pokemonIds.size)
        return false
    if (offer.money !in 0..stored.info.money) return false
    return offer.items.all { (id, quantity) -> quantity > 0 && (stored.items[id] ?: 0) >= quantity }
  }

  private fun rebuildPokemon(
      owner: StoredCharacter,
      removedIds: Set<Long>,
      received: List<Pokemon>,
      items: MutableMap<Int, Int>,
  ): StoredCharacter {
    val remainingParty = owner.pokemon.filter { it.id !in removedIds }
    val remainingPc = owner.pcStorage.filter { it.id !in removedIds }
    val room = (MAX_PARTY_SIZE - remainingParty.size).coerceAtLeast(0)
    val incomingParty = received.take(room)
    val incomingPc = received.drop(room)
    val party =
        (remainingParty + incomingParty).mapIndexed { slot, mon ->
          mon.copy(
              ownerId = owner.info.id,
              container = PokemonContainer.PARTY,
              containerSlot = slot.toShort(),
          )
        }
    val pc =
        (remainingPc + incomingPc).mapIndexed { slot, mon ->
          mon.copy(
              ownerId = owner.info.id,
              container = PokemonContainer.PC,
              containerSlot = slot.toShort(),
          )
        }
    return owner.copy(
        pokemon = party.toMutableList(), pcStorage = pc.toMutableList(), items = items)
  }

  private fun tradeItems(
      own: Map<Int, Int>,
      other: Map<Int, Int>,
      sent: Map<Int, Int>,
      received: Map<Int, Int>,
  ): MutableMap<Int, Int>? {
    val result = own.toMutableMap()
    for ((id, quantity) in sent) {
      val after = (result[id] ?: 0) - quantity
      if (after < 0) return null
      if (after == 0) result.remove(id) else result[id] = after
    }
    for ((id, quantity) in received) {
      val after = (result[id] ?: 0).toLong() + quantity
      if (after > Int.MAX_VALUE) return null
      result[id] = after.toInt()
    }
    return result
  }

  private fun allPokemon(stored: StoredCharacter): List<Pokemon> = stored.pokemon + stored.pcStorage

  /**
   * The current client sends party-picker clicks on the direction-specific 0x09 packet. Its compact
   * seven-byte form contains a zero-based little-endian party container slot in bytes two and three
   * (for example `01 01 00 00 02 00 00` for slot zero). Keep this decoder deliberately narrow so an
   * unrelated 0x09 payload can never mutate a trade offer.
   */
  private fun decodeCompactPartySlot(payload: ByteArray): Int? {
    if (payload.size != 7 || payload[0].toInt() != 1 || payload[1].toInt() != 1) return null
    return (payload[2].toInt() and 0xff) or ((payload[3].toInt() and 0xff) shl 8)
  }

  private fun selectPokemon(
      trade: PlayerTrade,
      charId: Long,
      selected: Pokemon,
      toggleExisting: Boolean,
  ) {
    val offer = trade.offers.getValue(charId)
    if (offer.locked) return
    var changed = false
    if (selected.id in offer.pokemonIds) {
      if (!toggleExisting) return
      offer.pokemonIds.remove(selected.id)
      // The client toggles an existing remote entry when it receives that same entity again.
      trade.session(trade.otherId(charId)).send(TradeListEntryPacket(selected))
      changed = true
    } else if (offer.pokemonIds.size < MAX_TRADE_SLOTS) {
      offer.pokemonIds += selected.id
      // TradeListEntryPacket always targets the remote (right-hand) offer. The selecting client
      // already updates its own left-hand offer from the picker callback, so echoing this packet
      // back would incorrectly render its Pokemon as belonging to the opponent.
      trade.session(trade.otherId(charId)).send(TradeListEntryPacket(selected))
      changed = true
    }
    if (!changed) return
    resetApprovals(trade)
    log.info { "Trade pokemon char=$charId id=${selected.id}" }
  }

  private fun snapshot(offer: TradeOffer) =
      TradeOffer(
          offer.pokemonIds.toMutableList(),
          offer.items.toMutableMap(),
          offer.money,
          offer.locked,
          offer.confirmed,
          offer.lastCompactPokemonId,
          offer.lastCompactSelectionAt,
      )

  private fun resetApprovals(trade: PlayerTrade) {
    trade.offers.values.forEach {
      it.locked = false
      it.confirmed = false
    }
    broadcastState(trade)
  }

  private fun broadcastState(trade: PlayerTrade) {
    val left = trade.offers.getValue(trade.leftId)
    val right = trade.offers.getValue(trade.rightId)
    val packed =
        ((if (left.locked) 1 else 0) or
                (if (right.locked) 2 else 0) or
                (if (left.confirmed) 4 else 0) or
                (if (right.confirmed) 8 else 0))
            .toByte()
    trade.leftSession.send(DuelInviteOutcomePacket(packed))
    trade.rightSession.send(DuelInviteOutcomePacket(packed))
  }

  private fun close(trade: PlayerTrade, message: String) {
    if (trade.closed) return
    trade.closed = true
    tradesByCharacter.remove(trade.leftId, trade)
    tradesByCharacter.remove(trade.rightId, trade)
    trade.leftSession.send(DuelInviteOutcomePacket(1))
    trade.rightSession.send(DuelInviteOutcomePacket(1))
    trade.leftSession.send(notice(message))
    trade.rightSession.send(notice(message))
  }
}
