package com.yoavst.sa.analysis.parity

import com.yoavst.sa.analysis.parity.Parity.*
import com.yoavst.sa.analysis.parity.Parity.Companion.fromIsEven
import com.yoavst.sa.analysis.utils.*
import com.yoavst.sa.isEven
import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.parsing.ASTAssumption.*
import com.yoavst.sa.parsing.ASTStatement
import com.yoavst.sa.parsing.ASTStatement.*
import com.yoavst.sa.parsing.ASTValue
import com.yoavst.sa.parsing.ASTValue.*
import java.math.BigInteger

private typealias InnerState = DisjointItem<String, Parity>
private typealias State = SuperSetItem<InnerState>

object ParityAnalysis : Analysis<State> {
    override val lattice: SupersetLattice<InnerState> = SupersetLattice(DisjointLattice(Parity))

    override fun transfer(statement: ASTStatement, state: State): State {
        return when (statement) {
            // assertions are checked after the state is stable on each node
            is AssertionStatement -> state
            is AssignmentStatement ->
                reduce(updateStateFromAstValue(state, statement.variable, statement.value))
            is AssumeStatement -> when (statement.assumption) {
                FalseAssumption -> lattice.bottom
                TrueAssumption -> state
                is VariableAssumption -> updateStateFromAssumption(state, statement.assumption)
            }
            SkipStatement -> state
        }
    }


    private fun updateStateFromAssumption(fullState: State, assumption: VariableAssumption): State {
        // check x != x assumptions
        // other x != y doesn't give us information about parity.
        if (!assumption.isEqual) {
            return when {
                assumption.value is VariableValue && assumption.value.variableName == assumption.variableName -> lattice.bottom
                assumption.value is VariableOpConstValue
                        && assumption.value.variableName == assumption.variableName
                        && assumption.value.const == BigInteger.ZERO -> lattice.bottom
                else -> fullState
            }
        }

        // from now the assumption is on equality

        if (assumption.value is UnknownValue) {
            // Doesn't give us any information
            return fullState
        }

        if (assumption.value is ConstValue) {
            val expected = fromIsEven(assumption.value.value.isEven())
            val variableName = assumption.variableName
            val newStates = mutableSetOf<InnerState>()
            // now go over each state, and check if it can match the assumption.
            // if it cannot match (wrong parity), drop this state

            for (state in fullState.data) {
                val current = state[variableName]
                if (current == expected || current == Unknown) {
                    newStates.add(state.updateKey(variableName, expected))
                }
            }
            // if there is no possible state, we are at the bottom.
            return if (newStates.isNotEmpty())
                State(false, newStates)
            else {
                // the assumption is incorrect
                lattice.bottom
            }

        }

        // now we know we have an assumption of the form x = y + c
        // If we have information on x, it sets info on y, and the other way around.
        val newStates = mutableSetOf<InnerState>()
        val variableName = assumption.variableName
        for (state in fullState.data) {
            val otherVariableName: String
            val isSameSign: Boolean
            when (assumption.value) {
                is VariableValue -> {
                    otherVariableName = assumption.value.variableName
                    isSameSign = true
                }
                is VariableOpConstValue -> {
                    otherVariableName = assumption.value.variableName
                    isSameSign = assumption.value.const.isEven()
                }
                else -> error("Cannot happen")
            }
            var variableState = state[variableName]
            var otherVariableState = state[otherVariableName]
            if (!isSameSign) {
                // try to replace the sign of one of the variables.
                when {
                    variableState == Odd -> {
                        variableState = Even
                    }
                    variableState == Even -> {
                        variableState = Odd
                    }
                    otherVariableState == Odd -> {
                        otherVariableState = Even
                    }
                    otherVariableState == Even -> {
                        otherVariableState = Odd
                    }
                    else -> { /* they are unknown or bottom */
                    }
                }
            }
            // check that the variables has the same sign.
            // If we replaced the sign of one of the variables, then we had x = y + ODD before, and the check will be correct also in this case.
            val newValue = Parity.gcd(otherVariableState, variableState)
            if (newValue != Bottom) {
                // possible state
                val newState = state.updateKey(variableName, newValue).updateKey(otherVariableName, newValue)
                newStates.add(newState)
            }
        }
        return if (newStates.isNotEmpty())
            State(false, newStates)
        else {
            // the assumption is incorrect
            lattice.bottom
        }
    }

    private fun updateStateFromAstValue(fullState: State, variable: String, value: ASTValue): State {
        if (fullState.isBottom) {
            // If the state before was bottom, assigning a variable will still keep it bottom.
            return fullState
        }

        val baseState = when {
            fullState.isTop() -> {
                // since {} == {()}, we can replace them so the code will work
                State(false, setOf(lattice.getBaseTop()))
            }
            else -> fullState
        }

        return State(false, baseState.data.flatMapTo(mutableSetOf()) { state ->
            when (value) {
                is ConstValue -> listOf(state.updateKey(variable, fromIsEven(value.value.isEven())))
                UnknownValue -> listOf(state.updateKey(variable, Unknown))
                else -> {
                    val otherVariableName: String
                    val isSameSign: Boolean
                    when (value) {
                        is VariableValue -> {
                            otherVariableName = value.variableName
                            isSameSign = true
                        }
                        is VariableOpConstValue -> {
                            otherVariableName = value.variableName
                            isSameSign = value.const.isEven()
                        }
                        else -> error("Cannot happen")
                    }
                    when (state[otherVariableName]) {
                        Bottom -> {
                            // assigning bottom to variable
                            listOf(state.updateKey(variable, Bottom))
                        }
                        Even -> listOf(state.updateKey(variable, fromIsEven(isSameSign)))
                        Odd -> listOf(state.updateKey(variable, fromIsEven(!isSameSign)))
                        Unknown -> {
                            when {
                                // here it becomes complicated, as we need to make a connection between the values of the two variables
                                isSameSign -> listOf(
                                    state.updateKey(variable, Even).updateKey(otherVariableName, Even),
                                    state.updateKey(variable, Odd).updateKey(otherVariableName, Odd)
                                )
                                else -> listOf(
                                    state.updateKey(variable, Even).updateKey(otherVariableName, Odd),
                                    state.updateKey(variable, Odd).updateKey(otherVariableName, Even)
                                )
                            }
                        }
                    }
                }
            }
        })
    }

    private fun reduce(fullState: State): State {
        // cannot reduce top or bottom
        if (fullState.isBottom || fullState.isTop())
            return fullState

        // we'll do a n**2 reducing. maybe we can do this better?
        val newStates = fullState.data.filterTo(mutableSetOf()) { state ->
            fullState.data.all {
                if (state !== it) {
                    val comparisonResult = lattice.lattice.compare(state, it)
                    comparisonResult == CompareResult.NonComparable || comparisonResult == CompareResult.MoreThan
                } else true
            }
        }

        return if (newStates.size == 1 && newStates.toList()[0] == lattice.lattice.top) {
            // we merge the {()} with {}
            State(false, emptySet())
        } else State(false, newStates)
    }

    override fun checkAssertions(assertion: List<List<ASTAssertion>>, state: State): Boolean? {
        // bottom fulfill any assertion
        if (state.isBottom)
            return true

        if (assertion.any { sub -> sub.any { it is ASTAssertion.SumAssertion } }) {
            // sum assertion is not supported
            return null
        }
        @Suppress("UNCHECKED_CAST")
        assertion as List<List<ASTAssertion.ParityAssertion>>

        if (assertion.isEmpty() && state.isTop()) {
            return true
        }

        val assertionsAsState = assertion.map { it.toState() }
        return state.data.all { possibleState ->
            assertionsAsState.any {
                lattice.lattice.compare(possibleState, it).isLessThanOrEqual()
            }
        }

    }

    private fun List<ASTAssertion.ParityAssertion>.toState() =
        InnerState(Unknown, this.associate { it.variableName to fromIsEven(it.isEven) })


}

