package com.yoavst.sa.parsing

import java.util.*

open class Node<T, E>(
    open val value: T,
    open val outEdges: List<Pair<E, Node<T, E>>>,
    open val inEdges: List<Pair<E, Node<T, E>>>
)

class MutableNode<T, E>(
    override val value: T,
    override val outEdges: MutableList<Pair<E, Node<T, E>>>,
    override val inEdges: MutableList<Pair<E, Node<T, E>>>
) :
    Node<T, E>(value, outEdges, inEdges)

typealias CFGNode = MutableNode<MutableList<ASTStatement>, ASTAssumption>

data class ControlFlowGraph(val startingNode: CFGNode, val tree: List<CFGNode>)

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
            if (statement !is ASTStatement.AssumeStatement || mapping[from]!!.value.isNotEmpty()) {
                println("If a node has more than one outgoing edges than these edges MUST BE annotated with assume commands.")
                return null
            }
        } else {
            if (statement !is ASTStatement.AssumeStatement) {
                mapping[from] = CFGNode(mutableListOf(statement), mutableListOf(), mutableListOf())
            } else {
                mapping[from] = CFGNode(mutableListOf(), mutableListOf(), mutableListOf())
            }
        }
    }
    // step 2. Add edges
    for ((from, to, statement) in edges) {
        val assumption =
            if (statement is ASTStatement.AssumeStatement) statement.assumption else ASTAssumption.TrueAssumption
        val vertexFrom = mapping[from]
        var vertexTo = mapping[to]
        if (vertexFrom == null) {
            println("Invalid edges in the program. Aborting.")
            return null
        } else if (vertexTo == null) {
            // make sense for the final vertex
            vertexTo = CFGNode(mutableListOf(), mutableListOf(), mutableListOf())
            mapping[to] = vertexTo
        }
        vertexFrom.outEdges.add(assumption to vertexTo)
        vertexTo.inEdges.add(assumption to vertexFrom)

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

    // Try merge nodes by BFS from starting node
    // We do this for better performance when doing the static analysis itself

    // we can merge node A with node B if:
    // A -> B by TRUE assumption
    // A is the only in edge to B
    // B is the only out edge to A
    val availableNodes = mutableSetOf<CFGNode>()
    val stack = ArrayDeque<CFGNode>()
    stack.push(startingNode)

    while (stack.isNotEmpty()) {
        val node = stack.pop()
        availableNodes.add(node)
        if (node.outEdges.size != 1) {
            // just add nodes to stack
            for ((_, next) in node.outEdges) {
                if (next !in availableNodes)
                    stack.push(next as CFGNode) // Kotlin inference bug
            }
        } else {
            val (assumption, next) = node.outEdges.first()
            if (assumption != ASTAssumption.TrueAssumption || next.inEdges.size != 1) {
                if (next !in availableNodes)
                    stack.push(next as CFGNode)
            } else {
                // merge them
                node.value.addAll(next.value)
                node.outEdges.clear()
                node.outEdges.addAll(next.outEdges)
                next.outEdges.forEach { (_, nextNext) ->
                    // replace in refs to next
                    val inEdges = nextNext.inEdges as MutableList
                    for (i in inEdges.indices) {
                        if (inEdges[i].second == next) {
                            inEdges[i] = inEdges[i].first to node
                        }
                    }
                }
                stack.push(node)
            }
        }
    }


    return ControlFlowGraph(startingNode, availableNodes.toList())
}