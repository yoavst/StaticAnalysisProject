package com.yoavst.sa.parsing

open class Node<T, E>(
    open val value: T,
    open val outEdges: List<Pair<E, Node<T, E>>>,
    open val inEdges: List<Pair<E, Node<T, E>>>
)

class MutableNode<T, E>(
    override var value: T,
    override val outEdges: MutableList<Pair<E, Node<T, E>>>,
    override val inEdges: MutableList<Pair<E, Node<T, E>>>
) :
    Node<T, E>(value, outEdges, inEdges)

typealias CFGNode = MutableNode<Int, ASTStatement>

data class ControlFlowGraph(val startingNode: CFGNode, val tree: List<CFGNode>) {
    override fun toString(): String = buildString {
        appendLine("digraph {")
        for (node in tree) {
            for (edge in node.outEdges) {
                appendLine("\tL${node.value} -> L${edge.second.value} [label=\"${
                    edge.first.toString().replace('"', '\'')
                }\"]")
            }
        }
        appendLine("}")
    }
}

fun ASTProgram.toCFG(): ControlFlowGraph? {
    // Checks that the variables that are referenced in the code, are listed on the variables list
    val usedVariables = edges.flatMapTo(mutableSetOf(), ASTEdge::variables)
    if (variables != usedVariables) {
        println("Mismatch in variable declaration.\nDeclared: $variables\nUsing: $usedVariables")
        return null
    }
    // create the tree, recursively
    // step 1. Create the nodes
    val mapping = mutableMapOf<Int, CFGNode>()
    for ((from, to, statement) in edges) {
        if (from in mapping) {
            if (statement !is ASTStatement.AssumeStatement || mapping[from]!!.outEdges.isNotEmpty()) {
                println("If a node has more than one outgoing edges than these edges MUST BE annotated with assume commands.")
                return null
            }
        } else {
            mapping[from] = CFGNode(from, mutableListOf(), mutableListOf())
        }
    }
    // step 2. Add edges
    for ((from, to, statement) in edges) {
        val vertexFrom = mapping[from]
        var vertexTo = mapping[to]
        if (vertexFrom == null) {
            println("Invalid edges in the program. Aborting.")
            return null
        } else if (vertexTo == null) {
            // make sense for the final vertex
            vertexTo = CFGNode(to, mutableListOf(), mutableListOf())
            mapping[to] = vertexTo
        }
        vertexFrom.outEdges.add(statement to vertexTo)
        vertexTo.inEdges.add(statement to vertexFrom)
    }

    // find starting node
    val startingNodes = mapping.filterValues { it.inEdges.size == 0 }
    val startingNode: CFGNode = when (startingNodes.size) {
        0 -> {
            println("No starting node in the graph. Aborting.")
            return null
        }
        1 -> startingNodes.values.first()
        else -> {
            println("Multiple starting node in the graph: ${mapping.keys}")
            return null
        }
    }

    return ControlFlowGraph(startingNode, mapping.values.toList())
}