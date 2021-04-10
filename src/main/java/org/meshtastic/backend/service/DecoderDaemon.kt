package org.meshtastic.backend.service

import com.geeksville.mesh.MQTTProtos
import com.geeksville.mesh.MeshProtos
import mu.KotlinLogging
import org.meshtastic.backend.model.ChannelDB
import org.springframework.stereotype.Component
import java.io.Closeable
import javax.annotation.PostConstruct

/**
 * Connects via MQTT to our broker and watches for encrypted messages from devices. If it has suitable channel keys it
 * decodes them and republishes in cleartext
 */
@Component
class DecoderDaemon(
    private val channels: ChannelDB,
    private val configuration: Configuration
) : Closeable {
    private val logger = KotlinLogging.logger {}

    private val filter = "${configuration.cryptRoot}#"

    private val mqtt = MQTTClient()

    @PostConstruct
    fun initialize() {
        logger.info("Creating decrypt daemon listening to $filter")
        mqtt.subscribe(filter) { topic, msg ->
            try {
                val e = MQTTProtos.ServiceEnvelope.parseFrom(msg.payload)

                if (e.hasPacket() && e.packet.payloadVariantCase == MeshProtos.MeshPacket.PayloadVariantCase.ENCRYPTED) {
                    val ch = channels.getById(e.channelId)
                    if (ch == null)
                        logger.warn("Topic $topic, channel not found")
                    else {
                        val psk = ch.psk.toByteArray()
                        val p = e.packet

                        if (psk == null)
                            logger.warn("Topic $topic, No PSK found, skipping")
                        else {
                            val decrypted = decrypt(psk, p.from, p.id, p.encrypted.toByteArray())
                            val decoded = MeshProtos.Data.parseFrom(decrypted)

                            // Swap out the encrypted version for the decoded version
                            val decodedPacket = p.toBuilder().clearEncrypted().setDecoded(decoded).build()
                            val decodedEnvelope = e.toBuilder().setPacket(decodedPacket).build()

                            // Show nodenums as standard nodeid strings
                            val nodeId = "!%08x".format(e.packet.from)
                            val cleartextTopic =
                                "${configuration.cleartextRoot}${e.channelId}/${nodeId}/${decoded.portnumValue}"
                            mqtt.publish(
                                cleartextTopic,
                                decodedEnvelope.toByteArray()
                            )

                            logger.debug("Republished cleartext to $cleartextTopic")
                        }

                    }
                }
            } catch (ex: Exception) {
                // Probably bad PSK
                logger.error("Topic $topic, ignored due to $ex")
            }
        }
    }

    override fun close() {
        mqtt.unsubscribe(filter)
        mqtt.disconnect()
        mqtt.close()
    }
}