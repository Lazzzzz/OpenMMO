package de.fiereu.openmmo.server.game.services

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class WorldSeasonTest :
    FunSpec({
      test("rotates seasons every month in the PokeMMO order") {
        (1..12).map(::seasonForMonth) shouldBe listOf<Short>(0, 1, 2, 3, 0, 1, 2, 3, 0, 1, 2, 3)
      }

      test("rejects invalid calendar months") {
        shouldThrow<IllegalArgumentException> { seasonForMonth(0) }
        shouldThrow<IllegalArgumentException> { seasonForMonth(13) }
      }
    })
