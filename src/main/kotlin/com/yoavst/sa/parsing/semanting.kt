package com.yoavst.sa.parsing

open class Node<T, E>(
    open val value: T,
    open val id: Int,
    open val outEdges: List<Pair<E, Node<T, E>>>,
    open val inEdges: List<Pair<E, Node<T, E>>>
) {
    override fun toString(): String = "L$id($value)"
}

class MutableNode<T, E>(
    override var value: T,
    override var id: Int,
    override val outEdges: MutableList<Pair<E, Node<T, E>>>,
    override val inEdges: MutableList<Pair<E, Node<T, E>>>
) :
    Node<T, E>(value, id, outEdges, inEdges)

typealias CFGNode = MutableNode<Any?, ASTStatement>

data class ControlFlowGraph(val startingNode: CFGNode, val tree: List<CFGNode>) {
    override fun toString(): String = buildString {
        appendLine("digraph {")
        for (node in tree) {
            if (node.value != null) {
                appendLine("\tL${node.id} [label=\"${node.value.toString().replace('"', '\'').replace("\n", "\\n")}\"];")
            }
            for (edge in node.outEdges) {
                appendLine("\tL${node.id} -> L${edge.second.id} [label=\"${
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
    for ((from, _, statement) in edges) {
        if (from in mapping) {
            if (statement !is ASTStatement.AssumeStatement || mapping[from]!!.outEdges.isNotEmpty()) {
                println("If a node has more than one outgoing edges than these edges MUST BE annotated with assume commands.")
                return null
            }
        } else {
            mapping[from] = CFGNode(null, from, mutableListOf(), mutableListOf())
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
            vertexTo = CFGNode(null, to, mutableListOf(), mutableListOf())
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
            println("Multiple starting node in the graph: ${startingNodes.keys}")
            return null
        }
    }

    return ControlFlowGraph(startingNode, mapping.values.toList())
}