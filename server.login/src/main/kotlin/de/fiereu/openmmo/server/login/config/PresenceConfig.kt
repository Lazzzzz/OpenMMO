package de.fiereu.openmmo.server.login.config

import java.time.Duration

data class PresenceConfig(
    val enabled: Boolean = true,
    val baseUrl: String = "http://127.0.0.1:7778",
    val secret: String = "",
    val timeout: Duration = Duration.ofSeconds(2),
)
