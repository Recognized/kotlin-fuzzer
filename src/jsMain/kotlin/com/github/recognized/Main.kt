package com.github.recognized

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import react.buildElement
import react.dom.render
import com.github.recognized.view.ApplicationComponent
import kotlin.browser.document
import kotlin.coroutines.CoroutineContext

val server = "http://localhost:8081"

private class Application : CoroutineScope {
    override val coroutineContext: CoroutineContext = Job()

    fun start() {
        document.getElementById("react-app")?.let {
            render(buildElement {
                child(ApplicationComponent::class) {
                }
            }, it)
        }
    }
}

fun main() {
    GlobalStyles.inject()

    Application().start()
}