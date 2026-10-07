package eu.crazymine.size

import org.bukkit.Material

data class SizePreset(
    val id: String,
    val scale: Double,
    val title: String,
    val description: String,
    val permission: String,
    val material: Material,
    val slot: Int
)
