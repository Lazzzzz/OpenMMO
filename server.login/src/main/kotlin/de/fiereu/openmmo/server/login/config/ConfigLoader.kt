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
    return LoginServerConfig(
        host = config.getString("server.host"),
        port = config.getInt("server.port"),
        checksumSize = config.getInt("server.checksumSize"),
        rootKeyResource = config.getString("server.rootKeyResource"),
        rootKey = config.stringOrNull("server.rootKey"),
        rootKeyFile = config.stringOrNull("server.rootKeyFile"),
        sessionSecret = secret.toByteArray(Charsets.UTF_8),
        rememberMeMaxAge = rememberMeMaxAge,
        presence =
            PresenceConfig(
                enabled = config.getBoolean("presence.enabled"),
                baseUrl = config.getString("presence.baseUrl"),
                secret = config.getString("presence.secret"),
                timeout = config.getDuration("presence.timeout"),
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
