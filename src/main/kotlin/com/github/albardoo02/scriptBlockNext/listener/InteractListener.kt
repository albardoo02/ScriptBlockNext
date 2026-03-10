package com.github.albardoo02.scriptBlockNext.listener

import com.github.albardoo02.scriptBlockNext.data.ScriptData
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import com.github.albardoo02.scriptBlockNext.manager.sendMsg
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent

class InteractListener: Listener {

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.hand != org.bukkit.inventory.EquipmentSlot.HAND) return
        val player = event.player
        val clickedBlock = event.clickedBlock ?: return
        val location = clickedBlock.location
        
        if (ScriptManager.creationMode.containsKey(player.uniqueId)) {
            val (type, commands) = ScriptManager.creationMode.remove(player.uniqueId) ?: return
            ScriptManager.addScript(location, ScriptData(commands, type, player.uniqueId))
            player.sendMsg("success_registered", "type" to type)
            event.isCancelled = true
            return
        }

        if (ScriptManager.addMode.containsKey(player.uniqueId)) {
            val (type, newCommands) = ScriptManager.addMode.remove(player.uniqueId) ?: return
            val existingData = ScriptManager.getScript(location, type)

            if (existingData != null) {
                val combinedCommands = existingData.commands + newCommands
                val newData = existingData.copy(commands = combinedCommands)
                ScriptManager.addScript(location, newData)
                player.sendMsg("success_added", "type" to type, "count" to combinedCommands.size.toString())
            } else {
                ScriptManager.addScript(location, ScriptData(newCommands, type, player.uniqueId))
                player.sendMsg("success_registered", "type" to type)
            }
            event.isCancelled = true
            return
        }

        val removeType = ScriptManager.removalMode.remove(player.uniqueId)
        if (removeType != null) {
            ScriptManager.removeScript(location, removeType)
            player.sendMsg("success_removed", "type" to removeType)
            event.isCancelled = true
            return
        }
        if (event.action != Action.RIGHT_CLICK_BLOCK && event.action != Action.LEFT_CLICK_BLOCK) return
        val scriptData = ScriptManager.getScript(location, "interact") ?: return
        event.isCancelled = true
        ScriptExecutor.run(player, scriptData)
    }
}