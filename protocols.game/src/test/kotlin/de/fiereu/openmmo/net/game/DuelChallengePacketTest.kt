package de.fiereu.openmmo.net.game

import de.fiereu.openmmo.common.test.decodeBytes
import de.fiereu.openmmo.common.test.encodeToBytes
import de.fiereu.openmmo.net.game.packets.DuelChallengePacket
import de.fiereu.openmmo.net.game.packets.DuelChallengePacketCodec
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class DuelChallengePacketTest :
    FunSpec({
      test("current client challenge extensions are consumed and round-trip") {
        for (size in listOf(17, 29)) {
          val extension = ByteArray(size) { (it + 1).toByte() }
          val packet =
              DuelChallengePacket(
                  targetPlayerName = "Opponent",
                  battleTypeId = 7,
                  timed = false,
                  battleFormat = 0,
                  typeRestriction = 0,
                  natureRestriction = 0,
                  allowedFormat = 0,
                  itemLevelCap = null,
                  natureCap = null,
                  items = null,
                  tier = null,
                  extension = extension,
              )

          val bytes = DuelChallengePacketCodec.encodeToBytes(packet)
          val decoded = DuelChallengePacketCodec.decodeBytes(bytes)

          decoded.targetPlayerName shouldBe "Opponent"
          decoded.battleTypeId shouldBe 7
          decoded.isDuel shouldBe true
          decoded.extension.toList() shouldBe extension.toList()
          DuelChallengePacketCodec.encodeToBytes(decoded).toList() shouldBe bytes.toList()
        }
      }

      test("current subtype zero is a duel when it carries extended rules") {
        val packet =
            DuelChallengePacket(
                "Opponent",
                0,
                false,
                0,
                0,
                0,
                0,
                null,
                null,
                null,
                null,
                byteArrayOf(1),
            )

        packet.isDuel shouldBe true
        packet.copy(extension = byteArrayOf()).isDuel shouldBe false
        packet.copy(extension = byteArrayOf(), items = emptyList()).isDuel shouldBe true
        packet.copy(extension = byteArrayOf(), battleFormat = 1).isDuel shouldBe true
      }

      test("the rules emitted by the production clients remain classified as a duel") {
        val capturedShape =
            DuelChallengePacket(
                targetPlayerName = "DEFFA",
                battleTypeId = 0,
                timed = false,
                battleFormat = 0,
                typeRestriction = 0,
                natureRestriction = 0,
                allowedFormat = 1,
                itemLevelCap = null,
                natureCap = 0,
                items = null,
                tier = 5,
            )

        capturedShape.isDuel shouldBe true
        capturedShape
            .copy(
                allowedFormat = 0,
                natureCap = null,
                tier = null,
            )
            .isDuel shouldBe false
      }
    })
