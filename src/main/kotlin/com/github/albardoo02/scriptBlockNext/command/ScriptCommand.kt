package com.github.albardoo02.scriptBlockNext.command

import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import org.bukkit.Bukkit
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandMap
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player
import org.bukkit.plugin.SimplePluginManager
import java.lang.reflect.Field

class ScriptCommand: CommandExecutor, TabCompleter {

    private val commandMap: CommandMap? by lazy {
        try {
            val field: Field = SimplePluginManager::class.java.getDeclaredField("commandMap")
            field.isAccessible = true
            field.get(Bukkit.getPluginManager()) as CommandMap
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (sender !is Player) {
            sender.sendMsg("error_only_player")
            return true
        }

        if (args.isEmpty()) {
            sender.sendMsg("cmd_usage_main")
            return true
        }

        if (args[0].lowercase() == "reload") {
            ScriptManager.loadScripts()
            sender.sendMsg("cmd_reloaded")
            return true
        }

        if (args.size < 2) {
            sender.sendMsg("cmd_usage_action")
            return true
        }

        val type = args[0].lowercase()
        val action = args[1].lowercase()

        if (type !in listOf("interact", "break", "walk", "hit")) {
            sender.sendMsg("error_invalid_type")
            return true
        }
        when (action) {
            "create", "add" -> {
                if (args.size < 3) {
                    sender.sendMsg("cmd_usage_args", "type" to type, "action" to action)
                    return true
                }
                val cmdString = args.drop(2).joinToString(" ")
                val regex = Regex("\\[(.*?)\\]")
                val matches = regex.findAll(cmdString)
                val commands = matches.map { it.groupValues[1] }.toList().ifEmpty { listOf(cmdString) }

                if (action == "create") {
                    ScriptManager.creationMode[sender.uniqueId] = type to commands
                    ScriptManager.addMode.remove(sender.uniqueId)
                    ScriptManager.removalMode.remove(sender.uniqueId)
                    sender.sendMsg("mode_creation", "type" to type, "commands" to commands.joinToString(", "))
                } else {
                    ScriptManager.addMode[sender.uniqueId] = type to commands
                    ScriptManager.creationMode.remove(sender.uniqueId)
                    ScriptManager.removalMode.remove(sender.uniqueId)
                    sender.sendMsg("mode_add", "type" to type, "commands" to commands.joinToString(", "))
                }
            }
            "remove" -> {
                ScriptManager.removalMode[sender.uniqueId] = type
                ScriptManager.creationMode.remove(sender.uniqueId)
                ScriptManager.addMode.remove(sender.uniqueId)
                sender.sendMsg("mode_removal", "type" to type)
            }
            else -> sender.sendMsg("error_unknown_action", "action" to action)
        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (sender !is Player) return emptyList()

        if (args.size == 1) {
            return listOf("interact", "break", "walk", "hit", "reload").filter { it.startsWith(args[0], ignoreCase = true) }
        }

        val first = args[0].lowercase()
        if (args.size == 2 && first in listOf("interact", "break", "walk", "hit")) {
            return listOf("create", "add", "remove").filter { it.startsWith(args[1], ignoreCase = true) }
        }

        if (args.size >= 3 && first in listOf("interact", "break", "walk", "hit")) {
            val currentArg = args.last()
            val fullInput = args.drop(2).joinToString(" ")

            val commandTags = listOf("[@command ", "[@console ", "[@bypass ", "[@bypassPERM:", "[@bypassGROUP:")
            val activeTag = commandTags.find { tag ->
                val lastIdx = fullInput.lastIndexOf(tag)
                lastIdx != -1 && !fullInput.substring(lastIdx).contains("]")
            }

            if (activeTag != null) {
                val cmdContent = fullInput.substring(fullInput.lastIndexOf(activeTag) + activeTag.length)
                val cmdArgs = cmdContent.split(" ").toMutableList()
                if (cmdArgs.size <= 1) {
                    val search = if (cmdArgs.isEmpty()) "" else cmdArgs[0]
                    return commandMap?.knownCommands?.keys?.filter { it.startsWith(search, ignoreCase = true) && !it.contains(":") }
                } else {
                    val label = cmdArgs[0].removePrefix("/")
                    val targetCmd = commandMap?.getCommand(label)
                    if (targetCmd != null) {
                        val subArgs = cmdArgs.drop(1).toTypedArray()
                        return try {
                            targetCmd.tabComplete(sender, label, subArgs)
                        } catch (e: Exception) {
                            null
                        }
                    }
                }
            }

            val options = listOf(
                "[@action:", "[@blocktype:", "[@group:", "[@perm:", "[@drole:", "[@dchannel:",
                "[@if ", "[@oldcooldown:", "[@cooldown:", "[@delay:", "[@hand:", $$"[$item:", $$"[$cost:",
                "[@groupADD:", "[@groupREMOVE:", "[@permADD:", "[@permREMOVE:", "[@droleADD:", "[@droleREMOVE:",
                "[@say ", "[@server ", "[@player ", "[@sound:", "[@title:", "[@actionbar:",
                "[@bypass ", "[@bypassPERM:", "[@bypassGROUP:", "[@cmd", "[@command ", "[@console ",
                "[@execute:", "[@amount:", "[@invalid]", "[@broadcast", "[@message",
                "[@velocity:", "[@checkpoint]", "[@return]", "[@nofall:", "[@potion:"
            )

            val lastBracket = currentArg.lastIndexOf('[')
            return if (lastBracket != null && lastBracket != -1) {
                val prefix = currentArg.substring(0, lastBracket)
                val filter = currentArg.substring(lastBracket)
                options.filter { it.startsWith(filter, ignoreCase = true) }.map { prefix + it }
            } else {
                options.filter { it.startsWith("[$currentArg", ignoreCase = true) }
            }
        }
        return emptyList()
    }
}