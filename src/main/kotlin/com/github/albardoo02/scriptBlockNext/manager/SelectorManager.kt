package com.github.albardoo02.scriptBlockNext.manager

import org.bukkit.Location
import org.bukkit.entity.Player
import com.github.albardoo02.scriptBlockNext.data.ScriptData // ← これをインポートに追加
import java.util.UUID

object SelectorManager {
    private val selections = mutableMapOf<UUID, Pair<Location?, Location?>>()

    val clipboard = mutableMapOf<UUID, ScriptData>()
    
    fun setPos1(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        selections[player.uniqueId] = Pair(loc, current?.second)
        player.sendMsg("pos1_set", "x" to loc.blockX.toString(), "y" to loc.blockY.toString(), "z" to loc.blockZ.toString())
    }

    fun setPos2(player: Player, loc: Location) {
        val current = selections[player.uniqueId]
        selections[player.uniqueId] = Pair(current?.first, loc)
        player.sendMsg("pos2_set", "x" to loc.blockX.toString(), "y" to loc.blockY.toString(), "z" to loc.blockZ.toString())
    }

    fun getSelection(player: Player): Pair<Location, Location>? {
        val pair = selections[player.uniqueId] ?: return null
        val p1 = pair.first ?: return null
        val p2 = pair.second ?: return null
        if (p1.world != p2.world) return null
        return Pair(p1, p2)
    }
}