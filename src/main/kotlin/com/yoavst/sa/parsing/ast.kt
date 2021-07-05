package com.yoavst.sa.parsing

import java.math.BigInteger

enum class ASTOperation {
    Plus, Minus
}

interface AST {
    val variables: Set<String>
}

private interface ASTWithNoVariables : AST {
    override val variables: Set<String> get() = emptySet()
}

sealed interface ASTValue : AST {
    @JvmInline
    value class VariableValue(val variableName: String) : ASTValue {
        override val variables: Set<String> get() = setOf(variableName)
    }

    @JvmInline
    value class ConstValue(val value: BigInteger) : ASTValue, ASTWithNoVariables
    data class VariableOpConstValue(val variableName: String, val op: ASTOperation, val const: BigInteger) : ASTValue {
        override val variables: Set<String> get() = setOf(variableName)
    }

    object UnknownValue : ASTValue, ASTWithNoVariables {
        override fun toString(): String = "?"
    }
}

sealed interface ASTAssumption : AST {
    object TrueAssumption : ASTAssumption, ASTWithNoVariables {
        override fun toString(): String = "True"
    }
    object FalseAssumption : ASTAssumption, ASTWithNoVariables {
        override fun toString(): String = "False"
    }
    data class VariableAssumption(val variableName: String, val isEqual: Boolean, val value: ASTValue) : ASTAssumption {
        override val variables: Set<String> get() = setOf(variableName)
    }
}

sealed interface ASTAssertion : AST {
    data class ParityAssertion(val isEven: Boolean, val variableName: String) : ASTAssertion {
        override val variables: Set<String> get() = setOf(variableName)
    }

    data class SumAssertion(val variables1: List<String>, val variables2: List<String>) : ASTAssertion {
        override val variables: Set<String> get() = (variables1 + variables2).toSet()
    }
}

sealed interface ASTStatement : AST {
    object SkipStatement : ASTStatement, ASTWithNoVariables {
        override fun toString(): String = "SkipStatement"
    }
    data class AssignmentStatement(val variable: String, val value: ASTValue) : ASTStatement {
        override val variables: Set<String> get() = (listOf(variable) + value.variables).toSet()
    }

    @JvmInline
    value class AssumeStatement(val assumption: ASTAssumption) : ASTStatement {
        override val variables: Set<String> get() = assumption.variables
    }

    @JvmInline
    value class AssertionStatement(val assertions: List<List<ASTAssertion>>) : ASTStatement {
        override val variables: Set<String> get() = (assertions.flatMap { it.flatMap(ASTAssertion::variables) }).toSet()
    }
}

data class ASTEdge(val from: Int, val to: Int, val statements: ASTStatement) : AST by statements {
    override fun toString(): String = "L$from -> L$to :: $statements"
}

data class ASTProgram(override val variables: Set<String>, val edges: List<ASTEdge>) : AST {
    override fun toString(): String = "$variables\n\n${edges.joinToString("\n")}"
}
