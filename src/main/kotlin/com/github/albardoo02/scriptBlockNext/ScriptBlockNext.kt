package com.github.albardoo02.scriptBlockNext

import com.github.albardoo02.scriptBlockNext.command.ScriptCommand
import com.github.albardoo02.scriptBlockNext.listener.InteractListener
import com.github.albardoo02.scriptBlockNext.listener.ScriptListener
import com.github.albardoo02.scriptBlockNext.manager.MessageManager
import com.github.albardoo02.scriptBlockNext.manager.ScriptManager
import net.milkbowl.vault.economy.Economy
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

class ScriptBlockNext : JavaPlugin() {

    companion object {
        lateinit var instance: ScriptBlockNext
        private set
    }

    var economy: Economy? = null

    override fun onEnable() {
        instance = this
        MessageManager.init(this)

        disableAndRenameOldPlugin()

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

    private fun disableAndRenameOldPlugin() {
        val pluginManager = server.pluginManager
        val oldPlugin = pluginManager.getPlugin("ScriptBlockPlus") ?: return
        logger.info("ScriptBlockPlusが検知されました。競合を防ぐため、安全に無効化を試みます...")
        pluginManager.disablePlugin(oldPlugin)
        logger.info("-> ScriptBlockPlusをメモリ上で無効化しました。")
        try {
            val jarFile = File(oldPlugin.javaClass.protectionDomain.codeSource.location.toURI())
            if (jarFile.extension == "jar") {
                val bakFile = File(jarFile.parentFile, jarFile.name + ".bak")
                if (jarFile.renameTo(bakFile)) {
                    logger.info("-> 成功: ${jarFile.name} を ${bakFile.name} にリネームしました！次回の起動時からは読み込まれません。")
                } else {
                    logger.warning("===================================================")
                    logger.warning("【警告】OSの制限により、ScriptBlockPlus.jar の自動リネームに失敗しました。")
                    logger.warning("現在の一時的な無効化には成功していますが、次回起動時に再び読み込まれてしまいます。")
                    logger.warning("サーバーを一度停止し、手動でプラグインフォルダから ScriptBlockPlus.jar を削除してください！")
                    logger.warning("===================================================")
                }
            }
        } catch (e: Exception) {
            logger.warning("JARファイルの特定に失敗しました。手動で旧プラグインを削除してください。")
        }
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