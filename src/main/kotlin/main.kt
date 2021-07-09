import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.yoavst.sa.analysis.parity.Parity
import com.yoavst.sa.analysis.parity.ParityAnalysis
import com.yoavst.sa.analysis.utils.*
import com.yoavst.sa.parsing.ASTParser
import com.yoavst.sa.parsing.toCFG
import java.io.File


fun main(args: Array<String>) {
    val programRaw = File("src/test/resources/example.txt").readText()

    val parser = ASTParser()
    val program = parser.parseToEnd(programRaw)

    val cfg = program.toCFG() ?: return

//    println(cfg.toString())


    val parityAnalysis = ParityAnalysis(program.variables.size)
    val analysisRunner = AnalysisRunner(cfg, parityAnalysis)

    analysisRunner.run()

    println()
    println(cfg.toString())

}