package dev.paddeck.data

import android.content.Context

data class Profile(
    val host: String = "",
    val port: Int = 5900,
    val password: String = "",
)

class Prefs(context: Context) {
    private val store = context.getSharedPreferences("paddeck", Context.MODE_PRIVATE)

    var profile: Profile
        get() = Profile(
            host = store.getString("host", "") ?: "",
            port = store.getInt("port", 5900),
            password = store.getString("password", "") ?: "",
        )
        set(value) {
            store.edit()
                .putString("host", value.host)
                .putInt("port", value.port)
                .putString("password", value.password)
                .apply()
        }

    var sensitivity: Float
        get() = store.getFloat("sensitivity", 1.4f)
        set(value) = store.edit().putFloat("sensitivity", value).apply()

    var naturalScroll: Boolean
        get() = store.getBoolean("natural_scroll", true)
        set(value) = store.edit().putBoolean("natural_scroll", value).apply()

    var tapToClick: Boolean
        get() = store.getBoolean("tap_click", true)
        set(value) = store.edit().putBoolean("tap_click", value).apply()

    var directTouch: Boolean
        get() = store.getBoolean("direct_touch", false)
        set(value) = store.edit().putBoolean("direct_touch", value).apply()

    var keyboardOverlay: Boolean
        get() = store.getBoolean("keyboard_overlay", true)
        set(value) = store.edit().putBoolean("keyboard_overlay", value).apply()
}
