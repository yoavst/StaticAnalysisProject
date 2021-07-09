package com.yoavst.sa.analysis.utils

import com.yoavst.sa.parsing.ASTStatement
import com.yoavst.sa.parsing.CFGNode
import com.yoavst.sa.parsing.ControlFlowGraph
import guru.nidi.graphviz.engine.Format
import guru.nidi.graphviz.engine.Graphviz
import guru.nidi.graphviz.model.MutableGraph
import guru.nidi.graphviz.parse.Parser
import java.io.File


class AnalysisRunner<T>(private val graph: ControlFlowGraph, private val analysis: Analysis<T>) {

    fun run() {
        val lattice = analysis.lattice
        // replace the state of the graph to the top value
        graph.tree.forEach { node ->
            node.value = analysis.lattice.top
        }

        // start with the top
        val stack = ArrayDeque(graph.tree)

        var i = 0
        // go over the stack
        while (stack.isNotEmpty()) {
            if (false) {
                val g: MutableGraph = Parser().read(graph.toString())
                Graphviz.fromGraph(g).width(1920).render(Format.PNG).toFile(File("build/img/step-$i.png"))
            }
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
        }

        // now check assertions
        for (node in graph.tree) {
            for ((statement, _) in node.outEdges) {
                if (statement is ASTStatement.AssertionStatement) {
                    val assertion = statement.assertions
                    if (analysis.checkAssertions(assertion, node.value as T)) {
                        println("$ANSI_GREEN Assertion passed: L${node.id} $statement $ANSI_RESET")
                    } else {
                        println("$ANSI_RED Assertion failed: L${node.id} $statement $ANSI_RESET")
                        println("\tState: ${node.value}")
                    }
                }
            }
        }
    }
}

const val ANSI_RESET = "\u001B[0m"
const val ANSI_RED = "\u001B[31m"
const val ANSI_GREEN = "\u001B[32m"
