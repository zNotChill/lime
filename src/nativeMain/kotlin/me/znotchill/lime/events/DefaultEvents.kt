package me.znotchill.lime.events

import kotlinx.serialization.json.Json
import me.znotchill.lime.MinecraftVersion
import me.znotchill.lime.ServerColors
import me.znotchill.lime.commands.CommandManager
import me.znotchill.lime.commands.SuggestionType
import me.znotchill.lime.commands.command
import me.znotchill.lime.components.component
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.log.Loggable
import me.znotchill.lime.packets.payloads.StatusPayload
import me.znotchill.lime.packets.payloads.StatusPlayers
import me.znotchill.lime.packets.payloads.StatusVersion
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
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
import me.znotchill.lime.servers.ServerManager

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

                    ctx.player.switcher.switchServer(server) { e ->
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

        PacketEventManager.register<StatusRequestPacket> { event ->
            val motd = MiniMessage.parse(
                ConfigManager.server.status.motd
            )
            event.player.clientConnection.sendPacket(
                StatusResponsePacket(
                    StatusPayload(
                        version = StatusVersion(
                            name = "Hello",
                            protocol = MinecraftVersion.`1_21_11`
                        ),
                        players = StatusPlayers(
                            max = 50000,
                            online = 1000
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
        }

        PacketEventManager.register<CommandsPacket> { event ->
            event.cancel()
            event.player.clientConnection.sendPacket(
                CommandManager.buildPacket(existingNodes = event.packet.nodes)
            )
        }

        PacketEventManager.register<CommandPacket> { event ->
            val command = event.packet.command
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