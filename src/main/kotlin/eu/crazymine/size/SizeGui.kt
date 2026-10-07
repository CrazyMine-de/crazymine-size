package eu.crazymine.size

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.inventory.InventoryClickEvent
import org.bukkit.event.inventory.InventoryDragEvent
import org.bukkit.inventory.Inventory
import org.bukkit.inventory.InventoryHolder
import org.bukkit.inventory.ItemStack
import java.util.Locale
import kotlin.math.abs

class SizeGui(private val plugin: SizePlugin, private val manager: SizeManager) : Listener {

    private class GuiHolder : InventoryHolder {
        lateinit var inv: Inventory
        override fun getInventory(): Inventory = inv
    }

    fun open(player: Player) {
        val holder = GuiHolder()
        val titleText = manager.getRawMessage("gui.title")
        val titleComponent = manager.mm.deserialize(titleText)
        val inv = plugin.server.createInventory(holder, manager.guiSize, titleComponent)
        holder.inv = inv

        val currentScale = manager.getScale(player)
        val formattedScale = String.format(Locale.US, "%.2f", currentScale)

        // Filler
        if (manager.guiFillerEnabled && manager.guiFillerMaterial != Material.AIR) {
            val filler = ItemStack(manager.guiFillerMaterial).apply {
                editMeta { meta -> meta.displayName(Component.empty()) }
            }
            for (i in 0 until manager.guiSize) {
                inv.setItem(i, filler)
            }
        }

        // Info Skull / Compass Item
        if (manager.guiInfoEnabled && manager.guiInfoSlot in 0 until manager.guiSize) {
            val infoItem = ItemStack(manager.guiInfoMaterial).apply {
                editMeta { meta ->
                    val nameStr = manager.getRawMessage("gui.current-info.name", "size" to formattedScale)
                    meta.displayName(manager.mm.deserialize(nameStr).decoration(TextDecoration.ITALIC, false))

                    val rawLore = manager.getRawList("gui.current-info.lore", "size" to formattedScale)
                    val lore = rawLore.map { line ->
                        manager.mm.deserialize(line).decoration(TextDecoration.ITALIC, false)
                    }
                    meta.lore(lore)
                }
            }
            inv.setItem(manager.guiInfoSlot, infoItem)
        }

        // Presets
        for (preset in manager.presets.values) {
            if (preset.slot !in 0 until manager.guiSize) continue
            val allowed = manager.canUsePreset(player, preset)
            val selected = abs(preset.scale - currentScale) < 0.05
            val presetScaleFormatted = String.format(Locale.US, "%.2f", preset.scale)

            val item = ItemStack(preset.material).apply {
                editMeta { meta ->
                    meta.displayName(manager.mm.deserialize(preset.title).decoration(TextDecoration.ITALIC, false))
                    val lore = mutableListOf<Component>()
                    for (desc in preset.description) {
                        lore.add(manager.mm.deserialize(desc))
                    }
                    lore.add(Component.empty())

                    val statusMsg = when {
                        selected -> manager.getRawMessage("gui.status.selected", "size" to presetScaleFormatted)
                        allowed -> manager.getRawMessage("gui.status.can-select", "size" to presetScaleFormatted)
                        else -> manager.getRawMessage("gui.status.no-permission", "size" to presetScaleFormatted)
                    }
                    lore.add(manager.mm.deserialize(statusMsg))

                    if (selected) {
                        meta.setEnchantmentGlintOverride(true)
                    }

                    meta.lore(lore.map { it.decoration(TextDecoration.ITALIC, false) })
                }
            }
            inv.setItem(preset.slot, item)
        }

        // Reset Item
        if (manager.guiResetEnabled && manager.guiResetSlot in 0 until manager.guiSize) {
            val resetItem = ItemStack(manager.guiResetMaterial).apply {
                editMeta { meta ->
                    val resetTitle = manager.getRawMessage("gui.reset-item.name")
                    meta.displayName(manager.mm.deserialize(resetTitle).decoration(TextDecoration.ITALIC, false))

                    val rawLore = manager.getRawList("gui.reset-item.lore")
                    val lore = rawLore.map { line ->
                        manager.mm.deserialize(line).decoration(TextDecoration.ITALIC, false)
                    }
                    meta.lore(lore)
                }
            }
            inv.setItem(manager.guiResetSlot, resetItem)
        }

        player.openInventory(inv)
        if (manager.soundsEnabled) {
            player.playSound(player.location, manager.soundGuiOpen, 0.6f, 1.3f)
        }
    }

    @EventHandler
    fun onClick(event: InventoryClickEvent) {
        if (event.view.topInventory.holder !is GuiHolder) return
        event.isCancelled = true

        val player = event.whoClicked as? Player ?: return
        if (event.clickedInventory?.holder !is GuiHolder) return

        val slot = event.slot

        // Reset click
        if (manager.guiResetEnabled && slot == manager.guiResetSlot) {
            manager.resetScale(player)
            manager.send(player, "size-reset")
            player.closeInventory()
            return
        }

        // Presets click
        val clickedPreset = manager.presets.values.firstOrNull { it.slot == slot } ?: return
        if (!manager.canUsePreset(player, clickedPreset)) {
            manager.send(player, "no-permission")
            if (manager.soundsEnabled) {
                player.playSound(player.location, manager.soundGuiDenied, 0.7f, 1.0f)
            }
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
