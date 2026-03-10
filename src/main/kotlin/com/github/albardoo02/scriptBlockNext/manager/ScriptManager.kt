package com.github.albardoo02.scriptBlockNext.manager

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.BlockLocation
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.data.ScriptEntry
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.google.gson.reflect.TypeToken

import org.bukkit.Location
import java.io.File
import java.io.FileReader
import java.io.FileWriter
import java.util.UUID

object ScriptManager {
    private lateinit var plugin: ScriptBlockNext
    private lateinit var configFile: File
    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()

    val scripts = mutableMapOf<String, MutableMap<BlockLocation, ScriptData>>()
    val creationMode = mutableMapOf<UUID, Pair<String, List<String>>>()
    val addMode = mutableMapOf<UUID, Pair<String, List<String>>>()
    val removalMode = mutableMapOf<UUID, String>()

    fun init(plugin: ScriptBlockNext) {
        this.plugin = plugin
        this.configFile = File(plugin.dataFolder, "scripts.json")
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

        // 互換性のため、もぁEscripts.yml があれ�E読み込んで JSON に移行すめE
        val yamlFile = File(plugin.dataFolder, "scripts.yml")
        if (yamlFile.exists()) {
            plugin.logger.info("Migrating scripts.yml to scripts.json...")
            loadFromYaml(yamlFile)
            saveScripts()
            yamlFile.renameTo(File(plugin.dataFolder, "scripts.yml.bak"))
            return
        }

        if (!configFile.exists()) return

        try {
            FileReader(configFile).use { reader ->
                val type = object : TypeToken<List<ScriptEntry>>() {}.type
                val entries: List<ScriptEntry> = gson.fromJson(reader, type) ?: return

                for (entry in entries) {
                    val blockLoc = BlockLocation(entry.world, entry.x.toInt(), entry.y.toInt(), entry.z.toInt())
                    val creator = entry.creator?.let { UUID.fromString(it) }
                    val data = ScriptData(entry.commands, entry.type, creator)
                    scripts.getOrPut(entry.type) { mutableMapOf() }[blockLoc] = data
                }
            }
        } catch (e: Exception) {
            plugin.logger.severe("Failed to load scripts: ${e.message}")
        }
    }

    private fun loadFromYaml(file: File) {
        val config = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(file)
        val keys = config.getKeys(false)
        for (key in keys) {
            val worldName = config.getString("$key.world") ?: continue
            val x = config.getInt("$key.x")
            val y = config.getInt("$key.y")
            val z = config.getInt("$key.z")
            val blockLoc = BlockLocation(worldName, x, y, z)

            val commands = config.getStringList("$key.commands")
            val type = config.getString("$key.type") ?: "interact"
            val creatorString = config.getString("$key.creator")
            val creator = if (creatorString != null) UUID.fromString(creatorString) else null

            val data = ScriptData(commands, type, creator)
            scripts.getOrPut(type) { mutableMapOf() }[blockLoc] = data
        }
    }

    private fun saveScripts() {
        val entries = mutableListOf<ScriptEntry>()
        for (typeMap in scripts.values) {
            for ((loc, data) in typeMap) {
                entries.add(ScriptEntry(
                    world = loc.world,
                    x = loc.x.toDouble(),
                    y = loc.y.toDouble(),
                    z = loc.z.toDouble(),
                    type = data.type,
                    commands = data.commands,
                    creator = data.creator?.toString()
                ))
            }
        }

        plugin.server.scheduler.runTaskAsynchronously(plugin, Runnable {
            try {
                if (!configFile.parentFile.exists()) {
                    configFile.parentFile.mkdirs()
                }
                FileWriter(configFile).use { writer ->
                    gson.toJson(entries, writer)
                }
            } catch (e: Exception) {
                plugin.logger.severe("Failed to save scripts: ${e.message}")
            }
        })
    }
}
