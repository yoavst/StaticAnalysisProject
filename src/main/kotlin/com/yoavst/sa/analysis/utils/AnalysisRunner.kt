package com.yoavst.sa.analysis.utils

import com.yoavst.sa.parsing.ASTStatement
import com.yoavst.sa.parsing.CFGNode
import com.yoavst.sa.parsing.ControlFlowGraph
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.parse.Parser
import java.io.File


class AnalysisRunner<T>(
    private val graph: ControlFlowGraph,
    private val analysis: Analysis<T>,
    private val shouldPlotStates: Boolean = false,
    private val shouldPlotFinalState: Boolean = false
) {
    init {
        if (shouldPlotStates || shouldPlotFinalState) {
            File("graphviz").mkdir()
        }
    }

    fun run() {
        val lattice = analysis.lattice
        // replace the state of the graph to the top value
        graph.tree.forEach { node ->
            node.value = analysis.lattice.bottom
        }
        graph.startingNode.value = analysis.lattice.top

        // start with the top
        val stack = ArrayDeque(graph.tree)

        var i = 0
        // go over the stack
        while (stack.isNotEmpty()) {
            if (i++ == 10000) {
                println("Analysis took too long. Exit.")
                break
            }

            val node = stack.removeFirst()
            val originalValue = node.value as T
            val newValue =
                if (node.inEdges.isEmpty()) originalValue else node.inEdges.fold(lattice.bottom) { curValue, (statement, beforeNode) ->
                    analysis.join(curValue, analysis.transfer(statement, beforeNode.value as T))
                }
            // if should propagate
            if (lattice.compare(newValue, originalValue) != CompareResult.Equal) {
                node.value = newValue
                for ((_, nextNode) in node.outEdges) {
                    stack.add(nextNode as CFGNode)
                }
            }
            if (shouldPlotStates) {
                val g: MutableGraph = Parser().read(graph.toString())
                Graphviz.fromGraph(g).render(Format.PNG).toFile(File("graphviz/step-$i.png"))
            }
        }
        if (shouldPlotFinalState) {
            val g: MutableGraph = Parser().read(graph.toString())
            Graphviz.fromGraph(g).render(Format.PNG).toFile(File("graphviz/step-$i.png"))
        }
        println("Run for $i rounds")

        // now check assertions
        for (node in graph.tree) {
            for ((statement, _) in node.outEdges) {
                if (statement is ASTStatement.AssertionStatement) {
                    val assertion = statement.assertions
                    val res = analysis.checkAssertions(assertion, node.value as T)
                    when {
                        res == null -> {
                            println("Assertion unsupported: L${node.id} $statement $ANSI_RESET")
                        }
                        res -> {
                            println("$ANSI_GREEN Assertion passed: L${node.id} $statement $ANSI_RESET")
                        }
                        else -> {
                            println("$ANSI_RED Assertion failed: L${node.id} $statement $ANSI_RESET")
                            println("\tState: ${node.value}")
                        }
                    }
                }
            }
        }
    }
}

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
