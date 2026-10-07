package eu.crazymine.size

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerRespawnEvent

class SizeListener(private val plugin: SizePlugin, private val manager: SizeManager) : Listener {

    @EventHandler(priority = EventPriority.MONITOR)
    fun onJoin(event: PlayerJoinEvent) {
        manager.applyOnJoin(event.player)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onRespawn(event: PlayerRespawnEvent) {
        plugin.server.scheduler.runTaskLater(plugin, Runnable {
            if (event.player.isOnline) {
                manager.applyOnJoin(event.player)
            }
        }, 1L)
    }
}
