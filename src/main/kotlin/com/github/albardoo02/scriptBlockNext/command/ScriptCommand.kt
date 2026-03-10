package com.github.albardoo02.scriptBlockNext.command

import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.command.TabCompleter
import org.bukkit.entity.Player

class ScriptCommand: CommandExecutor, TabCompleter {

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
            else -> sender.sendMsg("error_unknown_action", "action" to action)        }
        return true
    }

    override fun onTabComplete(sender: CommandSender, command: Command, alias: String, args: Array<out String>): List<String>? {
        if (args.size == 1) {
            return listOf("interact", "break", "walk", "hit", "reload").filter { it.startsWith(args[0], ignoreCase = true) }
        }
        if (args.size == 2) {
            val first = args[0].lowercase()
            if (first in listOf("interact", "break", "walk", "hit")) {
                return listOf("create", "add", "remove").filter { it.startsWith(args[1], ignoreCase = true) }
            }
        }
        return emptyList()
    }
}