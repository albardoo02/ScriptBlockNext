package com.github.albardoo02.scriptBlockNext.hook

import github.scarsz.discordsrv.DiscordSRV
import github.scarsz.discordsrv.dependencies.jda.api.entities.Member
import github.scarsz.discordsrv.util.DiscordUtil
import org.bukkit.plugin.Plugin
import java.util.UUID

object DiscordSRVManager {
    var isHooked = false
        private set

    fun init(plugin: Plugin) {
        if (plugin.server.pluginManager.isPluginEnabled("DiscordSRV")) {
            isHooked = true
            plugin.logger.info("DiscordSRV hooked successfully.")
        }
    }

    private fun getMemberById(uuid: UUID): Member? {
        if (!isHooked) return null
        val discordId = DiscordSRV.getPlugin().accountLinkManager?.getDiscordId(uuid) ?: return null
        return DiscordUtil.getMemberById(discordId)
    }

    fun getVoiceChannelId(uuid: UUID): String? {
        val member = getMemberById(uuid) ?: return null
        return member.voiceState?.channel?.id
    }

    fun hasRole(uuid: UUID, roleId: String): Boolean {
        val member = getMemberById(uuid) ?: return false
        return member.roles.any { it.id == roleId }
    }

    fun addRole(uuid: UUID, roleId: String) {
        val member = getMemberById(uuid) ?: return
        val role = member.jda.getRoleById(roleId) ?: return
        DiscordUtil.addRolesToMember(member, role)
    }

    fun removeRole(uuid: UUID, roleId: String) {
        val member = getMemberById(uuid) ?: return
        val role = member.jda.getRoleById(roleId) ?: return
        DiscordUtil.removeRolesFromMember(member, role)
    }
}