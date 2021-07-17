package com.yoavst.sa.utils.matrix

/**
 * Represents a rational number.
 * Assumes there is no overflow.
 *
 * Based on https://github.com/rahulgkatre/kotlin-matrix , but with support for rational number only.
 */
data class Fraction(val numerator: Int, val denominator: Int) : Comparable<Fraction> {
    constructor(numerator: Int) : this(numerator, 1)

    companion object {
        val ZERO = Fraction(0)
        val ONE = Fraction(1)
        val MINUS_ONE = Fraction(-1)

        fun gcd(n1: Int, n2: Int): Int = when {
            n1 == 0 && n2 == 0 -> {
                1
            }
            n2 == 0 -> {
                n1
            }
            else -> {
                gcd(n2, n1 % n2)
            }
        }
    }

    private fun simplify(): Fraction {
        if (denominator == 0) {
            throw Exception("Cannot divide by 0")
        }

        return when (numerator) {
            0 -> {
                ZERO
            }
            denominator -> {
                ONE
            }
            else -> {
                val gcd = gcd(numerator, denominator)
                if (gcd == 1)
                    this
                else {
                    if ((numerator > 0) != (denominator > 0)) {
                        Fraction(-kotlin.math.abs(numerator / gcd), kotlin.math.abs(denominator / gcd))
                    } else
                        Fraction(numerator / gcd, denominator / gcd)
                }
            }
        }
    }

    fun invert(): Fraction {
        return Fraction(denominator, numerator)
    }

    operator fun unaryMinus(): Fraction {
        return Fraction(-numerator, denominator).simplify()
    }

    operator fun plus(f: Fraction): Fraction {
        val n = f.numerator * denominator + numerator * f.denominator
        val d = denominator * f.denominator
        return Fraction(n, d).simplify()
    }

    operator fun minus(f: Fraction): Fraction {
        val n = numerator * f.denominator - f.numerator * denominator
        val d = denominator * f.denominator
        return Fraction(n, d).simplify()
    }

    operator fun times(f: Fraction): Fraction {
        return Fraction(numerator * f.numerator, denominator * f.denominator).simplify()
    }

    operator fun div(f: Fraction): Fraction {
        return Fraction(numerator * f.denominator, denominator * f.numerator).simplify()
    }

    override operator fun compareTo(other: Fraction): Int {
        if (this.denominator == other.denominator) return this.numerator - other.numerator
        return this.numerator * other.denominator - other.numerator * this.denominator
    }

    override fun toString(): String = if (denominator == 1) {
        "$numerator"
    } else {
        "$numerator / $denominator"
    }

    fun abs(): Fraction = Fraction(kotlin.math.abs(numerator), kotlin.math.abs(denominator))

    override fun equals(other: Any?): Boolean {
        if (other is Int) {
            val simplified = simplify()
            return simplified.denominator == 1 && other == simplified.numerator
        } else if (other is Fraction) {
            val simplified1 = simplify()
            val simplified2 = other.simplify()
            return simplified1.numerator == simplified2.numerator && simplified1.denominator == simplified2.denominator
        }
        return false
    }
}
