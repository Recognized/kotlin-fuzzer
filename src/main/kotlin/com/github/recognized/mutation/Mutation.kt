package com.github.recognized.mutation

import com.github.recognized.dataset.Sample
import com.github.recognized.kodein
import com.github.recognized.random.Chooser
import com.github.recognized.random.FineTunedSubtreeChooser
import com.github.recognized.random.SimpleSubtreeChooser
import com.github.recognized.runtime.logger
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiNamedElement
import org.jetbrains.kotlin.psi.KtElement
import org.jetbrains.kotlin.psi.psiUtil.findDescendantOfType
import org.jetbrains.kotlin.resolve.BindingContext
import org.jetbrains.kotlin.resolve.calls.callUtil.getCall
import org.jetbrains.kotlin.resolve.calls.callUtil.getResolvedCall
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
    old: PsiElement,
    contextNew: BindingContext,
    contextOld: BindingContext
): String {
    val renames = mutableListOf<Pair<TextRange, String>>()
    getRenamesUnresolved(new, old, contextNew, contextOld, renames)
    var offset = 0
    var out = file
    for ((range, replacement) in renames.sortedBy { it.first.startOffset }) {
        out = out.replaceRange(range.startOffset + offset until range.endOffset + offset, replacement)
        offset += replacement.length - range.length
    }
    return out
}

private fun getRenamesUnresolved(
    new: PsiElement,
    old: PsiElement,
    contextNew: BindingContext,
    contextOld: BindingContext,
    put: MutableList<Pair<TextRange, String>>
) {
    if (new is KtElement && old is KtElement) {
        val call = new.getCall(contextNew)
        val resolvedCall = call?.getResolvedCall(contextNew)
        val oldCall = old.getCall(contextOld)
        val oldResolvedCall = oldCall?.getResolvedCall(contextOld)
        if (resolvedCall != null && oldResolvedCall != null && !resolvedCall.status.isSuccess && oldResolvedCall.status.isSuccess) {
            val candidates = contextNew.getSliceContents(BindingContext.RESOLVED_CALL)
            for ((c, r) in candidates) {
                if (r.call.callType == call.callType && r.candidateDescriptor == resolvedCall.candidateDescriptor) {
                    val named = new.findDescendantOfType<PsiNamedElement>()
                    val newName = c.callElement.name
                    if (named != null && newName != null) {
                        put += named.textRange to newName
                    } else {
                        log.error { "Can't find what to rename $new -> $newName" }
                    }
                    return
                }
            }
        }
    }
    if (new.children.size != old.children.size) {
        log.info { "Mismatched trees ${old.text} -> ${new.text}" }
    }
    for (i in new.children.indices) {
        getRenamesUnresolved(new.children[i], old.children[i], contextNew, contextOld, put)
    }
}
