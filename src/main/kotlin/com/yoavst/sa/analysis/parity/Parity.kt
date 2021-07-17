package com.yoavst.sa.analysis.parity

import com.yoavst.sa.analysis.utils.CompareResult
import com.yoavst.sa.analysis.utils.Lattice

enum class Parity {
    Bottom, Even, Odd, Unknown;

    companion object : Lattice<Parity> {
        override val bottom: Parity = Bottom
        override val top: Parity = Unknown
        override fun gcd(item1: Parity, item2: Parity) = when {
            item1 == Unknown -> item2
            item2 == Unknown -> item1
            item1 == Even && item2 == Even -> Even
            item1 == Even -> Bottom
            item1 == Odd && item2 == Odd -> Odd
            else -> Bottom
        }

        override fun lcm(item1: Parity, item2: Parity) = when {
            item1 == Bottom -> item2
            item2 == Bottom -> item1
            item1 == Even && item2 == Even -> Even
            item1 == Even -> Unknown
            item1 == Odd && item2 == Odd -> Odd
            else -> Unknown
        }

        override fun compare(item1: Parity, item2: Parity) = when {
            item1 == item2 -> CompareResult.Equal
            item1 == Bottom || item2 == Unknown -> CompareResult.LessThan
            item1 == Unknown || item2 == Bottom -> CompareResult.MoreThan
            else -> CompareResult.NonComparable
        }

        fun fromIsEven(isEven: Boolean) = if (isEven) Parity.Even else Odd
    }
}