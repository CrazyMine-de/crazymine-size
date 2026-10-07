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

    // GUI settings
    var guiSize = 27
        private set
    var guiFillerEnabled = true
        private set
    var guiFillerMaterial = Material.GRAY_STAINED_GLASS_PANE
        private set
    var guiInfoEnabled = true
        private set
    var guiInfoSlot = 4
        private set
    var guiInfoMaterial = Material.COMPASS
        private set
    var guiResetEnabled = true
        private set
    var guiResetSlot = 26
        private set
    var guiResetMaterial = Material.REDSTONE
        private set

    // Effects
    var soundsEnabled = true
        private set
    var soundChangeScale = Sound.ENTITY_PLAYER_LEVELUP
        private set
    var soundGuiOpen = Sound.BLOCK_ENCHANTMENT_TABLE_USE
        private set
    var soundGuiDenied = Sound.ENTITY_VILLAGER_NO
        private set
    var particlesEnabled = true
        private set
    var particleType = Particle.ENCHANT
        private set
    var particleCount = 25
        private set

    val presets = LinkedHashMap<String, SizePreset>()
    private val messages = ConcurrentHashMap<String, String>()
    private val messageLists = ConcurrentHashMap<String, List<String>>()
    private val playerSizes = ConcurrentHashMap<UUID, Double>()
    private val storageLock = Any()

    private var storageFile: File = File(plugin.dataFolder, "sizes.yml")

    fun load() {
        // Ensure default configs exist
        plugin.saveDefaultConfig()
        plugin.reloadConfig()

        val messagesFile = File(plugin.dataFolder, "messages.yml")
        if (!messagesFile.exists()) {
            try {
                plugin.saveResource("messages.yml", false)
            } catch (e: Exception) {
                plugin.logger.warning("Konnte messages.yml nicht aus Ressourcen entpacken: ${e.message}")
            }
        }

        val config = plugin.config

        enabled = config.getBoolean("enabled", true)
        requirePermission = config.getBoolean("require-permission", true)
        minScale = config.getDouble("min-scale", 0.2)
        maxScale = config.getDouble("max-scale", 2.5)
        serverScale = config.getDouble("server-scale", 1.0)
        forceServerScale = config.getBoolean("force-server-scale", false)
        persistPlayerSize = config.getBoolean("storage.persist", config.getBoolean("persist-player-size", true))

        // Storage path
        val customPath = config.getString("storage.custom-file-path", "")?.trim() ?: ""
        storageFile = when {
            customPath.isNotEmpty() -> File(customPath)
            File("/opt/nimbus/shared/sizes").exists() -> File("/opt/nimbus/shared/sizes/sizes.yml")
            else -> File(plugin.dataFolder, "sizes.yml")
        }

        // GUI Config
        val rawGuiSize = config.getInt("gui.size", 27)
        guiSize = when {
            rawGuiSize in listOf(9, 18, 27, 36, 45, 54) -> rawGuiSize
            else -> 27
        }
        guiFillerEnabled = config.getBoolean("gui.filler.enabled", true)
        guiFillerMaterial = Material.matchMaterial(config.getString("gui.filler.material", "GRAY_STAINED_GLASS_PANE") ?: "")
            ?: Material.GRAY_STAINED_GLASS_PANE

        guiInfoEnabled = config.getBoolean("gui.info-item.enabled", true)
        guiInfoSlot = config.getInt("gui.info-item.slot", 4)
        guiInfoMaterial = Material.matchMaterial(config.getString("gui.info-item.material", "COMPASS") ?: "")
            ?: Material.COMPASS

        guiResetEnabled = config.getBoolean("gui.reset-item.enabled", true)
        guiResetSlot = config.getInt("gui.reset-item.slot", 26)
        guiResetMaterial = Material.matchMaterial(config.getString("gui.reset-item.material", "REDSTONE") ?: "")
            ?: Material.REDSTONE

        // Effects Config
        soundsEnabled = config.getBoolean("effects.sounds.enabled", true)
        soundChangeScale = parseSound(config.getString("effects.sounds.change-scale", "ENTITY_PLAYER_LEVELUP"), Sound.ENTITY_PLAYER_LEVELUP)
        soundGuiOpen = parseSound(config.getString("effects.sounds.gui-open", "BLOCK_ENCHANTMENT_TABLE_USE"), Sound.BLOCK_ENCHANTMENT_TABLE_USE)
        soundGuiDenied = parseSound(config.getString("effects.sounds.gui-denied", "ENTITY_VILLAGER_NO"), Sound.ENTITY_VILLAGER_NO)

        particlesEnabled = config.getBoolean("effects.particles.enabled", true)
        particleType = parseParticle(config.getString("effects.particles.particle", "ENCHANT"), Particle.ENCHANT)
        particleCount = config.getInt("effects.particles.count", 25)

        // Presets
        presets.clear()
        config.getConfigurationSection("presets")?.let { sec ->
            for (key in sec.getKeys(false)) {
                val scale = sec.getDouble("$key.scale", 1.0)
                val title = sec.getString("$key.title", key) ?: key
                val descList = if (sec.isList("$key.description")) {
                    sec.getStringList("$key.description")
                } else {
                    val single = sec.getString("$key.description", "") ?: ""
                    if (single.isNotBlank()) listOf(single) else emptyList()
                }
                val perm = sec.getString("$key.permission", "crazymine.size.$key") ?: "crazymine.size.$key"
                val matName = sec.getString("$key.material", "PLAYER_HEAD") ?: "PLAYER_HEAD"
                val mat = Material.matchMaterial(matName) ?: Material.PLAYER_HEAD
                val slot = sec.getInt("$key.slot", 13)
                presets[key.lowercase()] = SizePreset(key, scale, title, descList, perm, mat, slot)
            }
        }

        // Messages
        loadMessages(messagesFile)

        // Load stored sizes
        loadSavedSizes()
    }

    private fun parseSound(name: String?, fallback: Sound): Sound {
        if (name == null) return fallback
        return runCatching { Sound.valueOf(name.uppercase()) }.getOrElse { fallback }
    }

    private fun parseParticle(name: String?, fallback: Particle): Particle {
        if (name == null) return fallback
        return runCatching { Particle.valueOf(name.uppercase()) }.getOrElse { fallback }
    }

    private fun loadMessages(file: File) {
        messages.clear()
        messageLists.clear()

        val yaml = if (file.exists()) {
            YamlConfiguration.loadConfiguration(file)
        } else {
            YamlConfiguration()
        }

        fun traverse(prefix: String, section: org.bukkit.configuration.ConfigurationSection) {
            for (key in section.getKeys(false)) {
                val path = if (prefix.isEmpty()) key else "$prefix.$key"
                if (section.isConfigurationSection(key)) {
                    section.getConfigurationSection(key)?.let { traverse(path, it) }
                } else if (section.isList(key)) {
                    messageLists[path] = section.getStringList(key)
                } else {
                    messages[path] = section.getString(key, "") ?: ""
                }
            }
        }

        traverse("", yaml)
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

    private fun saveStoredSize(uuid: UUID) {
        if (!persistPlayerSize) return
        val scale = playerSizes[uuid]
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            synchronized(storageLock) {
                try {
                    storageFile.parentFile?.mkdirs()
                    val yaml = YamlConfiguration.loadConfiguration(storageFile)
                    if (scale != null) {
                        yaml.set(uuid.toString(), scale)
                    } else {
                        yaml.set(uuid.toString(), null)
                    }
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
            playChangeEffects(player, clamped)
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

    fun playChangeEffects(player: Player, targetScale: Double) {
        if (soundsEnabled) {
            val pitch = when {
                targetScale < 0.8 -> 1.7f
                targetScale > 1.2 -> 0.6f
                else -> 1.0f
            }
            player.playSound(player.location, soundChangeScale, 0.6f, pitch)
        }
        if (particlesEnabled) {
            player.world.spawnParticle(
                particleType,
                player.location.add(0.0, targetScale * 0.8, 0.0),
                particleCount,
                0.3,
                0.5,
                0.3,
                0.15
            )
        }
    }

    fun getRawMessage(key: String, vararg replacements: Pair<String, String>): String {
        var raw = messages[key] ?: key
        for ((k, v) in replacements) {
            raw = raw.replace("<$k>", v).replace("{$k}", v)
        }
        return raw
    }

    fun getRawList(key: String, vararg replacements: Pair<String, String>): List<String> {
        val list = messageLists[key] ?: return emptyList()
        return list.map { line ->
            var formatted = line
            for ((k, v) in replacements) {
                formatted = formatted.replace("<$k>", v).replace("{$k}", v)
            }
            formatted
        }
    }

    fun component(key: String, vararg replacements: Pair<String, String>): Component {
        val prefix = messages["prefix"] ?: ""
        val body = getRawMessage(key, *replacements)
        return mm.deserialize(prefix + body)
    }

    fun componentNoPrefix(key: String, vararg replacements: Pair<String, String>): Component {
        val body = getRawMessage(key, *replacements)
        return mm.deserialize(body)
    }

    fun send(sender: CommandSender, key: String, vararg replacements: Pair<String, String>) {
        sender.sendMessage(component(key, *replacements))
    }
}
