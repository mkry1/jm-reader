package com.par9uet.jm.ui.screens.readScreen

/** 释放窗口外的解码任务和位图引用，磁盘上的图片缓存继续保留。 */
class ReaderMemoryWindow {
    var retainedRange: IntRange = IntRange.EMPTY
        private set
    private val releases = mutableMapOf<Int, () -> Unit>()

    fun contains(index: Int): Boolean = releases.containsKey(index)

    fun retain(total: Int, first: Int, last: Int, prefetch: Int) {
        retainedRange = if (total <= 0) IntRange.EMPTY else {
            (minOf(first, last) - prefetch).coerceAtLeast(0)..
                (maxOf(first, last) + prefetch).coerceAtMost(total - 1)
        }
        releases.keys.filter { it !in retainedRange }.forEach { index -> releases.remove(index)?.invoke() }
    }

    fun register(index: Int, release: () -> Unit) {
        if (index in retainedRange) releases.put(index, release)?.invoke() else release()
    }

    fun clear() {
        retainedRange = IntRange.EMPTY
        val pending = releases.values.toList()
        releases.clear()
        pending.forEach { it() }
    }
}
