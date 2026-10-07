package eu.crazymine.size

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class SizeManager(private val plugin: SizePlugin) {
    val mm = MiniMessage.miniMessage()

    var enabled = true
        private set
    var requirePermission = true
        private set
    var minScale = 0.2
        private set
    var maxScale = 2.5
        private set
    var serverScale = 1.0
        private set
    var forceServerScale = false
        private set
    var persistPlayerSize = true
        private set

    val presets = LinkedHashMap<String, SizePreset>()
    private val messages = HashMap<String, String>()
    private val playerSizes = ConcurrentHashMap<UUID, Double>()
    private val storageLock = Any()

    private val storageFile: File by lazy {
        val sharedDir = File("/opt/nimbus/shared/sizes")
        if (sharedDir.exists() || sharedDir.mkdirs()) {
            File(sharedDir, "sizes.yml")
        } else {
            File(plugin.dataFolder, "sizes.yml")
        }
    }

    fun load() {
        plugin.reloadConfig()
        val config = plugin.config

        enabled = config.getBoolean("enabled", true)
        requirePermission = config.getBoolean("require-permission", true)
        minScale = config.getDouble("min-scale", 0.2)
        maxScale = config.getDouble("max-scale", 2.5)
        serverScale = config.getDouble("server-scale", 1.0)
        forceServerScale = config.getBoolean("force-server-scale", false)
        persistPlayerSize = config.getBoolean("persist-player-size", true)

        presets.clear()
        config.getConfigurationSection("presets")?.let { sec ->
            for (key in sec.getKeys(false)) {
                val scale = sec.getDouble("$key.scale", 1.0)
                val title = sec.getString("$key.title", key) ?: key
                val desc = sec.getString("$key.description", "") ?: ""
                val perm = sec.getString("$key.permission", "crazymine.size.$key") ?: "crazymine.size.$key"
                val matName = sec.getString("$key.material", "PLAYER_HEAD") ?: "PLAYER_HEAD"
                val mat = Material.matchMaterial(matName) ?: Material.PLAYER_HEAD
                val slot = sec.getInt("$key.slot", 13)
                presets[key.lowercase()] = SizePreset(key, scale, title, desc, perm, mat, slot)
            }
        }

        messages.clear()
        config.getConfigurationSection("messages")?.let { sec ->
            for (key in sec.getKeys(false)) {
                messages[key] = sec.getString(key, "") ?: ""
            }
        }

        loadSavedSizes()
    }

    private fun loadSavedSizes() {
        playerSizes.clear()
        if (!storageFile.exists()) return
        try {
            val yaml = YamlConfiguration.loadConfiguration(storageFile)
            for (key in yaml.getKeys(false)) {
                val uuid = runCatching { UUID.fromString(key) }.getOrNull() ?: continue
                val scale = yaml.getDouble(key, 1.0)
                playerSizes[uuid] = scale
            }
        } catch (e: Exception) {
            plugin.logger.warning("Konnte gespeicherte Spielergrößen nicht laden: ${e.message}")
        }
    }

    // sizes.yml wird von mehreren Servern geteilt: nur den geänderten Spieler in den aktuellen Dateistand mergen,
    // statt den eigenen (veralteten) Komplettstand zu schreiben.
    private fun saveStoredSize(uuid: UUID) {
        if (!persistPlayerSize) return
        val scale = playerSizes[uuid]
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            synchronized(storageLock) {
                try {
                    storageFile.parentFile?.mkdirs()
                    val yaml = YamlConfiguration.loadConfiguration(storageFile)
                    yaml.set(uuid.toString(), scale)
                    val tmp = File(storageFile.parentFile, "${storageFile.name}.${plugin.server.port}.tmp")
                    tmp.writeText(yaml.saveToString())
                    Files.move(tmp.toPath(), storageFile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
                } catch (e: Exception) {
                    plugin.logger.warning("Konnte Spielergröße nicht speichern: ${e.message}")
                }
            }
        })
    }

    private fun readStoredSize(uuid: UUID): Double? {
        if (!storageFile.exists()) return playerSizes[uuid]
        val scale = runCatching {
            val yaml = YamlConfiguration.loadConfiguration(storageFile)
            if (yaml.contains(uuid.toString())) yaml.getDouble(uuid.toString(), 1.0) else null
        }.getOrElse { return playerSizes[uuid] }
        if (scale == null) playerSizes.remove(uuid) else playerSizes[uuid] = scale
        return scale
    }

    fun getScale(player: Player): Double {
        return player.getAttribute(Attribute.SCALE)?.baseValue ?: 1.0
    }

    fun setScale(player: Player, scale: Double, playEffects: Boolean = true): Boolean {
        val attr = player.getAttribute(Attribute.SCALE) ?: return false
        val clamped = scale.coerceIn(0.0625, 16.0)
        attr.baseValue = clamped

        if (persistPlayerSize) {
            playerSizes[player.uniqueId] = clamped
            saveStoredSize(player.uniqueId)
        }

        if (playEffects) {
            val pitch = when {
                clamped < 0.8 -> 1.7f
                clamped > 1.2 -> 0.6f
                else -> 1.0f
            }
            player.playSound(player.location, Sound.ENTITY_PLAYER_LEVELUP, 0.6f, pitch)
            player.world.spawnParticle(Particle.ENCHANT, player.location.add(0.0, clamped * 0.8, 0.0), 25, 0.3, 0.5, 0.3, 0.15)
        }
        return true
    }

    fun resetScale(player: Player, playEffects: Boolean = true) {
        setScale(player, 1.0, playEffects)
        if (persistPlayerSize) {
            playerSizes.remove(player.uniqueId)
            saveStoredSize(player.uniqueId)
        }
    }

    fun setServerScale(scale: Double) {
        serverScale = scale
        plugin.config.set("server-scale", scale)
        plugin.saveConfig()

        for (player in plugin.server.onlinePlayers) {
            setScale(player, scale, playEffects = true)
        }
    }

    fun applyOnJoin(player: Player) {
        if (!enabled) return
        if (forceServerScale) {
            val attr = player.getAttribute(Attribute.SCALE)
            attr?.baseValue = serverScale
            return
        }

        val saved = if (persistPlayerSize) readStoredSize(player.uniqueId) else null
        val targetScale = saved ?: serverScale
        val attr = player.getAttribute(Attribute.SCALE)
        attr?.baseValue = targetScale
    }

    fun canUsePreset(player: Player, preset: SizePreset): Boolean {
        if (!requirePermission) return true
        if (player.hasPermission("crazymine.size.*") || player.isOp) return true
        return player.hasPermission(preset.permission)
    }

    fun format(key: String, vararg replacements: Pair<String, String>): String {
        var raw = messages[key] ?: key
        for ((k, v) in replacements) {
            raw = raw.replace("<$k>", v).replace("{$k}", v)
        }
        return raw
    }

    fun component(key: String, vararg replacements: Pair<String, String>): Component {
        val prefix = messages["prefix"] ?: ""
        val body = format(key, *replacements)
        return mm.deserialize(prefix + body)
    }

    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, String>) {
        sender.sendMessage(component(key, *replacements))
    }
}
