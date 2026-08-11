package de.fiereu.openmmo.server.game.handler

import de.fiereu.openmmo.net.game.packets.AddFriendPacket
import de.fiereu.openmmo.net.game.packets.BlockPlayerPacket
import de.fiereu.openmmo.net.game.packets.ChatMessagePacket
import de.fiereu.openmmo.net.game.packets.ChatMessageSendPacket
import de.fiereu.openmmo.net.game.packets.DeleteCharacterPacket
import de.fiereu.openmmo.net.game.packets.DuelChallengePacket
import de.fiereu.openmmo.net.game.packets.InGameChallengeResponsePacket
import de.fiereu.openmmo.net.game.packets.JoinPacket
import de.fiereu.openmmo.net.game.packets.KeepAlivePacket
import de.fiereu.openmmo.net.game.packets.MovementPacket
import de.fiereu.openmmo.net.game.packets.RemoveFriendPacket
import de.fiereu.openmmo.net.game.packets.battle.BattleActionPacket
import de.fiereu.openmmo.server.game.config.GameServerConfig
import de.fiereu.openmmo.server.game.di.DaggerGameServerComponent
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GameAppHandlerTest :
    FunSpec({
      test("dagger graph builds and handler registers expected packet types") {
        val config =
            GameServerConfig(
                host = "127.0.0.1",
                port = 0,
                checksumSize = 2,
                adminHost = "127.0.0.1",
                adminPort = 0,
                adminToken = "test-admin-token-with-at-least-32-chars",
                rootKeyResource = "game.private.pem",
                sessionSecret = "test-secret".toByteArray(),
            )
        val component = DaggerGameServerComponent.factory().create(config)
        val handler = component.handlerProvider().get()
        handler.isRegistered(JoinPacket::class) shouldBe true
        handler.isRegistered(MovementPacket::class) shouldBe true
        handler.isRegistered(ChatMessagePacket::class) shouldBe true
        handler.isRegistered(ChatMessageSendPacket::class) shouldBe true
        handler.isRegistered(KeepAlivePacket::class) shouldBe true
        handler.isRegistered(AddFriendPacket::class) shouldBe true
        handler.isRegistered(RemoveFriendPacket::class) shouldBe true
        handler.isRegistered(BlockPlayerPacket::class) shouldBe true
        handler.isRegistered(DeleteCharacterPacket::class) shouldBe true
        handler.isRegistered(DuelChallengePacket::class) shouldBe true
        handler.isRegistered(InGameChallengeResponsePacket::class) shouldBe true
        handler.isRegistered(BattleActionPacket::class) shouldBe true
      }
    })
