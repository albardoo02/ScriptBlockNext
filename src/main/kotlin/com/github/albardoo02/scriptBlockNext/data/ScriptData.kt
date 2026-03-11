package com.github.albardoo02.scriptBlockNext.data

import java.util.UUID

data class ScriptData (
    val commands: List<String>,
    val type: String = "interact",
    val creator: UUID? = null,
    val permission: String? = null
)

data class BlockLocation(
    val world: String,
    val x: Int,
    val y: Int,
    val z: Int
)

data class ScriptEntry(
    val commands: List<String>,
    val creator: String? = null
)