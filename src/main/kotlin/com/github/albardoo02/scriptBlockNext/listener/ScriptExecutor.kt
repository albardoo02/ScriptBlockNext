package com.github.albardoo02.scriptBlockNext.listener

import com.github.albardoo02.scriptBlockNext.ScriptBlockNext
import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack

object ScriptExecutor {

    fun run(player: Player, scriptData: ScriptData) {
        var requiredMoney = 0.0
        val requiredItems = mutableListOf<ItemStack>()

        for (line in scriptData.commands) {
            val cmd = line.replace("@player", player.name).replace("<player>", player.name)

            if (cmd.startsWith($$"$cost:")) {
                requiredMoney += cmd.substringAfter($$"$cost:").toDoubleOrNull() ?: 0.0
            }
            else if (cmd.startsWith("\$item:")) {
                val parts = cmd.substringAfter($$"$item:").split(":")
                if (parts.size >= 2) {
                    val mat = Material.matchMaterial(parts[0].uppercase()) ?: continue
                    val amount = parts[1].toIntOrNull() ?: 1
                    requiredItems.add(ItemStack(mat, amount))
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

        for (line in scriptData.commands) {
            val cmd = line.replace("@player", player.name).replace("<player>", player.name)

            if (cmd.startsWith("\$cost:") || cmd.startsWith("\$item:")) continue

            if (cmd.startsWith("@msg ")) {
                val msg = cmd.substringAfter("@msg ").replace("&", "§")
                player.sendMessage(msg)
            }
            else if (cmd.startsWith("@bypass ")) {
                val finalCmd = cmd.substringAfter("@bypass ").trim().removePrefix("/")
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd)
            }
            else {
                val finalCmd = cmd.trim().removePrefix("/")
                player.performCommand(finalCmd)
            }
        }
    }
}