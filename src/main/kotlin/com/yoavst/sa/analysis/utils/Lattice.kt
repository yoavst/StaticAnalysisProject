package com.yoavst.sa.analysis.utils

interface Lattice<T> {
    val bottom: T
    val top: T
    fun gcd(item1: T, item2: T): T
    fun lcm(item1: T, item2: T): T
    fun compare(item1: T, item2: T): CompareResult

    // some syntactic sugar

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("gcd1")
    infix fun T.gcd(t: T) = gcd(this, t)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("lcm1")
    infix fun T.lcm(t: T) = lcm(this, t)

    @Suppress("INAPPLICABLE_JVM_NAME")
    @JvmName("compare1")
    infix fun T.compare(t: T) = compare(this, t)
}

enum class CompareResult {
    LessThan, Equal, MoreThan, NonComparable
}

fun CompareResult.isLessThanOrEqual() = this == CompareResult.Equal || this == CompareResult.LessThan

fun <T> Lattice<T>.lcmEx(vararg ts: T) = ts.reduce(::lcm)
fun <T> Lattice<T>.lcmEx(ts: List<T>) = ts.reduce(::lcm)
fun <T> Lattice<T>.gcdEx(vararg ts: T) = ts.reduce(::gcd)
fun <T> Lattice<T>.gcdEx(ts: List<T>) = ts.reduce(::gcd)

data class DisjointItem<K, T>(val defaultValue: T, val map: Map<K, T> = emptyMap()) {
    operator fun get(key: K) = map.getOrDefault(key, defaultValue)

    override fun toString(): String = if (map.isEmpty()) "{$defaultValue}" else "$map"
}

fun <K, T> DisjointItem<K, T>.updateKey(key: K, value: T): DisjointItem<K, T> = when {
    this[key] == value -> this
    value == defaultValue && key in map -> {
        DisjointItem(defaultValue, map - key)
    }
    else -> {
        DisjointItem(defaultValue, map + (key to value))
    }
}

class DisjointLattice<T, K>(private val lattice: Lattice<T>) :
    Lattice<DisjointItem<K, T>> {
    override val bottom: DisjointItem<K, T> = DisjointItem(lattice.bottom)
    override val top: DisjointItem<K, T> = DisjointItem(lattice.top)

    override fun gcd(item1: DisjointItem<K, T>, item2: DisjointItem<K, T>): DisjointItem<K, T> = with(lattice) {
        if (item1 === item2) return@with item1
        // first, gcd their default value
        val newDefault = item1.defaultValue gcd item2.defaultValue

        // then, merge the maps
        when {
            item1.map.isEmpty() -> DisjointItem(newDefault, item2.map)
            item2.map.isEmpty() -> DisjointItem(newDefault, item1.map)
            else -> {
                val joinedMap = mutableMapOf<K, T>()
                for ((key, value) in item1.map) {
                    val otherValue = item2[key]
                    val newValue = value gcd otherValue
                    if (newValue != newDefault)
                        joinedMap[key] = newValue
                }
                for ((key, value) in item2.map) {
                    if (value != newDefault)
                        joinedMap.computeIfAbsent(key) { value }
                }
                DisjointItem(newDefault, joinedMap)
            }
        }
    }

    override fun lcm(item1: DisjointItem<K, T>, item2: DisjointItem<K, T>): DisjointItem<K, T> = with(lattice) {
        if (item1 === item2) return item1
        // first, lcm their default value
        val newDefault = item1.defaultValue lcm item2.defaultValue

        // then, merge the maps
        when {
            item1.map.isEmpty() -> DisjointItem(newDefault, item2.map)
            item2.map.isEmpty() -> DisjointItem(newDefault, item1.map)
            else -> {
                val joinedMap = mutableMapOf<K, T>()
                for ((key, value) in item1.map) {
                    val otherValue = item2[key]
                    val newValue = value lcm otherValue
                    if (newValue != newDefault)
                        joinedMap[key] = newValue
                }
                for ((key, value) in item2.map) {
                    if (value != newDefault)
                        joinedMap.computeIfAbsent(key) { value }
                }
                DisjointItem(newDefault, joinedMap)
            }
        }
    }

    override fun compare(item1: DisjointItem<K, T>, item2: DisjointItem<K, T>): CompareResult = with(lattice) {
        if (item1 === item2) return CompareResult.Equal

        // first, check if need to compare default
        var res = CompareResult.Equal

        if (res == CompareResult.NonComparable)
            return CompareResult.NonComparable

        // then check elements from item1
        for ((key, value) in item1.map) {
            val curRes = lattice.compare(value, item2[key])
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

        // and now from item2
        for ((key, value) in item2.map) {
            val curRes = lattice.compare(item1[key], value)
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

class SuperSetItem<T>(val isBottom: Boolean, val data: Set<T>) {
    override fun toString(): String = if (isBottom) "Bottom" else if (isTop()) "Top" else data.toString()
    fun isTop() = !isBottom && data.isEmpty()
}

/**
 * Every element is of the form {s ⊆ T | φ(s) }, where:
 * 1. T is a constraints lattice
 *  a. Top is no constraints
 *  b. Bottom is all constraints - invalid
 *  c. x < y if x is y plus more constraints.
 *  2. φ is represented by {t_1, ..., t_n }, and equals to (t_1(s) || ... || t_n(s))
 */
class SupersetLattice<T>(val lattice: Lattice<T>) : Lattice<SuperSetItem<T>> {
    override val bottom: SuperSetItem<T> = SuperSetItem(true, emptySet())
    override val top: SuperSetItem<T> = SuperSetItem(false, emptySet())


    /** lcm over constraints is union of the constraints **/
    override fun lcm(item1: SuperSetItem<T>, item2: SuperSetItem<T>): SuperSetItem<T> = when {
        item1.isTop() || item2.isTop() -> top
        item1.isBottom -> item2
        item2.isBottom -> item1
        else -> SuperSetItem(false, item1.data + item2.data)
    }

    override fun gcd(item1: SuperSetItem<T>, item2: SuperSetItem<T>): SuperSetItem<T> = when {
        item1.isTop() -> item2
        item2.isTop() -> item1
        item1.isBottom || item2.isBottom -> bottom
        else -> SuperSetItem(false, item1.data.intersect(item2.data))
    }

    override fun compare(item1: SuperSetItem<T>, item2: SuperSetItem<T>): CompareResult = when {
        item1.isTop() -> if (item2.isTop()) CompareResult.Equal else CompareResult.MoreThan
        item2.isTop() -> CompareResult.LessThan
        item1.isBottom -> if (item2.isBottom) CompareResult.Equal else CompareResult.LessThan
        item2.isBottom -> CompareResult.MoreThan
        else -> {
            val firstContainsSecond = item1.data.containsAll(item2.data)
            val secondContainsFirst = item2.data.containsAll(item1.data)

            when {
                firstContainsSecond && secondContainsFirst -> CompareResult.Equal
                firstContainsSecond -> CompareResult.MoreThan
                secondContainsFirst -> CompareResult.LessThan
                else -> CompareResult.NonComparable
            }
        }
    }

    fun getBaseTop() = lattice.top
}
