package de.fiereu.openmmo.net.game

import de.fiereu.openmmo.common.enums.Arch
import de.fiereu.openmmo.common.enums.Bitness
import de.fiereu.openmmo.common.enums.Platform
import de.fiereu.openmmo.common.test.decodeBytes
import de.fiereu.openmmo.common.test.encodeToBytes
import de.fiereu.openmmo.net.game.packets.JoinPacket
import de.fiereu.openmmo.net.game.packets.JoinPacketCodec
import de.fiereu.openmmo.net.game.packets.NewAuthData
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class JoinPacketTest :
    FunSpec({
      fun packet(trailer: ByteArray) =
          JoinPacket(
              authData = NewAuthData(7, byteArrayOf(1, 2, 3)),
              mac = byteArrayOf(1, 2, 3, 4, 5, 6),
              clientRevision = 32763,
              installationRevision = 32763,
              currentChatLanguage = 0,
              chatLanguages = 1,
              matchmakingLanguages = 1,
              romMask = 1,
              roms = emptyList(),
              clientInfo = emptyMap(),
              platform = Platform.WINDOWS,
              arch = Arch.X86,
              bitness = Bitness._64,
              unk1 = byteArrayOf(9, 8),
              unk2 = trailer,
          )

      test("roundtrips a fresh join with the legacy 32-byte trailer") {
        val expected = packet(ByteArray(32) { it.toByte() })

        JoinPacketCodec.decodeBytes(JoinPacketCodec.encodeToBytes(expected)) shouldBe expected
      }

      test("decodes a current desktop reconnect without the legacy trailer") {
        val expected = packet(ByteArray(0))

        JoinPacketCodec.decodeBytes(JoinPacketCodec.encodeToBytes(expected)) shouldBe expected
      }
    })
