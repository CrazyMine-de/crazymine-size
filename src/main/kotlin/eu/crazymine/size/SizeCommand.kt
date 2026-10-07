package eu.crazymine.size

import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import java.util.Locale

class SizeCommand(
    private val plugin: SizePlugin,
    private val manager: SizeManager,
    private val gui: SizeGui
) : CommandExecutor, TabCompleter {

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!manager.enabled) {
            manager.send(sender, "disabled")
            return true
        }

        if (args.isEmpty()) {
            if (sender is Player) {
                gui.open(sender)
            } else {
                sendHelp(sender)
            }
            return true
        }

        when (args[0].lowercase()) {
            "gui", "menu" -> {
                if (sender is Player) {
                    gui.open(sender)
                } else {
                    sender.sendMessage("Nur für Spieler im Spiel.")
                }
                return true
            }

            "reload" -> {
                if (!sender.hasPermission("crazymine.size.admin") && !sender.isOp) {
                    manager.send(sender, "no-permission")
                    return true
                }
                manager.load()
                manager.send(sender, "reloaded")
                return true
            }

            "server", "global" -> {
                if (!sender.hasPermission("crazymine.size.server") && !sender.hasPermission("crazymine.size.admin") && !sender.isOp) {
                    manager.send(sender, "no-permission")
                    return true
                }
                if (args.size < 2) {
                    sender.sendMessage(manager.mm.deserialize("<red>Verwendung: /size server <klein|normal|gross|reset|wert></red>"))
                    return true
                }
                val targetScale = parseScale(sender, args[1]) ?: return true
                manager.setServerScale(targetScale)
                manager.send(sender, "server-size-set", "size" to String.format(Locale.US, "%.2f", targetScale))
                return true
            }

            "reset" -> {
                if (sender is Player) {
                    manager.resetScale(sender)
                    manager.send(sender, "size-reset")
                } else {
                    sender.sendMessage("Nur für Spieler.")
                }
                return true
            }
        }

        // Check if args[0] is an online player (setting someone else's size)
        val targetPlayer = plugin.server.getPlayer(args[0])
        if (targetPlayer != null && args.size >= 2) {
            if (!sender.hasPermission("crazymine.size.others") && !sender.hasPermission("crazymine.size.admin") && !sender.isOp) {
                manager.send(sender, "no-permission")
                return true
            }
            val targetScale = parseScale(sender, args[1]) ?: return true
            manager.setScale(targetPlayer, targetScale)
            manager.send(sender, "size-target", "player" to targetPlayer.name, "size" to String.format(Locale.US, "%.2f", targetScale))
            manager.send(targetPlayer, "size-set", "size" to String.format(Locale.US, "%.2f", targetScale))
            return true
        }

        // If not targeting another player, apply to sender
        if (sender !is Player) {
            manager.send(sender, "player-not-found")
            return true
        }

        val targetScale = parseScale(sender, args[0]) ?: return true
        manager.setScale(sender, targetScale)
        manager.send(sender, "size-set", "size" to String.format(Locale.US, "%.2f", targetScale))
        return true
    }

    private fun parseScale(sender: CommandSender, input: String): Double? {
        val lower = input.lowercase()

        if (lower == "reset") return 1.0

        // Check presets first
        val preset = manager.presets[lower]
        if (preset != null) {
            if (sender is Player && !manager.canUsePreset(sender, preset)) {
                manager.send(sender, "no-permission")
                return null
            }
            return preset.scale
        }

        // Try parsing float/double number
        val num = lower.toDoubleOrNull()
        if (num == null) {
            sender.sendMessage(manager.mm.deserialize("<red>Ungültige Eingabe: '$input'. Nutze klein, normal, etwasgross, gross oder eine Zahl (z.B. 0.5, 1.2).</red>"))
            return null
        }

        // Number requires custom permission unless sender is console or op
        if (sender is Player) {
            if (!sender.hasPermission("crazymine.size.custom") && !sender.hasPermission("crazymine.size.admin") && !sender.isOp) {
                manager.send(sender, "no-permission")
                return null
            }

            val bypass = sender.hasPermission("crazymine.size.bypass") || sender.isOp
            if (!bypass && (num < manager.minScale || num > manager.maxScale)) {
                manager.send(
                    sender,
                    "invalid-size",
                    "min" to String.format(Locale.US, "%.2f", manager.minScale),
                    "max" to String.format(Locale.US, "%.2f", manager.maxScale)
                )
                return null
            }
        }

        return num
    }

    private fun sendHelp(sender: CommandSender) {
        sender.sendMessage(manager.mm.deserialize("<gradient:#ff7a18:#ffd166><bold>CrazyMine Size Hilfe</bold></gradient>"))
        sender.sendMessage(manager.mm.deserialize("<yellow>/size</yellow> <gray>- Öffnet das Größen-Menü</gray>"))
        sender.sendMessage(manager.mm.deserialize("<yellow>/size <klein|normal|etwasgross|gross|reset></yellow> <gray>- Setzt die Größe</gray>"))
        if (sender.hasPermission("crazymine.size.others") || sender.isOp) {
            sender.sendMessage(manager.mm.deserialize("<yellow>/size <spieler> <größe></yellow> <gray>- Ändert die Größe eines Spielers</gray>"))
        }
        if (sender.hasPermission("crazymine.size.server") || sender.isOp) {
            sender.sendMessage(manager.mm.deserialize("<yellow>/size server <größe></yellow> <gray>- Ändert die Größe für den gesamten Server</gray>"))
        }
        if (sender.hasPermission("crazymine.size.admin") || sender.isOp) {
            sender.sendMessage(manager.mm.deserialize("<yellow>/size reload</yellow> <gray>- Lädt die Konfiguration neu</gray>"))
        }
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String> {
        if (!manager.enabled) return emptyList()

        if (args.size == 1) {
            val list = mutableListOf("gui", "reset")
            list.addAll(manager.presets.keys)
            if (sender.hasPermission("crazymine.size.server") || sender.isOp) list.add("server")
            if (sender.hasPermission("crazymine.size.admin") || sender.isOp) list.add("reload")
            if (sender.hasPermission("crazymine.size.others") || sender.isOp) {
                list.addAll(plugin.server.onlinePlayers.map { it.name })
            }
            return list.filter { it.startsWith(args[0], ignoreCase = true) }
        }

        if (args.size == 2) {
            val first = args[0].lowercase()
            if (first == "server" || plugin.server.getPlayer(args[0]) != null) {
                val list = mutableListOf("reset", "0.5", "1.0", "1.3", "1.6")
                list.addAll(manager.presets.keys)
                return list.filter { it.startsWith(args[1], ignoreCase = true) }
            }
        }

        return emptyList()
    }
}
