import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.yoavst.sa.analysis.combined.JoinedAnalysis
import com.yoavst.sa.analysis.combined.SumAnalysisWithParitySupport
import com.yoavst.sa.analysis.parity.ParityAnalysis
import com.yoavst.sa.analysis.sum.HomogenousSumAnalysis
import com.yoavst.sa.analysis.sum.SumAnalysis
import com.yoavst.sa.analysis.utils.AnalysisRunner
import com.yoavst.sa.parsing.ASTParser
import com.yoavst.sa.parsing.toCFG
import java.io.File


fun main(args: Array<String>) {
    if (args.size !in 2..4) {
        printUsage()
        return
    }

    val mode = args[0].toIntOrNull()?.takeIf { it in 0..5 } ?: run {
        printUsage()
        return
    }


    val path = args[1]

    var shouldGraphviz = false
    var shouldGraphvizFinal = false
    if (args.size == 3) {
        val graphvizMode = args[0].toIntOrNull().takeIf { it in 0..3 } ?: run {
            printUsage()
            return
        }
        when(graphvizMode) {
            1 -> shouldGraphviz = true
            2 -> shouldGraphvizFinal = true
        }
    }

    val programRaw = File(path).readText()

    val parser = ASTParser()
    val program = parser.parseToEnd(programRaw)

    val cfg = program.toCFG() ?: return

    val mapping = program.variables.withIndex().associate { (ind, value) -> value to ind }
    println(mapping)

    val analysis = arrayOf(
        ParityAnalysis,
        SumAnalysis(mapping),
        JoinedAnalysis(mapping),
        SumAnalysisWithParitySupport(SumAnalysis(mapping)),
        SumAnalysisWithParitySupport(HomogenousSumAnalysis(mapping))
    )[mode]

    val analysisRunner = AnalysisRunner(cfg, analysis, shouldGraphviz, shouldGraphvizFinal)

    analysisRunner.run()

    println()
    println(cfg.toString())
}

fun printUsage() {
    println("Usage: java -jar analysis.jar MODE PATH_TO_PROGRAM [GRAPHVIZ_MODE]")
    println("""Mode:
            |   0 - Parity analysis
            |   1 - Sum analysis
            |   2 - Cartesian analysis
            |   3 - Sum analysis with parity support
            |   4 - Homogenous sum analysis with parity support 
            |Graphviz mode:
            |   0 - Disabled
            |   1 - Print state on each step
            |   2 - Print only the final step
        """.trimMargin())
}