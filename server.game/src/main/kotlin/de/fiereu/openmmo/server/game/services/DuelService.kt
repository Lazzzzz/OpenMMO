package de.fiereu.openmmo.server.game.services

import de.fiereu.network.PacketEvent
import de.fiereu.network.SessionContext
import de.fiereu.openmmo.common.enums.BattleAction
import de.fiereu.openmmo.net.game.packets.DuelChallengePacket
import de.fiereu.openmmo.net.game.packets.InGameChallengeResponsePacket
import de.fiereu.openmmo.net.game.packets.MapLoadedAckPacket
import de.fiereu.openmmo.net.game.packets.battle.BattleActionSelectPacket
import de.fiereu.openmmo.pokemon.SpeciesRegistry
import de.fiereu.openmmo.server.game.battle.BattleEvent
import de.fiereu.openmmo.server.game.battle.BattleInstance
import de.fiereu.openmmo.server.game.battle.BattleMonState
import de.fiereu.openmmo.server.game.battle.BattlePacketEmitter
import de.fiereu.openmmo.server.game.battle.BattleRegistry
import de.fiereu.openmmo.server.game.battle.BattleResult
import de.fiereu.openmmo.server.game.battle.BattleRng
import de.fiereu.openmmo.server.game.battle.BattleRules
import de.fiereu.openmmo.server.game.battle.StatCalculator
import de.fiereu.openmmo.server.game.battle.TurnEngine
import de.fiereu.openmmo.server.game.session.PLAYER_STATE
import de.fiereu.openmmo.server.game.session.SessionRegistry
import de.fiereu.openmmo.server.game.storage.CharacterStore
import de.fiereu.openmmo.server.game.world.interest.InterestManager
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

private val log = KotlinLogging.logger {}

private data class DuelInvitation(
    val challengerId: Long,
    val targetId: Long,
)

private data class DuelMatch(
    val left: BattleInstance,
    val right: BattleInstance,
) {
  val actions = mutableMapOf<Long, BattleActionSelectPacket>()
  val readyToLeave = mutableSetOf<Long>()
  var ended = false

  fun view(charId: Long): BattleInstance = if (left.charId == charId) left else right

  fun other(charId: Long): BattleInstance = if (left.charId == charId) right else left
}

/** Matchmaking and synchronized two-player turns for direct 1v1 challenges. */
@Singleton
class DuelService
@Inject
constructor(
    private val characters: CharacterStore,
    private val sessions: SessionRegistry,
    private val battles: BattleRegistry,
    private val engine: TurnEngine,
    private val emitter: BattlePacketEmitter,
    private val interests: InterestManager,
    private val species: SpeciesRegistry,
) {
  private val invitationsByTarget = ConcurrentHashMap<Long, DuelInvitation>()
  private val matchesByCharacter = ConcurrentHashMap<Long, DuelMatch>()

  fun onChallenge(event: PacketEvent<DuelChallengePacket>) {
    val challengerId = event.session.attributes[PLAYER_STATE]?.characterId ?: return
    val challenger = characters.getCharacter(challengerId) ?: return
    val targetEntry =
        sessions.onlineCharacterIds().firstNotNullOfOrNull { id ->
          val stored = characters.getCharacter(id)
          if (stored != null &&
              stored.info.name.equals(event.packet.targetPlayerName, ignoreCase = true)) {
            id to stored
          } else null
        }
    if (targetEntry == null) {
      event.session.send(notice("Ce joueur n'est pas connecté."))
      return
    }
    val (targetId, target) = targetEntry
    val targetSession = sessions.getByCharacterId(targetId) ?: return
    if (targetId == challengerId) {
      event.session.send(notice("Tu ne peux pas te défier toi-même."))
      return
    }
    if (battles.byChar(challengerId) != null || matchesByCharacter.containsKey(challengerId)) {
      event.session.send(notice("Tu es déjà en combat."))
      return
    }
    if (battles.byChar(targetId) != null || matchesByCharacter.containsKey(targetId)) {
      event.session.send(notice("${target.info.name} est déjà en combat."))
      return
    }

    // The repository does not know the real S2C battle-request opcode yet. A reciprocal challenge
    // gives the target an explicit accept action without sending the trade opcode that was
    // previously misidentified as a duel request.
    val reverse = invitationsByTarget[challengerId]
    if (reverse?.challengerId == targetId) {
      invitationsByTarget.remove(challengerId, reverse)
      log.info { "Accepted reciprocal duel ${target.info.name} <-> ${challenger.info.name}" }
      if (!start(reverse, targetSession, event.session)) {
        event.session.send(notice("Le duel n'a pas pu démarrer."))
      }
      return
    }

    val invitation = DuelInvitation(challengerId, targetId)
    val existing = invitationsByTarget.putIfAbsent(targetId, invitation)
    if (existing != null && existing.challengerId != challengerId) {
      event.session.send(notice("${target.info.name} a déjà un défi en attente."))
      return
    }
    event.session.send(notice("Défi envoyé à ${target.info.name}. En attente de son acceptation."))
    targetSession.send(
        notice(
            "${challenger.info.name} te défie en 1v1. " +
                "Pour accepter : clique sur ${challenger.info.name}, puis choisis Défi."))
    log.info { "Pending reciprocal duel ${challenger.info.name} -> ${target.info.name}" }
  }

  /** Kept registered because this opcode is also used by other in-game prompts. */
  fun onResponse(event: PacketEvent<InGameChallengeResponsePacket>) {
    log.debug { "Unmatched in-game challenge response: ${event.packet}" }
  }

  /** Returns true when the packet belonged to a PvP match. */
  fun onBattleAction(event: PacketEvent<BattleActionSelectPacket>): Boolean {
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return false
    val match = matchesByCharacter[charId] ?: return false
    synchronized(match) {
      if (match.ended) return true
      val view = match.view(charId)
      val action = event.packet
      if (view.activeMon().fainted) {
        if (action.action != BattleAction.SWITCH || !validSwitch(view, action.moveOrItemId)) {
          emitter.sendSwitchPrompt(view)
          return true
        }
        switch(match, view, action.moveOrItemId.toInt())
        match.actions.clear()
        incrementTurn(match)
        promptBoth(match)
        return true
      }
      when (action.action) {
        BattleAction.ITEM -> {
          emitter.sendNotice(view, "Les objets ne sont pas autorisés en duel.")
          emitter.sendPrompt(view)
          return true
        }
        BattleAction.RUN -> {
          end(match, winner = match.other(charId), reason = "abandon")
          return true
        }
        BattleAction.MOVE ->
            if (!validMove(view, action.moveOrItemId)) {
              emitter.sendNotice(view, "Cette attaque ne peut pas être utilisée.")
              emitter.sendPrompt(view)
              return true
            }
        BattleAction.SWITCH ->
            if (!validSwitch(view, action.moveOrItemId)) {
              emitter.sendPrompt(view)
              return true
            }
      }
      match.actions[charId] = action
      if (match.actions.size == 2) resolve(match)
    }
    return true
  }

  /** Returns true when a PvP transition consumed this acknowledgement. */
  fun onClientReady(event: PacketEvent<MapLoadedAckPacket>): Boolean {
    if (event.packet.data.isNotEmpty()) return false
    val charId = event.session.attributes[PLAYER_STATE]?.characterId ?: return false
    val match = matchesByCharacter[charId] ?: return false
    synchronized(match) {
      if (!match.ended) return false
      finishView(match, match.view(charId))
    }
    return true
  }

  /** Awards a running duel to the player who stayed connected. */
  fun onDisconnect(session: SessionContext): Boolean {
    val charId = session.attributes[PLAYER_STATE]?.characterId ?: return false
    invitationsByTarget.remove(charId)
    invitationsByTarget.entries.removeIf { it.value.challengerId == charId }
    val match = matchesByCharacter[charId] ?: return false
    synchronized(match) {
      val disconnected = match.view(charId)
      val remaining = match.other(charId)
      if (!match.ended) {
        match.ended = true
        remaining.pendingResult = BattleResult.VICTORY
        emitter.sendNotice(remaining, "Ton adversaire s'est déconnecté. Tu remportes le duel.")
        sendEnd(remaining)
      }
      finishView(match, disconnected)
    }
    return true
  }

  fun inDuel(charId: Long): Boolean = matchesByCharacter.containsKey(charId)

  /** Forfeits the caller's duel. Returns false when the character is not in PvP. */
  fun forfeit(session: SessionContext): Boolean {
    val charId = session.attributes[PLAYER_STATE]?.characterId ?: return false
    val match = matchesByCharacter[charId] ?: return false
    synchronized(match) {
      if (!match.ended) end(match, winner = match.other(charId), reason = "abandon")
    }
    return true
  }

  private fun start(
      invitation: DuelInvitation,
      challengerSession: SessionContext,
      targetSession: SessionContext,
  ): Boolean {
    if (battles.byChar(invitation.challengerId) != null ||
        battles.byChar(invitation.targetId) != null) {
      challengerSession.send(notice("Un des joueurs est déjà en combat."))
      return false
    }
    val challenger = characters.getCharacter(invitation.challengerId) ?: return false
    val target = characters.getCharacter(invitation.targetId) ?: return false
    val leftParty =
        buildParty(challenger.info.id, challenger.pokemon, challengerSession) ?: return false
    val rightParty = buildParty(target.info.id, target.pokemon, targetSession) ?: return false
    val rng = BattleRng()
    val rules = BattleRules(catchable = false, escapable = false, pvp = true)
    val left =
        battles.create(challenger.info.id, challengerSession, leftParty, rightParty, rng, rules)
    val right = battles.create(target.info.id, targetSession, rightParty, leftParty, rng, rules)
    initializeActive(left)
    initializeActive(right)
    val match = DuelMatch(left, right)
    matchesByCharacter[left.charId] = match
    matchesByCharacter[right.charId] = match
    interests.join(left.session, left.key)
    interests.join(right.session, right.key)
    emitter.sendStart(left, challenger.info.name)
    emitter.sendStart(right, target.info.name)
    log.info { "PvP duel started: ${challenger.info.name} vs ${target.info.name}" }
    return true
  }

  private fun buildParty(
      charId: Long,
      pokemon: List<de.fiereu.openmmo.common.Pokemon>,
      session: SessionContext,
  ): List<BattleMonState>? {
    if (pokemon.isEmpty()) {
      session.send(notice("Il te faut au moins un Pokémon pour combattre."))
      return null
    }
    val party =
        pokemon.mapIndexedNotNull { index, mon ->
          val def = species.get(mon.dexId) ?: return@mapIndexedNotNull null
          BattleMonState(mon.id, def, index, mon, StatCalculator.computeAll(def, mon))
        }
    if (party.size != pokemon.size) {
      session.send(notice("Une espèce de ton équipe n'est pas encore gérée."))
      return null
    }
    if (party.all { it.fainted }) {
      session.send(notice("Tous tes Pokémon sont K.O."))
      return null
    }
    return party
  }

  private fun initializeActive(view: BattleInstance) {
    view.activeSlot = view.party.indexOfFirst { !it.fainted }
    view.opponentSlot = view.opponent.indexOfFirst { !it.fainted }
    view.seenActive.clear()
    view.seenActive += view.activeSlot
    view.opponentSeen.clear()
    view.opponentSeen += view.opponentSlot
  }

  private fun validMove(view: BattleInstance, moveId: Short): Boolean =
      view.activeMon().moves.any { it.id == moveId && it.id.toInt() != 0 && it.pp > 0 }

  private fun validSwitch(view: BattleInstance, slot: Short): Boolean {
    val target = view.party.getOrNull(slot.toInt()) ?: return false
    return !target.fainted && slot.toInt() != view.activeSlot
  }

  private fun resolve(match: DuelMatch) {
    val leftAction = match.actions.getValue(match.left.charId)
    val rightAction = match.actions.getValue(match.right.charId)
    match.actions.clear()
    var events: List<BattleEvent> = emptyList()
    if (leftAction.action == BattleAction.SWITCH) {
      switch(match, match.left, leftAction.moveOrItemId.toInt())
    }
    if (rightAction.action == BattleAction.SWITCH) {
      switch(match, match.right, rightAction.moveOrItemId.toInt())
    }
    events =
        when {
          leftAction.action == BattleAction.MOVE && rightAction.action == BattleAction.MOVE ->
              engine.resolvePvpTurn(match.left, leftAction.moveOrItemId, rightAction.moveOrItemId)
          leftAction.action == BattleAction.MOVE ->
              engine.resolvePvpAttack(match.left, true, leftAction.moveOrItemId)
          rightAction.action == BattleAction.MOVE ->
              engine.resolvePvpAttack(match.left, false, rightAction.moveOrItemId)
          else -> emptyList()
        }
    emitter.sendEvents(match.left, events)
    emitter.sendEvents(match.right, events)
    afterTurn(match)
  }

  private fun switch(match: DuelMatch, view: BattleInstance, target: Int) {
    val old = view.activeSlot
    val fullBlock = target !in view.seenActive
    view.activeSlot = target
    view.seenActive += target
    val opposite = match.other(view.charId)
    opposite.opponentSlot = target
    opposite.opponentSeen += target
    emitter.sendSwitchIn(view, old, fullBlock)
    emitter.sendOpponentSwitchIn(opposite, old, fullBlock)
  }

  private fun afterTurn(match: DuelMatch) {
    when {
      match.left.party.all { it.fainted } -> end(match, match.right, "victoire")
      match.right.party.all { it.fainted } -> end(match, match.left, "victoire")
      match.left.activeMon().fainted -> emitter.sendSwitchPrompt(match.left)
      match.right.activeMon().fainted -> emitter.sendSwitchPrompt(match.right)
      else -> {
        incrementTurn(match)
        promptBoth(match)
      }
    }
  }

  private fun incrementTurn(match: DuelMatch) {
    match.left.turn += 1
    match.right.turn += 1
  }

  private fun promptBoth(match: DuelMatch) {
    emitter.sendPrompt(match.left)
    emitter.sendPrompt(match.right)
  }

  private fun end(match: DuelMatch, winner: BattleInstance, reason: String) {
    if (match.ended) return
    match.ended = true
    val loser = match.other(winner.charId)
    winner.pendingResult = BattleResult.VICTORY
    loser.pendingResult = BattleResult.DEFEAT
    emitter.sendNotice(winner, "Tu remportes le duel ($reason).")
    emitter.sendNotice(loser, "Tu perds le duel ($reason).")
    sendEnd(winner)
    sendEnd(loser)
    log.info { "PvP duel ended: winner=${winner.charId}, loser=${loser.charId}, reason=$reason" }
  }

  private fun sendEnd(view: BattleInstance) {
    val party = characters.getCharacter(view.charId)?.pokemon ?: emptyList()
    emitter.sendBattleEnd(view, party)
  }

  private fun finishView(match: DuelMatch, view: BattleInstance) {
    if (!match.readyToLeave.add(view.charId)) return
    interests.leave(view.session, view.key)
    battles.remove(view.charId)
    matchesByCharacter.remove(view.charId, match)
    view.completion.complete(view.pendingResult ?: BattleResult.DISCONNECTED)
  }
}
