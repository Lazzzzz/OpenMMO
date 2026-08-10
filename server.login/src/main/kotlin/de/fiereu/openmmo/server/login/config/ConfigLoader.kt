package de.fiereu.openmmo.server.login.config

import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

private fun Config.stringOrNull(path: String): String? =
    if (hasPath(path)) getString(path) else null

object ConfigLoader {
  fun load(): LoginServerConfig {
    val config = ConfigFactory.load()
    val secret = config.getString("server.sessionSecret")
    require(secret.isNotEmpty()) { "server.sessionSecret must not be empty" }
    val rememberMeMaxAge = config.getDuration("server.rememberMeMaxAge")
    require(!rememberMeMaxAge.isNegative && !rememberMeMaxAge.isZero) {
      "server.rememberMeMaxAge must be positive"
    }
    val gameServerPort = config.getInt("gameServer.port")
    require(gameServerPort in 1..UShort.MAX_VALUE.toInt()) {
      "gameServer.port must be between 1 and ${UShort.MAX_VALUE}"
    }
    val gameServerMaxPlayers = config.getInt("gameServer.maxPlayers")
    require(gameServerMaxPlayers in 1..UShort.MAX_VALUE.toInt()) {
      "gameServer.maxPlayers must be between 1 and ${UShort.MAX_VALUE}"
    }
    return LoginServerConfig(
        host = config.getString("server.host"),
        port = config.getInt("server.port"),
        checksumSize = config.getInt("server.checksumSize"),
        rootKeyResource = config.getString("server.rootKeyResource"),
        rootKey = config.stringOrNull("server.rootKey"),
        rootKeyFile = config.stringOrNull("server.rootKeyFile"),
        sessionSecret = secret.toByteArray(Charsets.UTF_8),
        rememberMeMaxAge = rememberMeMaxAge,
        gameNode =
            GameNodeConfig(
                iPv4Address = config.getString("gameServer.ipv4Address"),
                iPv6Address = config.getString("gameServer.ipv6Address"),
                port = gameServerPort,
                hostname = config.getString("gameServer.hostname"),
                maxPlayers = gameServerMaxPlayers,
            ),
        db =
            DbConfig(
                host = config.getString("db.host"),
                port = config.getInt("db.port"),
                name = config.getString("db.name"),
                user = config.getString("db.user"),
                password = config.getString("db.password"),
                poolSize = config.getInt("db.poolSize"),
                seedDev = config.getBoolean("db.seedDev"),
            ),
    )
  }
}
