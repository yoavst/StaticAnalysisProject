package com.yoavst.sa.parsing

import com.github.h0tk3y.betterParse.combinators.*
import com.github.h0tk3y.betterParse.grammar.Grammar
import com.github.h0tk3y.betterParse.lexer.literalToken
import com.github.h0tk3y.betterParse.lexer.regexToken
import com.github.h0tk3y.betterParse.parser.Parser
import java.math.BigInteger

class ASTParser : Grammar<ASTProgram>() {
    //region Tokens
    private val ws by regexToken("""\s+""", ignore = true)
    private val skip by literalToken("skip")
    private val assume by literalToken("assume")
    private val assert by literalToken("assert")
    private val trueValue by literalToken("true")
    private val falseValue by literalToken("false")
    private val lpar by literalToken("(")
    private val rpar by literalToken(")")
    private val plus by literalToken("+")
    private val minus by literalToken("-")
    private val equal by literalToken("=")
    private val notEqual by literalToken("!=")
    private val assign by literalToken(":=")
    private val unknown by literalToken("?")
    private val sum by literalToken("SUM")
    private val even by literalToken("EVEN")
    private val odd by literalToken("ODD")
    private val constValue by regexToken("""\d+""")
    private val vertex by regexToken("""L(\d+)""")
    private val variable by regexToken("""[a-z]+""")
    //endregion

    //region parsers
    private val constValueParser by constValue use { BigInteger(text) }
    private val variableParser by variable use { text }
    private val vertexParser by vertex use { BigInteger(text.substring(1)) }
    private val opParser by (plus asJust ASTOperation.Plus) or (plus asJust ASTOperation.Minus)
    private val equalParser by (equal asJust true) or (notEqual asJust false)
    private val sumParser by -sum * oneOrMore(variableParser)

    private val valueParser: Parser<ASTValue> by
    ((variableParser * opParser * constValueParser).map { (var1, op, var2) ->
        ASTValue.VariableOpConstValue(
            var1,
            op,
            var2
        )
    }) or
            variableParser.map(ASTValue::VariableValue) or
            constValueParser.map(ASTValue::ConstValue) or
            unknown.asJust(ASTValue.UnknownValue)

    private val assumptionParser: Parser<ASTAssumption> by
    trueValue.asJust(ASTAssumption.TrueAssumption) or falseValue.asJust(ASTAssumption.FalseAssumption) or
            ((variableParser * equalParser * valueParser) map { (var1, op, value) ->
                ASTAssumption.VariableAssumption(
                    var1,
                    op,
                    value
                )
            })

    private val assertionParser: Parser<ASTAssertion> by
    ((-odd * variableParser) map { ASTAssertion.ParityAssertion(false, it) }) or
            ((-even * variableParser) map { ASTAssertion.ParityAssertion(true, it) }) or
            ((sumParser * -equal * sumParser) map { (vars1, vars2) -> ASTAssertion.SumAssertion(vars1, vars2) })

    private val statementParser: Parser<ASTStatement> by
    skip.asJust(ASTStatement.SkipStatement) or
            ((variableParser * -assign * valueParser) map { (variable, value) ->
                ASTStatement.AssignmentStatement(
                    variable,
                    value
                )
            }) or
            (-assume * assumptionParser).map(ASTStatement::AssumeStatement) or
            (-assume * -lpar * assumptionParser * -rpar).map(ASTStatement::AssumeStatement) or
            (-assert * oneOrMore(
                -lpar * oneOrMore(assertionParser) * -rpar
            )).map(ASTStatement::AssertionStatement)

    private val edgeParser = (vertexParser * statementParser * vertexParser) map { (from, statement, to) ->
        ASTEdge(
            from.toInt(),
            to.toInt(),
            statement
        )
    }
    //endregion


    override val rootParser: Parser<ASTProgram> by (zeroOrMore(variableParser) * zeroOrMore(edgeParser)).map { (variables, edges) ->
        ASTProgram(
            variables.toSortedSet(),
            edges
        )
    }

}