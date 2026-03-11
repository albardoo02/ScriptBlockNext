package com.github.albardoo02.scriptBlockNext.hook

import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.plugin.Plugin

object VaultManager {
    var isHooked = false
        private set

    private var economy: Economy? = null

    fun init(plugin: Plugin) {
        if (plugin.server.pluginManager.isPluginEnabled("Vault")) {
            val rsp = Bukkit.getServicesManager().getRegistration(Economy::class.java)
            if (rsp != null) {
                economy = rsp.provider
                isHooked = true
                plugin.logger.info("Vault hooked successfully.")
            }
        }
    }

    fun has(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.has(player, amount) ?: false
    }

    fun withdraw(player: OfflinePlayer, amount: Double): Boolean {
        return economy?.withdrawPlayer(player, amount)?.transactionSuccess() ?: false
    }
}