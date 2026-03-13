package com.github.albardoo02.scriptBlockNext.manager

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.data.ScriptEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject

import org.bukkit.Location
import org.bukkit.command.CommandSender
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
    val checkpoints = mutableMapOf<UUID, Location>()
    val noFallPlayers = mutableMapOf<UUID, Long>()

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

        if (plugin.config.getBoolean("migrate_from_scriptblockplus", false)) {
            migrateFromScriptBlockPlus(null)
        }

        val jsonFiles = scriptsFolder.listFiles { file -> file.extension == "json" } ?: return
        for (file in jsonFiles) {
            val scriptType = file.nameWithoutExtension
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
                            val data = ScriptData(entry.commands, scriptType, creator)
                            scripts.getOrPut(scriptType) { mutableMapOf() }[blockLoc] = data
                        }
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to load ${file.name}: ${e.message}")
            }
        }
    }

    fun migrateFromScriptBlockPlus(sender: CommandSender?): Boolean {
        val pluginsFolder = plugin.dataFolder.parentFile
        val oldPluginFolder = File(pluginsFolder, "ScriptBlockPlus")

        if (!oldPluginFolder.exists() || !oldPluginFolder.isDirectory) {
            sender?.sendMsg("error_migration_not_found")
            plugin.logger.warning("ScriptBlockPlus folder was not found at ${oldPluginFolder.path}. Skipping migration.")
            return false
        }

        var migratedCount = 0
        val types = listOf("interact", "walk", "break", "hit")

        plugin.logger.info("Starting migration from ScriptBlockPlus...")

        val oldScriptsFolderYaml = File(oldPluginFolder, "scripts")
        if (oldScriptsFolderYaml.exists() && oldScriptsFolderYaml.isDirectory) {
            for (type in types) {
                val ymlFile = File(oldScriptsFolderYaml, "$type.yml")
                if (!ymlFile.exists()) continue
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
                            if (cmd.startsWith("[") && cmd.endsWith("]")) cmd.substring(1, cmd.length - 1) else cmd
                        }
                        val data = ScriptData(cleanCommands, type, authorUUID)
                        scripts.getOrPut(type) { mutableMapOf() }[blockLoc] = data
                        migratedCount++
                    }
                }
            }
        }

        val oldScriptsFolderJson = File(oldPluginFolder, "json/blockscript")
        if (oldScriptsFolderJson.exists() && oldScriptsFolderJson.isDirectory) {
            for (type in types) {
                val jsonFile = File(oldScriptsFolderJson, "$type.json")
                if (!jsonFile.exists()) continue

                try {
                    FileReader(jsonFile).use { reader ->
                        val jsonArray = gson.fromJson(reader, JsonArray::class.java) ?: return@use
                        for (element in jsonArray) {
                            val obj = element.asJsonObject
                            val coordsRaw = obj.get("blockcoords")?.asString ?: continue
                            val parts = coordsRaw.split(",")
                            if (parts.size != 4) continue
                            val worldName = parts[0]
                            val x = parts[1].toIntOrNull() ?: continue
                            val y = parts[2].toIntOrNull() ?: continue
                            val z = parts[3].toIntOrNull() ?: continue
                            val blockLoc = BlockLocation(worldName, x, y, z)

                            var authorUUID: UUID? = null
                            val authorArray = obj.getAsJsonArray("author")
                            if (authorArray != null && authorArray.size() > 0) {
                                val uuidStr = authorArray.get(0).asString
                                authorUUID = runCatching { UUID.fromString(uuidStr) }.getOrNull()
                            }

                            val cleanCommands = mutableListOf<String>()
                            val scriptArray = obj.getAsJsonArray("script")
                            if (scriptArray != null) {
                                for (cmdElement in scriptArray) {
                                    val cmd = cmdElement.asString
                                    if (cmd.startsWith("[") && cmd.endsWith("]")) {
                                        cleanCommands.add(cmd.substring(1, cmd.length - 1))
                                    } else {
                                        cleanCommands.add(cmd)
                                    }
                                }
                            }

                            val data = ScriptData(cleanCommands, type, authorUUID)
                            scripts.getOrPut(type) { mutableMapOf() }[blockLoc] = data
                            migratedCount++
                        }
                    }
                } catch (e: Exception) {
                    plugin.logger.severe("Failed to migrate ${jsonFile.name}: ${e.message}")
                }
            }
        }

        if (migratedCount > 0) {
            saveScripts()
            plugin.config.set("migrate_from_scriptblockplus", false)
            plugin.saveConfig()
            sender?.sendMsg("success_migration", "count" to migratedCount.toString())
            plugin.logger.info("Disabled 'migrate_from_scriptblockplus' in config.yml to prevent duplicate migration.")
            return true
        } else {
            sender?.sendMsg("error_migration_no_data")
            plugin.logger.info("No valid ScriptBlockPlus scripts were found to migrate.")
            return false
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
                            commands = data.commands,
                            creator = data.creator?.toString()
                        )
                        worldMap.getOrPut(loc.world) { mutableMapOf() }[coordsStr] = entry
                    }
                    val file = File(scriptsFolder, "$type.json")
                    FileWriter(file).use { writer ->
                        gson.toJson(worldMap, writer)
                    }
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to save scripts: ${e.message}")
            }
        })
    }
}