package com.yoavst.sa.analysis.utils

interface Lattice<T> {
    val bottom: T
    val top: T
    fun gcd(item1: T, item2: T): T
    fun lcm(item1: T, item2: T): T
    fun compare(item1: T, item2: T): CompareResult
}

enum class CompareResult {
    LessThan, Equal, MoreThan, NonComparable
}

fun <T> Lattice<T>.lcmEx(vararg ts: T) = ts.reduce(::lcm)
fun <T> Lattice<T>.lcmEx(ts: List<T>) = ts.reduce(::lcm)
fun <T> Lattice<T>.gcdEx(vararg ts: T) = ts.reduce(::gcd)
fun <T> Lattice<T>.gcdEx(ts: List<T>) = ts.reduce(::gcd)

class DisjointLattice<T>(private val lattice: Lattice<T>, private val dimension: Int) : Lattice<List<T>> {
    init {
        assert(dimension >= 1) { "Dimension must be greater than 1" }
    }

    override val bottom: List<T> = List(dimension) { lattice.bottom }
    override val top: List<T> = List(dimension) { lattice.top }

    override fun gcd(item1: List<T>, item2: List<T>): List<T> = List(dimension) { lattice.gcd(item1[it], item2[it]) }

    override fun lcm(item1: List<T>, item2: List<T>): List<T> = List(dimension) { lattice.lcm(item1[it], item2[it]) }

    override fun compare(item1: List<T>, item2: List<T>): CompareResult {
        var res = lattice.compare(item1[0], item2[0])
        if (res == CompareResult.NonComparable)
            return CompareResult.NonComparable

        for ((i1, i2) in item1.asSequence().zip(item2.asSequence())) {
            val curRes = lattice.compare(i1, i2)
            when {
                curRes == CompareResult.NonComparable -> return CompareResult.NonComparable
                res == curRes || curRes == CompareResult.Equal -> {
                }
                res == CompareResult.Equal -> {
                    // no longer equal, but less/greater
                    res = curRes
                }
                else -> return CompareResult.NonComparable
            }
        }
        return res
    }
}

private class TopSet<T> : Set<T> {
    override val size: Int
        get() = error("Should not call this method on top set")

    override fun contains(element: T): Boolean = error("Should not call this method on top set")

    override fun containsAll(elements: Collection<T>): Boolean = error("Should not call this method on top set")

    override fun isEmpty(): Boolean = error("Should not call this method on top set")

    override fun iterator(): Iterator<T> = error("Should not call this method on top set")
    override fun equals(other: Any?): Boolean = error("Should not call this method on top set")
    override fun hashCode(): Int = error("Should not call this method on top set")
}

fun <T> Set<T>.isPowerSetTop() = this is TopSet<*>

class PowerSetLattice<T> : Lattice<Set<T>> {
    override val bottom: Set<T> = emptySet()

    /* We'll treat this special ref as the top */
    override val top: Set<T> = TopSet()

    override fun gcd(item1: Set<T>, item2: Set<T>): Set<T> = when {
        item1.isPowerSetTop() -> item2
        item2.isPowerSetTop() -> item1
        else -> item1.intersect(item2)
    }

    override fun lcm(item1: Set<T>, item2: Set<T>): Set<T> = when {
        item1.isPowerSetTop() -> item1
        item2.isPowerSetTop() -> item2
        else -> item1.intersect(item2)
    }

    override fun compare(item1: Set<T>, item2: Set<T>): CompareResult {
        if (item1.isPowerSetTop()) {
            return if (item2.isPowerSetTop()) CompareResult.Equal else CompareResult.MoreThan
        } else if (item2.isPowerSetTop()) {
            return CompareResult.LessThan
        }

        val firstContainsSecond = item1.containsAll(item2)
        val secondContainsFirst = item2.containsAll(item1)

        return when {
            firstContainsSecond && secondContainsFirst -> CompareResult.Equal
            firstContainsSecond -> CompareResult.MoreThan
            secondContainsFirst -> CompareResult.LessThan
            else -> CompareResult.NonComparable
        }
    }

}

