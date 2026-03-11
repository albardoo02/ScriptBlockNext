package com.github.albardoo02.scriptBlockNext.manager

import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.UUID

object SelectorManager {
    private val selections = mutableMapOf<UUID, Pair<Location?, Location?>>()

    fun setPos1(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        selections[player.uniqueId] = Pair(loc, current?.second)
        player.sendMessage("§d[SBN] Pos1 を設定しました: ${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
    }

    fun setPos2(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        selections[player.uniqueId] = Pair(current?.first, loc)
        player.sendMessage("§d[SBN] Pos2 を設定しました: ${loc.blockX}, ${loc.blockY}, ${loc.blockZ}")
    }

    fun getSelection(player: Player): Pair<Location, Location>? {
        val pair = selections[player.uniqueId] ?: return null
        val p1 = pair.first ?: return null
        val p2 = pair.second ?: return null
        if (p1.world != p2.world) return null
        return Pair(p1, p2)
    }
}