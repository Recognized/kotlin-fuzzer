package com.github.recognized.mutation

import com.github.recognized.compile.TypeContext
import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.random.Chooser
import com.github.recognized.random.FineTunedSubtreeChooser
import com.github.recognized.random.SimpleSubtreeChooser
import com.github.recognized.runtime.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.js.translate.utils.PsiUtils
import org.jetbrains.kotlin.parsing.KotlinParser
import org.jetbrains.kotlin.parsing.KotlinParserDefinition
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.KtNameReferenceExpression
import org.jetbrains.kotlin.psi.KtReferenceExpression
import org.jetbrains.kotlin.psi.KtSimpleNameExpression
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.psi.psiUtil.getChildOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
import org.jetbrains.kotlin.resolve.calls.model.isReallySuccess
import org.kodein.di.Kodein
import org.kodein.di.generic.*

interface Mutation {
    val name: String get() = this::class.java.simpleName

    fun mutate(corpus: List<Sample>, sample: Sample): String?
}

fun Kodein.MainBuilder.mutations() {
    bind() from setBinding<Mutation>()
    bind<Chooser<PsiElement, PsiElement>>() with singleton { FineTunedSubtreeChooser(SimpleSubtreeChooser(0.2)) }

    bind<Mutation>().inSet() with singleton { Replace(instance(), instance()) }
    bind<Mutation>().inSet() with singleton { Add(instance(), instance(), instance()) }
}

class MutationInfo(val source: Sample, val mutation: Mutation, val result: String)

fun showChain(sample: Sample): String {

    val kernel by kodein.instance<com.github.recognized.service.Kernel>()

    fun depth(sample: Sample): Int = sample.parent?.let { depth(it.source) + 1 } ?: 0

    fun show(sample: Sample, depth: Int): String {
        return buildString {
            sample.parent?.let {
                append(show(it.source, depth - 1))
                append("\n----END OF FILE----\n")
            }
            append("$depth. ${sample.parent?.let { "Used ${it.mutation.name}" }
                ?: ""} ${sample.metrics?.show(kernel)} \n\n")
            append(sample.file)
        }
    }

    return show(sample, depth(sample))
}

private val log = logger("Utils")

fun renameUnresolved(
    file: String,
    new: PsiElement,
    contextNew: BindingContext,
    contextOld: BindingContext
): String {
    val renames = mutableListOf<Pair<TextRange, String>>()
    log.info { "Search renames" }
    getRenamesUnresolved(new, contextNew, contextOld, renames)
    log.info { "Renames: $renames" }
    var offset = 0
    var out = file
    for ((range, replacement) in renames.sortedBy { it.first.startOffset }) {
        out = out.replaceRange(range.startOffset + offset until range.endOffset + offset, replacement)
        offset += replacement.length - range.length
    }
    KotlinParser(TypeContext.project).parse(TODO(), TODO()).elementType
    return out
}

private fun getRenamesUnresolved(
    new: PsiElement,
    contextNew: BindingContext,
    contextOld: BindingContext,
    put: MutableList<Pair<TextRange, String>>
) {
    if (new is KtElement) {
        val call = new.getCall(contextNew)
        val resolvedCall = call?.getResolvedCall(contextNew)
        if (resolvedCall != null && !resolvedCall.status.isSuccess) {
            val candidates = contextNew.getSliceContents(BindingContext.RESOLVED_CALL)
            val oldCalls = contextOld.getSliceContents(BindingContext.RESOLVED_CALL)
            for ((oldC, oldR) in oldCalls) {
                if (oldC.callElement.text == call.callElement.text) {
                    log.info { "Name match: ${call.callElement.text}" }
                    for ((newC, newR) in candidates) {
                        if (newR.call.callType == oldC.callType && newR.candidateDescriptor == oldR.candidateDescriptor) {
                            log.info { "Call type match: ${newR.call.callType}" }
                            val named = new.findDescendantOfType<KtSimpleNameExpression>()
                            val newName = newC.callElement.text
                            if (named != null && newName != null) {
                                put += named.textRange to newName
                            } else {
                                log.error { "Can't find what to rename $new -> $newName" }
                            }
                            return
                        }
                    }
                } else {
                    log.info { "Not matched ${oldC.callElement.text} == ${call.callElement.text}" }
                }
            }
        }
    }
    for (i in new.children.indices) {
        getRenamesUnresolved(new.children[i], contextNew, contextOld, put)
    }
}
