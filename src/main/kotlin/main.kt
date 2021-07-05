import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.yoavst.sa.analysis.Parity
import com.yoavst.sa.analysis.utils.DisjointLattice
import com.yoavst.sa.analysis.utils.Lattice
import com.yoavst.sa.analysis.utils.PowerSetLattice
import com.yoavst.sa.parsing.ASTParser
import com.yoavst.sa.parsing.toCFG
import java.io.File


fun main(args: Array<String>) {
    val programRaw = File("src/test/resources/example.txt").readText()

    val parser = ASTParser()
    val program = parser.parseToEnd(programRaw)

    val cfg = program.toCFG()

    if (cfg != null) {
        println(cfg.toString())
    }

    val parityFullLattice: Lattice<Set<List<Parity>>> = PowerSetLattice(DisjointLattice(Parity, program.variables.size))

}