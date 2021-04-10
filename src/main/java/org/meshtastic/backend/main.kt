package org.meshtastic.backend

import mu.KotlinLogging
import org.meshtastic.backend.model.ChannelDB
import org.meshtastic.backend.service.DecoderDaemon
import org.meshtastic.backend.service.MQTTClient
import org.meshtastic.backend.service.ToJSONDaemon
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class BackendApplication

fun startDecoder() {
    val logger = KotlinLogging.logger {}

    logger.info("Meshtastic Backend starting...")
    val channels = ChannelDB()
    val mqtt = MQTTClient()
    val decoder = DecoderDaemon(mqtt, channels)
    val tojson = ToJSONDaemon(mqtt)
}

fun main(args: Array<String>) {
    startDecoder()
    runApplication<BackendApplication>(*args)
}
