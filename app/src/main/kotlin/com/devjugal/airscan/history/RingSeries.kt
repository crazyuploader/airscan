package com.devjugal.airscan.history

/** The last [capacity] samples, oldest first. A null sample is a gap, for when the radio reported nothing. */
class RingSeries<T>(
    private val capacity: Int,
) {
    private val items = ArrayDeque<T?>()

    val size: Int get() = items.size

    fun add(value: T?) {
        items.addLast(value)
        while (items.size > capacity) items.removeFirst()
    }

    fun clear() = items.clear()

    fun snapshot(): List<T?> = items.toList()

    fun last(count: Int): List<T?> = items.toList().takeLast(count)
}
