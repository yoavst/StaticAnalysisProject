package com.yoavst.sa.parsing

import java.math.BigInteger

enum class ASTOperation(private val repr: String) {
    Plus("+"), Minus("-");

    override fun toString(): String = repr
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
        override fun toString() = variableName
    }

    @JvmInline
    value class ConstValue(val value: BigInteger) : ASTValue, ASTWithNoVariables {
        override fun toString() = "$value"
    }
    data class VariableOpConstValue(val variableName: String, val op: ASTOperation, val const: BigInteger) : ASTValue {
        override val variables: Set<String> get() = setOf(variableName)
        override fun toString() = "$variableName $op $const"
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
        override fun toString() = "$variableName ${if (isEqual) "=" else "!="} $value"
    }
}

sealed interface ASTAssertion : AST {
    data class ParityAssertion(val isEven: Boolean, val variableName: String) : ASTAssertion {
        override val variables: Set<String> get() = setOf(variableName)
        override fun toString() = "${if (isEven) "EVEN" else "ODD"} $variableName"
    }

    data class SumAssertion(val variables1: List<String>, val variables2: List<String>) : ASTAssertion {
        override val variables: Set<String> get() = (variables1 + variables2).toSet()
        override fun toString() = "SUM $variables1 = SUM $variables2"
    }
}

sealed interface ASTStatement : AST {
    object SkipStatement : ASTStatement, ASTWithNoVariables {
        override fun toString(): String = "skip"
    }
    data class AssignmentStatement(val variable: String, val value: ASTValue) : ASTStatement {
        override val variables: Set<String> get() = (listOf(variable) + value.variables).toSet()
        override fun toString() = "$variable := $value"
    }

    @JvmInline
    value class AssumeStatement(val assumption: ASTAssumption) : ASTStatement {
        override val variables: Set<String> get() = assumption.variables
        override fun toString() = "assume $assumption"
    }

    @JvmInline
    value class AssertionStatement(val assertions: List<List<ASTAssertion>>) : ASTStatement {
        override val variables: Set<String> get() = (assertions.flatMap { it.flatMap(ASTAssertion::variables) }).toSet()
        override fun toString() = "assert $assertions"
    }
}

data class ASTEdge(val from: Int, val to: Int, val statements: ASTStatement) : AST by statements {
    override fun toString(): String = "L$from -> L$to :: $statements"
}

data class ASTProgram(override val variables: Set<String>, val edges: List<ASTEdge>) : AST {
    override fun toString(): String = "$variables\n\n${edges.joinToString("\n")}"
}
