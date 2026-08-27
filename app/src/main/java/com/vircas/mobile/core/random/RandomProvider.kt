package com.vircas.mobile.core.random

import java.security.SecureRandom
import kotlin.random.Random

interface RandomProvider {
    fun nextInt(from: Int, until: Int): Int
    fun nextDouble(): Double
}

class SecureRandomProvider : RandomProvider {
    private val random = SecureRandom()
    override fun nextInt(from: Int, until: Int): Int = from + random.nextInt(until - from)
    override fun nextDouble(): Double = random.nextDouble()
}

class SeededRandomProvider(seed: Long) : RandomProvider {
    private val random = Random(seed)
    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)
    override fun nextDouble(): Double = random.nextDouble()
}
