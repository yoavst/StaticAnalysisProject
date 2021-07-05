import com.github.h0tk3y.betterParse.grammar.parseToEnd
import com.yoavst.sa.parsing.ASTParser
import com.yoavst.sa.parsing.CFGNode
import com.yoavst.sa.parsing.ControlFlowGraph
import com.yoavst.sa.parsing.toCFG
import java.io.File



fun main(args: Array<String>) {
    val programRaw = File("src/test/resources/example.txt").readText()

    val parser = ASTParser()
    val program = parser.parseToEnd(programRaw)

    val cfg = program.toCFG()

    if (cfg != null) {
        val startingNode = cfg.startingNode
        println(drawGraph(cfg))
    }

}

private fun drawGraph(cfg: ControlFlowGraph) = buildString {
    val namingMap = mutableMapOf<CFGNode, String>()
    // create nodes
    appendLine("digraph {")
    for ((i, node) in cfg.tree.withIndex()) {
        val name = "n$i"
        namingMap[node] = name
        appendLine("$name[label=\"${node.value.joinToString("\\n")
            .replace('"', '\'')
           }\"]")
    }
    appendLine()
    // created edges
    for (node in cfg.tree) {
        for (edge in node.outEdges) {
            appendLine("${namingMap[node]} -> ${namingMap[edge.second]} [label=\"${
                edge.first.toString().replace('"', '\'')
            }\"]")
        }
    }
    appendLine("}")
}
