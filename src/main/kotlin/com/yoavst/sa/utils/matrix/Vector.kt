package com.yoavst.sa.utils.matrix

/** Based on https://github.com/rahulgkatre/kotlin-matrix **/
data class Vector(val elements: Array<Fraction>) {
    val dimension = elements.size

    fun zero(): Vector {
        return Vector(Array(dimension) { Fraction.ZERO })
    }

    fun square(): Fraction {
        var sum = Fraction.ZERO
        for (e in elements) {
            sum += e * e
        }

        return sum
    }

    operator fun unaryMinus(): Vector {
        return Vector(Array(dimension) { i -> -elements[i] })
    }

    operator fun plus(v: Vector): Vector {
        return Vector(Array(dimension) { i -> elements[i] + v.elements[i] })
    }

    operator fun minus(v: Vector): Vector {
        return plus(-v)
    }

    operator fun times(f: Fraction): Vector {
        return Vector(Array(dimension) { i -> elements[i] * f })
    }

    operator fun div(f: Fraction): Vector {
        return Vector(Array(dimension) { i -> elements[i] / f })
    }

    fun max(): Fraction {
        return elements.maxByOrNull { it.abs() }!!
    }

    operator fun times(v: Vector): Fraction {
        assert(dimension == v.dimension)
        var sum: Fraction = Fraction.ZERO
        for (i in elements.indices) {
            sum += elements[i] * v.elements[i]
        }

        return sum
    }


    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector) return false
        if (dimension != other.dimension) return false
        if (!elements.contentEquals(other.elements)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = elements.contentHashCode()
        result = 31 * result + dimension
        return result
    }

    override fun toString(): String = elements.joinToString(prefix = "[", postfix = "]", separator = ", ")
}