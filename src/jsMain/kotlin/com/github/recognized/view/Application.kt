package com.github.recognized.view

import Fuzzer
import com.github.recognized.App
import com.github.recognized.horizontal
import com.github.recognized.service.Snippet
import com.github.recognized.service.SortOrder
import com.github.recognized.service.State
import com.github.recognized.service.Statistics
import com.github.recognized.spring
import com.github.recognized.vertical
import contrib.ringui.header.ringHeader
import contrib.ringui.ringButton
import kotlinx.coroutines.*
import kotlinx.css.*
import kotlinx.css.properties.LineHeight
import react.RBuilder
import react.RComponent
import react.RProps
import react.RState
import react.dom.*
import styled.*
import kotlin.coroutines.CoroutineContext

object ApplicationStyles : StyleSheet("ApplicationStyles", isStatic = true)

data class ApplicationState(
    val stat: Statistics,
    val snippets: List<Snippet>,
    val order: SortOrder = SortOrder.Score,
    val showOnlyMutated: Boolean = false
)

class ApplicationProps : RProps

class Timestamp(val value: Int) : RState

class ApplicationComponent : RComponent<ApplicationProps, Timestamp>(), CoroutineScope {
    init {
        state = Timestamp(0)
    }

    private var st: ApplicationState = ApplicationState(Statistics(0, State.Stop, 0, 0.0, "Idle"), emptyList())

    private fun update(action: () -> Unit) {
        setState(transformState = {
            action()
            Timestamp(it.value + 1)
        })
    }

    private fun updateAndReload(action: () -> Unit) {
        update {
            action()
            launch {
                reloadList()
            }
        }
    }

    override fun componentDidMount() {
        updateLoop()
        genLoop()
    }

    override fun componentWillUnmount() {
        coroutineContext.cancel()
    }

    override val coroutineContext: CoroutineContext = Job()

    private var sampleSizes = 20

    private fun updateLoop() {
        loop(1_000L) {
            val statNew = App.client.Fuzzer.stat()
            update {
                st = st.copy(stat = statNew)
            }
        }
    }

    private suspend fun reloadList() {
        val snippets = App.client.Fuzzer.generation(0, sampleSizes, st.order, st.showOnlyMutated)
        update {
            st = st.copy(snippets = snippets)
        }
    }

    private fun loop(delay: Long, action: suspend () -> Unit) {
        launch {
            while (true) {
                try {
                    action()
                } catch (ex: Throwable) {
                    console.error(ex)
                    delay(delay)
                }
                delay(delay)
            }
        }
    }

    private fun genLoop() {
        loop(10_000L) {
            reloadList()
        }
    }

    override fun RBuilder.render() {
        ringHeader {
            horizontal {
                css {
                    flex(1.0)
                    padding(horizontal = 16.px)
                    lineHeight = LineHeight("14px")
                    alignItems = Align.center
                    justifyContent = JustifyContent.center
                }
                h3 {
                    +"Kotlin fuzzer"
                }

                spring()

                ringButton {
                    attrs {
                        onMouseDown = {
                            launch {
                                reloadList()
                            }
                        }
                        primary = true
                    }

                    +"Refresh"
                }

                gap(8.px)

                ringButton {
                    attrs {
                        onMouseDown = {
                            updateAndReload {
                                st = st.copy(showOnlyMutated = !st.showOnlyMutated)
                            }
                        }
                    }

                    if (st.showOnlyMutated) {
                        +"Only mutated"
                    } else {
                        +"All samples"
                    }
                }

                gap(8.px)

                if (st.stat.run != State.Stop) {
                    ringButton {
                        attrs {
                            onMouseDown = {
                                launch {
                                    App.client.Fuzzer.stop()
                                }
                            }
                            danger = true
                        }

                        +"Stop"
                    }
                }

                gap(8.px)

                ringButton {
                    attrs {
                        onMouseDown = {
                            launch {
                                val fz = App.client.Fuzzer
                                when (st.stat.run) {
                                    State.Start -> fz.pause()
                                    State.Pause, State.Stop -> fz.start()
                                }
                            }
                        }
                    }

                    +when (st.stat.run) {
                        State.Pause -> "Unpause"
                        State.Stop -> "Start"
                        State.Start -> "Pause"
                    }
                }

                gap(8.px)

                table {
                    thead {
                        tr {
                            td {
                                +"Uptime"
                            }
                            td {
                                +"State"
                            }
                            td {
                                +"Compile rate"
                            }
                        }
                    }
                    tbody {
                        tr {
                            val uptime = st.stat.uptime
                            td {
                                +"${(uptime / 60 / 60).twoDigit()}:${((uptime / 60) % 60).twoDigit()}:${(uptime % 60).twoDigit()}"
                            }
                            td {
                                +st.stat.state
                            }
                            td {
                                +"${(st.stat.compileSuccessRate * 100).toInt()}%"
                            }
                        }
                    }
                }
            }
        }

        vertical {
            css {
                padding(16.px)
            }

            styledTable {
                css {
                    borderCollapse = BorderCollapse.collapse
                }
                thead {
                    tr {
                        td {
                            orderBy("Snippet", SortOrder.Name)
                        }
                        td {
                            orderBy("Score", SortOrder.Score)
                        }
                        td {
                            orderBy("Analyze, ms", SortOrder.Analyze)
                        }
                        td {
                            orderBy("Generate, ms", SortOrder.Generate)
                        }
                        td {
                            orderBy("Psi count", SortOrder.PsiElement)
                        }
                        td {
                            orderBy("Text length", SortOrder.Symbols)
                        }
                    }
                }
                tbody {
                    st.snippets.forEach {
                        row(it)
                    }
                }
            }

            ringButton {
                attrs {
                    onMouseDown = {
                        updateAndReload {
                            sampleSizes += 20
                        }
                    }
                    text = true
                }

                +"Load more"
            }
        }
    }

    private fun RBuilder.orderBy(name: String, order: SortOrder) {
        ringButton {
            attrs {
                onMouseDown = {
                    updateAndReload {
                        st = st.copy(order = order)
                    }
                }
                active = order != st.order
            }

            +name
        }
    }

    private fun RBuilder.row(sample: Snippet) {
        styledTr {
            css {
                padding(top = 4.px, bottom = 4.px)
                borderTop = "1px solid #e0dada"
            }
            key = sample.id
            td {
                a("/code?id=${sample.id}") {
                    +sample.id
                }
            }
            td {
                +sample.value.toString()
            }
            td {
                +sample.metrics.analyze.toString()
            }
            td {
                +sample.metrics.generate.toString()
            }
            td {
                +sample.metrics.psiElements.toString()
            }
            td {
                +sample.metrics.symbols.toString()
            }
        }
    }
}

fun RBuilder.gap(size: LinearDimension) {
    styledDiv {
        css {
            display = Display.flex
            flexBasis = FlexBasis("$size")
            grow(Grow.NONE)
        }
    }
}

fun Number.twoDigit(): String {
    return toString().let { if (it.length == 1) "0$it" else it }
}