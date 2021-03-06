package com.jetbrains.snakecharm.lang.parser

import com.intellij.lang.PsiBuilder
import com.intellij.psi.tree.IElementType
import com.jetbrains.python.PyBundle
import com.jetbrains.python.PyBundle.message
import com.jetbrains.python.PyElementTypes
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.parsing.StatementParsing
import com.jetbrains.snakecharm.lang.psi.SMKRuleParameterListStatement
import com.jetbrains.snakecharm.lang.psi.SMKRuleRunParameter
import com.jetbrains.snakecharm.lang.psi.elementTypes.SnakemakeElementTypes

/**
 * @author Roman.Chernyatchik
 * @date 2018-12-31
 */
class SnakemakeStatementParsing(
        context: SnakemakeParserContext,
        futureFlag: FUTURE?
) : StatementParsing(context, futureFlag) {

    override fun getParsingContext() = myContext as SnakemakeParserContext

    // TODO cleanup
//    override fun getReferenceType(): IElementType {
//            return CythonElementTypes.REFERENCE_EXPRESSION
//        }

    override fun parseStatement() {
        val context = parsingContext
        val scope = context.scope

        // myBuilder.setDebugMode(true)

        val tt = myBuilder.tokenType
        if (tt !in SnakemakeTokenTypes.WORKFLOW_TOPLEVEL_DECORATORS || scope.inParamArgsList) {
            super.parseStatement()
            // TODO: context?
            return
        }
        when {
            tt === SnakemakeTokenTypes.RULE_KEYWORD -> parseRuleDeclaration(true)
            tt === SnakemakeTokenTypes.CHECKPOINT_KEYWORD -> parseRuleDeclaration(false)
            tt in SnakemakeTokenTypes.WORKFLOW_TOPLEVEL_PARAMLISTS_DECORATOR_KEYWORDS -> {
                val workflowParam = myBuilder.mark()
                nextToken()
                parsingContext.expressionParser.parseRuleParamArgumentList()
                workflowParam.done(SnakemakeElementTypes.WORKFLOW_PARAMETER_LIST_STATEMENT)
            }
            tt === SnakemakeTokenTypes.WORKFLOW_LOCALRULES_KEYWORD -> {
                val workflowParam = myBuilder.mark()
                nextToken()
                checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"))

                val res = parsingContext.expressionParser.parseRulesList(
                        PyTokenTypes.COMMA,
                        "',' expected",
                        "Expected a rule name identifier." // bundle
                )
                if (!res) {
                    myBuilder.error("Expected a comma separated list of rules that shall not be" +
                            " executed by the cluster command.") // bundle
                }

                nextToken()
                workflowParam.done(SnakemakeElementTypes.WORKFLOW_LOCALRULES_STATEMENT)
            }
            tt === SnakemakeTokenTypes.WORKFLOW_RULEORDER_KEYWORD  -> {
                val workflowParam = myBuilder.mark()
                nextToken()
                checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"))

                val res = parsingContext.expressionParser.parseRulesList(
                        PyTokenTypes.GT,
                        "'>' expected",
                        "Expected a rule name identifier" // bundle
                )
                if (!res) {
                    myBuilder.error("Expected a descending order of rule names, e.g. rule1 > rule2 > rule3 ...") // bundle
                }

                nextToken()
                workflowParam.done(SnakemakeElementTypes.WORKFLOW_RULESREORDER_STATEMENT)
            }
            tt in SnakemakeTokenTypes.WORKFLOW_TOPLEVEL_PYTHON_BLOCK_PARAMETER_KEYWORDS -> {
                val decoratorMarker = myBuilder.mark()
                nextToken()
                checkMatches(PyTokenTypes.COLON, message("PARSE.expected.colon"))
                parseSuite()
                decoratorMarker.done(SnakemakeElementTypes.WORKFLOW_PYTHON_BLOCK_PARAMETER)
            }
            else -> {
                myBuilder.error("Unexpected token type: $tt with text: '${myBuilder.tokenText}'") // bundle
                // XXX: let's do not throw exception here parsing error is better noticeable than bg exception + doesn't
                // XXX: break PSI dramatically. But it is like assertion here.

                // fail safe:
                super.parseStatement()
            }
        }
    }

    private fun parseRuleDeclaration(atRuleToken: Boolean) {
        val context = parsingContext
        val scope = context.scope
        context.pushScope(scope.withRule())

        val ruleMarker: PsiBuilder.Marker = myBuilder.mark()
        nextToken()

        // rule name
        if (atToken(PyTokenTypes.IDENTIFIER)) {
            nextToken()
        }
        checkMatches(PyTokenTypes.COLON, "Rule name identifier or ':' expected") // bundle

        val ruleStatements = myBuilder.mark()
        val multiline = atToken(PyTokenTypes.STATEMENT_BREAK)
        if (!multiline) {
            parseRuleParameter()
        } else {
            nextToken()
            checkMatches(PyTokenTypes.INDENT, "Indent expected...") // bundle
            while (!atToken(PyTokenTypes.DEDENT)) {
                if (!parseRuleParameter()) {
                    break
                }
            }
        }
        ruleStatements.done(PyElementTypes.STATEMENT_LIST)
        ruleMarker.done(when {
            atRuleToken -> SnakemakeElementTypes.RULE_DECLARATION
            else -> SnakemakeElementTypes.CHECKPOINT_DECLARATION
        })
        context.popScope()
        if (multiline) {
            nextToken()
        }
    }

    private fun parseRuleParameter(): Boolean {
        if (myBuilder.eof()) {
            return false
        }

        val keyword = myBuilder.tokenText
        val ruleParam = myBuilder.mark()

        if (myBuilder.tokenType != PyTokenTypes.IDENTIFIER) {
            myBuilder.error("Rule parameter identifier is expected") // bundle
            nextToken()
            ruleParam.drop()
            return false
        }
        nextToken()

        var result = false

        when (keyword) {
            in SMKRuleParameterListStatement.PARAMS_NAMES -> {
                // TODO: probably do this behaviour by default and use inspection error
                // instead of parsing errors..
                result = parsingContext.expressionParser.parseRuleParamArgumentList()
                ruleParam.done(SnakemakeElementTypes.RULE_PARAMETER_LIST_STATEMENT)
            }
            SMKRuleRunParameter.PARAM_NAME -> {
                checkMatches(PyTokenTypes.COLON, PyBundle.message("PARSE.expected.colon"))
                statementParser.parseSuite()
                ruleParam.done(SnakemakeElementTypes.RULE_RUN_STATEMENT)
            }
            else -> {
                // error
                myBuilder.error("Unexpected rule parameter '$keyword'") // bundle

                //TODO advance until eof or STATEMENT_END?
                // checkEndOfStatement()
                ruleParam.drop()
            }
        }
        return result
    }

    override fun filter(
            source: IElementType,
            start: Int, end: Int,
            text: CharSequence,
            checkLanguageLevel: Boolean
    ): IElementType {
        if (source in SnakemakeTokenTypes.WORKFLOW_TOPLEVEL_DECORATORS) {
            val scope = myContext.scope as SnakemakeParsingScope
            return when {
                scope.inRule -> PyTokenTypes.IDENTIFIER
                else -> source
            }
        }
        return super.filter(source, start, end, text, checkLanguageLevel)
    }
    // TODO: cleanup
//    override fun getFunctionParser(): FunctionParsing {
//        return super.getFunctionParser()
//    }
}