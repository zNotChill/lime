package me.znotchill.lime.events

import MiniMessage
import dev.whyoleg.cryptography.algorithms.RSA
import io.ktor.utils.io.core.*
import kotlinx.io.Buffer
import kotlinx.io.readByteArray
import kotlinx.serialization.json.Json
import me.znotchill.lime.LimeProxy
import me.znotchill.lime.MinecraftVersion
import me.znotchill.lime.ServerColors
import me.znotchill.lime.client.ConnectionState
import me.znotchill.lime.commands.CommandManager
import me.znotchill.lime.commands.SuggestionType
import me.znotchill.lime.commands.command
import me.znotchill.lime.components.component
import me.znotchill.lime.crypt.MojangCrypt
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.events.packets.PacketEventManager
import me.znotchill.lime.events.proxy.ProxyEventManager
import me.znotchill.lime.events.proxy.registry.*
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.payloads.StatusPayload
import me.znotchill.lime.packets.payloads.StatusPlayers
import me.znotchill.lime.packets.payloads.StatusVersion
import me.znotchill.lime.packets.registry.clientbound.login.EncryptionRequestPacket
import me.znotchill.lime.packets.registry.clientbound.login.LoginPluginRequestPacket
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
import me.znotchill.lime.packets.registry.clientbound.play.LoginPluginResponsePacket
import me.znotchill.lime.packets.registry.clientbound.play.Match
import me.znotchill.lime.packets.registry.clientbound.play.TabCompleteResponsePacket
import me.znotchill.lime.packets.registry.clientbound.status.PingResponsePacket
import me.znotchill.lime.packets.registry.clientbound.status.StatusResponsePacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket
import me.znotchill.lime.packets.registry.serverbound.play.CommandPacket
import me.znotchill.lime.packets.registry.serverbound.play.TabCompleteRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.PingRequestPacket
import me.znotchill.lime.packets.registry.serverbound.status.StatusRequestPacket
import me.znotchill.lime.packets.writeBoolean
import me.znotchill.lime.packets.writeMcString
import me.znotchill.lime.packets.writeUUID
import me.znotchill.lime.packets.writeVarInt
import me.znotchill.lime.players.PlayerManager
import me.znotchill.lime.servers.ServerManager
import kotlin.random.Random

object DefaultEvents : Loggable {
    override val loggerTag = "Default"

    private fun registerCommands() {
        CommandManager.register(command("server") {
            executes { ctx ->
                val currentServer = ctx.player.currentServer

                ctx.player.queue {
                    ctx.player.send(
                        component {
                            text("You are connected to") { color = ServerColors.primary }
                            space()
                            text(currentServer.name) { color = ServerColors.secondary }
                            text(".") { color = ServerColors.primary }
                        }
                    )
                    ctx.player.send(
                        component {
                            text("Available servers: ") { color = ServerColors.primary }
                            ServerManager.list().forEachIndexed { i, server ->
                                text(server.name) {
                                    color = if (server == currentServer) ServerColors.successLight
                                    else ServerColors.secondary
                                }
                                if (i < ServerManager.list().size - 1) {
                                    text(", ") { color = ServerColors.primary }
                                }
                            }
                        }
                    )
                }
            }
            argument("serverName") {
                executes { ctx ->
                    val currentServer = ctx.player.currentServer
                    val server = ctx.arg(0)

                    if (server.equals(currentServer.name, ignoreCase = true)) {
                        ctx.player.send(
                            component {
                                text("You are already connected to") { color = ServerColors.error }
                                space()
                                text(server) { color = ServerColors.errorLight }
                                text("!") { color = ServerColors.error }
                            }
                        )
                        return@executes
                    }

                    ctx.player.send(
                        component {
                            text("Connecting to") { color = ServerColors.success }
                            space()
                            text(server) { color = ServerColors.successLight }
                            text("...") { color = ServerColors.success }
                        }
                    )

                    ctx.player.switcher.switchServer(server) { e, s ->
                        ProxyEventManager.emit(
                            ctx.player,
                            PlayerServerSwitchFailedEvent(
                                newServer = s,
                                reason = e.message ?: "Unknown"
                            )
                        )
                        ctx.player.send(
                            component {
                                text("Failed to connect to") { color = ServerColors.error }
                                space()
                                text(server) { color = ServerColors.errorLight }
                                text(": ") { color = ServerColors.error }
                                text("${e.message ?: "Server is unreachable"}.") { color = ServerColors.errorMessage }
                            }
                        )
                    }
                }
                suggests(SuggestionType.ASK_SERVER) { sctx ->
                    ServerManager.list().map { it.name }
                        .filter { it.startsWith(sctx.input, ignoreCase = true) }
                }
            }
        })
    }

    fun register() {
        registerCommands()

        ProxyEventManager.register<PlayerJoinEvent>(EventPriority.HIGHEST) { event ->
            try {
                PlayerManager.add(event.player)
            } catch (_: Exception) {}
        }

        ProxyEventManager.register<PlayerQuitEvent>(EventPriority.HIGHEST) { event ->
            try {
                PlayerManager.remove(event.player)
            } catch (_: Exception) {}
        }

        PacketEventManager.register<StatusRequestPacket> { event ->
            val motd = MiniMessage.parse(
                ConfigManager.server.status.motd
            )
            event.player.clientConnection.sendPacket(
                StatusResponsePacket(
                    StatusPayload(
                        version = StatusVersion(
                            name = "Lime",
                            protocol = MinecraftVersion.fromProtocol(event.player.protocol) ?: MinecraftVersion.`1_21_11`
                        ),
                        players = StatusPlayers(
                            max = 50000,
                            online = PlayerManager.count()
                        ),
                        description = Json.parseToJsonElement(motd.toJson())
                    )
                )
            )
        }

        PacketEventManager.register<PingRequestPacket> { event ->
            event.player.clientConnection.sendPacket(
                PingResponsePacket(event.packet.time)
            )
        }

        PacketEventManager.register<LoginStartPacket> { event ->
            event.player.username = event.packet.name
            event.player.uuid = event.packet.uuid

            val nonce = Random.nextBytes(4)

            event.player.loginStartPacket = event.rawPacket

            event.player.pipeline.awaitingEncryptionResponse = true
            event.player.pipeline.encryptionNonce = nonce
            event.player.clientConnection.sendPacket(
                EncryptionRequestPacket(
                    serverId = "",
                    publicKey = LimeProxy.keypair.publicKey.encodeToByteArray(RSA.PublicKey.Format.DER).toList(),
                    verifyToken = nonce.toList(),
                )
            )

            ProxyEventManager.emit(event.player, PlayerLoginEvent())
        }

        PacketEventManager.register<LoginPluginRequestPacket> { event ->
            println("server sent plugin req: ${event.packet.channel}")

            val player = event.player

            val forwardingVersion = event.packet.data.first().toInt()
            when (forwardingVersion) {
                // LEGACY
                0x01 -> {

                }
                // MODERN
                0x04 -> {val buffer = Buffer()

                    println("======")
                    println("player uuid: ${player.uuid}")
                    println("player username: ${player.username}")
                    println("player properties count: ${player.properties.size}")
                    player.properties.forEach { println("  property: ${it.name} = ${it.value.take(20)}...") }

                    buffer.writeVarInt(0x04)
                    println("wrote version: 0x04")

                    val ip = "127.0.0.1"
                    buffer.writeMcString(ip)
                    println("wrote ip: $ip")

                    buffer.writeUUID(player.uuid)
                    println("wrote uuid: ${player.uuid}")

                    buffer.writeMcString(player.username)
                    println("wrote username: ${player.username}")

                    buffer.writeVarInt(player.properties.size)
                    println("wrote properties varint: ${player.properties.size}")
                    for (prop in player.properties) {
                        buffer.writeMcString(prop.name)
                        buffer.writeMcString(prop.value)
                        buffer.writeBoolean(prop.signature != null)
                        prop.signature?.let { buffer.writeMcString(it) }
                        println("wrote property: ${prop.name}, has signature: ${prop.signature != null}")
                    }

                    val payload = buffer.readByteArray()
                    println("payload size: ${payload.size}")
                    println("payload hex: ${payload.joinToString(" ") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }}")

                    val signature = MojangCrypt.hmacSign("abcdef".toByteArray(), payload)
                    println("signature size: ${signature.size}")
                    println("signature hex: ${signature.joinToString(" ") { it.toInt().and(0xFF).toString(16).padStart(2, '0') }}")

                    val response = signature + payload
                    println("total response size: ${response.size}")
                    println("=================================")

                    event.player.remoteConnection?.protocol = event.player.protocol
                    event.player.remoteConnection?.sendPacket(
                        LoginPluginResponsePacket(event.packet.messageId, response),
                        ConnectionState.LOGIN
                    )
                }
            }

        }

        PacketEventManager.register<CommandsPacket> { event ->
            event.cancel()
            event.player.clientConnection.sendPacket(
                CommandManager.buildPacket(existingNodes = event.packet.nodes)
            )
        }

        PacketEventManager.register<CommandPacket> { event ->
            val command = event.packet.command

            ProxyEventManager.emit(
                event.player,
                PlayerCommandEvent(
                    command
                )
            )

            log.i("${event.player.username} issued: /$command")
            val root = command.split(" ").first()

            if (CommandManager.exists(root)) {
                event.cancel()
                val handled = CommandManager.dispatch(event.player, "/$command")
                if (!handled) event.player.send("Unknown command.")
            }

        }

        PacketEventManager.register<ChatPacket> { event ->
            val msg = event.packet.message
            log.i("${event.player.username}: $msg")

            ProxyEventManager.emit(
                event.player,
                PlayerChatEvent(
                    msg
                )
            )
        }

        PacketEventManager.register<TabCompleteRequestPacket> { event ->
            event.cancel()
            val suggestions = CommandManager.suggest(event.player, event.packet.text)
            val input = event.packet.text
            val lastSpace = input.lastIndexOf(' ')

            event.player.clientConnection.sendPacket(
                TabCompleteResponsePacket(
                    transactionId = event.packet.transactionId,
                    start = lastSpace + 1,
                    length = input.length - lastSpace - 1,
                    matches = suggestions.map { Match(it) }
                )
            )
        }
    }
}