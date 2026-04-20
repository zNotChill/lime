package me.znotchill.lime.events

import me.znotchill.lime.MinecraftVersion
import me.znotchill.lime.commands.CommandManager
import me.znotchill.lime.commands.SuggestionType
import me.znotchill.lime.commands.command
import me.znotchill.lime.data.config.ConfigManager
import me.znotchill.lime.packets.payloads.StatusDescription
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

object DefaultEvents {

    private fun registerCommands() {
        CommandManager.register(command("servers") {
            executes { ctx ->
                ctx.player.send("Available servers: ${ServerManager.list().map { it.name }.joinToString { "," }}")
            }
            argument("serverName") {
                executes { ctx ->
                    val server = ctx.arg(0)
                    ctx.player.switchServer(server)
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
                        description = StatusDescription(
                            text = ConfigManager.server.status.motd
                        )
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
            val root = command.split(" ").first()

            if (CommandManager.exists(root)) {
                event.cancel()
                val handled = CommandManager.dispatch(event.player, "/$command")
                if (!handled) event.player.send("Unknown command.")
            }

            println("${event.player.username} issued: /$command")
        }

        PacketEventManager.register<ChatPacket> { event ->
            val msg = event.packet.message
            println("${event.player.username}: $msg")
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