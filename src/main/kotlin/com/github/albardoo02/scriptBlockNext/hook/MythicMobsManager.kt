package com.github.albardoo02.scriptBlockNext.hook

import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.plugin.Plugin

object MythicMobsManager {

    interface MythicMobsHook {
        fun getMythicId(item: ItemStack): String?
    }

    private class MythicMobsModern : MythicMobsHook {
        private val instMethod = Class.forName("io.lumine.mythic.bukkit.MythicBukkit").getMethod("inst")
        private val getItemManagerMethod: java.lang.reflect.Method
        private val getMythicTypeMethod: java.lang.reflect.Method

        init {
            val mythicBukkit = instMethod.invoke(null)
            getItemManagerMethod = mythicBukkit.javaClass.getMethod("getItemManager")
            val itemManager = getItemManagerMethod.invoke(mythicBukkit)
            getMythicTypeMethod = itemManager.javaClass.getMethod("getMythicTypeFromItem", ItemStack::class.java)
        }

        override fun getMythicId(item: ItemStack): String? {
            try {
                val mythicBukkit = instMethod.invoke(null)
                val itemManager = getItemManagerMethod.invoke(mythicBukkit)
                val result = getMythicTypeMethod.invoke(itemManager, item) ?: return null

                if (result is java.util.Optional<*>) {
                    return if (result.isPresent) result.get().toString() else null
                }
                return result.toString()
            } catch (e: Exception) {
                return null
            }
        }
    }

    private class MythicMobsLegacy : MythicMobsHook {
        private val serverVersion = Bukkit.getServer().javaClass.`package`.name.substringAfterLast(".")

        override fun getMythicId(item: ItemStack): String? {
            try {
                val craftItemStackClass = Class.forName("org.bukkit.craftbukkit.$serverVersion.inventory.CraftItemStack")
                val asNMSCopy = craftItemStackClass.getMethod("asNMSCopy", ItemStack::class.java)
                val nmsItem = asNMSCopy.invoke(null, item) ?: return null

                val hasTagMethod = nmsItem.javaClass.getMethod("hasTag")
                if (!(hasTagMethod.invoke(nmsItem) as Boolean)) return null

                val getTagMethod = nmsItem.javaClass.getMethod("getTag")
                val nbtTagCompound = getTagMethod.invoke(nmsItem) ?: return null

                val hasKeyMethod = nbtTagCompound.javaClass.getMethod("hasKey", String::class.java)
                if (!(hasKeyMethod.invoke(nbtTagCompound, "MYTHIC_TYPE") as Boolean)) return null

                val getStringMethod = nbtTagCompound.javaClass.getMethod("getString", String::class.java)
                val mythicType = getStringMethod.invoke(nbtTagCompound, "MYTHIC_TYPE") as String

                return mythicType.ifEmpty { null }
            } catch (e: Exception) {
                return null
            }
        }
    }

    private var hook: MythicMobsHook? = null

    fun init(plugin: Plugin) {
        if (!plugin.server.pluginManager.isPluginEnabled("MythicMobs")) return

        try {
            Class.forName("io.lumine.mythic.bukkit.MythicBukkit")
            hook = MythicMobsModern()
            plugin.logger.info("MythicMobs (v5+) API を検知しました。モダンAPIで連携します。")
        } catch (e: ClassNotFoundException) {
            try {
                Class.forName("io.lumine.xikage.mythicmobs.MythicMobs")
                hook = MythicMobsLegacy()
                plugin.logger.info("MythicMobs (Legacy v4) を検知しました。NMSを使用してNBTを解析します。")
            } catch (ex: Exception) {
                plugin.logger.warning("MythicMobsは導入されていますが、対応していないバージョンです。")
            }
        }
    }

    private fun getMythicId(item: ItemStack?): String? {
        if (item == null || item.type == Material.AIR || hook == null) return null
        return hook?.getMythicId(item)
    }

    fun countMythicItem(player: Player, mythicId: String): Int {
        var count = 0
        for (item in player.inventory.contents) {
            if (getMythicId(item) == mythicId) count += item!!.amount
        }
        return count
    }

    fun takeMythicItem(player: Player, mythicId: String, amount: Int) {
        var remaining = amount
        for (i in 0 until player.inventory.size) {
            val item = player.inventory.getItem(i)
            if (item != null && item.type != Material.AIR && getMythicId(item) == mythicId) {
                if (item.amount <= remaining) {
                    remaining -= item.amount
                    player.inventory.setItem(i, null)
                } else {
                    item.amount -= remaining
                    remaining = 0
                }
            }
            if (remaining <= 0) break
        }
    }
}