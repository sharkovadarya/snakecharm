package com.jetbrains.snakecharm.lang

import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.lang.folding.NamedFoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.FoldingGroup
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.TokenSet
import com.intellij.testFramework.LightVirtualFile
import com.jetbrains.python.PyTokenTypes
import com.jetbrains.python.PythonFoldingBuilder
import com.jetbrains.snakecharm.lang.psi.SnakemakeFile
import com.jetbrains.snakecharm.lang.psi.elementTypes.SnakemakeElementTypes
import java.util.*

/**
 * @author Roman.Chernyatchik
 * @date 2019-02-03
 */
class SnakeMakeFoldingBuilder : PythonFoldingBuilder() {
    companion object {
        val FOLDED_ELEMENTS = TokenSet.create(
                SnakemakeElementTypes.RULE_DECLARATION,
                SnakemakeElementTypes.RULE_RUN_STATEMENT,
                SnakemakeElementTypes.WORKFLOW_PYTHON_BLOCK_PARAMETER
        )
    }
//    override fun isRegionCollapsedByDefault(node: ASTNode): Boolean {
////        // TODO: snakemake specific settings fot this
////        return super.isRegionCollapsedByDefault(node)
//    }

    override fun buildLanguageFoldRegions(
            descriptors: MutableList<FoldingDescriptor>,
            root: PsiElement,
            document: Document,
            quick: Boolean
    ) {
        if (root !is SnakemakeFile || root.virtualFile !is LightVirtualFile) {
            collectDescriptors(root.node, descriptors)
        }

        // No dialect support for folding, so let's delegate to python folding
        super.buildLanguageFoldRegions(descriptors, root, document, quick)
    }

    private fun collectDescriptors(node: ASTNode, descriptors: MutableList<FoldingDescriptor>) {
        val type = node.elementType

        if (type in FOLDED_ELEMENTS) {
            val endOffset = node.textRange.endOffset
            val colon = node.findChildByType(PyTokenTypes.COLON)
            if (colon != null) {
                val startElement = colon.psi
                val range = TextRange(getVisibleTextOffset(startElement), endOffset)
                if (!range.isEmpty) {
                    descriptors.add(NamedFoldingDescriptor(node, range, FoldingGroup.newGroup("group"),
                            "...", isRegionCollapsedByDefault(node), Collections.emptySet()))
                }
            }
        }
        val children = node.getChildren(null)
        // Process line comments
        // processLineCommentRanges(node, children, descriptors)
        // Recursively process elements
        for (child in children) {
            collectDescriptors(child, descriptors)
        }
    }

    private fun getVisibleTextOffset(element: PsiElement): Int {
        return element.textRange.startOffset + element.text.length
    }

    override fun getLanguagePlaceholderText(node: ASTNode, range: TextRange): String {
        val type = node.elementType
        return when (type) {
            in FOLDED_ELEMENTS -> node.text
            else -> super.getLanguagePlaceholderText(node, range)
        }
    }
}