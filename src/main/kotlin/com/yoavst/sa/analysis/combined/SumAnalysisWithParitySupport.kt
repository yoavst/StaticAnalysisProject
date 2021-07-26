package com.yoavst.sa.analysis.combined

import com.yoavst.sa.analysis.parity.Parity
import com.yoavst.sa.analysis.sum.SumAnalysis
import com.yoavst.sa.analysis.sum.VariableRelationAnalysis
import com.yoavst.sa.analysis.sum.VariableRelations
import com.yoavst.sa.analysis.utils.*
import com.yoavst.sa.parsing.ASTAssertion
import com.yoavst.sa.utils.matrix.IMatrix
import com.yoavst.sa.utils.matrix.convertToWholeNumbers
import kotlin.math.abs

class SumAnalysisWithParitySupport(private val sumAnalysis: VariableRelationAnalysis) : Analysis<VariableRelations> by sumAnalysis {
    override fun checkAssertions(assertion: List<List<ASTAssertion>>, state: VariableRelations): Boolean {
        // bottom fulfill any assertion
        if (state.isBottom)
            return true

        // convert the state to F2
        val rrefMod2Matrix: List<Array<Int>>
        when (val wholeMatrix = state.matrix.convertToWholeNumbers()) {
            IMatrix.Zero -> {
                // top mean everything possible, and we require a variable to have a parity
                // so the assertion is wrong
                return false
            }
            is IMatrix.Matrix -> {
                // next step is take mod 2 to every variable
                val mod2Matrix = Array(wholeMatrix.rows) { i ->
                    Array(wholeMatrix.columns) { j ->
                        abs(wholeMatrix[i, j].numerator % 2)
                    }
                }
                val variables = wholeMatrix.columns - 1
                // now rref and take only non zero rows
                rrefMod2(mod2Matrix, variables)
                rrefMod2Matrix = mod2Matrix.filter { it.any { elem -> elem != 0 } }
                // now we have a set of equations on variable mod 2
                // since comparing linear space to DNF is NP-complete (yea, sucks)
                // we iterate over all the elements in the space, checking that they are a valid solution for the DNF.
            }
        }

        val filteredAssertions = assertion.filter {
            // filter those with sum constraint unsatisfied
            val sumOnly = it.filterIsInstance<ASTAssertion.SumAssertion>()
            !(sumOnly.isNotEmpty() && sumAnalysis.checkAssertions(listOf(sumOnly), state) != true)
        }.map { it.filterIsInstance<ASTAssertion.ParityAssertion>() }

        // if empty assertion after removing sum, return true
        if (filteredAssertions.isEmpty())
            return false
        if (filteredAssertions.any(List<ASTAssertion>::isEmpty))
            return true

        val assertionState =
            SuperSetItem(false, filteredAssertions.mapTo(mutableSetOf(), List<ASTAssertion.ParityAssertion>::toState))

        // iterate the solutions of the linear system
        // in case of no solution, it is bottom and therefore returning true
        val solution = rrefMod2Matrix.backSubstituteMod2() ?: return true
        val kernel = rrefMod2Matrix.map { it.dropLast(1).toTypedArray() }.kernel()

        // remove the variables that are not in the assertions
        val interestingVariables =
            filteredAssertions.flatMapTo(mutableSetOf()) { it.flatMap(ASTAssertion.ParityAssertion::variables) }
                .mapTo(mutableSetOf()) { sumAnalysis.variableToIndex[it]!! }
        val reverseVariableMapping = sumAnalysis.variableToIndex.entries.associate { (k, v) -> v to k }

        val tempNewMapping = mutableMapOf<Int, String>()
        var j = 0
        for (i in solution.indices) {
            if (i in interestingVariables) {
                tempNewMapping[j++] = reverseVariableMapping[i]!!
            }
        }

        val filteredSolution = solution.filterIndexed { index, _ -> index in interestingVariables }
        val filteredKernelTemp =
            kernel.map { it.filterIndexed { index, _ -> index in interestingVariables }.toTypedArray() }.toTypedArray()
        rrefMod2(filteredKernelTemp, filteredKernelTemp.size)
        val filteredKernel = filteredKernelTemp.filter { it.any { item -> item != 0 } }.let {
            if (it.isEmpty()) it else it.kernel()
        }

        val parityLattice: SupersetLattice<DisjointItem<String, Parity>> = SupersetLattice(DisjointLattice(Parity))
        if (filteredKernel.isEmpty()) {
            // single solution only, check it.
            val parityState = SuperSetItem(
                false, setOf(
                    DisjointItem(
                        Parity.Unknown,
                        filteredSolution.withIndex()
                            .associate { (index, value) -> tempNewMapping[index]!! to Parity.fromIsEven(value == 0) })
                )
            )

            return parityLattice.compare(parityState, assertionState).isLessThanOrEqual()
        }

        // Go over any solution, convert it to parity form, and use ParityLattice to check.
        val zeroArray = Array(filteredSolution.size) { 0 }
        return filteredKernel.map { listOf(zeroArray, it) }.getCartesianProduct().all { product ->
            val vector =
                product.fold(filteredSolution) { acc, cur -> if (cur === zeroArray) acc else List(acc.size) { acc[it] + cur[it] } }
            // convert vector to parity state
            val parityState = SuperSetItem(
                false, setOf(
                    DisjointItem(
                        Parity.Unknown,
                        vector.withIndex()
                            .associate { (index, value) -> tempNewMapping[index]!! to Parity.fromIsEven(value == 0) })
                )
            )

            parityLattice.compare(parityState, assertionState).isLessThanOrEqual()
        }
    }
}


private fun <T> Array<T>.swap(i: Int, j: Int) {
    if (i != j) {
        val tmp = this[i]
        this[i] = this[j]
        this[j] = tmp
    }
}

private fun rrefMod2(mod2Matrix: Array<Array<Int>>, variables: Int) {
    var currentReadOnlyRows = 0
    for (i in 0 until variables) {
        // find a row with row[i] != 0
        val rowIndex = mod2Matrix.withIndex()
            .indexOfFirst { (ind, row) -> ind >= currentReadOnlyRows && row[i] != 0 }
        // if no such row, try the next one
        if (rowIndex < 0)
            continue
        // if there is, move it to currentReadOnlyRows
        mod2Matrix.swap(currentReadOnlyRows, rowIndex)
        currentReadOnlyRows++
        // if there are no more rows, you can finish.
        if (currentReadOnlyRows == mod2Matrix.size)
            break
        // now reduce the other rows
        for (j in currentReadOnlyRows until mod2Matrix.size) {
            if (mod2Matrix[j][i] != 0) {
                // xor the lines
                for (k in i until mod2Matrix[j].size) {
                    mod2Matrix[j][k] = mod2Matrix[j][k] xor mod2Matrix[i][k]
                }
            }
        }
    }
}

fun List<Array<Int>>.backSubstituteMod2(): Array<Int>? {
    val columns = this[0].size
    val rows = size

    val solution = Array(columns) { if (it != (columns - 1)) 0 else 1 }
    for (row in rows - 1 downTo 0) {
        val rowVector = this[row]
        val firstIndex = rowVector.indexOfFirst { it != 0 }
        if (firstIndex == -1)
            break
        else if (firstIndex == columns - 1)
            return null

        var sum = 0
        for ((ind, value) in rowVector.withIndex()) {
            if (ind == firstIndex) continue
            sum -= value * solution[ind]
        }
        solution[firstIndex] = sum / rowVector[firstIndex]
    }
    return Array(columns - 1) { abs(solution[it] % 2) }
}

fun List<Array<Int>>.kernel(): List<Array<Int>> {
    val columns = this[0].size

    val zeroRow by lazy { Array(columns) { 0 } }

    val matrixData = map { it.copyOf() }

    val newMatrix = arrayOfNulls<Array<Int>>(columns)
    var j = 0
    for (i in 0 until columns) {
        when {
            j >= matrixData.size -> newMatrix[i] = zeroRow
            matrixData[j][i] == 1 -> {
                newMatrix[i] = matrixData[j]
                j++
            }
            else -> newMatrix[i] = zeroRow
        }
    }

    @Suppress("UNCHECKED_CAST")
    newMatrix as Array<Array<Int>>

    val basis = mutableListOf<Array<Int>>()
    for (i in newMatrix.indices) {
        if (newMatrix[i][i] == 0) {
            basis.add(Array(columns) { ind -> if (ind == i) 1 else abs(newMatrix[ind][i] % 2) })
        }
    }
    return basis
}

fun <T> Collection<Iterable<T>>.getCartesianProduct(): Iterable<List<T>> =
    if (isEmpty()) emptySet()
    else drop(1).fold(first().map(::listOf)) { acc, iterable ->
        acc.flatMap { list -> iterable.map(list::plus) }
    }

private fun List<ASTAssertion.ParityAssertion>.toState() =
    DisjointItem(Parity.Unknown, this.associate { it.variableName to Parity.fromIsEven(it.isEven) })