package com.yoavst.sa

import java.math.BigInteger

fun <T> List<T>.updateAtIndex(index: Int, value: T) = mapIndexed { i, t -> if (i != index) t else value }
fun BigInteger.isEven() = !testBit(0)