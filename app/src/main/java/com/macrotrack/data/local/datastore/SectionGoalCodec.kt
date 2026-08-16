package com.macrotrack.data.local.datastore

import com.macrotrack.domain.model.MacroType
import com.macrotrack.domain.model.SectionGoalPercentages
import java.util.Locale

/**
 * Compact JSON codec for [SectionGoalPercentages], matching the format
 * historically produced by the settings UI so persisted values round-trip:
 * `{"<sectionId>":{"PROTEIN":40.0,"CARBS":30.0,"FAT":30.0},...}`
 */
object SectionGoalCodec {

    fun serialize(distribution: Map<Long, Map<MacroType, Float>>): String {
        val sb = StringBuilder()
        sb.append("{")
        distribution.entries.forEachIndexed { i, (sectionId, macros) ->
            if (i > 0) sb.append(",")
            sb.append("\"$sectionId\":{")
            macros.entries.forEachIndexed { j, (type, percent) ->
                if (j > 0) sb.append(",")
                sb.append("\"${type.name}\":${"%.1f".format(Locale.US, percent)}")
            }
            sb.append("}")
        }
        sb.append("}")
        return sb.toString()
    }

    /** Parses a raw distribution JSON string into a map keyed by section id. */
    fun parseMap(json: String): Map<Long, Map<MacroType, Float>> {
        val result = mutableMapOf<Long, Map<MacroType, Float>>()
        val trimmed = json.trim().removeSurrounding("{", "}")
        if (trimmed.isBlank()) return result
        val parts = splitTopLevel(trimmed)
        for (part in parts) {
            val colonIdx = part.indexOf(':')
            if (colonIdx < 0) continue
            val key = part.substring(0, colonIdx).trim().removeSurrounding("\"")
            val sectionId = key.toLongOrNull() ?: continue
            val innerJson = part.substring(colonIdx + 1).trim().removeSurrounding("{", "}")
            val macroMap = mutableMapOf<MacroType, Float>()
            for (item in splitTopLevel(innerJson)) {
                val mColon = item.indexOf(':')
                if (mColon < 0) continue
                val macroKey = item.substring(0, mColon).trim().removeSurrounding("\"")
                val macroVal = item.substring(mColon + 1).trim().toFloatOrNull() ?: continue
                try {
                    macroMap[MacroType.valueOf(macroKey)] = macroVal
                } catch (_: Exception) {
                }
            }
            result[sectionId] = macroMap
        }
        return result
    }

    /** Parses a (possibly absent) stored JSON string into [SectionGoalPercentages]. */
    fun parse(json: String?): SectionGoalPercentages {
        if (json.isNullOrBlank()) return SectionGoalPercentages()
        return SectionGoalPercentages(percentages = parseMap(json))
    }

    private fun splitTopLevel(s: String): List<String> {
        val result = mutableListOf<String>()
        var depth = 0
        val current = StringBuilder()
        for (c in s) {
            when (c) {
                '{' -> {
                    depth++
                    current.append(c)
                }
                '}' -> {
                    depth--
                    current.append(c)
                }
                ',' -> {
                    if (depth == 0) {
                        if (current.isNotEmpty()) result.add(current.toString().trim())
                        current.clear()
                    } else current.append(c)
                }
                else -> current.append(c)
            }
        }
        if (current.isNotEmpty()) result.add(current.toString().trim())
        return result
    }
}