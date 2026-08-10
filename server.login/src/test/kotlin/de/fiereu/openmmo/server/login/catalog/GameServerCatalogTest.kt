package de.fiereu.openmmo.server.login.catalog

import de.fiereu.openmmo.server.login.config.GameNodeConfig
import de.fiereu.openmmo.server.login.config.LoginServerConfig
import io.kotest.core.spec.style.FunSpec
import io.kotest.matchers.shouldBe

class GameServerCatalogTest :
    FunSpec({
      test("advertises the configured public game server") {
        val config =
            LoginServerConfig(
                host = "0.0.0.0",
                port = 2106,
                checksumSize = 16,
                rootKeyResource = "game.private.pem",
                sessionSecret = "test-secret".toByteArray(),
                gameNode =
                    GameNodeConfig(
                        iPv4Address = "203.0.113.10",
                        iPv6Address = "2001:db8::10",
                        port = 17777,
                        hostname = "game.example.test",
                        maxPlayers = 42,
                    ),
            )

        val catalog = GameServerCatalog(config)
        catalog.list().single().maxPlayers shouldBe 42u
        val entry = catalog.find(0u)!!
        entry.node.iPv4Address.toString() shouldBe "203.0.113.10"
        entry.node.iPv6Address.toString() shouldBe "2001:db8:0:0:0:0:0:10"
        entry.node.port shouldBe 17777u
        entry.localAddress.toString() shouldBe "203.0.113.10"
        entry.localHostname shouldBe "game.example.test"
      }
    })
