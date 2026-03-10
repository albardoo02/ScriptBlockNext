package com.github.albardoo02.scriptBlockNext

import com.github.albardoo02.scriptBlockNext.command.ScriptCommand
import com.github.albardoo02.scriptBlockNext.listener.InteractListener
import com.github.albardoo02.scriptBlockNext.listener.ScriptListener
import com.github.albardoo02.scriptBlockNext.manager.MessageManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin

class ScriptBlockNext : JavaPlugin() {

    companion object {
        lateinit var instance: ScriptBlockNext
        private set
    }

    var economy: Economy? = null

    override fun onEnable() {
        instance = this
        MessageManager.init(this)

        if (!setupEconomy()) {
            logger.warning("Vault is not found. Continuing without economy features.")
        }

        val scriptCommand = ScriptCommand()
        getCommand("scriptblocknext")?.let {
            it.setExecutor(scriptCommand)
            it.tabCompleter = scriptCommand
        }
        ScriptManager.init(this)
        server.pluginManager.registerEvents(InteractListener(), this)
        server.pluginManager.registerEvents(ScriptListener(), this)
        logger.info("ScriptBlockNext has been enabled.")
    }

    override fun onDisable() {
        logger.info("ScriptBlockNext has been disabled.")
    }

    private fun setupEconomy(): Boolean {
        if (!server.pluginManager.isPluginEnabled("Vault")) {
            return false
        }
        val rsp = server.servicesManager.getRegistration(Economy::class.java) ?: return false
        economy = rsp.provider
        return economy != null
    }
}