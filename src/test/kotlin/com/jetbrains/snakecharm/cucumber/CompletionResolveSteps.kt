package com.jetbrains.snakecharm.cucumber

import com.intellij.codeInsight.completion.CompletionType
import com.intellij.codeInsight.lookup.Lookup
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementPresentation
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.LookupImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiPolyVariantReference
import com.intellij.psi.PsiReference
import com.intellij.testFramework.UsefulTestCase
import com.intellij.testFramework.fixtures.CodeInsightTestFixture
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import cucumber.api.DataTable
import cucumber.api.java.en.Then
import cucumber.api.java.en.When
import junit.framework.TestCase
import java.util.*
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/**
 * @author Roman.Chernyatchik
 * @date 2019-05-09
 */
class CompletionResolveSteps {
    @Then("^reference should not resolve$")
    fun referenceShouldNotResolve() {
        ApplicationManager.getApplication().invokeAndWait({
            val ref = getReferenceAtOffset()
            assertNotNull(ref)
            assertUnresolvedReference(ref)
        }, ModalityState.NON_MODAL)
    }


    @When("^I put the caret at (.+)$")
    fun iPutCaretAt(marker: String) {
        ApplicationManager.getApplication().invokeAndWait({
            val editor = SnakemakeWorld.fixture().editor
            val position = getPositionBySignature(editor, marker, false)
            editor.caretModel.moveToOffset(position)
        }, ModalityState.NON_MODAL)
    }

    @When("^I put the caret after (.+)$")
    fun iPutCaretAfter(marker: String) {
        ApplicationManager.getApplication().invokeAndWait({
            val editor = SnakemakeWorld.fixture().editor
            val position = getPositionBySignature(editor, marker, true)
            editor.caretModel.moveToOffset(position)
        }, ModalityState.NON_MODAL)
    }


    @Then("^reference should resolve to \"(.+)\" in \"(.+)\"$")
    fun referenceShouldResolveToIn(marker: String, file: String) {
        ApplicationManager.getApplication().runReadAction {
            val ref = getReferenceAtOffset()
            assertNotNull(ref)
            val result = resolve(ref)
            assertNotNull(result)
            val containingFile = result!!.containingFile
            assertNotNull(containingFile)
            assertEquals(file, containingFile.name)

            val text = TextRange.from(result.textOffset, marker.length).substring(containingFile.text)
            assertEquals(marker, text)
        }
    }

    @When("^I invoke autocompletion popup$")
    fun iInvokeAutocompletionPopup() {
        Registry.get("ide.completion.variant.limit").setValue(10000)
        doComplete()
    }

    @Then("^completion list should contain:$")
    fun completionListShouldContain(table: DataTable) {
        completionListShouldContainMethods(table.asList(String::class.java))
    }

    @Then("^completion list should contain items (.+)$")
    fun completionListShouldContainMethods(lookupItems: List<String>) {
        assertHasElements(SnakemakeWorld.comletionList(), lookupItems)
    }

    @Then("^completion list shouldn't contain:$")
    fun completionListShouldNotContain(table: DataTable) {
        val lookupStrings = table.asList(String::class.java)
        assertNotInCompletionList(SnakemakeWorld.comletionList(), lookupStrings)
    }

    @Then("^I invoke autocompletion popup, select \"([^\"]+)\" lookup item and see a text:")
    fun iInvokeAutocompletionPopupAndSelectItem(lookupText: String, text: String) {
        iInvokeAutocompletionPopup()

        val fixture = SnakemakeWorld.fixture()
        val lookupElements = fixture.lookupElements

        ApplicationManager.getApplication().invokeAndWait(
                {
                    checkCompletionResult(
                            LookupFilter.create(lookupText), lookupElements,
                            fixture, false,
                            StringUtil.convertLineSeparators(text)
                    )
                },
                ModalityState.NON_MODAL)
    }

    /*
    @When("^I press Enter$")
    fun iPressEnter() {
        // TODO: SnakemakeWorld.fixture().type("") ?

        ApplicationManager.getApplication().runWriteAction {
            val fixture = SnakemakeWorld.fixture()
            val project = fixture.project
            val action = Runnable {
                fixture.performEditorAction("EditorEnter")
            }
            CommandProcessor.getInstance().executeCommand(project, action, "SnakeCharmTestCmd", null)
        }
    }

    @When("^I press Space$")
    fun iPressSpace() {
        // TODO: SnakemakeWorld.fixture().type("") ? + ApplicationManager.getApplication().invokeAndWait( ..)
        SwingUtilities.invokeAndWait() {
//        SwingUtilities.invokeLater {
            SnakemakeWorld.fixture().type(' ')
        }
    }
    */

    private fun resolve(ref: PsiReference) = when (ref) {
        is PsiPolyVariantReference -> {
            val results = ref.multiResolve(false)
            assertNotNull(results)
            assertEquals(1, results.size.toLong())
            results[0].element
        }
        else -> ref.resolve()
    }

    private fun doComplete() {
        val fixture = SnakemakeWorld.fixture()
        fixture.complete(CompletionType.BASIC)
        SnakemakeWorld.myCompletionList = fixture.lookupElementStrings
    }


    private fun assertUnresolvedReference(ref: PsiReference) {
        when (ref) {
            is PsiPolyVariantReference -> {
                val results = ref.multiResolve(false)
                assertNotNull(results)
                assertEquals(0, results.size.toLong())
            }
            else -> TestCase.assertNull(ref.resolve())
        }
    }

    private fun assertHasElements(
            actualLookupItems: List<String>,
            expectedVariants: List<String>
    ) {
        val unmetElements = ArrayList<String>()

        for (variant in expectedVariants) {
            if (!actualLookupItems.contains(variant)) {
                unmetElements.add(variant)
            }
        }
        if (unmetElements.isNotEmpty()) {
            org.junit.Assert.fail(
                    """
                    "Not all elements were found in real collection. Following elements were missed :[
                    ${UsefulTestCase.toString(unmetElements)}] in collection:[
                    ${UsefulTestCase.toString(actualLookupItems)}]
                    """.trimIndent()
            )
        }
    }

    private fun assertNotInCompletionList(
            actualLookupItems: List<String>,
            expectedMissingVariants: List<String>
    ) {
        val lookupElementsSet = HashSet(actualLookupItems)
        val unexpectedVariants = ArrayList<String>()
        for (variant in expectedMissingVariants) {
            if (lookupElementsSet.contains(variant)) {
                unexpectedVariants.add(variant)
            }
        }
        if (unexpectedVariants.isEmpty()) {
            return
        }
        org.junit.Assert.fail(
                """
                    The following variants aren't expected in completion list.
                    Unexpected variants:
                    ${UsefulTestCase.toString(unexpectedVariants)}
                    Completion list:
                    ${UsefulTestCase.toString(actualLookupItems)}
                """.trimIndent())
    }

    private fun getReferenceAtOffset() = SnakemakeWorld.fixture()
            .file.findReferenceAt(getOffsetUnderCaret())

    private fun getPositionBySignature(editor: Editor, marker: String, after: Boolean): Int {
        val text = editor.document.text
        val pos = text.indexOf(marker)
        require(pos >= 0)
        return if (after) pos + marker.length else pos
    }

    private fun checkCompletionResult(
            lookupFilter: LookupFilter,
            lookupElements: Array<LookupElement>?,
            fixture: CodeInsightTestFixture,
            checkByFilePath: Boolean,
            completionResultTextOrFileRelativePath: String
    ) {
        // If completion list contained only one variant completion list will be closed and
        // variant will be automatically inserted
        if (lookupElements == null && LookupManager.getInstance(fixture.project).activeLookup == null) {
            if (checkByFilePath) {
                fixture.checkResultByFile(completionResultTextOrFileRelativePath)
            } else {
                fixture.checkResult(completionResultTextOrFileRelativePath)
            }
            return
        }

        // zero or several variants
        assertNotNull(lookupElements)

        selectItem(lookupFilter.findElement(lookupElements), Lookup.NORMAL_SELECT_CHAR, fixture.project)
        if (checkByFilePath) {
            fixture.checkResultByFile(completionResultTextOrFileRelativePath)
        } else {
            fixture.checkResult(completionResultTextOrFileRelativePath)
        }

    }

    private fun selectItem(item: LookupElement, ch: Char, project: Project) {
        val lookup = LookupManager.getInstance(project).activeLookup as LookupImpl?
        assertNotNull(lookup, message = "Lookup didn't show")
        lookup.currentItem = item
        UIUtil.invokeLaterIfNeeded {
            CommandProcessor.getInstance().executeCommand(
                    project, { lookup.finishLookup(ch) }, "", null)
        }
    }

    companion object {
        fun getOffsetUnderCaret() = SnakemakeWorld.fixture().editor.caretModel.offset
    }
}

class LookupFilter private constructor(
        private val myLookupString: String,
        private val myTypeString: String?
) : Condition<LookupElement> {

    override fun value(lookupElement: LookupElement?): Boolean {
        return accept(lookupElement)
    }

    fun accept(lookupElement: LookupElement?): Boolean {
        if (lookupElement == null) {
            return false
        }

        val presentation = LookupElementPresentation()
        lookupElement.renderElement(presentation)

        return if (myLookupString != presentation.itemText) {
            false
        } else StringUtil.isEmpty(myTypeString) || myTypeString == presentation.typeText
    }

    override fun toString(): String {
        return "Text: \"" + myLookupString + "\"; Type: \"" + (myTypeString ?: "any") + "\""
    }

    fun findElement(lookupElements: Array<LookupElement>): LookupElement {
        val filteredElements = ContainerUtil.filter(lookupElements, this)
        if (filteredElements.isEmpty()) {
            org.junit.Assert.fail(toString() + " - isn't in autocompletion popup")
        } else if (filteredElements.size > 1) {
            val error = StringBuilder()
            error.append("Several elements with the same conditions: ").append(toString())
            filteredElements.forEach { element -> error.append("\n - ").append(dumpElement(element)) }
            org.junit.Assert.fail(error.toString())
        }
        return filteredElements[0]
    }

    companion object {

        fun create(lookupString: String): LookupFilter {
            return LookupFilter(lookupString, null)
        }

        fun create(lookupString: String, typeString: String): LookupFilter {
            return LookupFilter(lookupString, typeString)
        }

        private fun dumpElement(element: LookupElement?): String {
            if (element == null) {
                return "null element"
            }
            val sb = StringBuilder()

            val elementPresentation = LookupElementPresentation()
            element.renderElement(elementPresentation)
            sb.append("Text: \"").append(elementPresentation.itemText).append("\"; ")
            sb.append("Tail: \"").append(elementPresentation.tailText).append("\"; ")
            sb.append("Type: \"").append(elementPresentation.typeText).append("\"; ")
            return sb.toString()
        }
    }
}
