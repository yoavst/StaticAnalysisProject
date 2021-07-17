package com.yoavst.sa.analysis.sum

import com.yoavst.sa.analysis.parity.ParityAnalysis
import com.yoavst.sa.analysis.utils.Analysis
import com.yoavst.sa.analysis.utils.isLessThanOrEqual
import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTAssumption.*
import com.yoavst.sa.parsing.ASTStatement
import com.yoavst.sa.parsing.ASTValue
import com.yoavst.sa.utils.matrix.Fraction
import com.yoavst.sa.utils.matrix.IMatrix
import com.yoavst.sa.utils.matrix.rowAddScale
import java.math.BigInteger

class SumAnalysis(private val variableToIndex: Map<String, Int>) : Analysis<VariableRelations>(VariableRelations) {
    private val oneIndex get() = variableToIndex.size

    override fun transfer(statement: ASTStatement, state: VariableRelations): VariableRelations {
        return when (statement) {
            // assertions are checked after the state is stable on each node
            is ASTStatement.AssertionStatement -> state
            is ASTStatement.AssignmentStatement ->
                updateStateFromAstValue(
                    state,
                    statement.variable,
                    statement.value
                )
            is ASTStatement.AssumeStatement -> when (statement.assumption) {
                TrueAssumption -> state
                FalseAssumption -> lattice.bottom
                is VariableAssumption -> updateStateFromAssumption(
                    state,
                    statement.assumption
                )
            }
            ASTStatement.SkipStatement -> state
        }
    }

    private fun updateStateFromAstValue(
        state: VariableRelations,
        variable: String,
        value: ASTValue
    ): VariableRelations {
        if (state.isBottom) return lattice.bottom
        val index = variableToIndex[variable]!!
        val assignmentState = varEqualValueToState(index, value)
        return when (value) {
            is ASTValue.ConstValue -> {
                // check if need to do anything
                if (lattice.compare(state, assignmentState).isLessThanOrEqual()) {
                    state
                } else {
                    // need to invalidate the variable
                    lattice.gcd(removeIndex(state, index), assignmentState)
                }
            }
            is ASTValue.UnknownValue -> {
                // Invalidate the variable
                removeIndex(state, index)
            }
            is ASTValue.VariableOpConstValue -> {
                if (assignmentState.isTop) {
                    // same variable: V_ind := V_ind + C|0
                    if (value.const == BigInteger.ZERO || state.isTop) {
                        // V_ind := V_ind
                        state
                    } else {
                        // V_ind := V_ind + C
                        // update the rows
                        state.matrix as IMatrix.Matrix
                        val rowIndex = state.matrix.entriesReadOnly().indexOfFirst { it[index] != Fraction.ZERO }
                        if (rowIndex == -1) {
                            // the index is not part of any equation (can have any value without influencing the others)
                            state
                        } else {
                            val data = state.matrix.entriesCopy()
                            for (row in data) {
                                row[oneIndex] -= row[index] * Fraction(value.const.toInt())
                            }
                            VariableRelations.from(IMatrix.Matrix(data))
                        }
                    }
                } else {
                    // V_ind := V_other + C|0
                    propagateEquality(state, index, assignmentState)
                }
            }
            is ASTValue.VariableValue -> {
                // if V := V
                if (assignmentState.isTop)
                    state
                // V := U
                else propagateEquality(state, index, assignmentState)
            }
        }
    }

    private fun removeIndex(state: VariableRelations, index: Int): VariableRelations {
        if (state.isTop || state.isBottom) return state

        state.matrix as IMatrix.Matrix

        val rowIndex = state.matrix.entriesReadOnly().indexOfFirst { it[index] != Fraction.ZERO }
        if (rowIndex == -1) {
            // the index is not part of any equation (can have any value without influencing the others)
            return state
        } else {
            // substitute the index with the other elements in the equation
            val data = state.matrix.entriesCopy()
            val rowValue = data[rowIndex][index]

            // 1. normalize the rows
            for (i in data.indices) {
                if (i != rowIndex && data[i][index] != Fraction.ZERO) {
                    rowAddScale(data, rowIndex, i, -data[i][index] / rowValue)
                }
            }

            // 2. remove row rowIndex
            for (i in data[0].indices) {
                data[rowIndex][i] = Fraction.ZERO
            }

            return VariableRelations.from(IMatrix.Matrix(data))
        }
    }

    private fun propagateEquality(
        state: VariableRelations,
        index: Int,
        variablesEquality: VariableRelations
    ): VariableRelations {
        // 1. Check if the equality already is the case
        //    If it is, then nothing needs to be done
        if (lattice.compare(state, variablesEquality).isLessThanOrEqual()) {
            return state
        }

        // 2. They weren't equal, so need to forget all the information about V_index and then add the new constraint
        return lattice.gcd(removeIndex(state, index), variablesEquality)
    }

    private fun updateStateFromAssumption(state: VariableRelations, assumption: VariableAssumption): VariableRelations {
        val index = variableToIndex[assumption.variableName]!!

        if (!assumption.isEqual) {
            val constraints = when (assumption.value) {
                is ASTValue.VariableValue -> {
                    val otherIndex = variableToIndex[assumption.value.variableName]!!
                    if (otherIndex == index)
                        return lattice.bottom

                    varEqualValueToState(index, assumption.value)
                }
                is ASTValue.VariableOpConstValue -> {
                    val otherIndex = variableToIndex[assumption.value.variableName]!!
                    if (otherIndex == index) {
                        return if (assumption.value.const == BigInteger.ZERO)
                            lattice.bottom
                        else {
                            // assume x != x + c -> no info.
                            state
                        }
                    }
                    varEqualValueToState(index, assumption.value)
                }
                is ASTValue.ConstValue -> varEqualValueToState(index, assumption.value)
                ASTValue.UnknownValue -> return state
            }

            // check if the constraints are true in our state.
            return if (lattice.compare(state, constraints).isLessThanOrEqual()) {
                lattice.bottom
            } else state
            // otherwise cannot save inequalities
        }

        val newInfo = varEqualValueToState(index, assumption.value)
        if (newInfo == lattice.top) {
            // happens in two cases:
            // 1. V := V
            // 2. V := V+C
            // The first case is cool assumption, the second case is bad assumption if C != 0
            if (assumption.value is ASTValue.VariableOpConstValue && assumption.value.const != BigInteger.ZERO)
                return lattice.bottom
            // otherwise we didn't learn any info
            return state
        }

        // otherwise, merge the new info
        return lattice.gcd(state, newInfo)
    }

    private fun varEqualValueToState(variable: Int, value: ASTValue): VariableRelations = when (value) {
        is ASTValue.ConstValue ->
            matrixFromEquationTwoVariablesEqual(variable, oneIndex, -Fraction(value.value.toInt()))
        ASTValue.UnknownValue -> VariableRelations.top
        is ASTValue.VariableOpConstValue ->
            if (variable == variableToIndex[value.variableName]) VariableRelations.top else
                matrixFromEquation {
                    when (it) {
                        variable -> Fraction.ONE
                        variableToIndex[value.variableName] -> Fraction.MINUS_ONE
                        oneIndex -> -Fraction(value.const.toInt())
                        else -> Fraction.ZERO
                    }
                }
        is ASTValue.VariableValue ->
            if (variable == variableToIndex[value.variableName])
                VariableRelations.top
            else
                matrixFromEquationTwoVariablesEqual(variable, variableToIndex[value.variableName]!!)
    }

    private inline fun matrixFromEquation(f: (Int) -> Fraction) =
        VariableRelations.from(IMatrix.Matrix(arrayOf(Array(variableToIndex.size + 1) { f(it) })))

    private fun matrixFromEquationTwoVariablesEqual(ind1: Int, ind2: Int, ind2Value: Fraction = Fraction.MINUS_ONE) =
        matrixFromEquation { if (it == ind1) Fraction.ONE else if (it == ind2) ind2Value else Fraction.ZERO }

    override fun checkAssertions(assertion: List<List<ASTAssertion>>, state: VariableRelations): Boolean? {
        // bottom fulfill any assertion
        if (state.isBottom)
            return true

        if (assertion.any { sub -> sub.any { it is ASTAssertion.ParityAssertion } }) {
            // parity assertion is not supported
            return null
        }
        @Suppress("UNCHECKED_CAST")
        assertion as List<List<ASTAssertion.SumAssertion>>

        if (assertion.isEmpty() && state.isTop) {
            return true
        }

        val assertionsAsState = assertion.map { it.toState() }
        return assertionsAsState.any {
            lattice.compare(state, it).isLessThanOrEqual()
        }
    }

    private fun List<ASTAssertion.SumAssertion>.toState() = map { (variables1, variables2) ->
        val counter = mutableMapOf<Int, Int>().withDefault { 0 }
        for (v in variables1.asSequence()) {
            val vInd = variableToIndex[v]!!
            counter[vInd] = counter.getValue(vInd) + 1
        }
        for (v in variables2.asSequence()) {
            val vInd = variableToIndex[v]!!
            counter[vInd] = counter.getValue(vInd) - 1
        }
        matrixFromEquation { Fraction(counter.getValue(it)) }
    }.reduce(lattice::gcd)
}