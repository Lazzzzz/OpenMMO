package de.fiereu.openmmo.server.login.catalog

import com.github.maltalex.ineter.base.IPAddress
import com.github.maltalex.ineter.base.IPv4Address
import com.github.maltalex.ineter.base.IPv6Address
import de.fiereu.openmmo.net.login.packets.GameServer
import de.fiereu.openmmo.net.login.packets.GameServerNode
import de.fiereu.openmmo.server.login.config.LoginServerConfig
import javax.inject.Inject
import javax.inject.Singleton

data class GameServerEntry(
    val server: GameServer,
    val node: GameServerNode,
    val localAddress: IPAddress,
    val localHostname: String,
)

@Singleton
class GameServerCatalog @Inject constructor(config: LoginServerConfig) {
  private val nodeConfig = config.gameNode
  private val entries: List<GameServerEntry> =
      listOf(
          GameServerEntry(
              server =
                  GameServer(
                      id = 0x00u,
                      name = "OpenMMO",
                      currentPlayers = 0u,
                      maxPlayers = nodeConfig.maxPlayers.toUShort(),
                      joinable = true,
                  ),
              node =
                  GameServerNode(
                      iPv4Address = IPv4Address.of(nodeConfig.iPv4Address),
                      iPv6Address = IPv6Address.of(nodeConfig.iPv6Address),
                      port = nodeConfig.port.toUShort(),
                      weight = 0x01u,
                  ),
              localAddress = IPAddress.of(nodeConfig.iPv4Address),
              localHostname = nodeConfig.hostname,
          ),
      )

  fun list(): List<GameServer> = entries.map { it.server }

  fun find(id: UByte): GameServerEntry? = entries.firstOrNull { it.server.id == id }
}
