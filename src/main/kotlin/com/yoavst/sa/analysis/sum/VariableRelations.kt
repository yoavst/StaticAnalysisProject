package com.yoavst.sa.analysis.sum

import com.yoavst.sa.analysis.utils.CompareResult
import com.yoavst.sa.analysis.utils.Lattice
import com.yoavst.sa.analysis.utils.isLessThanOrEqual
import com.yoavst.sa.utils.matrix.*

/**
 * Represent a set of constraints of the form:
 * Σαi*vi = c_n
 */

class VariableRelations private constructor(val isBottom: Boolean, val matrix: IMatrix) {

    val dimen get() = matrix.rows
    val isTop get() = !isBottom && matrix.rows == 0

    override fun toString(): String = if (isBottom) "Bottom" else if (dimen == 0) "Top" else "$matrix"

    companion object : Lattice<VariableRelations> {
        fun from(equations: IMatrix): VariableRelations = VariableRelations(false, equations.rrefMinimal())
        private fun fromReduced(equations: IMatrix) = VariableRelations(false, equations)

        override val bottom: VariableRelations = VariableRelations(true, IMatrix.Zero)

        override val top: VariableRelations = fromReduced(IMatrix.Zero)


        override fun gcd(item1: VariableRelations, item2: VariableRelations): VariableRelations {
            if (item1.isBottom || item2.isBottom) return bottom
            if (item1.isTop) return item2
            if (item2.isTop) return item1

            // intersect the linear subspaces
            // since items are not top, they are of type matrix and not zero
            item1.matrix as IMatrix.Matrix
            item2.matrix as IMatrix.Matrix

            val result = IMatrix.Matrix(item1.matrix.entriesCopy() + item2.matrix.entriesCopy())

            // check for bottom: check if we get c_n = 0
            val res = from(result)
            if (res.matrix is IMatrix.Matrix) {
                val invalidLinearSpace =
                    fromReduced(IMatrix.Matrix(arrayOf(Array(res.matrix.columns) { if (it == res.matrix.columns - 1) Fraction.ONE else Fraction.ZERO })))
                if (compare(res, invalidLinearSpace).isLessThanOrEqual())
                    return bottom
            }
            return res
        }

        @Suppress("LocalVariableName")
        override fun lcm(item1: VariableRelations, item2: VariableRelations): VariableRelations {
            if (item1.isTop || item2.isTop) return top
            if (item1.isBottom) return item2
            if (item2.isBottom) return item1

            // find a minimal union the affine subspaces
            // since items are not top, they are of type matrix and not zero
            item1.matrix as IMatrix.Matrix
            item2.matrix as IMatrix.Matrix

            val B = item1.matrix.dropColumn()
            val C = item2.matrix.dropColumn()

            val result = IMatrix.Matrix(item1.matrix.entriesCopy() + item2.matrix.entriesCopy())
            when (val res = from(result).matrix) {
                is IMatrix.Matrix -> {
                    // Given B+v, C+w
                    val intersectionItem = res.backSubstitute()
                    return if (intersectionItem == null) {
                        // no intersection between the affine subspaces
                        // return sp{B, C, v-w} + 1/2(v+w)
                        val v = item1.matrix.backSubstitute()!!
                        val w = item2.matrix.backSubstitute()!!
                        // now, there is a case that B+v is actually B. So yes, we need to check that.
//                        if (compare(from(plusOf(emptyList(), listOf(listOf(v)))), from(B)).isLessThanOrEqual()) {
//                            // return sp{B, C, w} instead
//                            from((plusOf(listOf(B, C), listOf(listOf(w))) as IMatrix.Matrix).toAffine(v))
//                        } else
//                            from((plusOf(listOf(B, C), listOf(listOf(v))) as IMatrix.Matrix).toAffine(w))
                        from(when(val resultMat = plusOf(listOf(B, C), listOf(listOf(v - w)))) {
                            is IMatrix.Matrix -> resultMat.toAffine((v + w) * Fraction(1,2))
                            IMatrix.Zero -> IMatrix.Zero
                        })
                    } else {
                        // return (B+C) + intersectionItem
                        from((plusOf(listOf(B, C)) as IMatrix.Matrix).toAffine(intersectionItem))
                    }
                }
                IMatrix.Zero -> {
                    error("Should not reach here")
                }
            }
        }

        override fun compare(item1: VariableRelations, item2: VariableRelations): CompareResult {
            when {
                item1 === item2 -> return CompareResult.Equal
                item1.isBottom -> {
                    return if (item2.isBottom) CompareResult.Equal else CompareResult.LessThan
                }
                item2.isBottom -> return CompareResult.MoreThan
                item1.isTop -> {
                    return if (item2.isTop) CompareResult.Equal else CompareResult.MoreThan
                }
                item2.isTop -> return CompareResult.LessThan


                else -> {
                    // since items are not top, they are of type matrix and not zero
                    item1.matrix as IMatrix.Matrix
                    item2.matrix as IMatrix.Matrix

                    // since both matrices are in reduced form, check if a matrix is sub matrix of the other.
                    val dim = IMatrix.Matrix(item1.matrix.entriesCopy() + item2.matrix.entriesCopy())
                        .rrefMinimal().rows
                    return when {
                        item1.dimen == item2.dimen && dim == item1.dimen -> CompareResult.Equal
                        item1.dimen < item2.dimen && dim == item2.dimen -> CompareResult.MoreThan
                        item2.dimen < item1.dimen && dim == item1.dimen -> CompareResult.LessThan
                        else -> CompareResult.NonComparable
                    }
                }
            }
        }
    }
}

