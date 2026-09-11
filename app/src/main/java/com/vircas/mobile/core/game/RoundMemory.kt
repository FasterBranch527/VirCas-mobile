package com.vircas.mobile.core.game

/** ViewModel-owned state, independent of a particular Activity or composition instance. */
class RoundMemory {
    private val entries = mutableMapOf<String, Any?>()

    @Suppress("UNCHECKED_CAST")
    fun <T> remember(key: String, initializer: () -> T): T {
        if (!entries.containsKey(key)) entries[key] = initializer()
        return entries[key] as T
    }

    fun clear() = entries.clear()
}
