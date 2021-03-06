package com.jetbrains.snakecharm.lang.parser

import com.intellij.lang.ASTFactory
import com.intellij.lang.LanguageASTFactory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import com.jetbrains.python.*
import com.jetbrains.python.psi.LanguageLevel
import com.jetbrains.python.psi.PyFunction
import com.jetbrains.python.psi.impl.PythonASTFactory
import com.jetbrains.snakecharm.SnakemakeTestUtil
import com.jetbrains.snakecharm.lang.SnakemakeTokenSetContributor


/**
 * @author Roman.Chernyatchik
 * @date 2018-12-31
 *
 */
class SnakemakeParsingTest : ParsingTestCase(
        "psi", "smk", SnakemakeParserDefinition(), PythonParserDefinition()
) {
    private var myLanguageLevel = LanguageLevel.getDefault()

    override fun setUp() {
        super.setUp()
        registerExtensionPoint(PythonDialectsTokenSetContributor.EP_NAME, PythonDialectsTokenSetContributor::class.java)
        registerExtension(PythonDialectsTokenSetContributor.EP_NAME, PythonTokenSetContributor())
        registerExtension(PythonDialectsTokenSetContributor.EP_NAME, SnakemakeTokenSetContributor())
        addExplicitExtension<ASTFactory>(LanguageASTFactory.INSTANCE, PythonLanguage.getInstance(), PythonASTFactory())
        PythonDialectsTokenSetProvider.reset()
    }

    override fun getTestDataPath() = SnakemakeTestUtil.getTestDataPath().toString()

    override fun createFile(name: String, text: String): PsiFile {
        val file = super.createFile(name, text)
        file.virtualFile.putUserData(LanguageLevel.KEY, myLanguageLevel)
        return file
    }

    fun testPythonCode() {
        doTest()
    }

    fun testRule() {
        doTest()
    }

    fun testRuleInPythonBlock() {
        doTest()
    }

    fun testCheckpoint() {
        doTest()
    }

    fun testRuleNoName() {
        doTest()
    }

    fun testRuleMultiple() {
        doTest()
    }

    fun testRuleMultipleSingleLine() {
        doTest()
    }

    fun testRuleParams() {
        doTest()
    }

    fun testRuleInvalid() {
        doTest()
    }


    fun testRuleInvalidNoParamBody() {
        doTest()
    }

    fun testRuleInvalidNoParamBodyEof() {
        doTest()
    }

    fun testRuleInvalidParam() {
        doTest()
    }

    fun testRuleMultipleSingleLineNoBreak() {
        doTest()
    }

    fun testRuleUnexpKeyword() {
        doTest()
    }

    fun testRuleParamsListArgs() {
        doTest()
    }

    fun testRuleParamsListArgsKeywords() {
        doTest()
    }

    /* TODO #16
    fun testRuleParamsListArgsStringMultiline() {
        Assume.assumeFalse(
                "Feature Not Implemented Yet, see: See issue" +
                        " https://github.com/JetBrains-Research/snakecharm/issues/16",
                true
        )
        doTest()
    } */

    fun testRuleParamsListArgsHangingComma() {
        doTest()
    }

    fun testRuleParamsListArgsMultiple() {
        doTest()
    }

    fun testRuleParamsListArgsIndents() {
        doTest()
    }

    fun testRuleParamsListKeywordArgs() {
        doTest()
    }

    fun testRuleParamsListKeywordArgsMultiple() {
        doTest()
    }

    fun testRuleRun() {
        doTest()
    }

    fun testRuleRunPythonBlock() {
        doTest()
    }

    fun testWorkflowParamsListArgsKeywords() {
        doTest()
    }

    fun testWorkflowParamsListArgsKeywordsInRule() {
        doTest()
    }

    fun testWorkflowTopLevelDecoratorsInRuleAsKeywordParams() {
        doTest()
    }

    fun testWorkflowPythonCodeBlockKeywords() {
        doTest()
    }

    /* TODO #30
    fun testWorkflowRuleReorder() {
        Assume.assumeFalse(
                "Feature Not Implemented Yet, see: See issue" +
                        " https://github.com/JetBrains-Research/snakecharm/issues/30",
                true
        )
        doTest()
    }*/

    fun testWorkflowRuleReorderHangingSeparator() {
        doTest()
    }

    fun testWorkflowRuleReorderInvalid() {
        doTest()
    }

    /* TODO #30
    fun testWorkflowLocalrules() {
        Assume.assumeFalse(
                "Feature Not Implemented Yet, see: See issue" +
                        " https://github.com/JetBrains-Research/snakecharm/issues/30",
                true
        )
        doTest()
    }*/

    fun testWorkflowLocalrulesInvalid() {
        doTest()
    }

    fun testWorkflowLocalrulesHangingComma() {
        doTest()
    }

    /**
     * Test with latest versions of Python 2 and Python 3.
     */
    private fun doTest() {
        doTest(LanguageLevel.fromPythonVersion("2"))
        doTest(LanguageLevel.fromPythonVersion("3"))
    }

    private fun doTest(languageLevel: LanguageLevel) {
        val prev = myLanguageLevel
        myLanguageLevel = languageLevel
        try {
            doTest(true)
        } finally {
            myLanguageLevel = prev
        }
        // TODO: rule...
        //ensureEachFunctionHasStatementList(myFile, CythonFunction::class.java)
        ensureEachFunctionHasStatementList(myFile, PyFunction::class.java)
    }

    private fun <T : PyFunction> ensureEachFunctionHasStatementList(
            parentFile: PsiFile,
            functionType: Class<T>
    ) {
        val functions = PsiTreeUtil.findChildrenOfType(parentFile, functionType)
        for (functionToCheck in functions) {
            functionToCheck.statementList //To make sure each function has statement list (does not throw exception)
        }
    }

}