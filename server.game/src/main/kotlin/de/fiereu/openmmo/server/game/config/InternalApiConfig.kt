package de.fiereu.openmmo.server.game.config

data class InternalApiConfig(
    val enabled: Boolean = true,
    val host: String = "127.0.0.1",
    val port: Int = 7778,
    val secret: String = "",
    val gameServerId: Int = 0,
)
