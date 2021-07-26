package com.yoavst.sa.analysis.combined

import com.yoavst.sa.analysis.parity.Parity
import com.yoavst.sa.analysis.parity.ParityAnalysis
import com.yoavst.sa.analysis.sum.SumAnalysis
import com.yoavst.sa.analysis.sum.VariableRelations
import com.yoavst.sa.analysis.utils.*
import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTStatement

private typealias ParityState = SuperSetItem<DisjointItem<String, Parity>>

class JoinedAnalysis(private val variableToIndex: Map<String, Int>) : Analysis<Pair<ParityState, VariableRelations>> {
    private val sumAnalysis = SumAnalysis(variableToIndex)
    private val parityAnalysis = ParityAnalysis

    override val lattice: Lattice<Pair<ParityState, VariableRelations>> =
        PairLattice(parityAnalysis.lattice, sumAnalysis.lattice)

    override fun transfer(
        statement: ASTStatement,
        state: Pair<ParityState, VariableRelations>
    ): Pair<ParityState, VariableRelations> {
        return parityAnalysis.transfer(statement, state.first) to sumAnalysis.transfer(statement, state.second)
    }

    override fun checkAssertions(
        assertion: List<List<ASTAssertion>>,
        state: Pair<ParityState, VariableRelations>
    ): Boolean? {
        // bottom fulfill any assertion
        if (state.first.isBottom && state.second.isBottom)
            return true

        val filteredAssertions = assertion.filter {
            // filter those with sum constraint unsatisfied
            val sumOnly = it.filterIsInstance<ASTAssertion.SumAssertion>()
            !(sumOnly.isNotEmpty() && sumAnalysis.checkAssertions(listOf(sumOnly), state.second) != true)
        }.map { it.filterIsInstance<ASTAssertion.ParityAssertion>() }

        // if empty assertion after removing sum, return true
        if (filteredAssertions.any(List<ASTAssertion>::isEmpty))
            return true

        return parityAnalysis.checkAssertions(filteredAssertions, state.first)
    }
}