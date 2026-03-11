package com.github.albardoo02.scriptBlockNext.manager

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.data.ScriptEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.reflect.TypeToken

import org.bukkit.Location
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID

object ScriptManager {

    private lateinit var plugin: ScriptBlockNext
    private lateinit var scriptsFolder: File
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    val scripts = mutableMapOf<String, MutableMap<BlockLocation, ScriptData>>()
    val creationMode = mutableMapOf<UUID, Pair<String, List<String>>>()
    val addMode = mutableMapOf<UUID, Pair<String, List<String>>>()
    val removalMode = mutableMapOf<UUID, String>()

    fun init(plugin: ScriptBlockNext) {
        this.plugin = plugin
        this.scriptsFolder = File(plugin.dataFolder, "scripts")
        if (!scriptsFolder.exists()) {
            scriptsFolder.mkdirs()
        }
        loadScripts()
    }

    private fun toBlockLoc(location: Location): BlockLocation {
        return BlockLocation(location.world?.name ?: "", location.blockX, location.blockY, location.blockZ)
    }

    fun addScript(location: Location, data: ScriptData) {
        val blockLoc = toBlockLoc(location)
        scripts.getOrPut(data.type) { mutableMapOf() }[blockLoc] = data
        saveScripts()
    }

    fun getScript(location: Location, type: String): ScriptData? {
        val blockLoc = toBlockLoc(location)
        return scripts[type]?.get(blockLoc)
    }

    fun removeScript(location: Location, type: String) {
        val blockLoc = toBlockLoc(location)
        scripts[type]?.remove(blockLoc)
        saveScripts()
    }

    fun removeAllScripts(location: Location) {
        val blockLoc = toBlockLoc(location)
        scripts.values.forEach { it.remove(blockLoc) }
        saveScripts()
    }

    fun loadScripts() {
        scripts.clear()
        migrateFromScriptBlockPlus()
        val jsonFiles = scriptsFolder.listFiles { file -> file.extension == "json" } ?: return
        for (file in jsonFiles) {
            try {
                FileReader(file).use { reader ->
                    val jsonObject = gson.fromJson(reader, JsonObject::class.java) ?: return@use
                    for (worldEntry in jsonObject.entrySet()) {
                        val worldName = worldEntry.key
                        val coordsObj = worldEntry.value.asJsonObject
                        for (coordEntry in coordsObj.entrySet()) {
                            val coordsStr = coordEntry.key
                            val parts = coordsStr.replace(" ", "").split(",")
                            if (parts.size != 3) continue
                            val x = parts[0].toIntOrNull() ?: continue
                            val y = parts[1].toIntOrNull() ?: continue
                            val z = parts[2].toIntOrNull() ?: continue
                            val blockLoc = BlockLocation(worldName, x, y, z)
                            val entry = gson.fromJson(coordEntry.value, ScriptEntry::class.java)
                            val creator = entry.creator?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                            val data = ScriptData(entry.commands, entry.type, creator)

                            scripts.getOrPut(entry.type) { mutableMapOf() }[blockLoc] = data
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load ${file.name}: ${e.message}")
            }
        }
    }

    private fun migrateFromScriptBlockPlus() {
        val shouldMigrate = plugin.config.getBoolean("migrate_from_scriptblockplus", false)
        if (!shouldMigrate) return
        val pluginsFolder = plugin.dataFolder.parentFile
        val oldPluginFolder = File(pluginsFolder, "ScriptBlockPlus")
        val oldScriptsFolder = File(oldPluginFolder, "scripts")

        if (!oldScriptsFolder.exists() || !oldScriptsFolder.isDirectory) {
            plugin.logger.warning("ScriptBlockPlus folder was not found at ${oldScriptsFolder.path}. Skipping migration.")
            return
        }

        plugin.logger.info("Starting automatic migration from ScriptBlockPlus...")
        val types = listOf("interact", "walk", "break", "hit")
        var migratedCount = 0
        for (type in types) {
            val ymlFile = File(oldScriptsFolder, "$type.yml")
            if (!ymlFile.exists()) continue
            plugin.logger.info("Migrating ScriptBlockPlus file: ${ymlFile.name} to JSON format...")
            val config = YamlConfiguration.loadConfiguration(ymlFile)
            for (worldName in config.getKeys(false)) {
                val worldSection = config.getConfigurationSection(worldName) ?: continue
                for (coordsString in worldSection.getKeys(false)) {
                    val path = "$worldName.$coordsString"
                    val parts = coordsString.replace(" ", "").split(",")
                    if (parts.size != 3) continue
                    val x = parts[0].toIntOrNull() ?: continue
                    val y = parts[1].toIntOrNull() ?: continue
                    val z = parts[2].toIntOrNull() ?: continue
                    val blockLoc = BlockLocation(worldName, x, y, z)
                    val authorStr = config.getString("$path.Author")
                    val authorUUID = authorStr?.let { runCatching { UUID.fromString(it) }.getOrNull() }
                    val rawCommands = config.getStringList("$path.Scripts")
                    val cleanCommands = rawCommands.map { cmd ->
                        if (cmd.startsWith("[") && cmd.endsWith("]")) {
                            cmd.substring(1, cmd.length - 1)
                        } else cmd
                    }
                    val data = ScriptData(cleanCommands, type, authorUUID)
                    scripts.getOrPut(type) { mutableMapOf() }[blockLoc] = data
                    migratedCount++
                }
            }
        }
        if (migratedCount > 0) {
            plugin.logger.info("Migration from ScriptBlockPlus completed successfully! ($migratedCount scripts migrated)")
            saveScripts()
            plugin.config.set("migrate_from_scriptblockplus", false)
            plugin.saveConfig()
            plugin.logger.info("Disabled 'migrate_from_scriptblockplus' in config.yml to prevent duplicate migration.")
        } else {
            plugin.logger.info("No valid ScriptBlockPlus scripts were found to migrate.")
        }
    }

    private fun saveScripts() {
        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                for ((type, typeMap) in scripts) {
                    val worldMap = mutableMapOf<String, MutableMap<String, ScriptEntry>>()
                    for ((loc, data) in typeMap) {
                        val coordsStr = "${loc.x}, ${loc.y}, ${loc.z}"
                        val entry = ScriptEntry(
                            type = data.type,
                            commands = data.commands,
                            creator = data.creator?.toString()
                        )
                        worldMap.getOrPut(loc.world) { mutableMapOf() }[coordsStr] = entry
                    }
                    val file = File(scriptsFolder, "$type.json")
                    FileWriter(file).use { writer ->
                        gson.toJson(worldMap, writer) // ★完成した2重Mapを保存
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to save scripts: ${e.message}")
            }
        })
    }
}
