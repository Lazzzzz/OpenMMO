package de.fiereu.openmmo.patcher

import java.io.StringReader
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.encoding.Base64
import org.bouncycastle.util.io.pem.PemReader

private const val POKEMMO_ANDROID_PUBKEY_GAME =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEtqx2myJz3ftlYWgd7cbNqf2t208itQMY7ouPNBDpQetbi7eXbEDxDDZy4Q9fMnI6mF5/D0qMdRd40SRXf0OS7Q=="
private const val POKEMMO_ANDROID_PUBKEY_CHAT =
    "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAEh4Vqgnd+8Fqebu0H40v+FgwhE6RwgAYxJMihb8mJmcHDy8r/rPz3kLHH1oabyKIRUa5Y2cK0TsxZky+mp7DKWA=="

private val POKEMMO_ANDROID_PUBKEYS_FEED =
    listOf(
        "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyfYQx1kSfIVGdGzcHmVVP7cbyLsMXGdLhwMnx2AD1MYgU170iFN5gHT+U248rH10L6D1UMlZK1LfCsbPkdQOir3C+8Do212NONyNm/7+ZGeIwbpy+jxEQH8Jfn4JYY7+Sn4qg249yW7DSY+XKvTOcphoXRNzSQp8u6IVj03mIw7zDA0SqMMFtnCXVP3NRmtjK1SuVVFLltFctz1Pp7f9uqgqnFlgD2l8/THnddTRM5IR6O9pbOXu7My0+Jli6+4zJgw5gQvgivYPCeess9gWRqpw66VTpMJERJYA6AIbVierAbjGmtRETRsHUOGAgo54G0oxtXXEaTWXF6n6mdgSE2Ra8q7P23stsSWU3mDNQjXO0XOhtAKQCZfvICxmsH3ed5hm8bEC5yga8z8m0vyZ71fWzP4Q3g6B+o6oDsMX1nWbV2GEHci/6nwFofgOJkLINaZfUTivAIRuxECVwjTTa7ruRNgFlA2ciGUIIke2Ev2cYzyBA4LLARky2FZiEM0VAgMBAAE=",
        "MIIBojANBgkqhkiG9w0BAQEFAAOCAY8AMIIBigKCAYEAyNb7iGEOL8/7hBkzvSm0edntPPO6/oaXlsl+1/OKukUifNnLlx+ApInkJyPy7PK6ds6R5C8liCvEEBD6G4/TRi8riG/8pIOCFAe/bjyBoXodzSJ2NRvArW6xVxax6Dpl5/tpsIBOSQDYH/BUXVZZM+DbIAbJhgvxK3r1eth7vO+kj3VLBVzJTrYpzEUz+cW+SxukOsDzHWxuUcogCEC3tzY0M3f/jMv8yrO/xVlWTzn7orrZtg0JfBMOs59NLLPKOlfB2Dtxg517Pbg2knpnNliKYE8vXwcYEJDb1jxgicqV4sYTNwu7MPSBEnYsPpBqbwbDIRI4Nrigqr+9D6Q7LPZjbI8JUZIJ1+pyYtXkSNOnnpbBPyT5WCMrviNDgocct+/PpzlYnwGRjGusqbR+6OTY9U2rptNETyK+0kmmNqxWCxAH93MqgiJk+WonkIaA5zkRlhpstlhIBwrQiYZWY6XgOMRvrdUom8FYLYKRIwRvoTRQG92gI8+WCL8+sjCpAgMBAAE=",
    )

private val POKEMMO_ANDROID_FEED_URLS =
    listOf(
            "dl.pokemmo.com",
            "dl.pokemmo.eu",
            "dl.pokemmo.download",
            "files.pokemmo.com",
            "files.pokemmo.eu")
        .flatMap { host ->
          listOf(
              "https://$host/live/current/feeds/main_feed.txt",
              "https://$host/live/current/feeds/main_feed.sig256",
          )
        }

private object PublicClientPatches {
  private fun fixedLengthPatches(origin: String): List<ClientPatcher.Patch> =
      listOf(
          ClientPatcher.Patch(
              "GamePubKeyPatch", POKEMMO_ANDROID_PUBKEY_GAME, publicKey("/game.public.pem")),
          ClientPatcher.Patch(
              "ChatPubKeyPatch", POKEMMO_ANDROID_PUBKEY_CHAT, publicKey("/chat.public.pem")),
      ) +
          POKEMMO_ANDROID_PUBKEYS_FEED.mapIndexed { index, key ->
            ClientPatcher.Patch("FeedPubKeyPatch$index", key, publicKey("/feed.public.pem"))
          } +
          POKEMMO_ANDROID_FEED_URLS.mapIndexed { index, url ->
            ClientPatcher.Patch("FeedUrlPatch$index", url, feedUrl(origin, url))
          }

  private fun feedUrl(origin: String, original: String): String {
    val fileName = original.substringAfterLast('/')
    val fixed = "$origin/$fileName"
    require(fixed.length <= original.length) {
      "Feed origin $origin is too long to replace $original without rebuilding the DEX"
    }
    return "$origin/${"x".repeat(original.length - fixed.length)}$fileName"
  }

  private fun publicKey(path: String): String =
      Base64.Default.encode(
          PublicClientPatches::class.java.getResourceAsStream(path).use { stream ->
            requireNotNull(stream) { "Key not found at path: $path" }
            PemReader(StringReader(stream.readBytes().decodeToString())).readPemObject().content
          })

  internal fun patchStandalone(
      bytes: ByteArray,
      feedOrigin: String
  ): Map<ClientPatcher.Patch, Int> =
      ClientPatcher(fixedLengthPatches(feedOrigin.removeSuffix("/"))).apply(bytes)

  internal fun smaliPatches(feedOrigin: String): List<TextPatch> {
    val origin = feedOrigin.removeSuffix("/")
    require(origin.startsWith("https://")) { "The Android feed origin must use HTTPS" }
    return listOf(
        TextPatch("GamePubKeyPatch", POKEMMO_ANDROID_PUBKEY_GAME, publicKey("/game.public.pem")),
        TextPatch("ChatPubKeyPatch", POKEMMO_ANDROID_PUBKEY_CHAT, publicKey("/chat.public.pem")),
    ) +
        POKEMMO_ANDROID_PUBKEYS_FEED.mapIndexed { index, key ->
          TextPatch("FeedPubKeyPatch$index", key, publicKey("/feed.public.pem"))
        } +
        POKEMMO_ANDROID_FEED_URLS.mapIndexed { index, url ->
          TextPatch("FeedUrlPatch$index", url, "$origin/${url.substringAfterLast('/')}")
        }
  }
}

internal data class TextPatch(val name: String, val original: String, val replacement: String)

internal fun replaceLiteral(text: String, patch: TextPatch): Pair<String, Int> {
  var count = 0
  var offset = 0
  while (true) {
    val match = text.indexOf(patch.original, offset)
    if (match < 0) break
    count++
    offset = match + patch.original.length
  }
  return text.replace(patch.original, patch.replacement) to count
}

/** Patches smali decoded by apktool, which then rebuilds a correctly sorted DEX string table. */
object AndroidSmaliPatcher {
  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size == 2) { "Usage: AndroidSmaliPatcher <apktool-directory> <feed-origin>" }
    val root = Path.of(args[0])
    require(Files.isDirectory(root)) { "Not an apktool directory: $root" }
    val patches = PublicClientPatches.smaliPatches(args[1])
    val counts = IntArray(patches.size)

    Files.walk(root).use { paths ->
      paths
          .filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".smali") }
          .forEach { path ->
            val original = Files.readString(path)
            var patched = original
            patches.forEachIndexed { index, patch ->
              val result = replaceLiteral(patched, patch)
              patched = result.first
              counts[index] += result.second
            }
            if (patched != original) Files.writeString(path, patched)
          }
    }

    patches.forEachIndexed { index, patch ->
      println("${patch.name}: replaced ${counts[index]} occurrences")
    }
    val missed = patches.indices.filter { counts[it] == 0 }.map { patches[it].name }
    check(missed.isEmpty()) { "No match for ${missed.joinToString()}. The Android client changed." }
  }
}

/** Patches a desktop native image to use the public signed feed without a local companion. */
object StandaloneClientPatcher {
  @JvmStatic
  fun main(args: Array<String>) {
    require(args.size == 3) { "Usage: StandaloneClientPatcher <input> <output> <feed-origin>" }
    val input = Path.of(args[0])
    val output = Path.of(args[1])
    val bytes = Files.readAllBytes(input)
    val results = PublicClientPatches.patchStandalone(bytes, args[2])
    results.forEach { (patch, count) -> println("${patch.name}: replaced $count occurrences") }
    val missed = results.filterValues { it == 0 }.keys
    check(missed.isEmpty()) {
      "No match for ${missed.joinToString { it.name }}. The desktop client probably changed."
    }
    output.parent?.let(Files::createDirectories)
    Files.write(output, bytes)
    println("Wrote standalone patched client to $output")
  }
}
