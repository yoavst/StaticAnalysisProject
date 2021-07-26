package com.yoavst.sa.analysis.utils

import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTStatement

interface Analysis<T> {
    val lattice: Lattice<T>

    fun transfer(statement: ASTStatement, state: T): T

    fun join(stateA: T, stateB: T) = lattice.lcm(stateA, stateB)

    fun checkAssertions(assertion: List<List<ASTAssertion>>, state: T): Boolean?
}
