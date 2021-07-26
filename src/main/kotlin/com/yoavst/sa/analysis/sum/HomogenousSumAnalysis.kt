package com.yoavst.sa.analysis.sum

import com.yoavst.sa.analysis.utils.Lattice
import com.yoavst.sa.analysis.utils.isLessThanOrEqual
import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTAssumption.*
import com.yoavst.sa.parsing.ASTStatement
import com.yoavst.sa.parsing.ASTValue
import com.yoavst.sa.utils.matrix.Fraction
import com.yoavst.sa.utils.matrix.IMatrix
import com.yoavst.sa.utils.matrix.rowAddScale
import java.math.BigInteger

class HomogenousSumAnalysis(override val variableToIndex: Map<String, Int>) : VariableRelationAnalysis {
    override val lattice: Lattice<VariableRelations> = VariableRelations
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
        return when (value) {
            is ASTValue.ConstValue -> {
                if (value.value == BigInteger.ZERO) {
                    val assignmentState = matrixFromEquationTwoVariablesEqual(index, index, Fraction.ONE)
                    // check if need to do anything
                    if (lattice.compare(state, assignmentState).isLessThanOrEqual()) {
                        state
                    } else {
                        // need to invalidate the variable and then add the equation
                        lattice.gcd(removeIndex(state, index), assignmentState)
                    }
                } else {
                    // same as unknown value
                    removeIndex(state, index)
                }
            }
            is ASTValue.UnknownValue -> {
                // Invalidate the variable
                removeIndex(state, index)
            }
            is ASTValue.VariableOpConstValue -> {
                val otherVariableIndex = variableToIndex[value.variableName]!!
                if (otherVariableIndex == index) {
                    // same variable: V_ind := V_ind + C|0
                    if (value.const == BigInteger.ZERO || state.isTop) {
                        // V_ind := V_ind
                        state
                    } else {
                        // Invalidate the variable
                        removeIndex(state, index)
                    }
                } else if (value.const != BigInteger.ZERO) {
                    // Invalidate the variable
                    removeIndex(state, index)
                } else {
                    // V_ind := V_other
                    propagateEquality(state, index, otherVariableIndex)
                }
            }
            is ASTValue.VariableValue -> {
                val otherVariableIndex = variableToIndex[value.variableName]!!
                // if V := V
                if (otherVariableIndex == index)
                    state
                // V := U
                else propagateEquality(state, index, otherVariableIndex)
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
        otherIndex: Int
    ): VariableRelations {
        val variablesEquality = matrixFromEquationTwoVariablesEqual(index, otherIndex)
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
            return if (!constraints.isTop && lattice.compare(state, constraints).isLessThanOrEqual()) {
                lattice.bottom
            } else state
            // otherwise cannot save inequalities
        }

        val newInfo = varEqualValueToState(index, assumption.value)
        // merge the new info
        return lattice.gcd(state, newInfo)
    }

    private fun varEqualValueToState(variable: Int, value: ASTValue): VariableRelations = when (value) {
        is ASTValue.ConstValue -> {
            // support only zero assignments
            if (value.value == BigInteger.ZERO)
                matrixFromEquationTwoVariablesEqual(variable, oneIndex, Fraction.ZERO)
            else VariableRelations.top
        }
        ASTValue.UnknownValue -> VariableRelations.top
        is ASTValue.VariableOpConstValue ->
            when {
                value.const != BigInteger.ZERO -> VariableRelations.top
                variable == variableToIndex[value.variableName] -> VariableRelations.top
                else -> matrixFromEquation {
                    when (it) {
                        variable -> Fraction.ONE
                        variableToIndex[value.variableName] -> Fraction.MINUS_ONE
                        else -> Fraction.ZERO
                    }
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

    fun List<ASTAssertion.SumAssertion>.toState() = map { (variables1, variables2) ->
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