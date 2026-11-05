// Copyright 2026 BTMicFix Contributors. SPDX-License-Identifier: Apache-2.0
package com.btmicfix.diagnostics

/** Only structured, identifier-free fields enter this bounded journal. */
class EventJournal(private val capacity: Int = 400) {
    private val entries = ArrayDeque<String>()
    private val aliases = mutableMapOf<String, String>()
    fun alias(identity: String): String = aliases.getOrPut(identity) { "device-${aliases.size + 1}" }
    fun add(elapsedSeconds: Long, event: String) {
        entries.addLast("+${elapsedSeconds}s $event")
        while (entries.size > capacity) entries.removeFirst()
    }
    fun text(): String = entries.joinToString("\n\n")
}
