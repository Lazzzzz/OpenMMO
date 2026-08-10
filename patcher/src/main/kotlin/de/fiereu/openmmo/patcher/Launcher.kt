package de.fiereu.openmmo.patcher

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import java.security.KeyFactory
import java.security.PrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import kotlin.io.encoding.Base64
import kotlin.system.exitProcess
import org.bouncycastle.util.io.pem.PemReader

private const val POKEMMO_PUBKEY_GAME =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtqx2myJz3ftlYWgd7cbNqf2t208itQMY7ouPNBDpQetbi7eXbEDxDDZy4Q9fMnI6mF5/D0qMdRd40SRXf0OS7Q=="
private const val POKEMMO_PUBKEY_CHAT =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEh4Vqgnd+8Fqebu0H40v+FgwhE6RwgAYxJMihb8mJmcHDy8r/rPz3kLHH1oabyKIRUa5Y2cK0TsxZky+mp7DKWA=="

/** The client accepts a feed signed by either of these, so both are replaced with ours. */
private val POKEMMO_PUBKEYS_FEED =
    listOf(
        "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyfYQx1kSfIVGdGzcHmVVP7cbyLsMXGdLhwMnx2AD1MYgU170iFN5gHT+U248rH10L6D1UMlZK1LfCsbPkdQOir3C+8Do212NONyNm/7+ZGeIwbpy+jxEQH8Jfn4JYY7+Sn4qg249yW7DSY+XKvTOcphoXRNzSQp8u6IVj03mIw7zDA0SqMMFtnCXVP3NRmtjK1SuVVFLltFctz1Pp7f9uqgqnFlgD2l8/THnddTRM5IR6O9pbOXu7My0+Jli6+4zJgw5gQvgivYPCeess9gWRqpw66VTpMJERJYA6AIbVierAbjGmtRETRsHUOGAgo54G0oxtXXEaTWXF6n6mdgSE2Ra8q7P23stsSWU3mDNQjXO0XOhtAKQCZfvICxmsH3ed5hm8bEC5yga8z8m0vyZ71fWzP4Q3g6B+o6oDsMX1nWbV2GEHci/6nwFofgOJkLINaZfUTivAIRuxECVwjTTa7ruRNgFlA2ciGUIIke2Ev2cYzyBA4LLARky2FZiEM0VAgMBAAE=",
        "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyNb7iGEOL8/7hBkzvSm0edntPPO6/oaXlsl+1/OKukUifNnLlx+ApInkJyPy7PK6ds6R5C8liCvEEBD6G4/TRi8riG/8pIOCFAe/bjyBoXodzSJ2NRvArW6xVxax6Dpl5/tpsIBOSQDYH/BUXVZZM+DbIAbJhgvxK3r1eth7vO+kj3VLBVzJTrYpzEUz+cW+SxukOsDzHWxuUcogCEC3tzY0M3f/jMv8yrO/xVlWTzn7orrZtg0JfBMOs59NLLPKOlfB2Dtxg517Pbg2knpnNliKYE8vXwcYEJDb1jxgicqV4sYTNwu7MPSBEnYsPpBqbwbDIRI4Nrigqr+9D6Q7LPZjbI8JUZIJ1+pyYtXkSNOnnpbBPyT5WCMrviNDgocct+/PpzlYnwGRjGusqbR+6OTY9U2rptNETyK+0kmmNqxWCxAH93MqgiJk+WonkIaA5zkRlhpstlhIBwrQiYZWY6XgOMRvrdUom8FYLYKRIwRvoTRQG92gI8+WCL8+sjCpAgMBAAE=",
    )

private const val POKEMMO_LOGINSERVER = "loginserver.pokemmo.com"

/** Mirrors the client tries in turn until one serves a feed. */
private val POKEMMO_FEED_HOSTS =
    listOf(
        "dl.pokemmo.com",
        "dl.pokemmo.eu",
        "dl.pokemmo.download",
        "files.pokemmo.com",
        "files.pokemmo.eu",
    )

private const val EXECUTABLE_PROPERTY = "openmmo.executable"
private const val WORKING_DIR_PROPERTY = "openmmo.workingDir"
private const val OUTPUT_PROPERTY = "openmmo.output"
private const val LOGIN_HOST_PROPERTY = "openmmo.loginHost"

private const val TRUSTSTORE_NAME = "openmmo-truststore.p12"

object Launcher {

  fun log(message: () -> String) {
    println("[OpenMMO Patcher] ${message()}")
  }

  @JvmStatic
  fun main(args: Array<String>) {
    val executable =
        Path.of(
            System.getProperty(EXECUTABLE_PROPERTY)
                ?: error("Missing -D$EXECUTABLE_PROPERTY=<path to PokeMMO.exe>"))
    require(Files.isRegularFile(executable)) { "Not a file: $executable" }

    val workingDir =
        System.getProperty(WORKING_DIR_PROPERTY)?.let(Path::of)
            ?: executable.parent
            ?: error("Missing -D$WORKING_DIR_PROPERTY=<path to the PokeMMO install directory>")

    val output =
        System.getProperty(OUTPUT_PROPERTY)?.let(Path::of)
            ?: Path.of(System.getProperty("java.io.tmpdir"), "PokeMMO-openmmo.exe")

    val keyStore = FeedTls.keyStore()
    val feed = FeedServer(loadPrivateKey("/feed.private.pem"), keyStore)
    val revision = readRevision(workingDir)
    val loginHost = System.getProperty(LOGIN_HOST_PROPERTY) ?: LOOPBACK
    feed.publish(revision, loginHost)
    feed.start()
    log {
      "Serving the main feed on https://$LOOPBACK:${feed.port} for revision $revision, " +
          "login server $loginHost"
    }

    val patcher = ClientPatcher(patches(feed.port))

    log { "Reading $executable" }
    val bytes = Files.readAllBytes(executable)

    val results = patcher.apply(bytes)
    results.forEach { (patch, count) -> log { "${patch.name}: replaced $count occurrences" } }
    val missed = results.filterValues { it == 0 }.keys
    if (missed.isNotEmpty()) {
      error(
          "No match for ${missed.joinToString { it.name }}. " +
              "The client probably changed the patched strings.")
    }

    output.parent?.let(Files::createDirectories)
    Files.write(output, bytes)
    if (!output.toFile().setExecutable(true)) {
      log { "Could not mark $output as executable" }
    }
    signForMacOs(output)
    log { "Wrote patched client to $output" }

    val trustStore =
        FeedTls.writeTrustStore(
            feed.certificate(), workingDir, output.resolveSibling(TRUSTSTORE_NAME))

    log { "Launching $output in $workingDir" }
    val process =
        ProcessBuilder(
                listOf(
                    output.toString(),
                    "-Djavax.net.ssl.trustStore=$trustStore",
                    "-Djavax.net.ssl.trustStorePassword=${FeedTls.password.concatToString()}",
                    "-Djavax.net.ssl.trustStoreType=PKCS12",
                ) + args)
            .directory(workingDir.toFile())
            .inheritIO()
            .start()
    val status = process.waitFor()
    feed.stop()
    exitProcess(status)
  }

  /** A modified Mach-O keeps its old signature, so macOS kills it before main can start. */
  private fun signForMacOs(output: Path) {
    if (!System.getProperty("os.name").startsWith("Mac", ignoreCase = true)) return

    log { "Signing the patched client for macOS" }
    val status =
        ProcessBuilder("codesign", "--force", "--sign", "-", output.toString())
            .inheritIO()
            .start()
            .waitFor()
    check(status == 0) { "codesign failed with exit code $status" }
  }

  private fun patches(feedPort: Int): List<ClientPatcher.Patch> =
      listOf(
          ClientPatcher.Patch(
              "GamePubKeyPatch", POKEMMO_PUBKEY_GAME, loadPublicKey("/game.public.pem")),
          ClientPatcher.Patch(
              "ChatPubKeyPatch", POKEMMO_PUBKEY_CHAT, loadPublicKey("/chat.public.pem")),
          ClientPatcher.Patch(
              "LoginServerPatch",
              POKEMMO_LOGINSERVER,
              Loopback.literal(POKEMMO_LOGINSERVER.length)),
      ) +
          POKEMMO_PUBKEYS_FEED.mapIndexed { index, key ->
            ClientPatcher.Patch("FeedPubKeyPatch$index", key, loadPublicKey("/feed.public.pem"))
          } +
          POKEMMO_FEED_HOSTS.map { host ->
            val origin = "https://$host"
            ClientPatcher.Patch("FeedPatch$host", origin, Loopback.origin(feedPort, origin.length))
          }

  private fun readRevision(workingDir: Path): Long =
      runCatching { Files.readString(workingDir.resolve("revision.txt")).trim().toLong() }
          .getOrDefault(0)

  private fun loadPublicKey(path: String): String = Base64.Default.encode(readPem(path))

  private fun loadPrivateKey(path: String): PrivateKey =
      KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(readPem(path)))

  private fun readPem(path: String): ByteArray {
    val stream =
        Launcher::class.java.getResourceAsStream(path)
            ?: throw IllegalArgumentException("Key not found at path: $path")
    return stream.use {
      PemReader(StringReader(it.readBytes().decodeToString())).readPemObject().content
    }
  }
}
