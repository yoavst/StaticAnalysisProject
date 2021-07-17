package com.yoavst.sa.utils.matrix

import kotlin.math.max


sealed class IMatrix(val rows: Int) {
    object Zero : IMatrix(0) {
        override fun toString(): String = "Zero"
    }

    class Matrix(private val entries: Array<Array<Fraction>>) : IMatrix(entries.size) {
        init {
            assert(entries.isNotEmpty())
        }

        val columns: Int = entries[0].size

        fun row(i: Int): Vector = Vector(entries[i])
        fun column(j: Int): Vector = Vector(Array(rows) { i -> entries[i][j] })

        operator fun get(i: Int, j: Int): Fraction = entries[i][j]

        fun entriesCopy(): Array<Array<Fraction>> = Array(rows) { i -> Array(columns) { j -> entries[i][j] } }

        fun entriesReadOnly(): Array<Array<Fraction>> = entries

        operator fun unaryMinus(): Matrix = Matrix(Array(rows) { i -> Array(columns) { j -> -entries[i][j] } })

        operator fun plus(m: Matrix): Matrix {
            assert(rows == m.rows && columns == m.columns)
            return Matrix(Array(rows) { i -> Array(columns) { j -> entries[i][j] + m.entries[i][j] } })
        }

        operator fun minus(m: Matrix): Matrix {
            assert(rows == m.rows && columns == m.columns)
            return Matrix(Array(rows) { i -> Array(columns) { j -> entries[i][j] - m.entries[i][j] } })
        }

        operator fun times(f: Fraction): Matrix {
            assert(f.denominator != 0)
            return Matrix(Array(rows) { i -> Array(columns) { j -> entries[i][j] * f } })
        }

        operator fun times(m: Matrix): Matrix {
            assert(columns == m.rows)
            return Matrix(Array(rows) { i -> Array(m.columns) { j -> row(i) * m.column(j) } })
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Matrix

            if (!entries.contentDeepEquals(other.entries)) return false
            if (rows != other.rows) return false
            if (columns != other.columns) return false

            return true
        }

        override fun hashCode(): Int {
            var result = entries.contentDeepHashCode()
            result = 31 * result + rows
            result = 31 * result + columns
            return result
        }

        override fun toString(): String {
            var output = ""
            val columnWidths = Array(columns) { 0 }
            for (j in 0 until columns) {
                var maxLength = 0
                for (i in 0 until rows) {
                    maxLength = max(entries[i][j].toString().length, maxLength)
                }

                columnWidths[j] = maxLength
            }

            for (i in 0 until rows) {
                output += "[ "
                for (j in 0 until columns) {
                    val remaining = columnWidths[j]
                    val string = entries[i][j].toString()
                    output += string
                    for (k in 0 until remaining - string.length + 2) {
                        output += " "
                    }
                }
                output += if (i != (rows - 1))
                    "]\n"
                else "]"
            }

            return output
        }
    }

    companion object {
        fun fromColumns(space: List<Vector>): IMatrix {
            if (space.isEmpty()) return Zero
            val data = Array(space[0].dimension) { i -> Array(space.size) { j -> space[j].elements[i] } }
            return Matrix(data)
        }

        fun fromRows(space: List<Vector>): IMatrix {
            if (space.isEmpty()) return Zero
            val data = Array(space.size) { i -> Array(space[0].dimension) { j -> space[i].elements[j] } }
            return Matrix(data)
        }

        fun identity(size: Int): IMatrix {
            if (size == 0) return Zero
            return Matrix(Array(size) { i -> Array(size) { j -> if (i == j) Fraction.ONE else Fraction.ZERO } })
        }
    }
}

fun IMatrix.transpose() = when (this) {
    is IMatrix.Matrix -> IMatrix.Matrix(Array(columns) { i -> Array(rows) { j -> this[j, i] } })
    IMatrix.Zero -> IMatrix.Zero
}

fun IMatrix.Matrix.kernel(): List<Vector> {
    val zeroRow by lazy { Array(columns) { Fraction.ZERO } }

    val n = columns
    val matrixData = entriesCopy()

    val newMatrix = arrayOfNulls<Array<Fraction>>(n)
    var j = 0
    for (i in 0 until n) {
        when {
            j >= matrixData.size -> newMatrix[i] = zeroRow
            matrixData[j][i] == Fraction.ONE -> {
                newMatrix[i] = matrixData[j]
                j++
            }
            else -> newMatrix[i] = zeroRow
        }
    }

    @Suppress("UNCHECKED_CAST")
    newMatrix as Array<Array<Fraction>>

    val basis = mutableListOf<Vector>()
    for (i in newMatrix.indices) {
        if (newMatrix[i][i] == Fraction.ZERO) {
            basis.add(Vector(Array(n) { ind -> if (ind == i) Fraction.MINUS_ONE else newMatrix[ind][i] }))
        }
    }
    return basis
}

fun IMatrix.Matrix.backSubstitute(): Vector? {
    val solution = Array(columns) { if (it != (columns - 1)) Fraction.ZERO else Fraction.ONE }
    for (row in rows - 1 downTo 0) {
        val rowVector = row(row)
        val firstIndex = rowVector.elements.indexOfFirst { it != Fraction.ZERO }
        if (firstIndex == -1)
            break
        else if (firstIndex == columns - 1)
            return null

        var sum = Fraction.ZERO
        for ((ind, value) in rowVector.elements.withIndex()) {
            if (ind == firstIndex) continue
            sum -= value * solution[ind]
        }
        solution[firstIndex] = sum / rowVector.elements[firstIndex]
    }
    return Vector(Array(columns - 1) { solution[it] })
}

fun IMatrix.rref(): IMatrix {
    val result = rrefInternal(this)
    return if (result.isEmpty()) IMatrix.Zero else IMatrix.Matrix(result)
}

fun IMatrix.rrefMinimal(): IMatrix {
    val result = rrefInternal(this).filter { it.any { x -> x != Fraction.ZERO } }
    return if (result.isEmpty()) IMatrix.Zero else IMatrix.Matrix(result.toTypedArray())
}


private fun rrefInternal(m: IMatrix): Array<Array<Fraction>> = when (m) {
    IMatrix.Zero -> emptyArray()
    is IMatrix.Matrix -> rrefInternalRaw(m.entriesCopy())
}

/**
 * Puts a matrix into reduced row echelon form
 *
 * @param matrix input matrix
 *
 * @return 2D result matrix
 */
private fun rrefInternalRaw(matrix: Array<Array<Fraction>>): Array<Array<Fraction>> {
    var lead = 0
    var i: Int

    // number of rows and columns in matrix
    val numRows = matrix.size
    val numColumns: Int = matrix[0].size
    for (rowInProgress in 0 until numRows) {
        if (numColumns <= lead) {
            break
        }
        var curRow = rowInProgress
        while (matrix[curRow][lead] == Fraction.ZERO) {
            curRow++
            if (numRows == curRow) {
                curRow = rowInProgress
                lead++
                if (numColumns == lead) {
                    return matrix
                }
            }
        }
        rowSwap(matrix, curRow, rowInProgress)
        if (matrix[rowInProgress][lead] != Fraction.ZERO) {
            rowScale(matrix, rowInProgress, Fraction(1) / matrix[rowInProgress][lead])
        }
        var i = 0
        while (i < numRows) {
            if (i != rowInProgress) {
                rowAddScale(matrix, rowInProgress, i, Fraction(-1) * matrix[i][lead])
            }
            i++
        }
        lead++
    }
    return matrix
}


fun rowSwap(matrix: Array<Array<Fraction>>, rowIndex1: Int, rowIndex2: Int) {
    // number of columns in matrix
    val numColumns: Int = matrix[0].size

    // holds number to be swapped
    for (k in 0 until numColumns) {
        val hold = matrix[rowIndex2][k]
        matrix[rowIndex2][k] = matrix[rowIndex1][k]
        matrix[rowIndex1][k] = hold
    }
}

fun rowScale(matrix: Array<Array<Fraction>>, rowIndex: Int, scalar: Fraction) {
    // number of columns in matrix
    val numColumns: Int = matrix[0].size
    for (k in 0 until numColumns) {
        matrix[rowIndex][k] *= scalar
    }
}

fun rowAddScale(matrix: Array<Array<Fraction>>, fromRow: Int, toRow: Int, scalar: Fraction) {
    // number of columns in matrix
    if (scalar != Fraction.ZERO) {
        val numColumns: Int = matrix[0].size
        for (k in 0 until numColumns) {
            matrix[toRow][k] += matrix[fromRow][k] * scalar
        }
    }
}

fun plusOf(matrices: List<IMatrix.Matrix>, kernels: List<List<Vector>> = emptyList()): IMatrix {
    assert(matrices.isNotEmpty() || kernels.isNotEmpty())
    when (val reducedBasis = IMatrix.fromRows(matrices.flatMap { it.kernel() } + kernels.flatten()).rrefMinimal()) {
        is IMatrix.Matrix -> {
            val kernel = reducedBasis.kernel()
            if (kernel.isEmpty()) return IMatrix.Zero
            return IMatrix.fromRows(kernel).rrefMinimal()
        }
        IMatrix.Zero -> {
            val size = if (matrices.isNotEmpty()) matrices[0].columns else kernels[0][0].dimension
            return IMatrix.identity(size)
        }
    }
}

fun IMatrix.Matrix.toAffine(solution: Vector): IMatrix.Matrix = IMatrix.Matrix(Array(rows) { i ->
        Array(columns + 1) { j ->
            if (j != columns) this[i, j]
            else {
                -row(i).elements.asSequence().mapIndexed { index, value -> solution.elements[index] * value }
                    .reduce(Fraction::plus)
            }
        }
    })

fun IMatrix.Matrix.dropColumn() = IMatrix.Matrix(Array(rows) { i -> Array(columns + -1) { j -> this[i, j] } })
