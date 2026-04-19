package me.znotchill.lime.events

import me.znotchill.lime.ConnectionState
import me.znotchill.lime.commands.CommandManager
import me.znotchill.lime.commands.SuggestionType
import me.znotchill.lime.commands.command
import me.znotchill.lime.packets.registry.clientbound.play.CommandsPacket
import me.znotchill.lime.packets.registry.serverbound.configuration.FinishConfigurationPacket
import me.znotchill.lime.packets.registry.serverbound.login.LoginStartPacket
import me.znotchill.lime.packets.registry.serverbound.play.ChatPacket
import me.znotchill.lime.packets.registry.serverbound.play.CommandPacket

object DefaultEvents {

    private fun registerCommands() {
        CommandManager.register(command("servers") {
            executes { ctx ->
                ctx.player.send("Available servers: lobby, survival, creative")
            }
            argument("serverName") {
                executes { ctx ->
                    val server = ctx.arg(0)
                    ctx.player.send("Connecting to $server...")
                }
                suggests(SuggestionType.ASK_SERVER) { sctx ->
                    listOf("lobby", "survival", "creative")
                        .filter { it.startsWith(sctx.input, ignoreCase = true) }
                }
            }
        })
    }

    fun register() {
        registerCommands()

        PacketEventManager.register<LoginStartPacket> { event ->
            event.player.username = event.packet.name
            event.player.uuid = event.packet.uuid
        }

        PacketEventManager.register<FinishConfigurationPacket> { event ->
            event.player.state = ConnectionState.PLAY
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
    }
}