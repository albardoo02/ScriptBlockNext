package com.github.albardoo02.scriptBlockNext.executor

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.manager.PlaceholderManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import net.md_5.bungee.api.ChatMessageType
import net.md_5.bungee.api.chat.TextComponent
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Sound
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID

object ScriptExecutor {

    private val playerCooldowns = mutableMapOf<UUID, MutableMap<Location, Long>>()
    private val globalCooldowns = mutableMapOf<Location, Long>()

    fun run(player: Player, scriptData: ScriptData, location: Location) {
        var requiredMoney = 0.0
        val requiredItems = mutableListOf<ItemStack>()
        var cooldownTime = 0
        var oldCooldownTime = 0

        for (line in scriptData.commands) {
            val cmd = PlaceholderManager.replace(player, line)
            if (cmd.startsWith("@cooldown:")) cooldownTime = cmd.substringAfter("@cooldown:").toIntOrNull() ?: 0
            if (cmd.startsWith("@oldcooldown:")) oldCooldownTime = cmd.substringAfter("@oldcooldown:").toIntOrNull() ?: 0
        }

        val now = System.currentTimeMillis()

        if (cooldownTime > 0) {
            val lastTime = playerCooldowns[player.uniqueId]?.get(location) ?: 0L
            val remaining = (cooldownTime * 1000L - (now - lastTime)) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return
            }
        }

        if (oldCooldownTime > 0) {
            val lastTime = globalCooldowns[location] ?: 0L
            val remaining = (oldCooldownTime * 1000L - (now - lastTime)) / 1000L
            if (remaining > 0) {
                player.sendMsg("error_cooldown", "time" to remaining.toString())
                return
            }
        }

        if (cooldownTime > 0) playerCooldowns.getOrPut(player.uniqueId) { mutableMapOf() }[location] = now
        if (oldCooldownTime > 0) globalCooldowns[location] = now

        for (line in scriptData.commands) {
            val cmd = PlaceholderManager.replace(player, line)
            if (cmd.startsWith("\$cost:")) {
                requiredMoney += cmd.substringAfter("\$cost:").toDoubleOrNull() ?: 0.0
            } else if (cmd.startsWith("\$item:")) {
                val parts = cmd.substringAfter("\$item:").split(":")
                if (parts.size >= 2) {
                    val mat = Material.matchMaterial(parts[0].uppercase()) ?: continue
                    val amount = parts[1].toIntOrNull() ?: 1
                    requiredItems.add(ItemStack(mat, amount))
                }
            } else if (cmd.startsWith("@if ")) {
                val ifArgs = cmd.substringAfter("@if ").split(" ", limit = 4)
                if (ifArgs.size >= 3) {
                    val val1 = ifArgs[0]
                    val operator = ifArgs[1]
                    val val2 = ifArgs[2]
                    val failMsg = if (ifArgs.size == 4) ifArgs[3] else null
                    val num1 = val1.toDoubleOrNull()
                    val num2 = val2.toDoubleOrNull()
                    val passed = if (num1 != null && num2 != null) {
                        when (operator) {
                            "==" -> num1 == num2; "!=" -> num1 != num2; ">" -> num1 > num2
                            ">=" -> num1 >= num2; "<" -> num1 < num2; "<=" -> num1 <= num2; else -> false
                        }
                    } else {
                        when (operator) { "==" -> val1 == val2; "!=" -> val1 != val2; else -> false }
                    }
                    if (!passed) {
                        if (failMsg != null) player.sendMessage(failMsg)
                        return
                    }
                }
            }
        }

        val eco = ScriptBlockNext.instance.economy
        if (requiredMoney > 0.0 && eco != null) {
            if (!eco.has(player, requiredMoney)) {
                player.sendMsg("error_no_money", "amount" to requiredMoney.toString())
                return
            }
        }

        for (item in requiredItems) {
            if (!player.inventory.containsAtLeast(item, item.amount)) {
                player.sendMsg("error_no_item", "item" to item.type.name, "amount" to item.amount.toString())
                return
            }
        }

        if (requiredMoney > 0.0 && eco != null) eco.withdrawPlayer(player, requiredMoney)
        for (item in requiredItems) player.inventory.removeItem(item)
        executeCommands(player, scriptData.commands, 0)
    }

    private fun executeCommands(player: Player, commands: List<String>, startIndex: Int) {
        if (!player.isOnline) return

        for (i in startIndex until commands.size) {
            val cmd = PlaceholderManager.replace(player, commands[i])

            if (cmd.startsWith($$"$cost:") || cmd.startsWith($$"$item:") ||
                cmd.startsWith("@if ") || cmd.startsWith("@cooldown:") || cmd.startsWith("@oldcooldown:")) continue
            if (cmd.startsWith("@delay:")) {
                val ticks = cmd.substringAfter("@delay:").toLongOrNull() ?: 20L
                Bukkit.getScheduler().runTaskLater(ScriptBlockNext.instance, Runnable {
                    executeCommands(player, commands, i + 1)
                }, ticks)
                return
            }
            if (cmd.startsWith("@velocity:")) {
                val args = cmd.substringAfter("@velocity:").split(",")
                if (args.size == 3) {
                    val x = args[0].toDoubleOrNull() ?: 0.0
                    val y = args[1].toDoubleOrNull() ?: 0.0
                    val z = args[2].toDoubleOrNull() ?: 0.0
                    player.velocity = Vector(x, y, z)
                }
                continue
            }
            if (cmd.startsWith("@checkpoint")) {
                ScriptManager.checkpoints[player.uniqueId] = player.location
                continue
            }
            if (cmd.startsWith("@return")) {
                val cp = ScriptManager.checkpoints[player.uniqueId]
                if (cp != null) {
                    player.teleport(cp)
                } else {
                    player.sendMessage("§cチェックポイントが設定されていません。")
                }
                continue
            }
            if (cmd.startsWith("@nofall:")) {
                val seconds = cmd.substringAfter("@nofall:").toIntOrNull() ?: 0
                if (seconds > 0) {
                    ScriptManager.noFallPlayers[player.uniqueId] = System.currentTimeMillis() + (seconds * 1000L)
                }
                continue
            }
            if (cmd.startsWith("@potion:")) {
                val args = cmd.substringAfter("@potion:").split(":")
                if (args.size >= 3) {
                    val type = PotionEffectType.getByName(args[0].uppercase())
                    val duration = (args[1].toIntOrNull() ?: 1) * 20
                    val amplifier = args[2].toIntOrNull() ?: 0
                    if (type != null) {
                        player.addPotionEffect(PotionEffect(type, duration, amplifier))
                    }
                }
                continue
            }

            when {
                cmd.startsWith("@player ") || cmd.startsWith("@msg ") -> player.sendMessage(cmd.substringAfter(" "))
                cmd.startsWith("@server ") -> Bukkit.broadcastMessage(cmd.substringAfter("@server "))
                cmd.startsWith("@console ") -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.substringAfter("@console ").trim().removePrefix("/"))
                cmd.startsWith("@command ") -> {
                    val finalCmd = cmd.substringAfter("@command ").trim().removePrefix("/")
                    player.performCommand(finalCmd)
                }
                cmd.startsWith("@bypass ") -> {
                    val isOp = player.isOp
                    try {
                        player.isOp = true
                        player.performCommand(cmd.substringAfter("@bypass ").trim().removePrefix("/"))
                    } finally {
                        player.isOp = isOp
                    }
                }
                cmd.startsWith("@actionbar:") -> player.spigot().sendMessage(ChatMessageType.ACTION_BAR, TextComponent(cmd.substringAfter("@actionbar:")))
                cmd.startsWith("@title:") -> {
                    val titleData = cmd.substringAfter("@title:").split("/")
                    player.sendTitle(titleData.getOrNull(0) ?: "", titleData.getOrNull(1) ?: "", 10, 70, 20)
                }
                cmd.startsWith("@sound:") -> {
                    val soundData = cmd.substringAfter("@sound:").split("-")
                    val sound = runCatching { Sound.valueOf(soundData.getOrNull(0)?.uppercase() ?: "") }.getOrNull()
                    if (sound != null) player.playSound(player.location, sound, soundData.getOrNull(1)?.toFloatOrNull() ?: 1.0f, soundData.getOrNull(2)?.toFloatOrNull() ?: 1.0f)
                }
                else -> player.performCommand(cmd.trim().removePrefix("/"))
            }
        }
    }
}