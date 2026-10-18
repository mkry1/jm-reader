package com.par9uet.jm.utils

fun normalizeReleaseVersion(value: String): String = value.trim()
    .removePrefix("v")
    .removePrefix("V")
    .substringBefore(" ")

fun compareReleaseVersions(left: String, right: String): Int {
    // 个人版标签形如 1.2.6-personal.3，最后的修订号也参与比较。
    val numbers = Regex("\\d+")
    val leftParts = numbers.findAll(normalizeReleaseVersion(left)).map { it.value.toInt() }.toList()
    val rightParts = numbers.findAll(normalizeReleaseVersion(right)).map { it.value.toInt() }.toList()
    for (index in 0 until maxOf(leftParts.size, rightParts.size)) {
        val comparison = leftParts.getOrElse(index) { 0 }.compareTo(rightParts.getOrElse(index) { 0 })
        if (comparison != 0) return comparison
    }
    return 0
}
