package eu.crazymine.size

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale

class SizeGui(private val plugin: SizePlugin, private val manager: SizeManager) : Listener {

    private class GuiHolder : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }

    fun open(player: Player) {
        val holder = GuiHolder()
        val title = manager.mm.deserialize("<gradient:#ff7a18:#ffd166>Größe wählen</gradient>")
        val inv = plugin.server.createInventory(holder, 27, title)
        holder.inv = inv

        val currentScale = manager.getScale(player)

        // Filler
        val filler = ItemStack(Material.GRAY_STAINED_GLASS_PANE).apply {
            editMeta { meta -> meta.displayName(Component.empty()) }
        }
        for (i in 0 until 27) {
            inv.setItem(i, filler)
        }

        // Info Skull / Compass in Slot 4
        val infoItem = ItemStack(Material.COMPASS).apply {
            editMeta { meta ->
                meta.displayName(manager.mm.deserialize("<gradient:#ff7a18:#ffd166><bold>Aktuelle Größe</bold></gradient>").decoration(TextDecoration.ITALIC, false))
                val lore = listOf(
                    manager.mm.deserialize("<gray>Deine Größe:</gray> <yellow>${String.format(Locale.US, "%.2f", currentScale)}x</yellow>"),
                    Component.empty(),
                    manager.mm.deserialize("<dark_gray>Wähle unten eine Option aus</dark_gray>")
                ).map { it.decoration(TextDecoration.ITALIC, false) }
                meta.lore(lore)
            }
        }
        inv.setItem(4, infoItem)

        // Presets
        for (preset in manager.presets.values) {
            if (preset.slot !in 0 until 27) continue
            val allowed = manager.canUsePreset(player, preset)
            val selected = Math.abs(preset.scale - currentScale) < 0.05

            val item = ItemStack(preset.material).apply {
                editMeta { meta ->
                    meta.displayName(manager.mm.deserialize(preset.title).decoration(TextDecoration.ITALIC, false))
                    val lore = mutableListOf<Component>()
                    if (preset.description.isNotBlank()) {
                        lore.add(manager.mm.deserialize(preset.description))
                    }
                    lore.add(Component.empty())
                    if (selected) {
                        lore.add(manager.mm.deserialize("<green>✔ Aktuell ausgewählt</green>"))
                        meta.setEnchantmentGlintOverride(true)
                    } else if (allowed) {
                        lore.add(manager.mm.deserialize("<yellow>Klicken zum Auswählen</yellow>"))
                    } else {
                        lore.add(manager.mm.deserialize("<red>✖ Keine Berechtigung</red>"))
                    }
                    meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                }
            }
            inv.setItem(preset.slot, item)
        }

        // Reset in Slot 26
        val resetItem = ItemStack(Material.REDSTONE).apply {
            editMeta { meta ->
                meta.displayName(manager.mm.deserialize("<red><bold>Zurücksetzen</bold></red>").decoration(TextDecoration.ITALIC, false))
                val lore = listOf(
                    manager.mm.deserialize("<gray>Setzt deine Größe auf <green>Normal (1.0x)</green> zurück.</gray>"),
                    Component.empty(),
                    manager.mm.deserialize("<yellow>Klicken zum Zurücksetzen</yellow>")
                ).map { it.decoration(TextDecoration.ITALIC, false) }
                meta.lore(lore)
            }
        }
        inv.setItem(26, resetItem)

        player.openInventory(inv)
        player.playSound(player.location, Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.3f)
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is GuiHolder) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory?.holder !is GuiHolder) return

        val slot = event.slot

        // Reset
        if (slot == 26) {
            manager.resetScale(player)
            manager.send(player, "size-reset")
            player.closeInventory()
            return
        }

        // Check presets
        val clickedPreset = manager.presets.values.firstOrNull { it.slot == slot } ?: return
        if (!manager.canUsePreset(player, clickedPreset)) {
            manager.send(player, "no-permission")
            player.playSound(player.location, Sound.ENTITY_VILLAGER_NO, 0.7f, 1.0f)
            return
        }

        manager.setScale(player, clickedPreset.scale)
        manager.send(player, "size-set", "size" to String.format(Locale.US, "%.2f", clickedPreset.scale))
        player.closeInventory()
    }

    @EventHandler
    fun onDrag(event: InventoryDragEvent) {
        if (event.view.topInventory.holder is GuiHolder) {
            event.isCancelled = true
        }
    }
}
