package com.github.albardoo02.scriptBlockNext.manager

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import me.clip.placeholderapi.PlaceholderAPI
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.entity.Player

object PlaceholderManager {

    private lateinit var plugin: ScriptBlockNext
    private var hasPlaceholderAPI = false

    fun init(plugin: ScriptBlockNext) {
        this.plugin = plugin
        hasPlaceholderAPI = Bukkit.getPluginManager().isPluginEnabled("PlaceholderAPI")
        if (hasPlaceholderAPI) {
            plugin.logger.info("PlaceholderAPI hooked successfully.")
        }
    }

    fun replace(player: Player, text: String): String {
        var result = text
        result = result.replace("<player>", player.name)
            .replace("<world>", player.world.name)
        val escapes = mapOf(
            "\\b" to " ", "\\c" to ",", "\\h" to "-", "\\o" to ":",
            "\\p" to "%", "\\s" to "/", "\\l" to "<", "\\g" to ">",
            "\\i" to "[", "\\j" to "]"
        )
        for ((key, value) in escapes) {
            result = result.replace(key, value)
        }
        if (hasPlaceholderAPI) {
            result = PlaceholderAPI.setPlaceholders(player, result)
        }
        if (result.contains("&rc")) {
            val colors = listOf("a", "b", "c", "d", "e", "f", "1", "2", "3", "4", "5", "6", "7", "8", "9")
            result = result.replace("&rc", "&${colors.random()}")
        }
        return ChatColor.translateAlternateColorCodes('&', result)
    }
}