package com.github.albardoo02.scriptBlockNext.manager

import org.bukkit.ChatColor
import org.bukkit.command.CommandSender
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

object MessageManager {
    private lateinit var plugin: JavaPlugin
    private val loadedLangs = mutableMapOf<String, YamlConfiguration>()

    private var prefix: String = ""
    private var defaultLang: String = "en"

    fun init(plugin: JavaPlugin) {
        this.plugin = plugin
        loadedLangs.clear()

        plugin.saveDefaultConfig()
        plugin.reloadConfig()
        prefix = plugin.config.getString("prefix") ?: "&7[&bScriptBlockNext7]&r"
        defaultLang = plugin.config.getString("default_language")?.lowercase() ?: "en"
        val msgFolder = File(plugin.dataFolder, "messages")
        if (!msgFolder.exists()) msgFolder.mkdirs()
        val defaultFiles = listOf("lang_ja.yml", "lang_en.yml")
        for (fileName in defaultFiles) {
            val file = File(msgFolder, fileName)
            if (!file.exists() && plugin.getResource("messages/$fileName") != null) {
                plugin.saveResource("messages/$fileName", false)
            }
        }
        msgFolder.listFiles()?.filter { it.extension == "yml" }?.forEach { file ->
            val langCode = file.nameWithoutExtension.removePrefix("lang_").lowercase()
            loadedLangs[langCode] = YamlConfiguration.loadConfiguration(file)
        }
    }

    fun sendMessage(sender: CommandSender, key: String, vararg placeholders: Pair<String, String>) {
        val rawLocale = (sender as? Player)?.locale?.lowercase() ?: defaultLang
        val locale = rawLocale.split("_").firstOrNull() ?: defaultLang
        val config = loadedLangs[locale] ?: loadedLangs[defaultLang] ?: loadedLangs.values.firstOrNull()
        val rawMessage = config?.getString(key) ?: "&cMessage-NotFound:$key"
        var finalMessage = "$prefix $rawMessage"
        for ((pKey, pValue) in placeholders) {
            finalMessage = finalMessage.replace("{$pKey}", pValue)
        }
        sender.sendMessage(ChatColor.translateAlternateColorCodes('&', finalMessage))
    }
}

fun CommandSender.sendMsg(key: String, vararg placeholders: Pair<String, String>) {
    MessageManager.sendMessage(this, key, *placeholders)
}