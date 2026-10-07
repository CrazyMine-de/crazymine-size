package eu.crazymine.size

import org.bukkit.plugin.java.JavaPlugin

class SizePlugin : JavaPlugin() {

    lateinit var manager: SizeManager
        private set
    lateinit var gui: SizeGui
        private set

    override fun onEnable() {
        saveDefaultConfig()

        manager = SizeManager(this)
        manager.load()

        gui = SizeGui(this, manager)

        val cmd = SizeCommand(this, manager, gui)
        getCommand("size")?.let {
            it.setExecutor(cmd)
            it.tabCompleter = cmd
        }

        server.pluginManager.registerEvents(gui, this)
        server.pluginManager.registerEvents(SizeListener(this, manager), this)

        logger.info("CrazyMineSize erfolgreich aktiviert! (Presets: ${manager.presets.size}, Enabled: ${manager.enabled})")
    }

    override fun onDisable() {
        logger.info("CrazyMineSize deaktiviert.")
    }
}
