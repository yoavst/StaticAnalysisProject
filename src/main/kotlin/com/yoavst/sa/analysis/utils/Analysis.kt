package com.yoavst.sa.analysis.utils

import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTStatement

abstract class Analysis<T>(val lattice: Lattice<T>) {
    abstract fun transfer(statement: ASTStatement, state: T): T

    fun join(vararg states: T) = lattice.lcmEx(*states)

    abstract fun checkAssertions(assertion: List<List<ASTAssertion>>, state: T): Boolean?
}
