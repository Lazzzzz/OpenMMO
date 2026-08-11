package de.fiereu.openmmo.net.game.packets

import de.fiereu.bytecodec.Codec
import de.fiereu.bytecodec.CodecScope
import de.fiereu.bytecodec.PacketCodec
import de.fiereu.bytecodec.ReadBuffer
import de.fiereu.bytecodec.WriteBuffer

/** Direction-specific client payload for opcode 0x09. */
data class ClientOpcode09Packet(val payload: ByteArray) {
  override fun equals(other: Any?): Boolean =
      other is ClientOpcode09Packet && payload.contentEquals(other.payload)

  override fun hashCode(): Int = payload.contentHashCode()
}

private val ClientOpcode09Payload =
    object : Codec<ByteArray> {
      override fun read(buf: ReadBuffer): ByteArray =
          ByteArray(buf.remaining()).also { if (it.isNotEmpty()) buf.readBytes(it) }

      override fun write(buf: WriteBuffer, value: ByteArray) {
        if (value.isNotEmpty()) buf.writeBytes(value)
      }
    }

object ClientOpcode09PacketCodec : PacketCodec<ClientOpcode09Packet>() {
  override fun CodecScope<ClientOpcode09Packet>.body() =
      ClientOpcode09Packet(field(ClientOpcode09Payload, ClientOpcode09Packet::payload))
}
