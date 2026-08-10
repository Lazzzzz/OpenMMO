package de.fiereu.openmmo.server.login.config

data class GameNodeConfig(
    val iPv4Address: String = "127.0.0.1",
    val iPv6Address: String = "::1",
    val port: Int = 7777,
    val hostname: String = "localhost",
    val maxPlayers: Int = 100,
)
