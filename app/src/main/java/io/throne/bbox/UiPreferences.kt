package io.throne.bbox

import android.content.Context
import io.nekohasekai.sagernet.ktx.app
import org.json.JSONArray
import org.json.JSONObject

/**
 * UI-only preferences that B-box kept in SharedPreferences and that have no business being in the
 * core configuration: theme, colour style, sort order, card layout.
 *
 * Stored separately from [Settings] so that no restart hint ever appears for them.
 */
object UiPreferences {

    private const val STORE = "throne_ui"

    private val prefs get() = app.getSharedPreferences(STORE, Context.MODE_PRIVATE)

    fun read(): Map<String, Any?> = HashMap<String, Any?>().apply {
        val all = prefs.all
        for ((key, value) in all) {
            val stored = when (value) {
                is String if value.startsWith("\u0000map:") -> JSONObject(value.removePrefix("\u0000map:"))
                is String if value.startsWith("\u0000list:") -> JSONArray(value.removePrefix("\u0000list:"))
                else -> value
            }
            put(key, unfold(stored))
        }
        putIfAbsent("theme", "system")
    }

    fun write(values: Map<String, Any?>) {
        val editor = prefs.edit()
        for ((key, value) in values) {
            if (key == "locale") continue // no language override on top of Throne's own setting
            when (value) {
                null -> editor.remove(key)
                is Boolean -> editor.putBoolean(key, value)
                is Number -> editor.putInt(key, value.toInt())
                is String -> editor.putString(key, value)
                is Map<*, *> -> editor.putString(key, "\u0000map:" + JSONObject(value.mapKeys { it.key.toString() }).toString())
                is Iterable<*> -> editor.putString(key, "\u0000list:" + JSONArray(value.toList()).toString())
                else -> editor.putString(key, value.toString())
            }
        }
        editor.apply()
    }

    private fun unfold(value: Any?): Any? = when (value) {
        is JSONObject -> value.keys().asSequence().associateWith { key -> unfold(value.get(key)) }
        is JSONArray -> (0 until value.length()).map { index -> unfold(value.get(index)) }
        else -> value
    }
}
