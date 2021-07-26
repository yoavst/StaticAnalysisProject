import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.yoavst.sa.analysis.combined.JoinedAnalysis
import com.yoavst.sa.analysis.combined.SumAnalysisWithParitySupport
import com.yoavst.sa.analysis.sum.HomogenousSumAnalysis
import com.yoavst.sa.analysis.sum.SumAnalysis
import com.yoavst.sa.analysis.sum.VariableRelations
import com.yoavst.sa.analysis.utils.AnalysisRunner
import com.yoavst.sa.parsing.ASTParser
import com.yoavst.sa.parsing.toCFG
import com.yoavst.sa.utils.matrix.Fraction
import com.yoavst.sa.utils.matrix.IMatrix
import com.yoavst.sa.utils.matrix.kernel
import com.yoavst.sa.utils.matrix.rrefMinimal
import java.io.File
import kotlin.system.exitProcess


fun main(args: Array<String>) {
//    test()
    val programRaw = File("src/test/resources/sum/i_plus_i.txt").readText()

    val parser = ASTParser()
    val program = parser.parseToEnd(programRaw)

    val cfg = program.toCFG() ?: return

    val mapping = program.variables.withIndex().associate { (ind, value) -> value to ind }
    println(mapping)

    // SumAnalysisWithParitySupport(SumAnalysis(mapping))
    val analysisRunner = AnalysisRunner(cfg, SumAnalysisWithParitySupport(HomogenousSumAnalysis(mapping)))

    analysisRunner.run()

    println()
    println(cfg.toString())


//    val analysisRunner = AnalysisRunner(cfg, ParityAnalysis)
//
//    analysisRunner.run()
//
//    println()
//    println(cfg.toString())

}

fun test() {

    val matrix = IMatrix.Matrix(
        arrayOf(
            arrayOf(Fraction(1), Fraction(1), Fraction(-6)),
        )
    )

    val matrix2 = IMatrix.Matrix(
        arrayOf(
            arrayOf(Fraction(1), Fraction(0), Fraction(0)),
            arrayOf(Fraction(1), Fraction(1), Fraction(0)),
        )
    )


    println(matrix)
    println(matrix.rrefMinimal())
    println("kernel: " + (matrix.rrefMinimal() as IMatrix.Matrix).kernel())

    println("\n")
    println(matrix2)
    println(matrix2.rrefMinimal())
    println("kernel: " + (matrix2.rrefMinimal() as IMatrix.Matrix).kernel())

    println("\n")

    val res = VariableRelations.lcm(VariableRelations.from(matrix), VariableRelations.from(matrix2))
    println(res)
    if (res.matrix is IMatrix.Matrix) {
        println("kernel: " + res.matrix.kernel())
    }

    exitProcess(0)
}