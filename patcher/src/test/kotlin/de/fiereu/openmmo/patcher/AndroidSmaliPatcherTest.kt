package de.fiereu.openmmo.patcher

import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class AndroidSmaliPatcherTest :
    FunSpec({
      test("replaces every literal even when the replacement length differs") {
        val patch = TextPatch("Feed", "old-url", "https://new.example/feed")

        val (text, count) = replaceLiteral("old-url then old-url", patch)

        text shouldBe "https://new.example/feed then https://new.example/feed"
        count shouldBe 2
      }
    })
